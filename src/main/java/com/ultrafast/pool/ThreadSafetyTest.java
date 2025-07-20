package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Тест потокобезопасности для BitmaskRingBufferClassic
 * Проверяет корректность работы в многопоточной среде
 */
public class ThreadSafetyTest {
    
    private static final int THREAD_COUNT = 8;
    private static final int OPERATIONS_PER_THREAD = 10000;
    private static final int POOL_SIZE = 1000;
    private static final int TIMEOUT_MS = 1000;
    
    public static void main(String[] args) {
        System.out.println("=== Thread Safety Test for BitmaskRingBufferClassic ===\n");
        
        // Тестируем исправленную версию
        testThreadSafety("BitmaskRingBufferClassic (Fixed)", 
            new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Test", 1024, 42.0), 
                POOL_SIZE / 2, POOL_SIZE, TIMEOUT_MS));
        
        // Тестируем модернизированную версию для сравнения
        testThreadSafety("BitmaskRingBufferClassicPreallocated", 
            new BitmaskRingBufferClassicPreallocated<>(() -> new HeavyTask(0, "Test", 1024, 42.0), 
                POOL_SIZE, TIMEOUT_MS));
    }
    
    private static void testThreadSafety(String poolName, ObjectPool<HeavyTask> pool) {
        System.out.println("--- Testing " + poolName + " ---");
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicInteger successfulAcquires = new AtomicInteger(0);
        AtomicInteger successfulReleases = new AtomicInteger(0);
        AtomicInteger failedAcquires = new AtomicInteger(0);
        AtomicInteger duplicateObjects = new AtomicInteger(0);
        AtomicLong totalAcquireTime = new AtomicLong(0);
        
        // Запускаем потоки
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Ждем сигнала старта
                    
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        long startTime = System.nanoTime();
                        HeavyTask task = pool.acquire();
                        long acquireTime = System.nanoTime() - startTime;
                        
                        if (task != null) {
                            successfulAcquires.incrementAndGet();
                            totalAcquireTime.addAndGet(acquireTime);
                            
                            // Имитируем работу с объектом
                            Thread.sleep(0, 100); // 100 наносекунд
                            
                            // Возвращаем объект
                            pool.release(task);
                            successfulReleases.incrementAndGet();
                        } else {
                            failedAcquires.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Thread " + threadId + " failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Запускаем тест
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long testEndTime = System.currentTimeMillis();
        long testDuration = testEndTime - testStartTime;
        
        // Получаем статистику пула
        ObjectPool.PoolStatistics stats = pool.getStatistics();
        
        // Анализируем результаты
        System.out.println("Test duration: " + testDuration + " ms");
        System.out.println("Total operations: " + (THREAD_COUNT * OPERATIONS_PER_THREAD));
        System.out.println("Successful acquires: " + successfulAcquires.get());
        System.out.println("Successful releases: " + successfulReleases.get());
        System.out.println("Failed acquires: " + failedAcquires.get());
        System.out.println("Operations per second: " + 
            (THREAD_COUNT * OPERATIONS_PER_THREAD * 1000L / testDuration));
        
        System.out.println("\nPool statistics:");
        System.out.println("  Max pool size: " + stats.maxPoolSize);
        System.out.println("  Available objects: " + stats.availableObjects);
        System.out.println("  Borrowed objects: " + stats.borrowedObjects);
        System.out.println("  Total acquires: " + stats.totalAcquires);
        System.out.println("  Total releases: " + stats.totalReleases);
        System.out.println("  Total creates: " + stats.totalCreates);
        System.out.println("  Total waits: " + stats.totalWaits);
        System.out.println("  Active objects: " + stats.activeObjects);
        
        // Проверяем корректность
        boolean isCorrect = true;
        String issues = "";
        
        if (successfulAcquires.get() != successfulReleases.get()) {
            isCorrect = false;
            issues += "Mismatch between acquires and releases. ";
        }
        
        if (stats.borrowedObjects != 0) {
            isCorrect = false;
            issues += "Objects still borrowed after test. ";
        }
        
        if (stats.availableObjects + stats.borrowedObjects != stats.totalCreates) {
            isCorrect = false;
            issues += "Object count mismatch. ";
        }
        
        if (stats.activeObjects > stats.maxPoolSize) {
            isCorrect = false;
            issues += "Active objects exceed max pool size. ";
        }
        
        System.out.println("\nThread safety check: " + (isCorrect ? "✅ PASSED" : "❌ FAILED"));
        if (!isCorrect) {
            System.out.println("Issues: " + issues);
        }
        
        System.out.println("Average acquire time: " + 
            (successfulAcquires.get() > 0 ? totalAcquireTime.get() / successfulAcquires.get() : 0) + " ns");
        
        executor.shutdown();
        System.out.println();
    }
} 