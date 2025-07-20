package com.ultrafast.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Классическая реализация пула объектов с использованием стандартных Java коллекций
 * 
 * Основные компоненты:
 * - ConcurrentLinkedQueue<T> - основная очередь для хранения доступных объектов
 * - ConcurrentHashMap<T, ObjectMetadata> - трекинг выданных объектов
 * - AtomicLong - счетчик статистики
 * - AtomicInteger - счетчик активных объектов
 * 
 * Эта реализация служит для сравнения производительности с оптимизированными
 * BitmaskRingBuffer версиями и демонстрирует разницу между стандартными
 * Java коллекциями и специализированными решениями.
 */
public class BitmaskRingBufferClassic<T> implements ObjectPool<T> {
    
    /**
     * Метаданные объекта для трекинга
     */
    private static class ObjectMetadata {
        final long acquireTime;
        final long threadId;
        final boolean isCreated; // Флаг, указывающий что объект был создан динамически
        
        ObjectMetadata(long acquireTime, long threadId, boolean isCreated) {
            this.acquireTime = acquireTime;
            this.threadId = threadId;
            this.isCreated = isCreated;
        }
    }
    
    // Основная очередь для хранения доступных объектов
    private final ConcurrentLinkedQueue<T> availableQueue;
    
    // Трекинг выданных объектов
    private final ConcurrentHashMap<T, ObjectMetadata> borrowedObjects;
    
    // Фабрика для создания новых объектов
    private final Supplier<T> objectFactory;
    
    // Максимальный размер пула
    private final int maxPoolSize;
    
    // Счетчики статистики
    private final AtomicLong totalAcquires = new AtomicLong(0);
    private final AtomicLong totalReleases = new AtomicLong(0);
    private final AtomicLong totalCreates = new AtomicLong(0);
    private final AtomicLong totalWaits = new AtomicLong(0);
    private final AtomicInteger activeObjects = new AtomicInteger(0);
    
    // Таймаут для ожидания объекта (в наносекундах)
    private final long acquireTimeoutNanos;
    
    /**
     * Конструктор классического пула объектов
     * 
     * @param objectFactory фабрика для создания новых объектов
     * @param initialSize начальный размер пула
     * @param maxPoolSize максимальный размер пула
     * @param acquireTimeoutMs таймаут ожидания объекта в миллисекундах
     */
    public BitmaskRingBufferClassic(Supplier<T> objectFactory, int initialSize, int maxPoolSize, long acquireTimeoutMs) {
        this.objectFactory = objectFactory;
        this.maxPoolSize = maxPoolSize;
        this.acquireTimeoutNanos = acquireTimeoutMs * 1_000_000L;
        
        // Инициализация коллекций
        this.availableQueue = new ConcurrentLinkedQueue<>();
        this.borrowedObjects = new ConcurrentHashMap<>();
        
        // Предварительное создание объектов
        for (int i = 0; i < initialSize; i++) {
            T obj = objectFactory.get();
            availableQueue.offer(obj);
            totalCreates.incrementAndGet();
        }
    }
    
