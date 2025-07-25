package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Экстремальный тест для принудительного создания ситуаций с переполнением пула
 */
public class BitmaskRingBufferUltraVarHandleSimpleExtremeTest {
    
    public static void main(String[] args) {
        System.out.println("=== ЭКСТРЕМАЛЬНЫЙ ТЕСТ BitmaskRingBufferUltraVarHandleSimple ===\n");
        
        // Тест 1: Принудительное создание объектов при пустом пуле
        System.out.println("ТЕСТ 1: Принудительное создание объектов при пустом пуле");
        testForcedCreation();
        
        // Тест 2: Тест переполнения с очень маленьким пулом
        System.out.println("\nТЕСТ 2: Тест переполнения с очень маленьким пулом");
        testOverflowWithTinyPool();
        
        // Тест 3: Тест с задержками для увеличения времени удержания объектов
        System.out.println("\nТЕСТ 3: Тест с задержками для увеличения времени удержания объектов");
        testWithDelays();
        
        System.out.println("\nЭкстремальный тест завершен!");
    }
    
    /**
     * Тест принудительного создания объектов
     */
    private static void testForcedCreation() {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(1, 
                () -> new HeavyTask(0, "ForcedTest", 64, 42.0));
        
        // Получаем больше объектов, чем есть в пуле
        HeavyTask[] tasks = new HeavyTask[10];
        for (int i = 0; i < 10; i++) {
            tasks[i] = pool.getFreeObject();
            System.out.println("  - Получен объект " + (i+1) + ": " + (tasks[i] != null ? "✓" : "✗"));
        }
        
        // Проверяем статистику до возврата
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats statsBefore = pool.getStats();
        System.out.println("  - Статистика до возврата: создано=" + statsBefore.totalCreates + ", отброшено=" + statsBefore.totalDrops);
        
        // Возвращаем все объекты
        for (int i = 0; i < 10; i++) {
            boolean returned = pool.setFreeObject(tasks[i]);
            System.out.println("  - Объект " + (i+1) + " возвращен: " + (returned ? "✓" : "✗"));
        }
        
        // Проверяем финальную статистику
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats statsAfter = pool.getStats();
        System.out.println("  - Финальная статистика: создано=" + statsAfter.totalCreates + ", отброшено=" + statsAfter.totalDrops);
        
        pool.cleanup();
    }
    
    /**
     * Тест переполнения с очень маленьким пулом
     */
    private static void testOverflowWithTinyPool() {
        System.out.println("  - Создаем пул с 2 объектами...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(2, 
                () -> new HeavyTask(0, "OverflowTest", 128, 42.0));
        
        // Получаем все объекты из пула
        HeavyTask task1 = pool.getFreeObject();
        HeavyTask task2 = pool.getFreeObject();
        HeavyTask task3 = pool.getFreeObject(); // Должен быть создан через фабрику
        HeavyTask task4 = pool.getFreeObject(); // Должен быть создан через фабрику
        
        System.out.println("  - Получены объекты: " + (task1 != null) + ", " + (task2 != null) + ", " + (task3 != null) + ", " + (task4 != null));
        
        // Проверяем, что объекты разные
        boolean allDifferent = (task1 != task2) && (task1 != task3) && (task1 != task4) && 
                              (task2 != task3) && (task2 != task4) && (task3 != task4);
        System.out.println("  - Все объекты разные: " + (allDifferent ? "✓" : "✗"));
        
        // Возвращаем все объекты (некоторые должны быть отброшены)
        boolean returned1 = pool.setFreeObject(task1);
        boolean returned2 = pool.setFreeObject(task2);
        boolean returned3 = pool.setFreeObject(task3);
        boolean returned4 = pool.setFreeObject(task4);
        
        System.out.println("  - Возвращены объекты: " + returned1 + ", " + returned2 + ", " + returned3 + ", " + returned4);
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
        System.out.println("  - Статистика: создано=" + stats.totalCreates + ", отброшено=" + stats.totalDrops);
        
        pool.cleanup();
    }
    
    /**
     * Тест с задержками для увеличения времени удержания объектов
     */
    private static void testWithDelays() {
        System.out.println("  - Создаем пул с 3 объектами и 8 потоками...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(3, 
                () -> new HeavyTask(0, "DelayTest", 256, 42.0));
        
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(8);
        
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalCreated = new AtomicLong(0);
        AtomicLong totalDropped = new AtomicLong(0);
        
        // Запускаем потоки
        for (int i = 0; i < 8; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < 50; j++) {
                        HeavyTask task = pool.getFreeObject();
                        
                        if (task != null) {
                            // Имитируем длительную работу
                            byte[] payload = task.getPayload();
                            for (int k = 0; k < payload.length; k += 32) {
                                payload[k] = (byte) (payload[k] + 1);
                            }
                            
                            // Задержка для увеличения времени удержания
                            Thread.sleep(1);
                            
                            boolean returned = pool.setFreeObject(task);
                            if (!returned) {
                                totalDropped.incrementAndGet();
                            }
                            
                            totalOperations.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Поток " + threadId + " завершился с ошибкой: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Запускаем тест
        startLatch.countDown();
        
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Получаем статистику
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
        
        System.out.println("  - Всего операций: " + totalOperations.get());
        System.out.println("  - Создано объектов: " + stats.totalCreates);
        System.out.println("  - Отброшено объектов: " + stats.totalDrops);
        System.out.println("  - Процент созданных: " + 
            (stats.totalGets > 0 ? String.format("%.2f%%", (stats.totalCreates * 100.0 / stats.totalGets)) : "0%"));
        System.out.println("  - Процент отброшенных: " + 
            (stats.totalReturns > 0 ? String.format("%.2f%%", (stats.totalDrops * 100.0 / stats.totalReturns)) : "0%"));
        
        executor.shutdown();
        pool.cleanup();
    }
} 