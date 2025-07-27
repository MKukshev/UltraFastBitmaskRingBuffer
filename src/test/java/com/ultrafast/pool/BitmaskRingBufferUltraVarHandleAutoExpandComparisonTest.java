package com.ultrafast.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Тесты сравнения BitmaskRingBufferUltraVarHandleAutoExpand с оригинальной версией
 */
public class BitmaskRingBufferUltraVarHandleAutoExpandComparisonTest {
    
    @Test
    @DisplayName("Сравнение производительности с оригинальной версией")
    void testPerformanceComparison() throws InterruptedException {
        int initialCapacity = 100;
        int threadCount = 4;
        int operationsPerThread = 10000;
        
        // Создаем пулы для сравнения
        BitmaskRingBufferUltraVarHandle<TestObject> originalPool = 
            new BitmaskRingBufferUltraVarHandle<>(initialCapacity, TestObject::new);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> autoExpandPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.2, 500);
        
        // Тестируем оригинальную версию
        long originalTime = testOriginalPoolPerformance(originalPool, threadCount, operationsPerThread, "Оригинальная версия");
        
        // Тестируем версию с auto expand
        long autoExpandTime = testAutoExpandPoolPerformance(autoExpandPool, threadCount, operationsPerThread, "Auto Expand версия");
        
        // Сравниваем результаты
        System.out.printf("Сравнение производительности:%n");
        System.out.printf("Оригинальная версия: %.2f мс%n", originalTime / 1_000_000.0);
        System.out.printf("Auto Expand версия: %.2f мс%n", autoExpandTime / 1_000_000.0);
        System.out.printf("Разница: %.2f%%%n", 
                         ((double) (autoExpandTime - originalTime) / originalTime) * 100);
        
        // Проверяем статистику auto expand версии
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = autoExpandPool.getStats();
        System.out.printf("Auto Expand статистика: %s%n", stats);
        
