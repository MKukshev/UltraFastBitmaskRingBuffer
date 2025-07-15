package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Бенчмарк для сравнения производительности оригинальной и оптимизированной версий BitmaskRingBuffer
 */
public class BitmaskRingBufferBenchmark {
    
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int OPERATIONS_PER_THREAD = 100_000;
    
    public static void main(String[] args) {
        System.out.println("=== BitmaskRingBuffer Performance Benchmark ===");
        System.out.println("Threads: " + THREAD_COUNT);
        System.out.println("Operations per thread: " + OPERATIONS_PER_THREAD);
        System.out.println("Total operations: " + (THREAD_COUNT * OPERATIONS_PER_THREAD));
        System.out.println();
        
        // Тестируем разные размеры пула
        int[] poolSizes = {1024, 4096, 16384};
        
        for (int poolSize : poolSizes) {
            System.out.println("=== Pool Size: " + poolSize + " ===");
            benchmarkPoolSize(poolSize);
            System.out.println();
        }
    }
    
    private static void benchmarkPoolSize(int poolSize) {
        // Прогрев JVM
        System.out.println("Warming up...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            runBenchmark("Original", new BitmaskRingBuffer<>(poolSize, 
                () -> new ProcessTask("Warmup-" + System.nanoTime())), false);
            runBenchmark("Optimized", new BitmaskRingBufferOptimized<>(poolSize, 
                () -> new ProcessTask("Warmup-" + System.nanoTime())), false);
        }
        
        // Основные тесты
        System.out.println("Running benchmarks...");
        long originalTotalTime = 0;
        long optimizedTotalTime = 0;
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            System.out.println("Iteration " + (i + 1) + "/" + BENCHMARK_ITERATIONS);
            
            long originalTime = runBenchmark("Original", new BitmaskRingBuffer<>(poolSize, 
                () -> new ProcessTask("Bench-" + System.nanoTime())), true);
            originalTotalTime += originalTime;
            
            long optimizedTime = runBenchmark("Optimized", new BitmaskRingBufferOptimized<>(poolSize, 
                () -> new ProcessTask("Bench-" + System.nanoTime())), true);
            optimizedTotalTime += optimizedTime;
            
            System.out.println();
        }
        
        // Результаты
        long originalAvgTime = originalTotalTime / BENCHMARK_ITERATIONS;
        long optimizedAvgTime = optimizedTotalTime / BENCHMARK_ITERATIONS;
        double speedup = (double) originalAvgTime / optimizedAvgTime;
        
        System.out.println("=== Results for Pool Size " + poolSize + " ===");
        System.out.printf("Original Average: %d ms (%.2f ops/sec)%n", 
            originalAvgTime, (THREAD_COUNT * OPERATIONS_PER_THREAD * 1000.0) / originalAvgTime);
        System.out.printf("Optimized Average: %d ms (%.2f ops/sec)%n", 
            optimizedAvgTime, (THREAD_COUNT * OPERATIONS_PER_THREAD * 1000.0) / optimizedAvgTime);
        System.out.printf("Speedup: %.2fx%n", speedup);
        System.out.printf("Improvement: %.1f%%%n", (speedup - 1.0) * 100);
    }
    
    private static long runBenchmark(String name, Object pool, boolean printStats) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger totalGets = new AtomicInteger(0);
        AtomicInteger totalReturns = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        long operationStart = System.nanoTime();
                        
                        // Получаем объект
                        Task task = null;
                        if (pool instanceof BitmaskRingBuffer) {
                            task = ((BitmaskRingBuffer<Task>) pool).getFreeObject();
                        } else if (pool instanceof BitmaskRingBufferOptimized) {
                            task = ((BitmaskRingBufferOptimized<Task>) pool).getFreeObject();
                        }
                        
                        if (task != null) {
                            totalGets.incrementAndGet();
                            
                            // Имитируем работу
                            task.start();
                            
                            // Возвращаем объект
                            task.stop();
                            boolean returned = false;
                            if (pool instanceof BitmaskRingBuffer) {
                                returned = ((BitmaskRingBuffer<Task>) pool).setFreeObject(task);
                            } else if (pool instanceof BitmaskRingBufferOptimized) {
                                returned = ((BitmaskRingBufferOptimized<Task>) pool).setFreeObject(task);
                            }
                            
                            if (returned) {
                                totalReturns.incrementAndGet();
                            }
                        }
                        
                        totalLatency.addAndGet(System.nanoTime() - operationStart);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            if (printStats) {
                System.out.printf("%s: %d ms, %d gets, %d returns, avg latency: %.2f μs%n", 
                    name, duration / 1_000_000, totalGets.get(), totalReturns.get(), 
                    totalLatency.get() / (double)(THREAD_COUNT * OPERATIONS_PER_THREAD) / 1000.0);
            }
            
            return duration / 1_000_000; // возвращаем время в миллисекундах
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
} 