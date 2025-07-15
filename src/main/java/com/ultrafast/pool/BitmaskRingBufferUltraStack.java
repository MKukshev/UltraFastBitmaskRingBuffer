package com.ultrafast.pool;

import sun.misc.Unsafe;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ultra BitmaskRingBuffer: Off-Heap + Bit Tricks + Lock-Free Stack
 */
public class BitmaskRingBufferUltraStack<T> {
    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final int capacity;
    private final T[] objects;
    private final long availabilityMaskAddr;
    private final long staleMaskAddr;
    private final int maskSizeLongs;
    private final int maskSizeBytes;
    private final LockFreeStack freeIndexStack;
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong totalUpdates = new AtomicLong(0);

    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraStack(int capacity, ObjectFactory<T> objectFactory) {
        this.capacity = capacity;
        this.objects = (T[]) new Object[capacity];
        this.maskSizeLongs = (capacity + 63) / 64;
        this.maskSizeBytes = maskSizeLongs * 8;
        this.availabilityMaskAddr = UNSAFE.allocateMemory(maskSizeBytes);
        this.staleMaskAddr = UNSAFE.allocateMemory(maskSizeBytes);
        this.freeIndexStack = new LockFreeStack();
        UNSAFE.setMemory(availabilityMaskAddr, maskSizeBytes, (byte) 0xFF);
        UNSAFE.setMemory(staleMaskAddr, maskSizeBytes, (byte) 0x00);
        for (int i = 0; i < capacity; i++) {
            objects[i] = objectFactory.createObject();
            freeIndexStack.push(i);
        }
    }

    public T getFreeObject() {
        int index = freeIndexStack.pop();
        if (index != -1) {
            if (isBitSet(availabilityMaskAddr, index)) {
                setBit(availabilityMaskAddr, index, false);
                totalGets.incrementAndGet();
                return objects[index];
            } else {
                freeIndexStack.push(index);
            }
        }
        // Bit tricks: ищем первый свободный бит в каждом long
        for (int i = 0; i < maskSizeLongs; i++) {
            long mask = UNSAFE.getLongVolatile(null, availabilityMaskAddr + i * 8);
            if (mask != 0) {
                int bitIndex = Long.numberOfTrailingZeros(mask);
                int globalIndex = i * 64 + bitIndex;
                if (globalIndex < capacity) {
                    setBit(availabilityMaskAddr, globalIndex, false);
                    totalGets.incrementAndGet();
                    return objects[globalIndex];
                }
            }
        }
        return null;
    }

    public boolean setFreeObject(T object) {
        for (int i = 0; i < capacity; i++) {
            if (objects[i] == object) {
                if (!isBitSet(availabilityMaskAddr, i)) {
                    setBit(availabilityMaskAddr, i, true);
                    freeIndexStack.push(i);
                    totalReturns.incrementAndGet();
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public void stopAll() {
        for (T object : objects) {
            if (object instanceof Task) {
                ((Task) object).stop();
            }
        }
    }

    public List<T> getBusyObjects() {
        List<T> busyObjects = new java.util.ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMaskAddr, i)) {
                busyObjects.add(objects[i]);
            }
        }
        return busyObjects;
    }

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
            0,
            totalGets.get(),
            totalReturns.get(),
            totalUpdates.get()
        );
    }

    public int getCapacity() { return capacity; }
    public T getObject(int index) {
        if (index < 0 || index >= capacity) throw new IndexOutOfBoundsException();
        return objects[index];
    }
    public boolean isAvailable(int index) {
        if (index < 0 || index >= capacity) return false;
        return isBitSet(availabilityMaskAddr, index);
    }
    public boolean isMarkedForUpdate(int index) { return false; }

    private void setBit(long baseAddr, int bitIndex, boolean value) {
        int byteIndex = bitIndex / 8;
        int bitOffset = bitIndex % 8;
        long addr = baseAddr + byteIndex;
        byte currentByte = UNSAFE.getByteVolatile(null, addr);
        byte newByte = value ? (byte) (currentByte | (1 << bitOffset)) : (byte) (currentByte & ~(1 << bitOffset));
        UNSAFE.putByteVolatile(null, addr, newByte);
    }
    private boolean isBitSet(long baseAddr, int bitIndex) {
        int byteIndex = bitIndex / 8;
        int bitOffset = bitIndex % 8;
        long addr = baseAddr + byteIndex;
        byte currentByte = UNSAFE.getByteVolatile(null, addr);
        return (currentByte & (1 << bitOffset)) != 0;
    }
    public void cleanup() {
        UNSAFE.freeMemory(availabilityMaskAddr);
        UNSAFE.freeMemory(staleMaskAddr);
    }

    public static class PoolStats {
        public final int capacity, freeCount, busyCount, updateCount;
        public final long totalGets, totalReturns, totalUpdates;
        public PoolStats(int capacity, int freeCount, int busyCount, int updateCount, long totalGets, long totalReturns, long totalUpdates) {
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
            return String.format("PoolStats{capacity=%d, free=%d, busy=%d, updates=%d, gets=%d, returns=%d, updates=%d}",
                capacity, freeCount, busyCount, updateCount, totalGets, totalReturns, totalUpdates);
        }
    }
    @FunctionalInterface
    public interface ObjectFactory<T> { T createObject(); }
} 