        // Auto expand версия должна работать не хуже оригинальной (с учетом расширения)
        assertTrue(autoExpandTime <= originalTime * 1.5, 
                  "Auto expand версия не должна быть значительно медленнее оригинальной");
    }
    
    @Test
    @DisplayName("Сравнение поведения при исчерпании пула")
    void testExhaustionBehaviorComparison() {
        int initialCapacity = 10;
        int operations = 1000;
        
        // Создаем пулы
        BitmaskRingBufferUltraVarHandle<TestObject> originalPool = 
            new BitmaskRingBufferUltraVarHandle<>(initialCapacity, TestObject::new);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> autoExpandPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 0.5, 200);
        
        // Тестируем оригинальную версию
        int originalSuccessfulGets = 0;
        for (int i = 0; i < operations; i++) {
            TestObject obj = originalPool.getFreeObject();
            if (obj != null) {
                originalSuccessfulGets++;
                originalPool.setFreeObject(obj);
            }
        }
        
        // Тестируем auto expand версию
        int autoExpandSuccessfulGets = 0;
        for (int i = 0; i < operations; i++) {
            TestObject obj = autoExpandPool.getFreeObject();
            if (obj != null) {
                autoExpandSuccessfulGets++;
                autoExpandPool.setFreeObject(obj);
            }
        }
        
        System.out.printf("Сравнение при исчерпании пула:%n");
        System.out.printf("Оригинальная версия: %d успешных получений из %d попыток%n", 
                         originalSuccessfulGets, operations);
        System.out.printf("Auto Expand версия: %d успешных получений из %d попыток%n", 
                         autoExpandSuccessfulGets, operations);
        
        // Auto expand версия должна иметь больше успешных получений
        assertTrue(autoExpandSuccessfulGets >= originalSuccessfulGets, 
                  "Auto expand версия должна иметь не меньше успешных получений");
        
        // Проверяем, что auto expand версия расширилась
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = autoExpandPool.getStats();
        assertTrue(stats.totalExpansions > 0, "Должны произойти расширения");
        assertTrue(stats.capacity > initialCapacity, "Емкость должна увеличиться");
    }
    
    @Test
    @DisplayName("Сравнение памяти и производительности при различных сценариях")
    void testMemoryAndPerformanceScenarios() {
        int initialCapacity = 50;
        int operations = 10000;
        
        // Сценарий 1: Нормальная нагрузка
        System.out.println("=== Сценарий 1: Нормальная нагрузка ===");
        testScenario(initialCapacity, operations, 0.1, "Нормальная нагрузка");
        
        // Сценарий 2: Высокая нагрузка
        System.out.println("=== Сценарий 2: Высокая нагрузка ===");
        testScenario(initialCapacity, operations, 0.5, "Высокая нагрузка");
        
        // Сценарий 3: Очень высокая нагрузка
        System.out.println("=== Сценарий 3: Очень высокая нагрузка ===");
        testScenario(initialCapacity, operations, 0.9, "Очень высокая нагрузка");
    }
    
    @Test
    @DisplayName("Сравнение API совместимости")
    void testApiCompatibility() {
        int initialCapacity = 10;
        
        // Создаем пулы
        BitmaskRingBufferUltraVarHandle<TestObject> originalPool = 
            new BitmaskRingBufferUltraVarHandle<>(initialCapacity, TestObject::new);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> autoExpandPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new);
        
        // Проверяем совместимость основных методов
        TestObject originalObj = originalPool.getFreeObject();
        TestObject autoExpandObj = autoExpandPool.getFreeObject();
        
        assertNotNull(originalObj, "Оригинальная версия должна возвращать объект");
        assertNotNull(autoExpandObj, "Auto expand версия должна возвращать объект");
        
        assertTrue(originalPool.setFreeObject(originalObj), "Оригинальная версия должна принимать объект");
        assertTrue(autoExpandPool.setFreeObject(autoExpandObj), "Auto expand версия должна принимать объект");
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandle.PoolStats originalStats = originalPool.getStats();
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats autoExpandStats = autoExpandPool.getStats();
        
        assertEquals(originalStats.capacity, autoExpandStats.capacity, "Емкости должны совпадать");
        assertEquals(originalStats.totalGets, autoExpandStats.totalGets, "Количество получений должно совпадать");
        assertEquals(originalStats.totalReturns, autoExpandStats.totalReturns, "Количество возвратов должно совпадать");
        
        // Проверяем дополнительные методы auto expand версии
        assertEquals(initialCapacity, autoExpandPool.getInitialCapacity(), "Начальная емкость должна совпадать");
        assertEquals(0.2, autoExpandPool.getExpansionPercentage(), "Процент расширения по умолчанию должен быть 0.2");
        assertEquals(100, autoExpandPool.getMaxExpansionPercentage(), "Максимальный процент расширения по умолчанию должен быть 100");
    }
    
    @Test
    @DisplayName("Сравнение поведения при граничных условиях")
    void testEdgeCaseBehaviorComparison() {
        int initialCapacity = 1;
        
        // Создаем пулы с минимальной емкостью
        BitmaskRingBufferUltraVarHandle<TestObject> originalPool = 
            new BitmaskRingBufferUltraVarHandle<>(initialCapacity, TestObject::new);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> autoExpandPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, 1.0, 1000);
        
        // Получаем объекты
        TestObject originalObj = originalPool.getFreeObject();
        TestObject autoExpandObj = autoExpandPool.getFreeObject();
        
        // Пытаемся получить еще объекты
        TestObject originalObj2 = originalPool.getFreeObject();
        TestObject autoExpandObj2 = autoExpandPool.getFreeObject();
        
        // Оригинальная версия должна вернуть null, auto expand версия должна расшириться
        assertNull(originalObj2, "Оригинальная версия должна вернуть null при исчерпании");
        assertNotNull(autoExpandObj2, "Auto expand версия должна расшириться и вернуть объект");
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = autoExpandPool.getStats();
        assertEquals(1, stats.totalExpansions, "Должно произойти одно расширение");
        assertTrue(stats.capacity > initialCapacity, "Емкость должна увеличиться");
        
        // Возвращаем объекты
        originalPool.setFreeObject(originalObj);
        autoExpandPool.setFreeObject(autoExpandObj);
        autoExpandPool.setFreeObject(autoExpandObj2);
    }
    
    /**
     * Вспомогательный метод для тестирования производительности оригинального пула
     */
    private long testOriginalPoolPerformance(BitmaskRingBufferUltraVarHandle<TestObject> pool, 
                                           int threadCount, int operationsPerThread, String poolName) 
                                           throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            obj.performWork();
                            if (pool.setFreeObject(obj)) {
                                successfulOperations.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long duration = endTime - startTime;
        
        System.out.printf("%s: %d операций за %.2f мс (%.2f оп/мс)%n", 
                         poolName, successfulOperations.get(), 
                         duration / 1_000_000.0,
                         (double) successfulOperations.get() / (duration / 1_000_000.0));
        
        return duration;
    }
    
    /**
     * Вспомогательный метод для тестирования производительности auto expand пула
     */
    private long testAutoExpandPoolPerformance(BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool, 
                                             int threadCount, int operationsPerThread, String poolName) 
                                             throws InterruptedException {
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            obj.performWork();
                            if (pool.setFreeObject(obj)) {
                                successfulOperations.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        long endTime = System.nanoTime();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        long duration = endTime - startTime;
        
        System.out.printf("%s: %d операций за %.2f мс (%.2f оп/мс)%n", 
                         poolName, successfulOperations.get(), 
                         duration / 1_000_000.0,
                         (double) successfulOperations.get() / (duration / 1_000_000.0));
        
        return duration;
    }
    
    /**
     * Вспомогательный метод для тестирования сценария
     */
    private void testScenario(int initialCapacity, int operations, double loadFactor, String scenarioName) {
        // Создаем пул с настраиваемой нагрузкой
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, TestObject::new, loadFactor, 500);
        
        long startTime = System.nanoTime();
        
        List<TestObject> objects = new ArrayList<>();
        int successfulGets = 0;
        
        // Выполняем операции с имитацией нагрузки
        for (int i = 0; i < operations; i++) {
            TestObject obj = pool.getFreeObject();
            if (obj != null) {
                objects.add(obj);
                successfulGets++;
                
                // Возвращаем объекты в зависимости от нагрузки
                if (Math.random() < (1.0 - loadFactor)) {
                    if (!objects.isEmpty()) {
                        TestObject returnObj = objects.remove(objects.size() - 1);
                        pool.setFreeObject(returnObj);
                    }
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
        
        System.out.printf("%s: %d успешных получений за %.2f мс (%.2f оп/мс)%n", 
                         scenarioName, successfulGets, 
                         duration / 1_000_000.0,
                         (double) successfulGets / (duration / 1_000_000.0));
        System.out.printf("Расширений: %d, финальная емкость: %d%n", 
                         stats.totalExpansions, stats.capacity);
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
            for (int i = 0; i < 50; i++) {
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