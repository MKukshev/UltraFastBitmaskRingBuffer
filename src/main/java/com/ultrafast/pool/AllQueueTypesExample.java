package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Примеры использования всех типов BitmaskRingBuffer
 * Демонстрирует особенности и производительность каждой реализации
 */
public class AllQueueTypesExample {
    
    public static void main(String[] args) {
        System.out.println("=== Примеры использования всех типов BitmaskRingBuffer ===\n");
        
        // Пример 1: Original BitmaskRingBuffer
        originalExample();
        
        // Пример 2: Optimized BitmaskRingBuffer (VarHandle)
        optimizedExample();
        
        // Пример 3: Off-Heap BitmaskRingBuffer
        offHeapExample();
        
        // Пример 4: Bit Tricks BitmaskRingBuffer
        bitTricksExample();
        
        // Пример 5: Ultra BitmaskRingBuffer
        ultraExample();
        
        // Пример 6: Minimal BitmaskRingBuffer
        minimalExample();
        
        // Пример 7: Off-Heap + Lock-Free Stack
        offHeapStackExample();
        
        // Пример 8: Bit Tricks + Lock-Free Stack
        bitTricksStackExample();
        
        // Пример 9: Ultra + Lock-Free Stack
        ultraStackExample();
        
        // Пример 10: Ultra + VarHandle (Unsafe replacement)
        ultraVarHandleExample();
        
        // Пример 11: Сравнение производительности
        performanceComparisonExample();
        
        System.out.println("\n=== Все примеры завершены ===");
    }
    
    /**
     * Пример 1: Original BitmaskRingBuffer
     */
    private static void originalExample() {
        System.out.println("--- 1. Original BitmaskRingBuffer ---");
        System.out.println("Особенности: Базовая реализация с AtomicReferenceArray");
        
        BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
            1024, 
            () -> new ProcessTask("Original-" + System.nanoTime())
        );
        
