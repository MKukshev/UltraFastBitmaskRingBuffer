package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демонстрация Auto-Expanding Pool с конфигурируемым расширением
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpandExample {
    
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
        System.out.println("=== Демонстрация Auto-Expanding Pool с конфигурируемым расширением ===\n");
        
        // Демонстрация различных конфигураций расширения
        demonstrateExpansionConfigurations();
        
        // Демонстрация производительности с разными настройками
        demonstratePerformanceComparison();
    }
    
    private static void demonstrateExpansionConfigurations() {
        System.out.println("--- Демонстрация различных конфигураций расширения ---\n");
        
        int initialCapacity = 10;
        AtomicInteger objectId = new AtomicInteger(0);
        
        // Конфигурация 1: Консервативное расширение (10%, максимум 50%)
        System.out.println("1. Консервативное расширение (10%, максимум 50%):");
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> conservativePool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet()),
                0.1, // 10% расширение
                50   // максимум 50%
            );
        printPoolConfiguration(conservativePool);
        
        // Конфигурация 2: Умеренное расширение (20%, максимум 100%) - по умолчанию
        System.out.println("\n2. Умеренное расширение (20%, максимум 100%) - по умолчанию:");
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> moderatePool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet())
                // Использует значения по умолчанию: 20%, максимум 100%
            );
        printPoolConfiguration(moderatePool);
        
        // Конфигурация 3: Агрессивное расширение (50%, максимум 200%)
        System.out.println("\n3. Агрессивное расширение (50%, максимум 200%):");
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> aggressivePool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet()),
                0.5, // 50% расширение
                200  // максимум 200%
            );
        printPoolConfiguration(aggressivePool);
        
        // Конфигурация 4: Максимальное расширение (100%, максимум 500%)
        System.out.println("\n4. Максимальное расширение (100%, максимум 500%):");
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> maxPool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet()),
                1.0, // 100% расширение
                500  // максимум 500%
            );
        printPoolConfiguration(maxPool);
        
        // Очистка
        conservativePool.cleanup();
        moderatePool.cleanup();
        aggressivePool.cleanup();
        maxPool.cleanup();
    }
    
    private static void printPoolConfiguration(BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool) {
        System.out.println("  - Начальная емкость: " + pool.getInitialCapacity());
        System.out.println("  - Процент расширения: " + (pool.getExpansionPercentage() * 100) + "%");
        System.out.println("  - Максимальный процент расширения: " + pool.getMaxExpansionPercentage() + "%");
        System.out.println("  - Максимально допустимая емкость: " + pool.getMaxAllowedCapacity());
        System.out.println("  - Текущая емкость: " + pool.getCapacity());
    }
    
    private static void demonstratePerformanceComparison() {
        System.out.println("\n--- Сравнение производительности с разными настройками расширения ---\n");
        
        int initialCapacity = 10;
        AtomicInteger objectId = new AtomicInteger(0);
        
        // Тестируем разные конфигурации
        testConfiguration("Консервативное (10%, 50%)", 0.1, 50, initialCapacity, objectId);
        testConfiguration("Умеренное (20%, 100%)", 0.2, 100, initialCapacity, objectId);
        testConfiguration("Агрессивное (50%, 200%)", 0.5, 200, initialCapacity, objectId);
        testConfiguration("Максимальное (100%, 500%)", 1.0, 500, initialCapacity, objectId);
    }
    
    private static void testConfiguration(String name, double expansionPercentage, int maxExpansionPercentage, 
                                        int initialCapacity, AtomicInteger objectId) {
        System.out.println(name + ":");
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectId.incrementAndGet()),
                expansionPercentage,
                maxExpansionPercentage
            );
        
        // Запускаем тест с высокой нагрузкой
        long startTime = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(20);
        
        for (int i = 0; i < 20; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            // Минимальная задержка
                            Thread.sleep(0, 1000);
                            pool.setFreeObject(obj);
                        }
                        
                        // Показываем прогресс каждые 200 операций
                        if (j % 200 == 0) {
                            BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
                            System.out.println("    Поток " + threadId + ", операция " + j + 
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
        System.out.println("  - Начальная емкость: " + pool.getInitialCapacity());
        System.out.println("  - Финальная емкость: " + stats.capacity);
        System.out.println("  - Общее количество расширений: " + stats.totalExpansions);
        System.out.println("  - Количество обращений к auto-expansion: " + stats.autoExpansionHits);
        System.out.println("  - Общее количество получений: " + stats.totalGets);
        System.out.println("  - Общее количество возвратов: " + stats.totalReturns);
        System.out.println("  - Свободных объектов: " + stats.freeCount);
        System.out.println("  - Занятых объектов: " + stats.busyCount);
        System.out.println("  - Hits по stack: " + stats.stackHits);
        System.out.println("  - Hits по striped tail: " + stats.stripedTailHits);
        System.out.println("  - Hits по bit tricks: " + stats.bitTrickHits);
        System.out.println();
        
        executor.shutdown();
        pool.cleanup();
    }
} 