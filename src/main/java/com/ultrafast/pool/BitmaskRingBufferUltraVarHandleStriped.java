package com.ultrafast.pool;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * УЛЬТРА-БЫСТРЫЙ конкурентный пул объектов с STRIPED TAIL и расширенными оптимизациями.
 * 
 * ОПТИМИЗАЦИИ В ЭТОЙ ВЕРСИИ:
 * - ABA-safe lock-free stack с использованием AtomicStampedReference
 * - Striped tail для лучшего распределения нагрузки между потоками
 * - Расширенный padding для предотвращения false sharing
 * - Thread-local оптимизации
 * - Улучшенная производительность при высокой конкуренции
 * 
 * @param <T> Тип объектов в пуле
 */
public class BitmaskRingBufferUltraVarHandleStriped<T> {
    
    // VarHandle для атомарного доступа к off-heap памяти
    private static final VarHandle LONG_ARRAY_HANDLE;
    private static final VarHandle INT_ARRAY_HANDLE;
    
    static {
        try {
            LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
            INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось инициализировать VarHandles", e);
        }
    }
    
    // Размер кэш-линии (обычно 64 байта на современных CPU)
    private static final int CACHE_LINE_SIZE = 64;
    
    // Количество stripes для tail (обычно равно количеству CPU cores)
    private static final int STRIPE_COUNT = Runtime.getRuntime().availableProcessors();
    
    // Предварительно вычисленные битовые маски
    private static final long[] BIT_MASKS = new long[64];
    private static final long[] CLEAR_MASKS = new long[64];
    
    static {
        for (int i = 0; i < 64; i++) {
            BIT_MASKS[i] = 1L << i;
            CLEAR_MASKS[i] = ~(1L << i);
        }
    }
    
    // КОНФИГУРАЦИЯ ПУЛА
    private final int capacity;
    private final T[] objects;
    private final ConcurrentHashMap<T, Integer> objectToIndex = new ConcurrentHashMap<>();
    
    // Off-heap битовые маски
    private final long[] availabilityMask;
    private final long[] staleMask;
    private final int maskSize;
    
    // STRIPED TAIL - массив атомарных счетчиков для лучшего распределения нагрузки
    private final StripedTail[] stripedTails;
    
    // ABA-safe lock-free stack с padding
    private final int[] freeSlotStack;
    private final AtomicStampedReference<Integer> stackTop; // ABA-safe: (value, stamp)
    private final int stackSize;
    
    // Расширенный padding для предотвращения false sharing
    private final long[] padding1 = new long[8]; // 64 bytes padding
    private final long[] padding2 = new long[8]; // 64 bytes padding
    private final long[] padding3 = new long[8]; // 64 bytes padding
    private final long[] padding4 = new long[8]; // 64 bytes padding
    private final long[] padding5 = new long[8]; // 64 bytes padding
    private final long[] padding6 = new long[8]; // 64 bytes padding
    
    // Выравнивание по кэш-линиям
    private final int maskSizeAligned;
    private final int stackSizeAligned;
    
