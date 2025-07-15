package com.ultrafast.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;

/**
 * <b>BitmaskRingBuffer</b> — высокопроизводительный пул объектов для многопоточной среды.
 * Использует lock-free алгоритмы, битовые маски и кольцевой буфер для минимального потребления памяти
 * и максимальной скорости операций get/return.
 *
 * <p>Основные возможности:
 * <ul>
 *   <li>Мгновенное получение и возврат объектов (без блокировок)</li>
 *   <li>Контроль занятых и свободных объектов через битовые маски</li>
 *   <li>Отслеживание зависших объектов и статистика использования</li>
 *   <li>Поддержка CLI и внешнего мониторинга</li>
 * </ul>
 *
 * @param <T> Тип объектов в пуле (например, Task)
 */
public class BitmaskRingBuffer<T> {
    /** Размер буфера (степень 2 для оптимизации побитовых операций) */
    private final int capacity;
    /** Маска для быстрого вычисления индекса (capacity - 1) */
    private final int mask;
    /** Массив объектов пула (thread-safe) */
    private final AtomicReferenceArray<T> objects;
    /** Битовая маска занятости: 1 — занят, 0 — свободен */
    private final long[] availabilityMask;
    /** Битовая маска обновления: 1 — требует обновления */

    /** Атомарный head — позиция для выдачи объекта */
    private final AtomicLong head;
    /** Атомарный tail — позиция для возврата объекта */
    private final AtomicLong tail;
    /** Количество занятых объектов */
    private final AtomicLong occupiedCount;
    /** Время последнего использования для каждого объекта (мс) */
    private final long[] lastUsedTimes;
    /** Фабрика для создания новых объектов */
    private final Supplier<T> objectFactory;
    /** Статистика: всего get */
    private final AtomicLong totalGets;
    /** Статистика: всего return */
    private final AtomicLong totalReturns;
    /** Статистика: всего ожиданий (wait) */
    private final AtomicLong totalWaits;

    /**
     * Конструктор пула.
     * @param capacity Желаемая емкость (будет округлена до степени 2)
     * @param objectFactory Фабрика для создания объектов
     */
    public BitmaskRingBuffer(int capacity, Supplier<T> objectFactory) {
        this.capacity = nextPowerOf2(capacity); // округление до степени 2
        this.mask = this.capacity - 1;
        this.objectFactory = objectFactory;
        this.objects = new AtomicReferenceArray<>(this.capacity);
        this.availabilityMask = new long[(this.capacity + 63) / 64];

        this.lastUsedTimes = new long[this.capacity];
        this.head = new AtomicLong(0);
        this.tail = new AtomicLong(0);
        this.occupiedCount = new AtomicLong(0);
        this.totalGets = new AtomicLong(0);
        this.totalReturns = new AtomicLong(0);
        this.totalWaits = new AtomicLong(0);
        initializeObjects(); // предварительное создание объектов
    }

    /**
     * Получить свободный объект из пула (без ожидания).
     * @return свободный объект или null, если все заняты
     */
    public T getFreeObject() {
        return getFreeObject(0);
    }

    /**
     * Получить свободный объект с возможным ожиданием.
     * @param maxWaitNanos максимальное время ожидания (нс), 0 — не ждать
     * @return свободный объект или null, если не удалось получить
     */
    public T getFreeObject(long maxWaitNanos) {
        long startTime = System.nanoTime();
        long attempts = 0;
        while (true) {
            long currentHead = head.get();
            long currentTail = tail.get();
            // Если все объекты заняты
            if (currentHead - currentTail >= capacity) {
                if (maxWaitNanos > 0) {
                    long elapsed = System.nanoTime() - startTime;
                    if (elapsed >= maxWaitNanos) {
                        return null; // Время ожидания истекло
                    }
                    LockSupport.parkNanos(100); // короткая пауза
                    totalWaits.incrementAndGet();
                    attempts++;
                    if (attempts > 1000) {
                        LockSupport.parkNanos(1000); // адаптивная пауза
                    }
                    continue;
                } else {
                    return null; // Нет свободных объектов
                }
            }
            // CAS — атомарно увеличиваем head (lock-free)
            if (head.compareAndSet(currentHead, currentHead + 1)) {
                int index = (int) (currentHead & mask); // быстрый расчет индекса
                T object = objects.get(index);
                if (object == null) {
                    object = objectFactory.get();
                    objects.set(index, object);
                }
                setBit(availabilityMask, index, true); // помечаем как занятый
                lastUsedTimes[index] = System.currentTimeMillis();
                occupiedCount.incrementAndGet();
                totalGets.incrementAndGet();
                return object;
            }
        }
    }

