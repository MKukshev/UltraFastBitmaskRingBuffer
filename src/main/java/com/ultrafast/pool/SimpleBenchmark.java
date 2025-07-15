package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Простой бенчмарк для быстрого сравнения производительности
 */
public class SimpleBenchmark {
    
    private static final int THREAD_COUNT = 4;
    private static final int OPERATIONS_PER_THREAD = 10_000; // уменьшено для быстрого теста
    private static final long TIMEOUT_NANOS = 1_000_000; // 1 ms
    
    public static void main(String[] args) {
        System.out.println("=== Simple Performance Comparison ===");
        System.out.println("Threads: " + THREAD_COUNT);
        System.out.println("Operations per thread: " + OPERATIONS_PER_THREAD);
        System.out.println("Total operations: " + (THREAD_COUNT * OPERATIONS_PER_THREAD));
        System.out.println();
        
        // Тест с пулом размером 1024
        testPool(1024);
        
        // Тест с пулом размером 4096
        testPool(4096);
    }
    
    private static void testPool(int poolSize) {
        System.out.println("=== Testing Pool Size: " + poolSize + " ===");
        
        // Тест оригинальной версии
        long originalTime = runTest("Original", new BitmaskRingBuffer<>(poolSize, 
            () -> new ProcessTask("Test-" + System.nanoTime())));
        
        // Тест оптимизированной версии
        long optimizedTime = runTest("Optimized", new BitmaskRingBufferOptimized<>(poolSize, 
            () -> new ProcessTask("Test-" + System.nanoTime())));
        
        // Результаты
        double speedup = (double) originalTime / optimizedTime;
        System.out.println();
        System.out.println("Results for pool size " + poolSize + ":");
        System.out.printf("Original: %d ms%n", originalTime);
        System.out.printf("Optimized: %d ms%n", optimizedTime);
        System.out.printf("Speedup: %.2fx%n", speedup);
        System.out.printf("Improvement: %.1f%%%n", (speedup - 1.0) * 100);
        System.out.println();
    }
    
    private static long runTest(String name, Object pool) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger totalOperations = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        // Получаем объект с таймаутом
                        Task task = null;
                        if (pool instanceof BitmaskRingBuffer) {
                            task = ((BitmaskRingBuffer<Task>) pool).getFreeObject(TIMEOUT_NANOS);
                        } else if (pool instanceof BitmaskRingBufferOptimized) {
                            task = ((BitmaskRingBufferOptimized<Task>) pool).getFreeObject(TIMEOUT_NANOS);
                        }
                        if (task == null) continue; // если не получили — пропускаем

                        totalOperations.incrementAndGet();
                        task.start();
                        task.stop();
                        if (pool instanceof BitmaskRingBuffer) {
                            ((BitmaskRingBuffer<Task>) pool).setFreeObject(task);
                        } else if (pool instanceof BitmaskRingBufferOptimized) {
                            ((BitmaskRingBufferOptimized<Task>) pool).setFreeObject(task);
                        }
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
            long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
            
            System.out.printf("%s: %d ms, %d operations%n", name, duration, totalOperations.get());
            
            return duration;
            
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