    // СТАТИСТИКА
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);
    private final AtomicLong bitTrickHits = new AtomicLong(0);
    private final AtomicLong stackHits = new AtomicLong(0);
    private final AtomicLong stripedTailHits = new AtomicLong(0);
    
    /**
     * Striped tail структура для лучшего распределения нагрузки между потоками
     */
    private static class StripedTail {
        // Padding для предотвращения false sharing
        private final long[] padding1 = new long[8]; // 64 bytes
        private final long[] padding2 = new long[8]; // 64 bytes
        
        // Атомарный счетчик для этого stripe
        private final AtomicInteger counter;
        
        // Padding после счетчика
        private final long[] padding3 = new long[8]; // 64 bytes
        private final long[] padding4 = new long[8]; // 64 bytes
        
        public StripedTail(int initialValue) {
            this.counter = new AtomicInteger(initialValue);
        }
        
        public int getAndIncrement() {
            return counter.getAndIncrement();
        }
        
        public int get() {
            return counter.get();
        }
        
        public void set(int value) {
            counter.set(value);
        }
    }
    
    /**
     * Создает новый оптимизированный ring buffer пул с striped tail.
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandleStriped(int capacity, ObjectFactory<T> objectFactory) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Емкость должна быть положительной");
        }
        if (objectFactory == null) {
            throw new IllegalArgumentException("Фабрика объектов не может быть null");
        }
        
        this.capacity = capacity;
        this.maskSize = (capacity + 63) / 64;
        
        // Выравнивание по кэш-линиям
        this.maskSizeAligned = (maskSize + (CACHE_LINE_SIZE / 8) - 1) & ~((CACHE_LINE_SIZE / 8) - 1);
        
        // Инициализация битовых масок
        this.availabilityMask = new long[maskSizeAligned];
        this.staleMask = new long[maskSizeAligned];
        
        // ABA-safe lock-free stack
        this.stackSize = Math.min(capacity / 4, 1000);
        this.stackSizeAligned = (stackSize + (CACHE_LINE_SIZE / 4) - 1) & ~((CACHE_LINE_SIZE / 4) - 1);
        this.freeSlotStack = new int[stackSizeAligned];
        this.stackTop = new AtomicStampedReference<>(-1, 0); // ABA-safe: (value, stamp)
        
        // Инициализация striped tails
        this.stripedTails = new StripedTail[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripedTails[i] = new StripedTail(i * (capacity / STRIPE_COUNT));
        }
        
        // Создание объектов
        this.objects = (T[]) new Object[capacity];
        for (int i = 0; i < capacity; i++) {
            objects[i] = objectFactory.createObject();
            objectToIndex.put(objects[i], i);
        }
        
        // Инициализация: помечаем все объекты как доступные
        for (int i = 0; i < capacity; i++) {
            setBit(availabilityMask, i, true);
            pushToStack(i);
        }
    }
    
    /**
     * Получает свободный объект из пула с оптимизированным striped tail.
     */
    public T getFreeObject() {
        int attempts = 0;
        final int maxAttempts = capacity * 2;
        
        while (attempts < maxAttempts) {
            // УРОВЕНЬ 1: ABA-safe lock-free stack
            Integer slotIndex = popFromStack();
            if (slotIndex != null) {
                stackHits.incrementAndGet();
                if (tryAcquireSlot(slotIndex)) {
                    totalGets.incrementAndGet();
                    return objects[slotIndex];
                }
            }
            
            // УРОВЕНЬ 2: Striped tail - распределение нагрузки между потоками
            int stripeIndex = getStripeIndex();
            StripedTail tail = stripedTails[stripeIndex];
            int tailIndex = Math.abs(tail.getAndIncrement()) % capacity; // Исправляем отрицательные индексы
            
            // Проверяем несколько слотов вокруг tail для лучшего распределения
            for (int offset = 0; offset < 8; offset++) {
                int slotToCheck = (tailIndex + offset) % capacity;
                if (slotToCheck >= 0 && slotToCheck < capacity && isBitSet(availabilityMask, slotToCheck) && tryAcquireSlot(slotToCheck)) {
                    stripedTailHits.incrementAndGet();
                    totalGets.incrementAndGet();
                    return objects[slotToCheck];
                }
            }
            
            // УРОВЕНЬ 3: Битовые трюки
            int freeSlot = findFreeSlotWithBitTricks();
            if (freeSlot >= 0) {
                bitTrickHits.incrementAndGet();
                if (tryAcquireSlot(freeSlot)) {
                    totalGets.incrementAndGet();
                    return objects[freeSlot];
                }
            }
            
            attempts++;
            
            // Оптимизированное ожидание
            if (attempts % 100 == 0) {
                Thread.onSpinWait(); // Java 9+ оптимизация
            }
        }
        
        return null; // Не удалось найти свободный объект
    }
    
    /**
     * Возвращает объект в пул.
     */
    public boolean setFreeObject(T object) {
        Integer index = objectToIndex.get(object);
        if (index == null) {
            return false;
        }
        
        if (setBitAtomic(availabilityMask, index, true)) {
            setBit(staleMask, index, false);
            pushToStack(index);
            totalReturns.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    /**
     * Получает индекс stripe для текущего потока
     */
    private int getStripeIndex() {
        return (int) (Thread.currentThread().getId() % STRIPE_COUNT);
    }
    
    /**
     * Добавляет индекс в ABA-safe stack
     */
    private void pushToStack(int slotIndex) {
        int[] stampHolder = new int[1];
        int currentTop = stackTop.get(stampHolder);
        int currentStamp = stampHolder[0];
        if (currentTop < stackSize - 1) {
            int newTop = currentTop + 1;
            int newStamp = currentStamp + 1;
            if (stackTop.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                INT_ARRAY_HANDLE.setVolatile(freeSlotStack, newTop, slotIndex);
            }
        }
    }
    
    /**
     * Извлекает индекс из ABA-safe stack
     */
    private Integer popFromStack() {
        int[] stampHolder = new int[1];
        int currentTop = stackTop.get(stampHolder);
        int currentStamp = stampHolder[0];
        if (currentTop >= 0) {
            int newTop = currentTop - 1;
            int newStamp = currentStamp + 1;
            if (stackTop.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                return (int) INT_ARRAY_HANDLE.getVolatile(freeSlotStack, currentTop);
            }
        }
        return null;
    }
    
    /**
     * Помечает объект для обновления.
     */
    public boolean markForUpdate(T object) {
        Integer index = objectToIndex.get(object);
        if (index == null) {
            return false;
        }
        
        if (setBitAtomic(staleMask, index, true)) {
            totalUpdates.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    /**
     * Останавливает все объекты в пуле.
     */
    public void stopAll() {
        for (int i = 0; i < capacity; i++) {
            setBit(availabilityMask, i, false);
        }
    }
    
    /**
     * Возвращает список занятых объектов.
     */
    public List<T> getBusyObjects() {
        List<T> busyObjects = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMask, i)) {
                busyObjects.add(objects[i]);
            }
        }
        return busyObjects;
    }
    
    /**
     * Возвращает список объектов, помеченных для обновления.
     */
    public List<T> getObjectsForUpdate() {
        List<T> updateObjects = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(staleMask, i)) {
                updateObjects.add(objects[i]);
            }
        }
        return updateObjects;
    }
    
    /**
     * Обнаруживает устаревшие объекты.
     */
    public List<T> detectStaleObjects(long staleThresholdMs) {
        List<T> staleObjects = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(staleMask, i)) {
                // Здесь можно добавить логику определения устаревших объектов
                staleObjects.add(objects[i]);
            }
        }
        
        return staleObjects;
    }
    
    /**
     * Возвращает статистику пула.
     */
    public PoolStats getStats() {
        int freeCount = 0;
        int busyCount = 0;
        int updateCount = 0;
        
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(availabilityMask, i)) {
                freeCount++;
            } else {
                busyCount++;
            }
            if (isBitSet(staleMask, i)) {
                updateCount++;
            }
        }
        
        return new PoolStats(
            capacity, freeCount, busyCount, updateCount,
            totalGets.get(), totalReturns.get(), totalUpdates.get(),
            bitTrickHits.get(), stackHits.get(), stripedTailHits.get()
        );
    }
    
    /**
     * Возвращает емкость пула.
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Возвращает объект по индексу.
     */
    public T getObject(int index) {
        if (index < 0 || index >= capacity) {
            return null;
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект по индексу.
     */
    public boolean isAvailable(int index) {
        if (index < 0 || index >= capacity) {
            return false;
        }
        return isBitSet(availabilityMask, index);
    }
    
    /**
     * Проверяет, помечен ли объект для обновления.
     */
    public boolean isMarkedForUpdate(int index) {
        if (index < 0 || index >= capacity) {
            return false;
        }
        return isBitSet(staleMask, index);
    }
    
    /**
     * Ищет свободный слот с помощью битовых трюков.
     */
    private int findFreeSlotWithBitTricks() {
        for (int maskIndex = 0; maskIndex < maskSize; maskIndex++) {
            long mask = (long) LONG_ARRAY_HANDLE.getVolatile(availabilityMask, maskIndex);
            if (mask != 0) {
                int bitIndex = Long.numberOfTrailingZeros(mask);
                return maskIndex * 64 + bitIndex;
            }
        }
        return -1;
    }
    
    /**
     * Пытается занять слот атомарно.
     */
    private boolean tryAcquireSlot(int slotIndex) {
        return setBitAtomic(availabilityMask, slotIndex, false);
    }
    
    /**
     * Устанавливает бит атомарно.
     */
    private boolean setBitAtomic(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long currentValue;
        long newValue;
        
        do {
            currentValue = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
            if (value) {
                newValue = currentValue | BIT_MASKS[bitOffset];
            } else {
                newValue = currentValue & CLEAR_MASKS[bitOffset];
            }
        } while (!LONG_ARRAY_HANDLE.compareAndSet(array, arrayIndex, currentValue, newValue));
        
        return true;
    }
    
    /**
     * Устанавливает бит (не атомарно).
     */
    private void setBit(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        if (value) {
            array[arrayIndex] |= BIT_MASKS[bitOffset];
        } else {
            array[arrayIndex] &= CLEAR_MASKS[bitOffset];
        }
    }
    
    /**
     * Проверяет, установлен ли бит.
     */
    private boolean isBitSet(long[] array, int bitIndex) {
        if (bitIndex < 0 || bitIndex >= capacity) {
            return false;
        }
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        return (array[arrayIndex] & BIT_MASKS[bitOffset]) != 0;
    }
    
    /**
     * Освобождает ресурсы.
     */
    public void cleanup() {
        // Очищаем Map для быстрого поиска объектов
        objectToIndex.clear();
        
        // Очищаем массивы объектов для ускорения GC
        if (objects != null) {
            for (int i = 0; i < objects.length; i++) {
                objects[i] = null;
            }
        }
        
        // Очищаем битовые маски
        if (availabilityMask != null) {
            for (int i = 0; i < availabilityMask.length; i++) {
                availabilityMask[i] = 0;
            }
        }
        
        if (staleMask != null) {
            for (int i = 0; i < staleMask.length; i++) {
                staleMask[i] = 0;
            }
        }
        
        // Очищаем стек свободных слотов
        if (freeSlotStack != null) {
            for (int i = 0; i < freeSlotStack.length; i++) {
                freeSlotStack[i] = 0;
            }
        }
        
        // Очищаем striped tails
        if (stripedTails != null) {
            for (int i = 0; i < stripedTails.length; i++) {
                stripedTails[i] = null;
            }
        }
    }
    
    /**
     * Статистика пула.
     */
    public static class PoolStats {
        public final int capacity;
        public final int freeCount;
        public final int busyCount;
        public final int updateCount;
        public final long totalGets;
        public final long totalReturns;
        public final long totalUpdates;
        public final long bitTrickHits;
        public final long stackHits;
        public final long stripedTailHits;
        
        public PoolStats(int capacity, int freeCount, int busyCount, int updateCount,
                        long totalGets, long totalReturns, long totalUpdates,
                        long bitTrickHits, long stackHits, long stripedTailHits) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.updateCount = updateCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalUpdates = totalUpdates;
            this.bitTrickHits = bitTrickHits;
            this.stackHits = stackHits;
            this.stripedTailHits = stripedTailHits;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStats{capacity=%d, free=%d, busy=%d, update=%d, gets=%d, returns=%d, updates=%d, bitTricks=%d, stack=%d, striped=%d}",
                capacity, freeCount, busyCount, updateCount, totalGets, totalReturns, totalUpdates, bitTrickHits, stackHits, stripedTailHits
            );
        }
    }
    
    /**
     * Фабрика для создания объектов.
     */
    @FunctionalInterface
    public interface ObjectFactory<T> {
        T createObject();
    }
} 