    /**
     * Вернуть объект в пул.
     * @param object объект для возврата
     * @return true, если успешно возвращен
     */
    public boolean setFreeObject(T object) {
        if (object == null) {
            return false;
        }
        int index = findObjectIndex(object);
        if (index == -1) {
            return false; // объект не найден
        }
        setBit(availabilityMask, index, false); // помечаем как свободный
        occupiedCount.decrementAndGet();
        totalReturns.incrementAndGet();
        tail.incrementAndGet(); // освобождаем место
        return true;
    }

    /**
     * Получить список всех занятых объектов.
     * @return список занятых объектов
     */
    public List<T> getOccupiedObjects() {
        int occupied = occupiedCount.intValue();
        if (occupied == 0) {
            return new ArrayList<>();
        }
        List<T> result = new ArrayList<>(occupied);
        for (int i = 0; i < capacity; i++) {
            if (getBit(availabilityMask, i)) {
                T object = objects.get(i);
                if (object != null) {
                    result.add(object);
                }
            }
        }
        return result;
    }



    /**
     * Остановить все занятые объекты (например, при остановке сервера).
     */
    public void stopAllOccupied() {
        for (int i = 0; i < capacity; i++) {
            if (getBit(availabilityMask, i)) {
                T object = objects.get(i);
                if (object instanceof Task) {
                    ((Task) object).stop();
                }
            }
        }
    }

    /**
     * Найти зависшие объекты (выданные и не возвращенные за maxAgeMs миллисекунд).
     * @param maxAgeMs максимальный возраст объекта (мс)
     * @return список зависших объектов
     */
    public List<T> findStaleObjects(long maxAgeMs) {
        long currentTime = System.currentTimeMillis();
        List<T> result = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (getBit(availabilityMask, i) && 
                (currentTime - lastUsedTimes[i]) > maxAgeMs) {
                T object = objects.get(i);
                if (object != null) {
                    result.add(object);
                }
            }
        }
        return result;
    }

    /**
     * Получить статистику пула (емкость, количество занятых, операции и ожидания).
     */
    public PoolStatistics getStatistics() {
        return new PoolStatistics(
            capacity,
            occupiedCount.get(),
            totalGets.get(),
            totalReturns.get(),
            totalWaits.get()
        );
    }

    // ================= Вспомогательные методы =================

    /**
     * Предварительно создает объекты в пуле для ускорения доступа.
     */
    private void initializeObjects() {
        for (int i = 0; i < capacity; i++) {
            T object = objectFactory.get();
            objects.set(i, object);
        }
    }

    /**
     * Поиск индекса объекта в пуле (по ссылке).
     */
    private int findObjectIndex(T object) {
        for (int i = 0; i < capacity; i++) {
            if (objects.get(i) == object) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Установить или сбросить бит в маске.
     * @param mask массив long (битовая маска)
     * @param index индекс объекта
     * @param value true — установить бит, false — сбросить
     */
    private void setBit(long[] mask, int index, boolean value) {
        int arrayIndex = index / 64;
        int bitIndex = index % 64;
        long bit = 1L << bitIndex;
        if (value) {
            mask[arrayIndex] |= bit;
        } else {
            mask[arrayIndex] &= ~bit;
        }
    }

    /**
     * Проверить бит в маске.
     * @param mask массив long (битовая маска)
     * @param index индекс объекта
     * @return true — бит установлен, false — сброшен
     */
    private boolean getBit(long[] mask, int index) {
        int arrayIndex = index / 64;
        int bitIndex = index % 64;
        long bit = 1L << bitIndex;
        return (mask[arrayIndex] & bit) != 0;
    }

    /**
     * Округление до ближайшей степени 2 (для оптимизации кольцевого буфера).
     */
    private static int nextPowerOf2(int value) {
        int power = 1;
        while (power < value) {
            power <<= 1;
        }
        return power;
    }

    /**
     * Статистика пула: емкость, количество занятых, операции и ожидания.
     */
    public static class PoolStatistics {
        /** Емкость пула */
        public final int capacity;
        /** Количество занятых объектов */
        public final long occupiedCount;
        /** Всего get операций */
        public final long totalGets;
        /** Всего return операций */
        public final long totalReturns;
        /** Всего ожиданий (wait) */
        public final long totalWaits;

        public PoolStatistics(int capacity, long occupiedCount, 
                            long totalGets, long totalReturns, long totalWaits) {
            this.capacity = capacity;
            this.occupiedCount = occupiedCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalWaits = totalWaits;
        }

        @Override
        public String toString() {
            return String.format(
                "PoolStatistics{capacity=%d, occupied=%d, gets=%d, returns=%d, waits=%d}",
                capacity, occupiedCount, totalGets, totalReturns, totalWaits
            );
        }
    }
} 