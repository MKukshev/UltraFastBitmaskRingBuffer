package com.ultrafast.pool;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BitmaskRingBuffer с bit tricks и lock-free stack для свободных индексов
 * Комбинация: Bit Tricks + Lock-Free Stack
 */
public class BitmaskRingBufferBitTricksStack<T> {
    
    private static final VarHandle LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
    
    private final int capacity;
    private final T[] objects;
    
    // Bitmasks для отслеживания состояния объектов
    private final long[] availabilityMask;  // Tracks free/busy objects
    private final long[] staleMask;         // Tracks stale objects
    
    private final int maskSize;
    
    // Lock-free stack для свободных индексов
    private final LockFreeStack freeIndexStack;
    
    // Статистика
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);
    
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferBitTricksStack(int capacity, ObjectFactory<T> objectFactory) {
        this.capacity = capacity;
        this.objects = (T[]) new Object[capacity];
        this.freeIndexStack = new LockFreeStack();
        
        // Вычисляем размер bitmask
        this.maskSize = (capacity + 63) / 64;
        
        // Инициализируем bitmasks
        this.availabilityMask = new long[maskSize];
        this.staleMask = new long[maskSize];
        
        // Устанавливаем все биты в availabilityMask (все объекты свободны)
        for (int i = 0; i < maskSize; i++) {
            availabilityMask[i] = -1L; // Все биты установлены
        }
        
        // Создаем объекты и инициализируем стек
        for (int i = 0; i < capacity; i++) {
            objects[i] = objectFactory.createObject();
            freeIndexStack.push(i);
        }
    }
    
    /**
     * Получает свободный объект из пула
     */
    public T getFreeObject() {
        // Сначала пробуем получить индекс из стека
        int index = freeIndexStack.pop();
        
        if (index != -1) {
            // Проверяем, что объект действительно свободен
            if (isBitSet(availabilityMask, index)) {
                // Помечаем объект как занятый
                clearBit(availabilityMask, index);
                totalGets.incrementAndGet();
                return objects[index];
            } else {
                // Объект уже занят, возвращаем индекс в стек
                freeIndexStack.push(index);
            }
        }
        
        // Если стек пуст, ищем свободный объект с помощью bit tricks
        for (int i = 0; i < maskSize; i++) {
            long mask = availabilityMask[i];
            if (mask != 0) {
                // Находим первый установленный бит
                int bitIndex = Long.numberOfTrailingZeros(mask);
                int globalIndex = i * 64 + bitIndex;
                
                if (globalIndex < capacity) {
                    // Помечаем объект как занятый
                    clearBit(availabilityMask, globalIndex);
                    totalGets.incrementAndGet();
                    return objects[globalIndex];
                }
            }
        }
        
        return null; // Нет свободных объектов
    }
    
    /**
     * Возвращает объект в пул
     */
    public boolean setFreeObject(T object) {
        for (int i = 0; i < capacity; i++) {
            if (objects[i] == object) {
                if (!isBitSet(availabilityMask, i)) {
                    setBit(availabilityMask, i);
                    // Добавляем индекс в стек для быстрого доступа
                    freeIndexStack.push(i);
                    totalReturns.incrementAndGet();
                    return true;
                }
                return false; // Объект уже свободен
            }
        }
        return false; // Объект не найден
    }
    
    /**
     * Останавливает все объекты
     */
    public void stopAll() {
        for (T object : objects) {
            if (object instanceof Task) {
                ((Task) object).stop();
            }
        }
    }
    
    /**
     * Получает список занятых объектов
     */
    public List<T> getBusyObjects() {
        List<T> busyObjects = new java.util.ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMask, i)) {
                busyObjects.add(objects[i]);
            }
        }
        return busyObjects;
    }
    
    /**
     * Обнаруживает устаревшие объекты
     */
    public List<T> detectStaleObjects(long staleThresholdMs) {
        List<T> staleObjects = new java.util.ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMask, i)) {
                T object = objects[i];
                if (object instanceof Task) {
                    Task task = (Task) object;
                    if (currentTime - task.getLastUsedTime() > staleThresholdMs) {
                        setBit(staleMask, i);
                        staleObjects.add(object);
                    }
                }
            }
        }
        
        return staleObjects;
    }
    
    /**
     * Получает статистику пула
     */
    public PoolStats getStats() {
        int busyCount = 0;
        
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMask, i)) {
                busyCount++;
            }
        }
        
        return new PoolStats(
            capacity,
            capacity - busyCount,
            busyCount,
            0, // No update count anymore
            totalGets.get(),
            totalReturns.get(),
            totalUpdates.get()
        );
    }
    
    /**
     * Получает емкость пула
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Получает объект по индексу
     */
    public T getObject(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Capacity: " + capacity);
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект
     */
    public boolean isAvailable(int index) {
        if (index < 0 || index >= capacity) {
            return false;
        }
        return isBitSet(availabilityMask, index);
    }
    
    /**
     * Проверяет, помечен ли объект для обновления
     */
    public boolean isMarkedForUpdate(int index) {
        // No longer supported - always returns false
        return false;
    }
    
    // Bit manipulation methods
    
    /**
     * Устанавливает бит в bitmask
     */
    private void setBit(long[] mask, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        long oldValue = (long) LONG_ARRAY_HANDLE.getVolatile(mask, arrayIndex);
        long newValue = oldValue | (1L << bitOffset);
        LONG_ARRAY_HANDLE.setVolatile(mask, arrayIndex, newValue);
    }
    
    /**
     * Очищает бит в bitmask
     */
    private void clearBit(long[] mask, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        long oldValue = (long) LONG_ARRAY_HANDLE.getVolatile(mask, arrayIndex);
        long newValue = oldValue & ~(1L << bitOffset);
        LONG_ARRAY_HANDLE.setVolatile(mask, arrayIndex, newValue);
    }
    
    /**
     * Проверяет, установлен ли бит в bitmask
     */
    private boolean isBitSet(long[] mask, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        long value = (long) LONG_ARRAY_HANDLE.getVolatile(mask, arrayIndex);
        return (value & (1L << bitOffset)) != 0;
    }
    
    /**
     * Статистика пула
     */
    public static class PoolStats {
        public final int capacity;
        public final int freeCount;
        public final int busyCount;
        public final int updateCount;
        public final long totalGets;
        public final long totalReturns;
        public final long totalUpdates;
        
        public PoolStats(int capacity, int freeCount, int busyCount, int updateCount,
                        long totalGets, long totalReturns, long totalUpdates) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.updateCount = updateCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalUpdates = totalUpdates;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStats{capacity=%d, free=%d, busy=%d, updates=%d, gets=%d, returns=%d, updates=%d}",
                capacity, freeCount, busyCount, updateCount, totalGets, totalReturns, totalUpdates
            );
        }
    }
    
    /**
     * Фабрика для создания объектов
     */
    @FunctionalInterface
    public interface ObjectFactory<T> {
        T createObject();
    }
} 