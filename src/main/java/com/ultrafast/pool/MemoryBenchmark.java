package com.ultrafast.pool;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

/**
 * Бенчмарк для сравнения потребления памяти между различными реализациями пулов объектов
 * с использованием JOL (Java Object Layout) для точного измерения размеров объектов
 * 
 * Сравнивает:
 * - BitmaskRingBufferClassic (ConcurrentLinkedQueue + ConcurrentHashMap)
 * - BitmaskRingBufferUltraVarHandle (оптимизированная версия с VarHandle)
 * 
 * Измеряет:
 * - Точный размер объектов через JOL
 * - Размер пула и его компонентов
 * - Overhead пула на объект
 */
public class MemoryBenchmark {
    
    // Размеры пулов для тестирования
    private static final int[] POOL_SIZES = {1, 10, 100, 1000, 10000, 50000, 100000, 500000, 1000000};
    private static final int OPERATIONS_PER_TEST = 10000;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    private static final int PAYLOAD_SIZE = 1024; // 1 КБ на объект
    
    public static void main(String[] args) {
        System.out.println("=== Memory Usage Benchmark with JOL ===");
        System.out.println("Pool sizes: " + java.util.Arrays.toString(POOL_SIZES));
        System.out.println("Operations per test: " + OPERATIONS_PER_TEST);
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();
        
        // Анализируем размер одного HeavyTask объекта
        HeavyTask sampleTask = new HeavyTask(0, "Sample", PAYLOAD_SIZE, 42.0);
        System.out.println("=== HeavyTask Object Layout ===");
        System.out.println(ClassLayout.parseInstance(sampleTask).toPrintable());
        System.out.println("Total size: " + GraphLayout.parseInstance(sampleTask).totalSize() + " bytes");
        System.out.println();
        
        for (int poolSize : POOL_SIZES) {
            System.out.printf("=== Pool Size: %d ===%n", poolSize);
            System.out.printf("HeavyTask payload: %d bytes, total task size: %d bytes%n", 
                PAYLOAD_SIZE, GraphLayout.parseInstance(sampleTask).totalSize());
            
            // Тестируем Classic версию
            benchmarkMemoryUsageWithJOL("Classic", poolSize, () -> 
                new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Heavy", PAYLOAD_SIZE, 42.0), poolSize/2, poolSize, 1000));
            
            // Тестируем UltraVarHandle версию
            benchmarkMemoryUsageWithJOL("UltraVarHandle", poolSize, () -> 
                new BitmaskRingBufferUltraVarHandle<>(poolSize, () -> new HeavyTask(0, "Heavy", PAYLOAD_SIZE, 42.0)));
            
            System.out.println();
        }
    }
    
    private static void benchmarkMemoryUsageWithJOL(String versionName, int poolSize, PoolFactory poolFactory) {
        try {
            System.out.printf("--- %s ---%n", versionName);
            
            // Создаем пул
            Object pool = poolFactory.create();
            
            // Анализируем размер пула без объектов
            long poolSizeBytes = GraphLayout.parseInstance(pool).totalSize();
            System.out.printf("Pool structure size: %d bytes (%6.2f bytes/object)%n", 
                poolSizeBytes, (double) poolSizeBytes / poolSize);
            
            // Создаем список объектов для анализа
            List<Object> objects = new ArrayList<>();
            for (int i = 0; i < Math.min(poolSize, 1000); i++) { // Ограничиваем для больших пулов
                Object obj = getObject(pool);
                if (obj != null) {
                    objects.add(obj);
                }
            }
            
            long singleObjectSize = 0;
            if (!objects.isEmpty()) {
                // Анализируем размер объектов
                long objectsSizeBytes = GraphLayout.parseInstance(objects).totalSize();
                singleObjectSize = GraphLayout.parseInstance(objects.get(0)).totalSize();
                
                System.out.printf("Sample objects size (%d objects): %d bytes%n", objects.size(), objectsSizeBytes);
                System.out.printf("Single object size: %d bytes%n", singleObjectSize);
                System.out.printf("Estimated total objects size: %d bytes%n", singleObjectSize * poolSize);
                
                // Возвращаем объекты в пул
                for (Object obj : objects) {
                    returnObject(pool, obj);
                }
            }
            
            // Анализируем размер пула с объектами (если возможно)
            long poolWithObjectsSize = GraphLayout.parseInstance(pool).totalSize();
            System.out.printf("Pool with objects size: %d bytes%n", poolWithObjectsSize);
            
            // Вычисляем overhead
            if (singleObjectSize > 0) {
                long estimatedTotalSize = poolSizeBytes + (singleObjectSize * poolSize);
                long actualOverhead = poolWithObjectsSize - (singleObjectSize * poolSize);
                System.out.printf("Pool overhead: %d bytes (%6.2f bytes/object)%n", 
                    actualOverhead, (double) actualOverhead / poolSize);
            } else {
                System.out.printf("Pool overhead: %d bytes (не удалось измерить размер объекта)%n", 
                    poolWithObjectsSize - poolSizeBytes);
            }
            
            // Статистика пула
            Object stats = getStatistics(pool);
            if (stats instanceof ObjectPool.PoolStatistics) {
                ObjectPool.PoolStatistics poolStats = (ObjectPool.PoolStatistics) stats;
                System.out.printf("Pool statistics: maxSize=%d, available=%d, borrowed=%d%n",
                    poolStats.maxPoolSize, poolStats.availableObjects, poolStats.borrowedObjects);
            } else if (stats instanceof BitmaskRingBufferUltraVarHandle.PoolStats) {
                BitmaskRingBufferUltraVarHandle.PoolStats poolStats = (BitmaskRingBufferUltraVarHandle.PoolStats) stats;
                System.out.printf("Pool statistics: capacity=%d, free=%d, busy=%d%n",
                    poolStats.capacity, poolStats.freeCount, poolStats.busyCount);
            }
            
            // Очистка ресурсов
            if (pool instanceof BitmaskRingBufferUltraVarHandle) {
                ((BitmaskRingBufferUltraVarHandle<?>) pool).cleanup();
            }
            
        } catch (Exception e) {
            System.err.printf("Error benchmarking %s: %s%n", versionName, e.getMessage());
            e.printStackTrace();
        }
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