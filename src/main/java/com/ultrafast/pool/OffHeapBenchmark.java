package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark to compare performance of different BitmaskRingBuffer implementations:
 * - Original (AtomicReferenceArray)
 * - Optimized (VarHandle)
 * - Off-heap (Unsafe)
 * - Bit Tricks (Long.numberOfTrailingZeros + Lock-free stack)
 * - Ultra (Off-heap + Bit Tricks combined)
 * - Minimal (Off-heap + Bit Tricks - updateMask)
 */
public class OffHeapBenchmark {
    
    // Размеры пулов для тестирования
    private static final int[] POOL_SIZES = {1000, 5000, 10000, 50000, 100000};
    // Количество потоков для тестирования
    private static final int[] THREAD_COUNTS = {1, 4, 8, 12};
    private static final int OPERATIONS_PER_THREAD = 10000;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    
    public static void main(String[] args) {
        System.out.println("=== Off-Heap BitmaskRingBuffer Benchmark ===");
        System.out.println("Pool sizes: " + java.util.Arrays.toString(POOL_SIZES));
        System.out.println("Thread counts: " + java.util.Arrays.toString(THREAD_COUNTS));
        System.out.println("Operations per thread: " + OPERATIONS_PER_THREAD);
        System.out.println("Warmup iterations: " + WARMUP_ITERATIONS);
        System.out.println("Benchmark iterations: " + BENCHMARK_ITERATIONS);
        System.out.println();
        
        for (int poolSize : POOL_SIZES) {
            for (int threadCount : THREAD_COUNTS) {
                System.out.printf("=== Pool Size: %d, Threads: %d ===%n", poolSize, threadCount);
                
                // Benchmark original version
                benchmarkVersion("Original", poolSize, threadCount, 
                    () -> new BitmaskRingBuffer<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark VarHandle version
                benchmarkVersion("VarHandle", poolSize, threadCount, 
                    () -> new BitmaskRingBufferOptimized<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark off-heap version
                benchmarkVersion("Off-Heap", poolSize, threadCount, 
                    () -> new BitmaskRingBufferOffHeap<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark bit tricks version
                benchmarkVersion("BitTricks", poolSize, threadCount, 
                    () -> new BitmaskRingBufferBitTricks<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark ultra version (off-heap + bit tricks)
                benchmarkVersion("Ultra", poolSize, threadCount, 
                    () -> new BitmaskRingBufferUltra<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark minimal version (off-heap + bit tricks - updateMask)
                benchmarkVersion("Minimal", poolSize, threadCount, 
                    () -> new BitmaskRingBufferMinimal<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark off-heap+stack version
                benchmarkVersion("OffHeapStack", poolSize, threadCount, 
                    () -> new BitmaskRingBufferOffHeapStack<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark bit tricks+stack version
                benchmarkVersion("BitTricksStack", poolSize, threadCount, 
                    () -> new BitmaskRingBufferBitTricksStack<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark ultra+stack version
                benchmarkVersion("UltraStack", poolSize, threadCount, 
                    () -> new BitmaskRingBufferUltraStack<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark ultra+varhandle version (Unsafe replacement)
                benchmarkVersion("UltraVarHandle", poolSize, threadCount, 
                    () -> new BitmaskRingBufferUltraVarHandle<>(poolSize, () -> new ProcessTask("Task")));
                
                // Benchmark classic version (ConcurrentLinkedQueue + ConcurrentHashMap)
                benchmarkVersion("Classic", poolSize, threadCount, 
                    () -> new BitmaskRingBufferClassic<>(() -> new ProcessTask("Task"), poolSize/2, poolSize, 1000));
                
                System.out.println();
            }
        }
    }
    
    private static void benchmarkVersion(String versionName, int poolSize, int threadCount, 
                                       PoolFactory poolFactory) {
        try {
            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runBenchmark(poolFactory.create(), threadCount, OPERATIONS_PER_THREAD);
            }
            
            // Actual benchmark
            long totalTime = 0;
            long totalOperations = 0;
            
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                Object pool = poolFactory.create();
                long startTime = System.nanoTime();
                long operations = runBenchmark(pool, threadCount, OPERATIONS_PER_THREAD);
                long endTime = System.nanoTime();
                
                totalTime += (endTime - startTime);
                totalOperations += operations;
                
                // Cleanup for off-heap versions
                if (pool instanceof BitmaskRingBufferOffHeap) {
                    ((BitmaskRingBufferOffHeap<?>) pool).cleanup();
                } else if (pool instanceof BitmaskRingBufferUltra) {
                    ((BitmaskRingBufferUltra<?>) pool).cleanup();
                } else if (pool instanceof BitmaskRingBufferMinimal) {
                    ((BitmaskRingBufferMinimal<?>) pool).cleanup();
                } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
                    ((BitmaskRingBufferOffHeapStack<?>) pool).cleanup();
                        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            ((BitmaskRingBufferUltraStack<?>) pool).cleanup();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<?>) pool).cleanup();
        }
            }
            
            double avgTimeMs = totalTime / (double) BENCHMARK_ITERATIONS / 1_000_000;
            double avgOperations = totalOperations / (double) BENCHMARK_ITERATIONS;
            double opsPerMs = avgOperations / avgTimeMs;
            
            System.out.printf("%-10s: %.2f ms, %.0f ops, %.2f ops/ms%n", 
                versionName, avgTimeMs, avgOperations, opsPerMs);
            
        } catch (Exception e) {
            System.err.printf("Error benchmarking %s: %s%n", versionName, e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static long runBenchmark(Object pool, int threadCount, int operationsPerThread) 
            throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    long operations = 0;
                    for (int i = 0; i < operationsPerThread; i++) {
                        Object obj = getObject(pool);
                        if (obj != null) {
                            returnObject(pool, obj);
                            operations++;
                        }
                    }
                    totalOperations.addAndGet(operations);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.SECONDS);
        
        return totalOperations.get();
    }
    
    @SuppressWarnings("unchecked")
    private static Object getObject(Object pool) {
        if (pool instanceof BitmaskRingBuffer) {
            return ((BitmaskRingBuffer<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOptimized) {
            return ((BitmaskRingBufferOptimized<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferBitTricks) {
            return ((BitmaskRingBufferBitTricks<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferMinimal) {
            return ((BitmaskRingBufferMinimal<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
            return ((BitmaskRingBufferOffHeapStack<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferBitTricksStack) {
            return ((BitmaskRingBufferBitTricksStack<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            return ((BitmaskRingBufferUltraStack<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<Object>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferClassic) {
            return ((BitmaskRingBufferClassic<Object>) pool).acquire();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static void returnObject(Object pool, Object obj) {
        if (pool instanceof BitmaskRingBuffer) {
            ((BitmaskRingBuffer<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOptimized) {
            ((BitmaskRingBufferOptimized<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            ((BitmaskRingBufferOffHeap<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferBitTricks) {
            ((BitmaskRingBufferBitTricks<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltra) {
            ((BitmaskRingBufferUltra<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferMinimal) {
            ((BitmaskRingBufferMinimal<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
            ((BitmaskRingBufferOffHeapStack<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferBitTricksStack) {
            ((BitmaskRingBufferBitTricksStack<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            ((BitmaskRingBufferUltraStack<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferClassic) {
            ((BitmaskRingBufferClassic<Object>) pool).release(obj);
        }
    }
    
    @FunctionalInterface
    private interface PoolFactory {
        Object create();
    }
} 