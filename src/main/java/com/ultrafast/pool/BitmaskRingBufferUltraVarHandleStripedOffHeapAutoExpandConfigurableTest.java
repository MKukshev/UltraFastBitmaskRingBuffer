package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Тест для демонстрации конфигурируемого расширения пула
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpandConfigurableTest {
    
    static class TestObject {
        private final int id;
        private final long creationTime;
        
        public TestObject(int id) {
            this.id = id;
            this.creationTime = System.currentTimeMillis();
        }
        
        public int getId() {
            return id;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + ", created=" + creationTime + "}";
        }
    }
    
    public static void main(String[] args) {
        System.out.println("=== Демонстрация конфигурируемого Auto-Expanding Pool ===\n");
        
        // Тест 1: 10% расширение, максимум 50%
        testConfigurableExpansion("10% расширение, максимум 50%", 0.1, 50);
        
        // Тест 2: 20% расширение, максимум 100%
        testConfigurableExpansion("20% расширение, максимум 100%", 0.2, 100);
        
        // Тест 3: 50% расширение, максимум 200%
        testConfigurableExpansion("50% расширение, максимум 200%", 0.5, 200);
        
        // Тест 4: 100% расширение, максимум 500%
        testConfigurableExpansion("100% расширение, максимум 500%", 1.0, 500);
    }
    
    private static void testConfigurableExpansion(String description, double expansionPercentage, int maxExpansionPercentage) {
        System.out.println("--- " + description + " ---");
        
        int initialCapacity = 5; // Уменьшаем начальную емкость для более быстрого расширения
        AtomicInteger objectId = new AtomicInteger(0);
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet()),
                expansionPercentage,
                maxExpansionPercentage
            );
        
        System.out.println("Начальная емкость: " + pool.getInitialCapacity());
        System.out.println("Процент расширения: " + (pool.getExpansionPercentage() * 100) + "%");
        System.out.println("Максимальный процент расширения: " + pool.getMaxExpansionPercentage() + "%");
        System.out.println("Максимально допустимая емкость: " + pool.getMaxAllowedCapacity());
        System.out.println();
        
        // Запускаем агрессивный тест с высокой нагрузкой
        testAggressiveLoad(pool, 10, 200);
        
        // Финальная статистика
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
        System.out.println("Финальная статистика:");
        System.out.println("  - Текущая емкость: " + stats.capacity);
        System.out.println("  - Свободных объектов: " + stats.freeCount);
        System.out.println("  - Занятых объектов: " + stats.busyCount);
        System.out.println("  - Всего расширений: " + stats.totalExpansions);
        System.out.println("  - Обращений к auto-expansion: " + stats.autoExpansionHits);
        System.out.println("  - Процент расширения: " + (stats.expansionPercentage * 100) + "%");
        System.out.println("  - Максимальный процент: " + stats.maxExpansionPercentage + "%");
        System.out.println("  - Максимальная емкость: " + stats.maxAllowedCapacity);
        System.out.println();
        
        pool.cleanup();
    }
    
    private static void testAggressiveLoad(BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool, 
                                         int threadCount, int operationsPerThread) {
        System.out.println("Агрессивный тест с " + threadCount + " потоками, " + operationsPerThread + " операций на поток:");
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            // Минимальная задержка для имитации работы
                            Thread.sleep(0, 1000); // 1 микросекунда
                            pool.setFreeObject(obj);
                        }
                        
                        // Показываем прогресс каждые 50 операций
                        if (j % 50 == 0) {
                            BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
                            System.out.println("  Поток " + threadId + ", операция " + j + 
                                             ": емкость=" + stats.capacity + 
                                             ", свободно=" + stats.freeCount + 
                                             ", занято=" + stats.busyCount + 
                                             ", расширений=" + stats.totalExpansions);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
        System.out.println("  - Время выполнения: " + duration + " мс");
        System.out.println("  - Текущая емкость: " + stats.capacity);
        System.out.println("  - Свободных объектов: " + stats.freeCount);
        System.out.println("  - Занятых объектов: " + stats.busyCount);
        System.out.println("  - Всего расширений: " + stats.totalExpansions);
        System.out.println("  - Обращений к auto-expansion: " + stats.autoExpansionHits);
        
        executor.shutdown();
        System.out.println();
    }
} 