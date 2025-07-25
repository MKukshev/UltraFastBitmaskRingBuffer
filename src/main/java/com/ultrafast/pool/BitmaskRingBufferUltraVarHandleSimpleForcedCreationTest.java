package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Тест для принудительного создания ситуаций с нехваткой объектов
 */
public class BitmaskRingBufferUltraVarHandleSimpleForcedCreationTest {
    
    public static void main(String[] args) {
        System.out.println("=== ТЕСТ ПРИНУДИТЕЛЬНОГО СОЗДАНИЯ ОБЪЕКТОВ ===\n");
        
        // Тест 1: Забираем больше объектов, чем есть в пуле
        System.out.println("ТЕСТ 1: Забираем больше объектов, чем есть в пуле");
        testOverflowByTakingMore();
        
        // Тест 2: Многопоточный тест с задержками
        System.out.println("\nТЕСТ 2: Многопоточный тест с задержками");
        testMultithreadedWithDelays();
        
        // Тест 3: Тест с постепенным забором и возвратом
        System.out.println("\nТЕСТ 3: Тест с постепенным забором и возвратом");
        testGradualTakeAndReturn();
        
        System.out.println("\nТест принудительного создания объектов завершен!");
    }
    
    /**
     * Тест: забираем больше объектов, чем есть в пуле
     */
    private static void testOverflowByTakingMore() {
        System.out.println("  - Создаем пул с 3 объектами...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(3, 
                () -> new HeavyTask(0, "OverflowTest", 128, 42.0));
        
        // Забираем больше объектов, чем есть в пуле
        HeavyTask[] tasks = new HeavyTask[10];
        System.out.println("  - Забираем 10 объектов из пула емкостью 3...");
        
        for (int i = 0; i < 10; i++) {
            tasks[i] = pool.getFreeObject();
            System.out.println("  - Получен объект " + (i+1) + ": " + (tasks[i] != null ? "✓" : "✗"));
        }
        
        // Проверяем статистику до возврата
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats statsBefore = pool.getStats();
        System.out.println("  - Статистика до возврата:");
        System.out.println("    - Создано: " + statsBefore.totalCreates);
        System.out.println("    - Отброшено: " + statsBefore.totalDrops);
        System.out.println("    - Получений: " + statsBefore.totalGets);
        System.out.println("    - Возвратов: " + statsBefore.totalReturns);
        
        // Возвращаем все объекты
        System.out.println("  - Возвращаем все объекты...");
        for (int i = 0; i < 10; i++) {
            boolean returned = pool.setFreeObject(tasks[i]);
            System.out.println("  - Объект " + (i+1) + " возвращен: " + (returned ? "✓" : "✗"));
        }
        
        // Проверяем финальную статистику
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats statsAfter = pool.getStats();
        System.out.println("  - Финальная статистика:");
        System.out.println("    - Создано: " + statsAfter.totalCreates);
        System.out.println("    - Отброшено: " + statsAfter.totalDrops);
        System.out.println("    - Получений: " + statsAfter.totalGets);
        System.out.println("    - Возвратов: " + statsAfter.totalReturns);
        
        pool.cleanup();
    }
    
    /**
     * Многопоточный тест с задержками
     */
    private static void testMultithreadedWithDelays() {
        System.out.println("  - Создаем пул с 2 объектами и 10 потоками...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(2, 
                () -> new HeavyTask(0, "DelayTest", 256, 42.0));
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(10);
        
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalCreated = new AtomicLong(0);
        AtomicLong totalDropped = new AtomicLong(0);
        
        // Запускаем потоки
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < 20; j++) {
                        HeavyTask task = pool.getFreeObject();
                        
                        if (task != null) {
                            // Имитируем длительную работу
                            byte[] payload = task.getPayload();
                            for (int k = 0; k < payload.length; k += 64) {
                                payload[k] = (byte) (payload[k] + 1);
                            }
                            
                            // Задержка для увеличения времени удержания объекта
                            Thread.sleep(10); // 10 миллисекунд
                            
                            boolean returned = pool.setFreeObject(task);
                            if (!returned) {
                                totalDropped.incrementAndGet();
                            }
                            
                            totalOperations.incrementAndGet();
                        }
                        
                        // Небольшая пауза между операциями
                        Thread.sleep(5);
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
        
        System.out.println("  - Результаты многопоточного теста:");
        System.out.println("    - Всего операций: " + totalOperations.get());
        System.out.println("    - Создано объектов: " + stats.totalCreates);
        System.out.println("    - Отброшено объектов: " + stats.totalDrops);
        System.out.println("    - Процент созданных: " + 
            (stats.totalGets > 0 ? String.format("%.2f%%", (stats.totalCreates * 100.0 / stats.totalGets)) : "0%"));
        System.out.println("    - Процент отброшенных: " + 
            (stats.totalReturns > 0 ? String.format("%.2f%%", (stats.totalDrops * 100.0 / stats.totalReturns)) : "0%"));
        
        executor.shutdown();
        pool.cleanup();
    }
    
    /**
     * Тест с постепенным забором и возвратом
     */
    private static void testGradualTakeAndReturn() {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(1, 
                () -> new HeavyTask(0, "GradualTest", 64, 42.0));
        
        // Забираем объекты постепенно, не возвращая их сразу
        HeavyTask[] tasks = new HeavyTask[5];
        System.out.println("  - Забираем 5 объектов из пула емкостью 1...");
        
        for (int i = 0; i < 5; i++) {
            tasks[i] = pool.getFreeObject();
            System.out.println("  - Получен объект " + (i+1) + ": " + (tasks[i] != null ? "✓" : "✗"));
            
            // Проверяем статистику после каждого забора
            BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
            System.out.println("    - Создано: " + stats.totalCreates + ", Получений: " + stats.totalGets);
        }
        
        // Возвращаем объекты с задержкой
        System.out.println("  - Возвращаем объекты с задержкой...");
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(50); // 50 миллисекунд задержки
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            boolean returned = pool.setFreeObject(tasks[i]);
            System.out.println("  - Объект " + (i+1) + " возвращен: " + (returned ? "✓" : "✗"));
            
            // Проверяем статистику после каждого возврата
            BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
            System.out.println("    - Отброшено: " + stats.totalDrops + ", Возвратов: " + stats.totalReturns);
        }
        
        // Финальная статистика
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats finalStats = pool.getStats();
        System.out.println("  - Финальная статистика:");
        System.out.println("    - Создано: " + finalStats.totalCreates);
        System.out.println("    - Отброшено: " + finalStats.totalDrops);
        System.out.println("    - Получений: " + finalStats.totalGets);
        System.out.println("    - Возвратов: " + finalStats.totalReturns);
        
        pool.cleanup();
    }
} 