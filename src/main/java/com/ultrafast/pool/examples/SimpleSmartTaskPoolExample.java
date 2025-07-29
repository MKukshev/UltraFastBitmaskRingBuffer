package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.smart.SmartTaskPool;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Упрощенный пример использования SmartTaskPool.
 * Демонстрирует базовые возможности элегантного API.
 */
public class SimpleSmartTaskPoolExample {
    
    public static void main(String[] args) {
        System.out.println("=== Simple SmartTaskPool Example ===\n");
        
        // Создаем фабрику для задач
        BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory<AutoReturnSimpleTask> factory = 
            () -> new AutoReturnSimpleTask();
        
        // Создаем умный пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        SmartTaskPool<AutoReturnSimpleTask> smartPool = new SmartTaskPool<>(
            pool,
            Executors.newFixedThreadPool(4)
        );
        
        try {
            // Пример 1: Простое использование
            example1_SimpleUsage(smartPool);
            
            // Пример 2: Fluent API
            example2_FluentAPI(smartPool);
            
            // Пример 3: Управление задачами
            example3_TaskManagement(smartPool);
            
        } finally {
            // Завершаем работу пула
            smartPool.shutdown();
        }
    }
    
    /**
     * Пример 1: Простое использование SmartTaskPool
     */
    private static void example1_SimpleUsage(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 1: Простое использование ---");
        
        // Простая отправка задачи
        Future<Void> future = smartPool.submit(task -> {
            task.setData("Simple Task");
            task.execute();
            return null;
        });
        
        // Получение результата
        try {
            future.get(5, TimeUnit.SECONDS);
            System.out.println("Задача выполнена успешно");
        } catch (Exception e) {
            System.out.println("Ошибка: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Пример 2: Fluent API с настройками
     */
    private static void example2_FluentAPI(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 2: Fluent API с настройками ---");
        
        // Использование fluent API с различными настройками
        Future<?> future = smartPool.submit()
            .withTimeout(Duration.ofSeconds(10))
            .autoCancelOnError()
            .withName("FluentTask")
            .preProcess(task -> {
                System.out.println("Инициализация задачи...");
                task.setData("Fluent Task Data");
            })
            .postProcess(task -> {
                System.out.println("Очистка после выполнения...");
            })
            .execute(task -> {
                task.execute();
                return null;
            });
        
        // Отмена задачи
        try {
            Thread.sleep(100); // Даем время на запуск
            boolean cancelled = future.cancel(true);
            System.out.println("Задача отменена: " + cancelled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    /**
     * Пример 3: Управление задачами
     */
    private static void example3_TaskManagement(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 3: Управление задачами ---");
        
        // Отправляем несколько задач
        Future<Void> future1 = smartPool.submit(task -> {
            task.setData("Long Task 1");
            try {
                Thread.sleep(1000); // Долгая задача
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        Future<Void> future2 = smartPool.submit(task -> {
            task.setData("Long Task 2");
            try {
                Thread.sleep(1000); // Долгая задача
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        try {
            Thread.sleep(500); // Даем время на запуск задач
            
            // Показываем активные задачи
            System.out.println("Активные задачи: " + smartPool.getActiveTaskIds());
            
            // Отменяем одну задачу
            future1.cancel(true);
            System.out.println("Задача 1 отменена");
            
            // Отменяем все остальные задачи
            smartPool.cancelAllTasks();
            System.out.println("Все задачи отменены");
            
            // Получаем статистику
            SmartTaskPool.TaskPoolStatistics stats = smartPool.getStatistics();
            System.out.println("Статистика пула:");
            System.out.println("  Всего задач: " + stats.getTotalTasks());
            System.out.println("  Активных задач: " + stats.getActiveTasks());
            System.out.println("  Завершенных задач: " + stats.getCompletedTasks());
            System.out.println("  Отмененных задач: " + stats.getCancelledTasks());
            System.out.println("  Неудачных задач: " + stats.getFailedTasks());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
} 