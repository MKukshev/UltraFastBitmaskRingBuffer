package com.ultrafast.pool;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BitmaskRingBuffer с off-heap bitmasks и lock-free stack для свободных индексов
 * Комбинация: Off-Heap + Lock-Free Stack
 */
public class BitmaskRingBufferOffHeapStack<T> {
    
    private static final Unsafe UNSAFE;
    private static final long BYTE_ARRAY_BASE_OFFSET;
    
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
            BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    // Константы для выравнивания по кэш-линиям
    private static final int CACHE_LINE_SIZE = 64;
    
    private final int capacity;
    private final T[] objects;
    
    // Off-heap bitmasks
    private final long availabilityMaskAddr;  // Tracks free/busy objects
    private final long staleMaskAddr;         // Tracks stale objects
    
    private final int maskSizeBytes;
    
    // Lock-free stack для свободных индексов
    private final LockFreeStack freeIndexStack;
    
    // Статистика
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);
    
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferOffHeapStack(int capacity, ObjectFactory<T> objectFactory) {
        this.capacity = capacity;
        this.objects = (T[]) new Object[capacity];
        this.freeIndexStack = new LockFreeStack();
        
        // Вычисляем размер bitmask в байтах
        this.maskSizeBytes = (capacity + 7) / 8;
        
        // Выделяем off-heap память для bitmasks
        this.availabilityMaskAddr = UNSAFE.allocateMemory(maskSizeBytes);
        this.staleMaskAddr = UNSAFE.allocateMemory(maskSizeBytes);
        
        // Инициализируем bitmasks
        UNSAFE.setMemory(availabilityMaskAddr, maskSizeBytes, (byte) 0xFF); // Все объекты свободны
        UNSAFE.setMemory(staleMaskAddr, maskSizeBytes, (byte) 0x00);        // Нет устаревших объектов
        
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
            if (isBitSet(availabilityMaskAddr, index)) {
                // Помечаем объект как занятый
                setBit(availabilityMaskAddr, index, false);
                totalGets.incrementAndGet();
                return objects[index];
            } else {
                // Объект уже занят, возвращаем индекс в стек
                freeIndexStack.push(index);
            }
        }
        
        // Если стек пуст, ищем свободный объект в bitmask
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(availabilityMaskAddr, i)) {
                setBit(availabilityMaskAddr, i, false);
                totalGets.incrementAndGet();
                return objects[i];
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
                if (!isBitSet(availabilityMaskAddr, i)) {
                    setBit(availabilityMaskAddr, i, true);
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
            if (!isBitSet(availabilityMaskAddr, i)) {
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
            if (!isBitSet(availabilityMaskAddr, i)) {
                T object = objects[i];
                if (object instanceof Task) {
                    Task task = (Task) object;
                    if (currentTime - task.getLastUsedTime() > staleThresholdMs) {
                        setBit(staleMaskAddr, i, true);
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
            if (!isBitSet(availabilityMaskAddr, i)) {
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
        return isBitSet(availabilityMaskAddr, index);
    }
    
    /**
     * Проверяет, помечен ли объект для обновления
     */
    public boolean isMarkedForUpdate(int index) {
        // No longer supported - always returns false
        return false;
    }
    
    // Off-heap bit manipulation methods
    
    /**
     * Устанавливает бит в off-heap памяти
     */
    private void setBit(long baseAddr, int bitIndex, boolean value) {
        int byteIndex = bitIndex / 8;
        int bitOffset = bitIndex % 8;
        long addr = baseAddr + byteIndex;
        
        byte currentByte = UNSAFE.getByteVolatile(null, addr);
        byte newByte;
        
        if (value) {
            newByte = (byte) (currentByte | (1 << bitOffset));
        } else {
            newByte = (byte) (currentByte & ~(1 << bitOffset));
        }
        
        UNSAFE.putByteVolatile(null, addr, newByte);
    }
    
    /**
     * Проверяет, установлен ли бит в off-heap памяти
     */
    private boolean isBitSet(long baseAddr, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitOffset = bitIndex % 8;
        long addr = baseAddr + byteIndex;
        
        byte currentByte = UNSAFE.getByteVolatile(null, addr);
        return (currentByte & (1 << bitOffset)) != 0;
    }
    
    /**
     * Очищает off-heap память
     */
    public void cleanup() {
        UNSAFE.freeMemory(availabilityMaskAddr);
        UNSAFE.freeMemory(staleMaskAddr);
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