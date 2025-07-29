package com.ultrafast.pool.examples;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.smart.SmartTaskPool;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Пример демонстрации различных способов отмены задач в SmartTaskPool.
 * Показывает:
 * - Отмену через task.cancelTask() (изнутри задачи)
 * - Отмену через Future.cancel() (извне)
 * - Отмену через SmartTaskPool.cancelTask() (по ID)
 * - Отмену через SmartTaskPool.cancelAllTasks() (все задачи)
 * - Отмену длительных задач с периодическими проверками
 */
public class TaskCancellationExample {
    
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    
    public static void main(String[] args) {
        System.out.println("🚀 Пример отмены задач в SmartTaskPool");
        System.out.println("=" .repeat(80));
        
        // Создаем фабрику для задач
        BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory<AutoReturnSimpleTask> factory = 
            () -> new AutoReturnSimpleTask();
        
        // Создаем пул объектов с авторасширением
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory, 0.5, 200);
        
        // Создаем умный пул задач
        SmartTaskPool<AutoReturnSimpleTask> smartPool = new SmartTaskPool<>(
            pool,
            Executors.newFixedThreadPool(4)
        );
        
        try {
            // Демонстрация различных способов отмены
            demonstrateTaskSelfCancellation(smartPool);
            demonstrateFutureCancellation(smartPool);
            demonstrateSmartPoolCancellation(smartPool);
            demonstrateLongRunningTaskCancellation(smartPool);
            demonstrateBatchCancellation(smartPool);
            
        } finally {
            // Элегантное завершение работы
            System.out.println("\n🔄 Завершение работы SmartTaskPool...");
            smartPool.shutdown();
            
            try {
                if (smartPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("✅ SmartTaskPool успешно завершил работу");
                } else {
                    System.out.println("⚠️ SmartTaskPool принудительно завершен");
                    smartPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                smartPool.shutdownNow();
            }
        }
    }
    
