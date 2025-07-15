package com.ultrafast.pool;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;

/**
 * Бенчмарк для сравнения потребления памяти между различными реализациями пулов объектов
 * 
 * Сравнивает:
 * - BitmaskRingBufferClassic (ConcurrentLinkedQueue + ConcurrentHashMap)
 * - BitmaskRingBufferUltraVarHandle (оптимизированная версия с VarHandle)
 * 
 * Измеряет:
 * - Потребление памяти при создании пула
 * - Потребление памяти при заполнении пула объектами
 * - Потребление памяти при активном использовании
 * - Размер пула в байтах на объект
 */
public class MemoryBenchmark {
    
    private static final int[] POOL_SIZES = {50000, 100000, 500000, 1000000};
    private static final int OPERATIONS_PER_TEST = 10000;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    private static final int PAYLOAD_SIZE = 1024; // 1 КБ на объект
    
    public static void main(String[] args) {
        System.out.println("=== Memory Usage Benchmark ===");
        System.out.println("Pool sizes: " + java.util.Arrays.toString(POOL_SIZES));
        System.out.println("Operations per test: " + OPERATIONS_PER_TEST);
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();
        
        for (int poolSize : POOL_SIZES) {
            System.out.printf("=== Pool Size: %d ===%n", poolSize);
            System.out.printf("HeavyTask payload: %d bytes, total task size (примерно): %d bytes%n", PAYLOAD_SIZE, (PAYLOAD_SIZE + 64));
            
            // Тестируем Classic версию
            benchmarkMemoryUsage("Classic", poolSize, () -> 
                new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Heavy", PAYLOAD_SIZE, 42.0), poolSize/2, poolSize, 1000));
            
            // Тестируем UltraVarHandle версию
            benchmarkMemoryUsage("UltraVarHandle", poolSize, () -> 
                new BitmaskRingBufferUltraVarHandle<>(poolSize, () -> new HeavyTask(0, "Heavy", PAYLOAD_SIZE, 42.0)));
            
            System.out.println();
        }
    }
    
    private static void benchmarkMemoryUsage(String versionName, int poolSize, PoolFactory poolFactory) {
        try {
            System.out.printf("--- %s ---%n", versionName);
            
            // Измеряем базовое потребление памяти
            long baseMemory = getUsedMemory();
            
            // Создаем пул
            Object pool = poolFactory.create();
            long afterCreation = getUsedMemory();
            long creationMemory = afterCreation - baseMemory;
            
            // Заполняем пул объектами
            List<Object> borrowedObjects = new ArrayList<>();
            for (int i = 0; i < poolSize; i++) {
                Object obj = getObject(pool);
                if (obj != null) {
                    borrowedObjects.add(obj);
                }
            }
            long afterBorrow = getUsedMemory();
            long borrowMemory = afterBorrow - afterCreation;
            
            // Возвращаем объекты
            for (Object obj : borrowedObjects) {
                returnObject(pool, obj);
            }
            long afterReturn = getUsedMemory();
            long returnMemory = afterReturn - afterBorrow;
            
            // Активное использование
            long totalActiveMemory = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                List<Object> tempObjects = new ArrayList<>();
                
                // Получаем объекты
                for (int j = 0; j < OPERATIONS_PER_TEST; j++) {
                    Object obj = getObject(pool);
                    if (obj != null) {
                        tempObjects.add(obj);
                    }
                }
                
                long activeMemory = getUsedMemory();
                totalActiveMemory += activeMemory;
                
                // Возвращаем объекты
                for (Object obj : tempObjects) {
                    returnObject(pool, obj);
                }
            }
            long avgActiveMemory = totalActiveMemory / BENCHMARK_ITERATIONS;
            
            // Очистка ресурсов
            if (pool instanceof BitmaskRingBufferUltraVarHandle) {
                ((BitmaskRingBufferUltraVarHandle<?>) pool).cleanup();
            }
            long afterCleanup = getUsedMemory();
            long cleanupMemory = afterCleanup - avgActiveMemory;
            
            // Выводим результаты
            System.out.printf("Creation memory:     %8d bytes (%6.2f bytes/object)%n", 
                creationMemory, (double) creationMemory / poolSize);
            System.out.printf("Borrow memory:       %8d bytes (%6.2f bytes/object)%n", 
                borrowMemory, (double) borrowMemory / poolSize);
            System.out.printf("Return memory:       %8d bytes (%6.2f bytes/object)%n", 
                returnMemory, (double) returnMemory / poolSize);
            System.out.printf("Active memory:       %8d bytes (%6.2f bytes/object)%n", 
                avgActiveMemory - afterCreation, (double) (avgActiveMemory - afterCreation) / poolSize);
            System.out.printf("Cleanup memory:      %8d bytes (%6.2f bytes/object)%n", 
                cleanupMemory, (double) cleanupMemory / poolSize);
            System.out.printf("Total overhead:      %8d bytes (%6.2f bytes/object)%n", 
                avgActiveMemory - baseMemory, (double) (avgActiveMemory - baseMemory) / poolSize);
            
            // Статистика пула
            Object stats = getStatistics(pool);
            if (stats instanceof ObjectPool.PoolStatistics) {
                ObjectPool.PoolStatistics poolStats = (ObjectPool.PoolStatistics) stats;
                System.out.printf("Pool statistics:     maxSize=%d, available=%d, borrowed=%d%n",
                    poolStats.maxPoolSize, poolStats.availableObjects, poolStats.borrowedObjects);
            } else if (stats instanceof BitmaskRingBufferUltraVarHandle.PoolStats) {
                BitmaskRingBufferUltraVarHandle.PoolStats poolStats = (BitmaskRingBufferUltraVarHandle.PoolStats) stats;
                System.out.printf("Pool statistics:     capacity=%d, free=%d, busy=%d%n",
                    poolStats.capacity, poolStats.freeCount, poolStats.busyCount);
            }
            
        } catch (Exception e) {
            System.err.printf("Error benchmarking %s: %s%n", versionName, e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static long getUsedMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }
    
    @SuppressWarnings("unchecked")
    private static Object getObject(Object pool) {
        if (pool instanceof BitmaskRingBufferClassic) {
            return ((BitmaskRingBufferClassic<Object>) pool).acquire();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<Object>) pool).getFreeObject();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static void returnObject(Object pool, Object obj) {
        if (pool instanceof BitmaskRingBufferClassic) {
            ((BitmaskRingBufferClassic<Object>) pool).release(obj);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<Object>) pool).setFreeObject(obj);
        }
    }
    
    @SuppressWarnings("unchecked")
    private static Object getStatistics(Object pool) {
        if (pool instanceof BitmaskRingBufferClassic) {
            return ((BitmaskRingBufferClassic<Object>) pool).getStatistics();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<Object>) pool).getStats();
        }
        return null;
    }
    
    @FunctionalInterface
    private interface PoolFactory {
        Object create();
    }
} 