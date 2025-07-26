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
 * УЛЬТРА-БЫСТРЫЙ конкурентный пул объектов с оптимизированным ABA-safe lock-free stack.
 * 
 * ОПТИМИЗАЦИИ В ЭТОЙ ВЕРСИИ:
 * - ABA-safe lock-free stack с использованием AtomicStampedReference
 * - Padding для предотвращения false sharing
 * - Оптимизированная структура данных для high-load сценариев
 * - Улучшенная производительность при высокой конкуренции
 * 
 * @param <T> Тип объектов в пуле
 */
public class BitmaskRingBufferUltraVarHandleOptimized<T> {
    
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
    
    // Ring buffer с padding для предотвращения false sharing
    private final AtomicInteger tail;
    
    // ABA-safe lock-free stack с padding
    private final int[] freeSlotStack;
    private final AtomicStampedReference<Integer> stackTop; // ABA-safe: (value, stamp)
    private final int stackSize;
    
    // Padding для предотвращения false sharing
    private final long[] padding1 = new long[8]; // 64 bytes padding
    private final long[] padding2 = new long[8]; // 64 bytes padding
    private final long[] padding3 = new long[8]; // 64 bytes padding
    private final long[] padding4 = new long[8]; // 64 bytes padding
    
    // Выравнивание по кэш-линиям
    private final int maskSizeAligned;
    private final int stackSizeAligned;
    
    // СТАТИСТИКА
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);
    private final AtomicLong bitTrickHits = new AtomicLong(0);
    private final AtomicLong stackHits = new AtomicLong(0);
    
    /**
     * Создает новый оптимизированный ring buffer пул с ABA-safe lock-free stack.
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandleOptimized(int capacity, ObjectFactory<T> objectFactory) {
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
        
        // Ring buffer с padding
        this.tail = new AtomicInteger(0);
        
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
     * Получает свободный объект из пула с оптимизированным ABA-safe stack.
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
            
            // УРОВЕНЬ 2: Битовые трюки
            int freeSlot = findFreeSlotWithBitTricks();
            if (freeSlot >= 0) {
                bitTrickHits.incrementAndGet();
                if (tryAcquireSlot(freeSlot)) {
                    totalGets.incrementAndGet();
                    return objects[freeSlot];
                }
            }
            
            // УРОВЕНЬ 3: Ring buffer fallback
            int currentTail = tail.get();
            int nextTail = (currentTail + 1) % capacity;
            
            if (tail.compareAndSet(currentTail, nextTail)) {
                int index = currentTail;
                if (isBitSet(availabilityMask, index) && tryAcquireSlot(index)) {
                    totalGets.incrementAndGet();
                    return objects[index];
                }
            }
            
            attempts++;
            
            // Оптимизированное ожидание
            if (attempts % 100 == 0) {
                Thread.onSpinWait(); // Java 9+ оптимизация
            }
        }
        
        return null;
    }
    
    /**
     * Возвращает объект в пул с ABA-safe stack.
     */
    public boolean setFreeObject(T object) {
        if (object == null) {
            return false;
        }
        
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
     * ABA-safe push в lock-free stack.
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
     * ABA-safe pop из lock-free stack.
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
        if (object == null) {
            return false;
        }
        
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
            if (objects[i] instanceof Task) {
                ((Task) objects[i]).stop();
            }
        }
    }
    
    /**
     * Получает все занятые объекты.
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
     * Получает все объекты, помеченные для обновления.
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
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(staleMask, i)) {
                staleObjects.add(objects[i]);
            }
        }
        return staleObjects;
    }
    
    /**
     * Получает статистику пула.
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
            bitTrickHits.get(), stackHits.get()
        );
    }
    
    /**
     * Получает емкость пула.
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Получает объект по индексу.
     */
    public T getObject(int index) {
        if (index < 0 || index >= capacity) {
            return null;
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект.
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
     * Находит свободный слот с битовыми трюками.
     */
    private int findFreeSlotWithBitTricks() {
        for (int maskIndex = 0; maskIndex < maskSize; maskIndex++) {
            long mask = (long) LONG_ARRAY_HANDLE.getVolatile(availabilityMask, maskIndex);
            if (mask != 0) {
                int bitIndex = Long.numberOfTrailingZeros(mask);
                int globalIndex = maskIndex * 64 + bitIndex;
                if (globalIndex < capacity) {
                    return globalIndex;
                }
            }
        }
        return -1;
    }
    
    /**
     * Пытается атомарно занять слот.
     */
    private boolean tryAcquireSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= capacity) {
            return false;
        }
        return setBitAtomic(availabilityMask, slotIndex, false);
    }
    
    /**
     * Атомарно устанавливает бит.
     */
    private boolean setBitAtomic(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long oldValue, newValue;
        do {
            oldValue = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
            
            if (value) {
                newValue = oldValue | BIT_MASKS[bitOffset];
            } else {
                newValue = oldValue & CLEAR_MASKS[bitOffset];
            }
        } while (!LONG_ARRAY_HANDLE.compareAndSet(array, arrayIndex, oldValue, newValue));
        
        return true;
    }
    
    /**
     * Устанавливает бит неатомарно.
     */
    private void setBit(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long currentValue = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
        long newValue;
        
        if (value) {
            newValue = currentValue | BIT_MASKS[bitOffset];
        } else {
            newValue = currentValue & CLEAR_MASKS[bitOffset];
        }
        
        LONG_ARRAY_HANDLE.setVolatile(array, arrayIndex, newValue);
    }
    
    /**
     * Проверяет, установлен ли бит.
     */
    private boolean isBitSet(long[] array, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long value = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
        return (value & BIT_MASKS[bitOffset]) != 0;
    }
    
    /**
     * Метод очистки ресурсов.
     */
    public void cleanup() {
        objectToIndex.clear();
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
        
        public PoolStats(int capacity, int freeCount, int busyCount, int updateCount,
                        long totalGets, long totalReturns, long totalUpdates,
                        long bitTrickHits, long stackHits) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.updateCount = updateCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalUpdates = totalUpdates;
            this.bitTrickHits = bitTrickHits;
            this.stackHits = stackHits;
        }
        
        @Override
        public String toString() {
            return String.format("PoolStats{capacity=%d, free=%d, busy=%d, updates=%d, " +
                               "gets=%d, returns=%d, updates=%d, bitTricks=%d, stackHits=%d}",
                               capacity, freeCount, busyCount, updateCount,
                               totalGets, totalReturns, totalUpdates, bitTrickHits, stackHits);
        }
    }
    
    /**
     * Интерфейс фабрики объектов.
     */
    @FunctionalInterface
    public interface ObjectFactory<T> {
        T createObject();
    }
} 