    /**
     * Получение объекта из пула
     * 
     * Алгоритм:
     * 1. Пытаемся получить объект из очереди
     * 2. Если очередь пуста и не достигнут лимит - создаем новый
     * 3. Если достигнут лимит - ждем освобождения объекта
     * 4. Трекируем выданный объект
     * 
     * @return объект из пула или null при таймауте
     */
    @Override
    public T acquire() {
        long startTime = System.nanoTime();
        
        while (true) {
            // Пытаемся получить объект из очереди
            T obj = availableQueue.poll();
            
            if (obj != null) {
                // Объект найден, трекируем его атомарно (false = не созданный объект)
                if (trackBorrowedObjectAtomic(obj, startTime, false)) {
                    totalAcquires.incrementAndGet();
                    return obj;
                } else {
                    // Объект уже трекируется, возвращаем его в очередь и пробуем снова
                    availableQueue.offer(obj);
                    continue;
                }
            }
            
            // Очередь пуста, проверяем возможность создания нового объекта
            int currentActive = activeObjects.get();
            if (currentActive < maxPoolSize) {
                // Пытаемся атомарно увеличить счетчик активных объектов
                if (activeObjects.compareAndSet(currentActive, currentActive + 1)) {
                    // Создаем новый объект
                    obj = objectFactory.get();
                    totalCreates.incrementAndGet();
                    
                    // Трекируем новый объект атомарно (true = созданный объект)
                    if (trackBorrowedObjectAtomic(obj, startTime, true)) {
                        totalAcquires.incrementAndGet();
                        return obj;
                    } else {
                        // Неожиданная ошибка - объект уже трекируется
                        activeObjects.decrementAndGet();
                        throw new IllegalStateException("Newly created object already tracked: " + obj);
                    }
                }
                // CAS не удался, пробуем снова
                continue;
            }
            
            // Достигнут лимит, проверяем таймаут
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= acquireTimeoutNanos) {
                totalWaits.incrementAndGet();
                return null; // Таймаут
            }
            
            // Ждем немного перед следующей попыткой
            try {
                Thread.sleep(0, 1000); // 1 микросекунда
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
    
    /**
     * Возврат объекта в пул
     * 
     * Алгоритм:
     * 1. Атомарно удаляем объект из трекинга
     * 2. Возвращаем в очередь доступных объектов
     * 3. Обновляем статистику
     * 
     * @param obj объект для возврата
     */
    @Override
    public void release(T obj) {
        if (obj == null) {
            return;
        }
        
        // Атомарно удаляем из трекинга
        ObjectMetadata metadata = borrowedObjects.remove(obj);
        if (metadata != null) {
            // Возвращаем в очередь
            availableQueue.offer(obj);
            totalReleases.incrementAndGet();
            
            // Уменьшаем счетчик активных объектов, если это был созданный объект
            if (metadata.isCreated) {
                activeObjects.decrementAndGet();
            }
        }
    }
    
    /**
     * Атомарный трекинг выданного объекта
     * 
     * @param obj объект для трекинга
     * @param acquireTime время получения
     * @param isCreated флаг, указывающий что объект был создан динамически
     * @return true если объект успешно добавлен в трекинг, false если уже существует
     */
    private boolean trackBorrowedObjectAtomic(T obj, long acquireTime, boolean isCreated) {
        long threadId = Thread.currentThread().getId();
        ObjectMetadata metadata = new ObjectMetadata(acquireTime, threadId, isCreated);
        
        // Пытаемся добавить объект в трекинг атомарно
        ObjectMetadata existing = borrowedObjects.putIfAbsent(obj, metadata);
        return existing == null; // true если объект был добавлен, false если уже существовал
    }
    
    /**
     * Трекинг выданного объекта (устаревший метод, оставлен для совместимости)
     * 
     * @param obj объект для трекинга
     * @param acquireTime время получения
     * @deprecated Используйте trackBorrowedObjectAtomic вместо этого метода
     */
    @Deprecated
    private void trackBorrowedObject(T obj, long acquireTime) {
        if (!trackBorrowedObjectAtomic(obj, acquireTime, false)) {
            throw new IllegalStateException("Object already tracked: " + obj);
        }
    }
    
    /**
     * Получение статистики пула
     * 
     * @return статистика пула
     */
    @Override
    public ObjectPool.PoolStatistics getStatistics() {
        return new ObjectPool.PoolStatistics(
            maxPoolSize,
            availableQueue.size(),
            borrowedObjects.size(),
            totalAcquires.get(),
            totalReleases.get(),
            totalCreates.get(),
            totalWaits.get(),
            activeObjects.get()
        );
    }
    
    /**
     * Очистка ресурсов пула
     * 
     * В классической реализации просто очищаем коллекции
     */
    @Override
    public void close() {
        availableQueue.clear();
        borrowedObjects.clear();
        activeObjects.set(0);
    }
    
    /**
     * Получение информации о пуле
     * 
     * @return строка с информацией о пуле
     */
    @Override
    public String toString() {
        ObjectPool.PoolStatistics stats = getStatistics();
        return String.format(
            "BitmaskRingBufferClassic{maxSize=%d, available=%d, borrowed=%d, " +
            "acquires=%d, releases=%d, creates=%d, waits=%d, active=%d}",
            stats.maxPoolSize,
            stats.availableObjects,
            stats.borrowedObjects,
            stats.totalAcquires,
            stats.totalReleases,
            stats.totalCreates,
            stats.totalWaits,
            stats.activeObjects
        );
    }
} 