package com.ultrafast.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/**
 * Тесты производительности для BitmaskRingBufferUltraVarHandleAutoExpand
 */
public class BitmaskRingBufferUltraVarHandleAutoExpandPerformanceTest {
    
    @Test
    @DisplayName("Тест производительности при высокой нагрузке")
    void testHighLoadPerformance() throws InterruptedException {
        int initialCapacity = 100;
        int threadCount = 8;
        int operationsPerThread = 10000;
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.2, 500);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                long threadStartTime = System.nanoTime();
                int localSuccessCount = 0;
                
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            // Имитируем работу с объектом
                            obj.performWork();
                            if (pool.setFreeObject(obj)) {
                                localSuccessCount++;
                            }
                        }
                    }
                } finally {
                    long threadEndTime = System.nanoTime();
                    totalTime.addAndGet(threadEndTime - threadStartTime);
                    successfulOperations.addAndGet(localSuccessCount);
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long totalDuration = endTime - startTime;
        long avgThreadTime = totalTime.get() / threadCount;
        
        // Проверяем результаты
        assertEquals(threadCount * operationsPerThread, successfulOperations.get(), 
                    "Все операции должны быть успешными");
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        assertTrue(stats.totalExpansions > 0, "Должны произойти расширения при высокой нагрузке");
        assertTrue(stats.capacity > initialCapacity, "Емкость должна увеличиться");
        
        System.out.printf("Производительность: %d операций за %.2f мс (%.2f оп/мс)%n", 
                         successfulOperations.get(), 
                         totalDuration / 1_000_000.0,
                         (double) successfulOperations.get() / (totalDuration / 1_000_000.0));
        System.out.printf("Среднее время потока: %.2f мс%n", avgThreadTime / 1_000_000.0);
        System.out.printf("Статистика пула: %s%n", stats);
    }
    
    @Test
    @DisplayName("Тест производительности расширения пула")
    void testExpansionPerformance() {
        int initialCapacity = 10;
        int targetOperations = 100000;
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.5, 1000);
        
        long startTime = System.nanoTime();
        
        // Выполняем операции, которые вызовут множество расширений
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < targetOperations; i++) {
            TestObject obj = pool.getFreeObject();
            assertNotNull(obj, "Объект должен быть получен");
            objects.add(obj);
            
            // Возвращаем каждый 10-й объект для создания давления
            if (i % 10 == 0 && !objects.isEmpty()) {
                TestObject returnObj = objects.remove(objects.size() - 1);
                assertTrue(pool.setFreeObject(returnObj), "Объект должен быть возвращен");
            }
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        // Возвращаем оставшиеся объекты
        for (TestObject obj : objects) {
            pool.setFreeObject(obj);
        }
        
        // Проверяем результаты
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        assertTrue(stats.totalExpansions > 0, "Должны произойти расширения");
        assertTrue(stats.capacity > initialCapacity, "Емкость должна увеличиться");
        
        System.out.printf("Расширение пула: %d операций за %.2f мс (%.2f оп/мс)%n", 
                         targetOperations, 
                         duration / 1_000_000.0,
                         (double) targetOperations / (duration / 1_000_000.0));
        System.out.printf("Количество расширений: %d%n", stats.totalExpansions);
        System.out.printf("Финальная емкость: %d%n", stats.capacity);
    }
    
    @Test
    @DisplayName("Тест производительности при различных размерах пула")
    void testDifferentPoolSizes() {
        int[] poolSizes = {10, 100, 1000, 10000};
        int operationsPerSize = 10000;
        
        for (int size : poolSizes) {
            BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
                new BitmaskRingBufferUltraVarHandleAutoExpand<>(size, TestObject::new, 0.2, 200);
            
            long startTime = System.nanoTime();
            
            // Выполняем операции
            List<TestObject> objects = new ArrayList<>();
            for (int i = 0; i < operationsPerSize; i++) {
                TestObject obj = pool.getFreeObject();
                assertNotNull(obj, "Объект должен быть получен");
                objects.add(obj);
            }
            
            // Возвращаем объекты
            for (TestObject obj : objects) {
                assertTrue(pool.setFreeObject(obj), "Объект должен быть возвращен");
            }
            
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
            
            System.out.printf("Размер пула %d: %d операций за %.2f мс (%.2f оп/мс), расширений: %d%n", 
                             size, operationsPerSize, 
                             duration / 1_000_000.0,
                             (double) operationsPerSize / (duration / 1_000_000.0),
                             stats.totalExpansions);
        }
    }
    
    @Test
    @DisplayName("Тест производительности при различных процентах расширения")
    void testDifferentExpansionPercentages() {
        int initialCapacity = 100;
        int operations = 50000;
        double[] expansionPercentages = {0.1, 0.2, 0.5, 1.0};
        
        for (double percentage : expansionPercentages) {
            BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
                new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, percentage, 500);
            
            long startTime = System.nanoTime();
            
            // Выполняем операции
            List<TestObject> objects = new ArrayList<>();
            for (int i = 0; i < operations; i++) {
                TestObject obj = pool.getFreeObject();
                assertNotNull(obj, "Объект должен быть получен");
                objects.add(obj);
            }
            
            // Возвращаем объекты
            for (TestObject obj : objects) {
                assertTrue(pool.setFreeObject(obj), "Объект должен быть возвращен");
            }
            
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
            
            System.out.printf("Расширение %.1f%%: %d операций за %.2f мс (%.2f оп/мс), расширений: %d, финальная емкость: %d%n", 
                             percentage * 100, operations, 
                             duration / 1_000_000.0,
                             (double) operations / (duration / 1_000_000.0),
                             stats.totalExpansions, stats.capacity);
        }
    }
    
    @RepeatedTest(5)
    @DisplayName("Стресс-тест с повторениями")
    void stressTest() throws InterruptedException {
        int initialCapacity = 50;
        int threadCount = 16;
        int operationsPerThread = 5000;
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.3, 300);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            // Имитируем работу с объектом
                            obj.performWork();
                            if (pool.setFreeObject(obj)) {
                                successfulOperations.incrementAndGet();
                            } else {
                                failedOperations.incrementAndGet();
                            }
                        } else {
                            failedOperations.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Проверяем результаты
        int totalOperations = threadCount * operationsPerThread;
        assertEquals(totalOperations, successfulOperations.get() + failedOperations.get(), 
                    "Общее количество операций должно совпадать");
        
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        
        System.out.printf("Стресс-тест: успешно %d, неудачно %d, расширений %d, емкость %d%n", 
                         successfulOperations.get(), failedOperations.get(), 
                         stats.totalExpansions, stats.capacity);
        
        // Проверяем, что пул все еще работает корректно
        TestObject testObj = pool.getFreeObject();
        assertNotNull(testObj, "Пул должен продолжать работать после стресс-теста");
        assertTrue(pool.setFreeObject(testObj), "Объект должен быть возвращен");
    }
    
    @Test
    @DisplayName("Тест производительности при достижении максимального расширения")
    void testMaxExpansionPerformance() {
        int initialCapacity = 10;
        int maxExpansionPercentage = 50; // Максимум 50% расширения
        int operations = 100000;
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.2, maxExpansionPercentage);
        
        long startTime = System.nanoTime();
        
        List<TestObject> objects = new ArrayList<>();
        int successfulGets = 0;
        
        // Выполняем операции до достижения максимума
        for (int i = 0; i < operations; i++) {
            TestObject obj = pool.getFreeObject();
            if (obj != null) {
                objects.add(obj);
                successfulGets++;
            } else {
                // Достигли максимума, возвращаем некоторые объекты
                if (!objects.isEmpty()) {
                    TestObject returnObj = objects.remove(objects.size() - 1);
                    pool.setFreeObject(returnObj);
                }
            }
        }
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        // Возвращаем оставшиеся объекты
        for (TestObject obj : objects) {
            pool.setFreeObject(obj);
        }
        
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        
        System.out.printf("Максимальное расширение: %d успешных получений за %.2f мс (%.2f оп/мс)%n", 
                         successfulGets, duration / 1_000_000.0,
                         (double) successfulGets / (duration / 1_000_000.0));
        System.out.printf("Расширений: %d, финальная емкость: %d, максимум: %d%n", 
                         stats.totalExpansions, stats.capacity, pool.getMaxAllowedCapacity());
        
        assertTrue(stats.capacity <= pool.getMaxAllowedCapacity(), 
                  "Емкость не должна превышать максимально допустимую");
    }
    
    /**
     * Тестовый объект для пула с имитацией работы
     */
    private static class TestObject {
        private final int id;
        private static int nextId = 0;
        private volatile boolean working = false;
        
        public TestObject() {
            this.id = nextId++;
        }
        
        public int getId() {
            return id;
        }
        
        public void performWork() {
            working = true;
            // Имитируем работу
            for (int i = 0; i < 100; i++) {
                Math.sqrt(i);
            }
            working = false;
        }
        
        public boolean isWorking() {
            return working;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + ", working=" + working + "}";
        }
    }
} 