        demonstrateBasicUsage(pool, "Original");
        System.out.println();
    }
    
    /**
     * Пример 2: Optimized BitmaskRingBuffer (VarHandle)
     */
    private static void optimizedExample() {
        System.out.println("--- 2. Optimized BitmaskRingBuffer (VarHandle) ---");
        System.out.println("Особенности: Оптимизированный доступ через VarHandle");
        
        BitmaskRingBufferOptimized<Task> pool = new BitmaskRingBufferOptimized<>(
            1024, 
            () -> new ProcessTask("Optimized-" + System.nanoTime())
        );
        
        demonstrateBasicUsage(pool, "Optimized");
        System.out.println();
    }
    
    /**
     * Пример 3: Off-Heap BitmaskRingBuffer
     */
    private static void offHeapExample() {
        System.out.println("--- 3. Off-Heap BitmaskRingBuffer ---");
        System.out.println("Особенности: Off-heap bitmasks, отсутствие GC пауз");
        
        BitmaskRingBufferOffHeap<Task> pool = new BitmaskRingBufferOffHeap<>(
            1024, 
            () -> new ProcessTask("OffHeap-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "Off-Heap");
            demonstrateOffHeapFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем off-heap память
        }
        System.out.println();
    }
    
    /**
     * Пример 4: Bit Tricks BitmaskRingBuffer
     */
    private static void bitTricksExample() {
        System.out.println("--- 4. Bit Tricks BitmaskRingBuffer ---");
        System.out.println("Особенности: O(1) поиск свободных слотов через Long.numberOfTrailingZeros");
        
        BitmaskRingBufferBitTricks<Task> pool = new BitmaskRingBufferBitTricks<>(
            1024, 
            () -> new ProcessTask("BitTricks-" + System.nanoTime())
        );
        
        demonstrateBasicUsage(pool, "BitTricks");
        System.out.println();
    }
    
    /**
     * Пример 5: Ultra BitmaskRingBuffer
     */
    private static void ultraExample() {
        System.out.println("--- 5. Ultra BitmaskRingBuffer ---");
        System.out.println("Особенности: Off-heap + Bit Tricks + Lock-free stack");
        
        BitmaskRingBufferUltra<Task> pool = new BitmaskRingBufferUltra<>(
            1024, 
            () -> new ProcessTask("Ultra-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "Ultra");
            demonstrateUltraFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем off-heap память
        }
        System.out.println();
    }
    
    /**
     * Пример 6: Minimal BitmaskRingBuffer
     */
    private static void minimalExample() {
        System.out.println("--- 6. Minimal BitmaskRingBuffer ---");
        System.out.println("Особенности: Минимальное потребление памяти, без updateMask");
        
        BitmaskRingBufferMinimal<Task> pool = new BitmaskRingBufferMinimal<>(
            1024, 
            () -> new ProcessTask("Minimal-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "Minimal");
            demonstrateMinimalFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем off-heap память
        }
        System.out.println();
    }
    
    /**
     * Пример 7: Off-Heap + Lock-Free Stack
     */
    private static void offHeapStackExample() {
        System.out.println("--- 7. Off-Heap + Lock-Free Stack ---");
        System.out.println("Особенности: Off-heap bitmasks + Lock-free stack для свободных индексов");
        
        BitmaskRingBufferOffHeapStack<Task> pool = new BitmaskRingBufferOffHeapStack<>(
            1024, 
            () -> new ProcessTask("OffHeapStack-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "OffHeapStack");
            demonstrateStackFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем off-heap память
        }
        System.out.println();
    }
    
    /**
     * Пример 8: Bit Tricks + Lock-Free Stack
     */
    private static void bitTricksStackExample() {
        System.out.println("--- 8. Bit Tricks + Lock-Free Stack ---");
        System.out.println("Особенности: Bit tricks + Lock-free stack для быстрого доступа");
        
        BitmaskRingBufferBitTricksStack<Task> pool = new BitmaskRingBufferBitTricksStack<>(
            1024, 
            () -> new ProcessTask("BitTricksStack-" + System.nanoTime())
        );
        
        demonstrateBasicUsage(pool, "BitTricksStack");
        demonstrateStackFeatures(pool);
        System.out.println();
    }
    
    /**
     * Пример 9: Ultra + Lock-Free Stack
     */
    private static void ultraStackExample() {
        System.out.println("--- 9. Ultra + Lock-Free Stack ---");
        System.out.println("Особенности: Off-heap + Bit Tricks + Lock-free stack (максимальная производительность)");
        
        BitmaskRingBufferUltraStack<Task> pool = new BitmaskRingBufferUltraStack<>(
            1024, 
            () -> new ProcessTask("UltraStack-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "UltraStack");
            demonstrateUltraStackFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем off-heap память
        }
        System.out.println();
    }
    
    /**
     * Пример 10: Ultra + VarHandle (Unsafe replacement)
     */
    private static void ultraVarHandleExample() {
        System.out.println("--- 10. Ultra + VarHandle (Unsafe replacement) ---");
        System.out.println("Особенности: Off-heap + Bit Tricks + Lock-free stack + VarHandle (безопасная замена Unsafe)");
        
        BitmaskRingBufferUltraVarHandle<Task> pool = new BitmaskRingBufferUltraVarHandle<>(
            1024, 
            () -> new ProcessTask("UltraVarHandle-" + System.nanoTime())
        );
        
        try {
            demonstrateBasicUsage(pool, "UltraVarHandle");
            demonstrateUltraVarHandleFeatures(pool);
        } finally {
            pool.cleanup(); // Важно: освобождаем память
        }
        System.out.println();
    }
    
    /**
     * Демонстрирует особенности UltraVarHandle версии
     */
    private static void demonstrateUltraVarHandleFeatures(BitmaskRingBufferUltraVarHandle<Task> pool) {
        System.out.println("Особенности UltraVarHandle:");
        System.out.println("  Использует VarHandle вместо Unsafe для совместимости с будущими версиями Java");
        System.out.println("  Сохраняет все оптимизации Ultra версии:");
        System.out.println("  - Off-heap bitmasks");
        System.out.println("  - Bit tricks для O(1) поиска");
        System.out.println("  - Lock-free stack для быстрого доступа");
        System.out.println("  - Безопасная замена Unsafe API");
        System.out.println("  Рекомендуется для production систем с долгосрочной поддержкой");
    }
    
    /**
     * Пример 11: Сравнение производительности
     */
    private static void performanceComparisonExample() {
        System.out.println("--- 10. Сравнение производительности ---");
        System.out.println("Тестируем все реализации на одинаковой нагрузке");
        
        int poolSize = 1000;
        int threadCount = 4;
        int operationsPerThread = 1000;
        
        System.out.println("Параметры теста:");
        System.out.println("  Размер пула: " + poolSize);
        System.out.println("  Количество потоков: " + threadCount);
        System.out.println("  Операций на поток: " + operationsPerThread);
        System.out.println();
        
        // Тестируем все реализации
        testPerformance("Original", new BitmaskRingBuffer<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("Optimized", new BitmaskRingBufferOptimized<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("Off-Heap", new BitmaskRingBufferOffHeap<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("BitTricks", new BitmaskRingBufferBitTricks<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("Ultra", new BitmaskRingBufferUltra<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("Minimal", new BitmaskRingBufferMinimal<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("OffHeapStack", new BitmaskRingBufferOffHeapStack<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("BitTricksStack", new BitmaskRingBufferBitTricksStack<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("UltraStack", new BitmaskRingBufferUltraStack<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        testPerformance("UltraVarHandle", new BitmaskRingBufferUltraVarHandle<>(poolSize, () -> new ProcessTask("Test")), threadCount, operationsPerThread);
        
        System.out.println();
    }
    
    /**
     * Демонстрирует базовое использование пула
     */
    private static void demonstrateBasicUsage(Object pool, String poolType) {
        System.out.println("Базовое использование " + poolType + ":");
        
        // Получаем объекты
        Task task1 = getObject(pool);
        Task task2 = getObject(pool);
        Task task3 = getObject(pool);
        
        System.out.println("  Получено 3 объекта");
        
        // Работаем с объектами
        if (task1 != null) {
            task1.start();
            System.out.println("  Запущена задача 1");
        }
        
        if (task2 != null) {
            task2.start();
            System.out.println("  Запущена задача 2");
        }
        
        // Возвращаем объекты
        if (task1 != null) {
            task1.stop();
            returnObject(pool, task1);
            System.out.println("  Возвращена задача 1");
        }
        
        if (task2 != null) {
            task2.stop();
            returnObject(pool, task2);
            System.out.println("  Возвращена задача 2");
        }
        
        if (task3 != null) {
            task3.stop();
            returnObject(pool, task3);
            System.out.println("  Возвращена задача 3");
        }
        
        // Показываем статистику
        Object stats = getStats(pool);
        System.out.println("  Статистика: " + stats);
    }
    
    /**
     * Демонстрирует особенности Off-Heap версии
     */
    private static void demonstrateOffHeapFeatures(BitmaskRingBufferOffHeap<Task> pool) {
        System.out.println("Особенности Off-Heap:");
        
        // Получаем несколько объектов
        Task[] tasks = new Task[5];
        for (int i = 0; i < 5; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].start();
            }
        }
        
        // Проверяем доступность объектов
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                boolean available = pool.isAvailable(i);
                System.out.println("  Объект " + i + " доступен: " + !available);
            }
        }
        
        // Возвращаем объекты
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                tasks[i].stop();
                pool.setFreeObject(tasks[i]);
            }
        }
        
        System.out.println("  Off-heap память освобождена при cleanup()");
    }
    
    /**
     * Демонстрирует особенности Ultra версии
     */
    private static void demonstrateUltraFeatures(BitmaskRingBufferUltra<Task> pool) {
        System.out.println("Особенности Ultra:");
        
        // Получаем несколько объектов
        Task[] tasks = new Task[5];
        for (int i = 0; i < 5; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].start();
            }
        }
        
        // Демонстрируем off-heap stack
        System.out.println("  Использует off-heap lock-free stack для свободных индексов");
        System.out.println("  Комбинирует off-heap bitmasks и bit tricks");
        
        // Возвращаем объекты
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                tasks[i].stop();
                pool.setFreeObject(tasks[i]);
            }
        }
    }
    
    /**
     * Демонстрирует особенности Minimal версии
     */
    private static void demonstrateMinimalFeatures(BitmaskRingBufferMinimal<Task> pool) {
        System.out.println("Особенности Minimal:");
        
        // Получаем несколько объектов
        Task[] tasks = new Task[5];
        for (int i = 0; i < 5; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].start();
            }
        }
        
        // Проверяем время последнего использования
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                long lastUsed = pool.getLastUsedTime(i);
                System.out.println("  Объект " + i + " последний раз использован: " + lastUsed);
            }
        }
        
        // Возвращаем объекты
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                tasks[i].stop();
                pool.setFreeObject(tasks[i]);
            }
        }
        
        System.out.println("  Минимальное потребление памяти (без updateMask)");
    }
    
    /**
     * Демонстрирует особенности Stack версий
     */
    private static void demonstrateStackFeatures(Object pool) {
        System.out.println("Особенности Lock-Free Stack:");
        System.out.println("  O(1) доступ к свободным индексам через lock-free stack");
        System.out.println("  Предсказуемое время доступа к свободным объектам");
        System.out.println("  Эффективно при частом освобождении объектов");
    }
    
    /**
     * Демонстрирует особенности UltraStack версии
     */
    private static void demonstrateUltraStackFeatures(BitmaskRingBufferUltraStack<Task> pool) {
        System.out.println("Особенности UltraStack:");
        System.out.println("  Максимальная комбинация оптимизаций:");
        System.out.println("  - Off-heap bitmasks");
        System.out.println("  - Bit tricks для O(1) поиска");
        System.out.println("  - Lock-free stack для быстрого доступа");
        System.out.println("  Рекомендуется для высоконагруженных систем");
    }
    
    /**
     * Тестирует производительность конкретной реализации
     */
    private static void testPerformance(String name, Object pool, int threadCount, int operationsPerThread) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalGets = new AtomicInteger(0);
        AtomicInteger totalReturns = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                                                 Task obj = getObject(pool);
                         if (obj != null) {
                             totalGets.incrementAndGet();
                             returnObject(pool, obj);
                            totalReturns.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            double opsPerMs = (threadCount * operationsPerThread) / (duration / 1_000_000.0);
            
            System.out.printf("  %-15s: %.2f ms, %.2f ops/ms%n", name, duration / 1_000_000.0, opsPerMs);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            
            // Cleanup для off-heap версий
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
    }
    
    // Вспомогательные методы для работы с разными типами пулов
    
    @SuppressWarnings("unchecked")
    private static Task getObject(Object pool) {
        if (pool instanceof BitmaskRingBuffer) {
            return ((BitmaskRingBuffer<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOptimized) {
            return ((BitmaskRingBufferOptimized<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferBitTricks) {
            return ((BitmaskRingBufferBitTricks<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferMinimal) {
            return ((BitmaskRingBufferMinimal<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
            return ((BitmaskRingBufferOffHeapStack<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferBitTricksStack) {
            return ((BitmaskRingBufferBitTricksStack<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            return ((BitmaskRingBufferUltraStack<Task>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<Task>) pool).getFreeObject();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private static void returnObject(Object pool, Task obj) {
        if (pool instanceof BitmaskRingBuffer) {
            ((BitmaskRingBuffer<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOptimized) {
            ((BitmaskRingBufferOptimized<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            ((BitmaskRingBufferOffHeap<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferBitTricks) {
            ((BitmaskRingBufferBitTricks<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltra) {
            ((BitmaskRingBufferUltra<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferMinimal) {
            ((BitmaskRingBufferMinimal<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
            ((BitmaskRingBufferOffHeapStack<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferBitTricksStack) {
            ((BitmaskRingBufferBitTricksStack<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            ((BitmaskRingBufferUltraStack<Task>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<Task>) pool).setFreeObject(obj);
        }
    }
    
    private static Object getStats(Object pool) {
        if (pool instanceof BitmaskRingBuffer) {
            return ((BitmaskRingBuffer<?>) pool).getStatistics();
        } else if (pool instanceof BitmaskRingBufferOptimized) {
            return ((BitmaskRingBufferOptimized<?>) pool).getStatistics();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferBitTricks) {
            return ((BitmaskRingBufferBitTricks<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferMinimal) {
            return ((BitmaskRingBufferMinimal<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferOffHeapStack) {
            return ((BitmaskRingBufferOffHeapStack<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferBitTricksStack) {
            return ((BitmaskRingBufferBitTricksStack<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferUltraStack) {
            return ((BitmaskRingBufferUltraStack<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<?>) pool).getStats();
        }
        return null;
    }
} 