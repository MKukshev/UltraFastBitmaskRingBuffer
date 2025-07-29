package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.smart.SmartTaskPool;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Пример использования SmartTaskPool - элегантного решения для контроля Future.
 * Демонстрирует различные способы использования API.
 */
public class SmartTaskPoolExample {
    
    public static void main(String[] args) {
        System.out.println("=== SmartTaskPool Example ===\n");
        
        // Создаем фабрику для задач
        BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory<AutoReturnSimpleTask> factory = 
            () -> new AutoReturnSimpleTask();
        
        // Создаем умный пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory);
        SmartTaskPool<AutoReturnSimpleTask> smartPool = new SmartTaskPool<>(
            pool,
            Executors.newFixedThreadPool(4)
        );
        
        try {
            // Пример 1: Простое использование
            example1_SimpleUsage(smartPool);
            
            // Пример 2: Fluent API с настройками
            example2_FluentAPI(smartPool);
            
            // Пример 3: Batch обработка
            example3_BatchProcessing(smartPool);
            
            // Пример 4: Управление задачами
            example4_TaskManagement(smartPool);
            
            // Пример 5: Мониторинг и статистика
            example5_Monitoring(smartPool);
            
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
        Future<String> future1 = smartPool.submit(task -> {
            task.setData("Task 1");
            task.execute();
            return "Task 1 completed";
        });
        
        // Получение результата
        try {
            String result = future1.get(5, TimeUnit.SECONDS);
            System.out.println("Результат: " + result);
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
        Future<?> future2 = smartPool.submit()
            .withTimeout(Duration.ofSeconds(10))
            .autoCancelOnError()
            .withName("ComplexTask")
            .preProcess(task -> {
                System.out.println("Инициализация задачи...");
                task.setData("Complex Task Data");
            })
            .postProcess(task -> {
                System.out.println("Очистка после выполнения...");
            })
            .retryOnFailure(3)
            .execute(task -> {
                task.execute();
                return null;
            });
        
        // Отмена задачи
        try {
            Thread.sleep(100); // Даем время на запуск
            boolean cancelled = future2.cancel(true);
            System.out.println("Задача отменена: " + cancelled);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    /**
     * Пример 3: Batch обработка множественных задач
     */
    private static void example3_BatchProcessing(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 3: Batch обработка ---");
        
        // Создаем список задач
        List<Function<AutoReturnSimpleTask, ?>> tasks = Arrays.asList(
            task -> {
                task.setData("Batch Task 1");
                task.execute();
                return null;
            },
            task -> {
                task.setData("Batch Task 2");
                task.execute();
                return null;
            },
            task -> {
                task.setData("Batch Task 3");
                task.execute();
                return null;
            }
        );
        
        // Отправляем все задачи одновременно
        List<Future<?>> futures = smartPool.submitAll(tasks);
        
        System.out.println("Отправлено задач: " + futures.size());
        
        // Ожидаем завершения всех задач
        for (int i = 0; i < futures.size(); i++) {
            try {
                String result = (String) futures.get(i).get(5, TimeUnit.SECONDS);
                System.out.println("Задача " + (i + 1) + " завершена: " + result);
            } catch (Exception e) {
                System.out.println("Задача " + (i + 1) + " завершена с ошибкой: " + e.getMessage());
            }
        }
        
        System.out.println();
    }
    
    /**
     * Пример 4: Управление задачами
     */
    private static void example4_TaskManagement(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 4: Управление задачами ---");
        
        // Отправляем несколько задач
        Future<?> future1 = smartPool.submit(task -> {
            task.setData("Long Task 1");
            try {
                Thread.sleep(2000); // Долгая задача
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        Future<?> future2 = smartPool.submit(task -> {
            task.setData("Long Task 2");
            try {
                Thread.sleep(2000); // Долгая задача
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        Future<?> future3 = smartPool.submit(task -> {
            task.setData("Long Task 3");
            try {
                Thread.sleep(2000); // Долгая задача
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
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    /**
     * Пример 5: Мониторинг и статистика
     */
    private static void example5_Monitoring(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("--- Пример 5: Мониторинг и статистика ---");
        
        // Отправляем задачи для статистики
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            smartPool.submit(task -> {
                task.setData("Stats Task " + taskNum);
                task.execute();
                return "Stats task " + taskNum + " completed";
            });
        }
        
        // Отменяем несколько задач
        try {
            Thread.sleep(100);
            smartPool.cancelAllTasks();
            
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