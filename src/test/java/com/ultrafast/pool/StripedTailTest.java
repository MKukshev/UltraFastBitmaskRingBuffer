package com.ultrafast.pool;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Тест для проверки корректности striped tail реализации
 */
public class StripedTailTest {
    
    private BitmaskRingBufferUltraVarHandleStriped<ProcessTask> pool;
    private static final int POOL_SIZE = 1000;
    
    @BeforeEach
    void setUp() {
        pool = new BitmaskRingBufferUltraVarHandleStriped<>(POOL_SIZE, 
            () -> new ProcessTask("Task-" + System.nanoTime()));
    }
    
    @Test
    void testBasicFunctionality() {
        // Проверяем базовую функциональность
        ProcessTask task = pool.getFreeObject();
        assertNotNull(task, "Должен вернуть объект из пула");
        
        boolean returned = pool.setFreeObject(task);
        assertTrue(returned, "Должен успешно вернуть объект в пул");
    }
    
    @Test
    void testPoolCapacity() {
        assertEquals(POOL_SIZE, pool.getCapacity(), "Емкость пула должна соответствовать заданной");
    }
    
    @Test
    void testAllObjectsAreUnique() {
        Set<ProcessTask> objects = new HashSet<>();
        
        // Получаем все объекты из пула
        for (int i = 0; i < POOL_SIZE; i++) {
            ProcessTask task = pool.getFreeObject();
            assertNotNull(task, "Должен вернуть объект " + i);
            assertTrue(objects.add(task), "Объект " + i + " должен быть уникальным");
        }
        
        assertEquals(POOL_SIZE, objects.size(), "Все объекты должны быть уникальными");
        
        // Возвращаем все объекты обратно
        for (ProcessTask task : objects) {
            assertTrue(pool.setFreeObject(task), "Должен успешно вернуть объект в пул");
        }
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        ProcessTask task = pool.getFreeObject();
                        if (task != null) {
                            // Имитируем работу с объектом
                            task.start();
                            task.stop();
                            
                            if (pool.setFreeObject(task)) {
                                successCount.incrementAndGet();
                            } else {
                                failureCount.incrementAndGet();
                            }
                        } else {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        int totalOperations = threadCount * operationsPerThread;
        int totalSuccess = successCount.get();
        int totalFailure = failureCount.get();
        
        System.out.printf("Concurrent test results: success=%d, failure=%d, total=%d%n", 
            totalSuccess, totalFailure, totalOperations);
        
        assertTrue(totalSuccess > totalFailure, "Успешных операций должно быть больше неудачных");
        assertEquals(totalOperations, totalSuccess + totalFailure, "Общее количество операций должно совпадать");
    }
    
    @RepeatedTest(5)
    void testHighConcurrencyStress() throws InterruptedException {
        int threadCount = 16;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        ProcessTask task = pool.getFreeObject();
                        if (task != null) {
                            // Имитируем работу с объектом
                            task.start();
                            task.stop();
                            
                            if (pool.setFreeObject(task)) {
                                successCount.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        int totalOperations = threadCount * operationsPerThread;
        int totalSuccess = successCount.get();
        
        System.out.printf("Stress test results: success=%d, total=%d, success rate=%.2f%%%n", 
            totalSuccess, totalOperations, (double) totalSuccess / totalOperations * 100);
        
        assertTrue(totalSuccess > totalOperations * 0.8, "Успешность должна быть выше 80%");
    }
    
    @Test
    void testStripedTailDistribution() {
        // Проверяем, что striped tail правильно распределяет нагрузку
        Set<Integer> usedIndices = new HashSet<>();
        
        // Получаем объекты и проверяем их индексы
        for (int i = 0; i < Math.min(POOL_SIZE, 100); i++) {
            ProcessTask task = pool.getFreeObject();
            if (task != null) {
                // Получаем индекс объекта
                for (int j = 0; j < POOL_SIZE; j++) {
                    if (pool.getObject(j) == task) {
                        usedIndices.add(j);
                        break;
                    }
                }
                pool.setFreeObject(task);
            }
        }
        
        // Проверяем, что используются разные индексы (striped tail работает)
        assertTrue(usedIndices.size() > 1, "Striped tail должен использовать разные индексы");
        System.out.printf("Used indices count: %d%n", usedIndices.size());
    }
    
    @Test
    void testStatistics() {
        // Проверяем статистику пула
        BitmaskRingBufferUltraVarHandleStriped.PoolStats stats = pool.getStats();
        
        assertEquals(POOL_SIZE, stats.capacity, "Емкость должна совпадать");
        assertEquals(POOL_SIZE, stats.freeCount, "Все объекты должны быть свободны изначально");
        assertEquals(0, stats.busyCount, "Занятых объектов быть не должно");
        assertEquals(0, stats.totalGets, "Количество получений должно быть 0");
        assertEquals(0, stats.totalReturns, "Количество возвратов должно быть 0");
        
        // Получаем и возвращаем объект
        ProcessTask task = pool.getFreeObject();
        assertNotNull(task);
        pool.setFreeObject(task);
        
        stats = pool.getStats();
        assertEquals(1, stats.totalGets, "Количество получений должно быть 1");
        assertEquals(1, stats.totalReturns, "Количество возвратов должно быть 1");
        assertTrue(stats.stackHits > 0 || stats.stripedTailHits > 0 || stats.bitTrickHits > 0, 
            "Должны быть hits в одном из методов поиска");
    }
    
    @Test
    void testMarkForUpdate() {
        ProcessTask task = pool.getFreeObject();
        assertNotNull(task);
        
        // Помечаем для обновления
        assertTrue(pool.markForUpdate(task), "Должен успешно пометить объект для обновления");
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandleStriped.PoolStats stats = pool.getStats();
        assertEquals(1, stats.totalUpdates, "Количество обновлений должно быть 1");
        
        // Возвращаем объект
        pool.setFreeObject(task);
    }
    
    @Test
    void testGetBusyObjects() {
        Set<ProcessTask> busyObjects = new HashSet<>();
        
        // Получаем несколько объектов
        for (int i = 0; i < 5; i++) {
            ProcessTask task = pool.getFreeObject();
            if (task != null) {
                busyObjects.add(task);
            }
        }
        
        // Проверяем список занятых объектов
        var busyList = pool.getBusyObjects();
        assertEquals(busyObjects.size(), busyList.size(), "Количество занятых объектов должно совпадать");
        
        // Возвращаем объекты
        for (ProcessTask task : busyObjects) {
            pool.setFreeObject(task);
        }
    }
    
    @Test
    void testGetObjectsForUpdate() {
        Set<ProcessTask> updateObjects = new HashSet<>();
        
        // Получаем и помечаем несколько объектов для обновления
        for (int i = 0; i < 3; i++) {
            ProcessTask task = pool.getFreeObject();
            if (task != null) {
                pool.markForUpdate(task);
                updateObjects.add(task);
            }
        }
        
        // Проверяем список объектов для обновления
        var updateList = pool.getObjectsForUpdate();
        assertEquals(updateObjects.size(), updateList.size(), "Количество объектов для обновления должно совпадать");
        
        // Возвращаем объекты
        for (ProcessTask task : updateObjects) {
            pool.setFreeObject(task);
        }
    }
} 