    /**
     * Демонстрация 1: Отмена задачи изнутри через task.cancelTask()
     */
    private static void demonstrateTaskSelfCancellation(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 1: Отмена задачи изнутри");
        System.out.println("-".repeat(50));
        
        System.out.println("🎯 Отмена задачи через task.cancelTask() (изнутри задачи):");
        
        // Задача, которая отменяет сама себя через некоторое время
        Future<?> future = smartPool.submit(task -> {
            task.setData("Самоотменяющаяся задача " + taskCounter.incrementAndGet());
            task.setLoggingEnabled(true);
            
            // Работаем некоторое время, затем отменяем себя
            try {
                Thread.sleep(1000); // Работаем 1 секунду
                System.out.println("   🔄 Задача решает отменить себя...");
                task.cancelTask(); // Отменяем задачу изнутри
                System.out.println("   ✅ Задача успешно отменена изнутри");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return null;
        });
        
        try {
            // Ждем завершения задачи
            future.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("   ⏰ Таймаут ожидания задачи");
        } catch (Exception e) {
            System.out.println("   ❌ Ошибка: " + e.getMessage());
        }
        
        System.out.println("   🎯 Результат: Задача успешно отменена изнутри!");
    }
    
    /**
     * Демонстрация 2: Отмена задачи через Future.cancel()
     */
    private static void demonstrateFutureCancellation(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 2: Отмена через Future");
        System.out.println("-".repeat(50));
        
        System.out.println("🎯 Отмена задачи через Future.cancel() (извне):");
        
        // Задача, которая работает долго
        Future<?> future = smartPool.submit(task -> {
            task.setData("Долгая задача для Future отмены " + taskCounter.incrementAndGet());
            task.setLoggingEnabled(true);
            
            // Долгая работа
            try {
                System.out.println("   🔄 Задача начинает долгую работу...");
                Thread.sleep(5000); // 5 секунд работы
                System.out.println("   ✅ Задача завершила работу");
            } catch (InterruptedException e) {
                System.out.println("   🚫 Задача была прервана");
                Thread.currentThread().interrupt();
            }
            
            return null;
        });
        
        try {
            // Даем задаче немного поработать, затем отменяем
            Thread.sleep(1000);
            System.out.println("   🚫 Отменяем задачу через Future.cancel()...");
            boolean cancelled = future.cancel(true);
            System.out.println("   📊 Результат отмены: " + cancelled);
            
            // Проверяем статус
            System.out.println("   📊 Статус Future:");
            System.out.println("      • isCancelled: " + future.isCancelled());
            System.out.println("      • isDone: " + future.isDone());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("   🎯 Результат: Задача отменена через Future!");
    }
    
    /**
     * Демонстрация 3: Отмена через SmartTaskPool.cancelTask()
     */
    private static void demonstrateSmartPoolCancellation(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 3: Отмена через SmartTaskPool");
        System.out.println("-".repeat(50));
        
        System.out.println("🎯 Отмена задачи через SmartTaskPool.cancelTask() (по ID):");
        
        // Отправляем несколько задач
        Future<?> future1 = smartPool.submit(task -> {
            task.setData("Задача 1 для отмены по ID " + taskCounter.incrementAndGet());
            task.setLoggingEnabled(true);
            
            try {
                System.out.println("   🔄 Задача 1 начинает работу...");
                Thread.sleep(3000);
                System.out.println("   ✅ Задача 1 завершила работу");
            } catch (InterruptedException e) {
                System.out.println("   🚫 Задача 1 была прервана");
                Thread.currentThread().interrupt();
            }
            
            return null;
        });
        
        Future<?> future2 = smartPool.submit(task -> {
            task.setData("Задача 2 для отмены по ID " + taskCounter.incrementAndGet());
            task.setLoggingEnabled(true);
            
            try {
                System.out.println("   🔄 Задача 2 начинает работу...");
                Thread.sleep(3000);
                System.out.println("   ✅ Задача 2 завершила работу");
            } catch (InterruptedException e) {
                System.out.println("   🚫 Задача 2 была прервана");
                Thread.currentThread().interrupt();
            }
            
            return null;
        });
        
        try {
            Thread.sleep(500);
            
            // Получаем ID активных задач
            System.out.println("   📊 Активные задачи: " + smartPool.getActiveTaskIds());
            
            // Отменяем конкретную задачу по ID
            String taskId = smartPool.getActiveTaskIds().iterator().next();
            System.out.println("   🚫 Отменяем задачу с ID: " + taskId);
            smartPool.cancelTask(taskId);
            
            Thread.sleep(500);
            
            // Проверяем статус
            System.out.println("   📊 Активные задачи после отмены: " + smartPool.getActiveTaskIds());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("   🎯 Результат: Конкретная задача отменена через SmartTaskPool!");
    }
    
    /**
     * Демонстрация 4: Отмена длительных задач с периодическими проверками
     */
    private static void demonstrateLongRunningTaskCancellation(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 4: Отмена длительных задач");
        System.out.println("-".repeat(50));
        
        System.out.println("🎯 Отмена длительных задач с периодическими проверками отмены:");
        
        // Длительная задача с проверками отмены
        Future<?> future = smartPool.submit(task -> {
            task.setData("Длительная задача с проверками " + taskCounter.incrementAndGet());
            task.setLoggingEnabled(true);
            task.setLongRunningTask(true); // Включаем режим длительной задачи
            task.setCancellationCheckInterval(200); // Проверяем отмену каждые 200мс
            
            task.execute(); // Выполняем с периодическими проверками отмены
            
            return null;
        });
        
        try {
            // Даем задаче поработать, затем отменяем
            Thread.sleep(2000);
            System.out.println("   🚫 Отменяем длительную задачу...");
            boolean cancelled = future.cancel(true);
            System.out.println("   📊 Результат отмены: " + cancelled);
            
            // Ждем немного для завершения
            Thread.sleep(500);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("   🎯 Результат: Длительная задача отменена с периодическими проверками!");
    }
    
    /**
     * Демонстрация 5: Batch отмена всех задач
     */
    private static void demonstrateBatchCancellation(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 5: Batch отмена всех задач");
        System.out.println("-".repeat(50));
        
        System.out.println("🎯 Отмена всех задач через SmartTaskPool.cancelAllTasks():");
        
        // Отправляем несколько задач
        for (int i = 0; i < 5; i++) {
            final int taskNum = i + 1;
            smartPool.submit(task -> {
                task.setData("Batch задача " + taskNum + " " + taskCounter.incrementAndGet());
                task.setLoggingEnabled(true);
                
                try {
                    System.out.println("   🔄 Batch задача " + taskNum + " начинает работу...");
                    Thread.sleep(2000 + (long)(Math.random() * 1000)); // 2-3 секунды
                    System.out.println("   ✅ Batch задача " + taskNum + " завершила работу");
                } catch (InterruptedException e) {
                    System.out.println("   🚫 Batch задача " + taskNum + " была прервана");
                    Thread.currentThread().interrupt();
                }
                
                return null;
            });
        }
        
        try {
            Thread.sleep(1000);
            
            // Проверяем активные задачи
            System.out.println("   📊 Активные задачи перед отменой: " + smartPool.getActiveTaskIds());
            System.out.println("   📊 Количество активных задач: " + smartPool.getActiveTaskIds().size());
            
            // Отменяем все задачи
            System.out.println("   🚫 Отменяем ВСЕ задачи...");
            smartPool.cancelAllTasks();
            
            Thread.sleep(500);
            
            // Проверяем результат
            System.out.println("   📊 Активные задачи после отмены: " + smartPool.getActiveTaskIds());
            System.out.println("   📊 Количество активных задач: " + smartPool.getActiveTaskIds().size());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("   🎯 Результат: Все задачи отменены через batch операцию!");
    }
} 