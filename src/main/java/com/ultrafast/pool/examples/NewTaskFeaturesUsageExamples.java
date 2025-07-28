package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.factory.IntegratedObjectFactory;
import com.ultrafast.pool.factory.TaskFactory;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Примеры использования новой функциональности задач:
 * - Поддержка исключений
 * - Таймауты выполнения
 * - Поддержка отмены задач
 * - Логирование выполнения
 */
public class NewTaskFeaturesUsageExamples {
    
    public static void main(String[] args) {
        System.out.println("=== New Task Features Usage Examples ===\n");
        
        // Пример 1: Базовое использование новой функциональности
        example1_BasicUsage();
        
        // Пример 2: Обработка исключений в реальных сценариях
        example2_ExceptionHandling();
        
        // Пример 3: Таймауты в production-среде
        example3_TimeoutHandling();
        
        // Пример 4: Отмена задач в многопоточной среде
        example4_CancellationInMultithreading();
        
        // Пример 5: Логирование и мониторинг
        example5_LoggingAndMonitoring();
        
        // Пример 6: Комбинированное использование всех возможностей
        example6_CombinedFeatures();
        
        // Пример 7: Использование с фабриками
        example7_UsageWithFactories();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: Базовое использование новой функциональности
     */
    private static void example1_BasicUsage() {
        System.out.println("--- Example 1: Basic Usage ---");
        
        // Создаем пул с фабрикой
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("BasicTask");
            task.setTaskDescription("Basic task with enhanced features");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(5));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем и настраиваем задачу
        SimpleTask task = pool.getFreeObject();
        task.setData("Basic Task Data");
        
        System.out.println("Task configuration:");
        System.out.println("  Name: " + task.getTaskName());
        System.out.println("  Description: " + task.getTaskDescription());
        System.out.println("  Timeout: " + task.getTimeout().toSeconds() + " seconds");
        System.out.println("  Logging enabled: " + task.isLoggingEnabled());
        
        // Выполняем задачу с новой функциональностью
        task.executeWithExceptionHandling();
        
        // Анализируем результат
        System.out.println("\nTask execution results:");
        System.out.println("  Status: " + task.getStatus());
        System.out.println("  Execution count: " + task.getExecutionCount());
        System.out.println("  Last execution time: " + task.getLastExecutionTime() + "ms");
        System.out.println("  Has exception: " + task.hasException());
        System.out.println("  Is completed: " + task.isCompleted());
        
        // Возвращаем в пул
        pool.setFreeObject(task);
        System.out.println("Basic usage example completed\n");
    }
    
    /**
     * Пример 2: Обработка исключений в реальных сценариях
     */
    private static void example2_ExceptionHandling() {
        System.out.println("--- Example 2: Exception Handling ---");
        
        // Создаем пул для задач с обработкой исключений
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("ExceptionHandlingTask");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Сценарий 1: Задача с симуляцией исключения
        SimpleTask exceptionTask = pool.getFreeObject();
        exceptionTask.setData("Exception Test");
        exceptionTask.setSimulateException(true);
        
        System.out.println("Executing task with exception simulation...");
        exceptionTask.executeWithExceptionHandling();
        
        System.out.println("Exception task results:");
        System.out.println("  Status: " + exceptionTask.getStatus());
        System.out.println("  Exception count: " + exceptionTask.getExceptionCount());
        if (exceptionTask.hasException()) {
            System.out.println("  Last exception: " + exceptionTask.getLastException().getMessage());
        }
        
        pool.setFreeObject(exceptionTask);
        
        // Сценарий 2: Нормальная задача
        SimpleTask normalTask = pool.getFreeObject();
        normalTask.setData("Normal Task");
        
        System.out.println("\nExecuting normal task...");
        normalTask.executeWithExceptionHandling();
        
        System.out.println("Normal task results:");
        System.out.println("  Status: " + normalTask.getStatus());
        System.out.println("  Exception count: " + normalTask.getExceptionCount());
        
        pool.setFreeObject(normalTask);
        System.out.println("Exception handling example completed\n");
    }
    
    /**
     * Пример 3: Таймауты в production-среде
     */
    private static void example3_TimeoutHandling() {
        System.out.println("--- Example 3: Timeout Handling ---");
        
        // Создаем пул с разными таймаутами
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("TimeoutTask");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Сценарий 1: Короткий таймаут
        SimpleTask shortTimeoutTask = pool.getFreeObject();
        shortTimeoutTask.setData("Short Timeout Task");
        shortTimeoutTask.setTimeout(Duration.ofMillis(100));
        shortTimeoutTask.setSimulateTimeout(true);
        
        System.out.println("Executing task with short timeout (100ms)...");
        shortTimeoutTask.executeWithExceptionHandling();
        
        System.out.println("Short timeout task results:");
        System.out.println("  Status: " + shortTimeoutTask.getStatus());
        System.out.println("  Execution time: " + shortTimeoutTask.getLastExecutionTime() + "ms");
        System.out.println("  Timeout count: " + shortTimeoutTask.getTimeoutCount());
        
        pool.setFreeObject(shortTimeoutTask);
        
        // Сценарий 2: Длинный таймаут
        SimpleTask longTimeoutTask = pool.getFreeObject();
        longTimeoutTask.setData("Long Timeout Task");
        longTimeoutTask.setTimeout(Duration.ofSeconds(10));
        
        System.out.println("\nExecuting task with long timeout (10s)...");
        longTimeoutTask.executeWithExceptionHandling();
        
        System.out.println("Long timeout task results:");
        System.out.println("  Status: " + longTimeoutTask.getStatus());
        System.out.println("  Execution time: " + longTimeoutTask.getLastExecutionTime() + "ms");
        System.out.println("  Timeout count: " + longTimeoutTask.getTimeoutCount());
        
        pool.setFreeObject(longTimeoutTask);
        System.out.println("Timeout handling example completed\n");
    }
    
    /**
     * Пример 4: Отмена задач в многопоточной среде
     */
    private static void example4_CancellationInMultithreading() {
        System.out.println("--- Example 4: Cancellation in Multithreading ---");
        
        // Создаем пул
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("CancellationTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(30));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // Запускаем несколько задач
        for (int i = 0; i < 3; i++) {
            final int taskId = i + 1;
            executor.submit(() -> {
                try {
                    SimpleTask task = pool.getFreeObject();
                    task.setData("Cancellation Test Task " + taskId);
                    
                    System.out.println("Starting task " + taskId + "...");
                    
                    // Симулируем долгую работу
                    if (taskId == 2) {
                        task.setSimulateTimeout(true);
                    }
                    
                    task.executeWithExceptionHandling();
                    
                    System.out.println("Task " + taskId + " completed with status: " + task.getStatus());
                    
                    pool.setFreeObject(task);
                } catch (Exception e) {
                    System.err.println("Error in task " + taskId + ": " + e.getMessage());
                }
            });
        }
        
        // Даем время на выполнение
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем все задачи в пуле
        System.out.println("\nCancelling all tasks in pool...");
        for (int i = 0; i < 3; i++) {
            SimpleTask task = pool.getFreeObject();
            if (task != null) {
                task.cancel();
                System.out.println("Task cancelled: " + task.getTaskName());
                pool.setFreeObject(task);
            }
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Cancellation example completed\n");
    }
    
    /**
     * Пример 5: Логирование и мониторинг
     */
    private static void example5_LoggingAndMonitoring() {
        System.out.println("--- Example 5: Logging and Monitoring ---");
        
        // Создаем пул с детальным логированием
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("MonitoringTask");
            task.setTaskDescription("Task for monitoring and logging demonstration");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Выполняем несколько задач для накопления статистики
        for (int i = 0; i < 5; i++) {
            SimpleTask task = pool.getFreeObject();
            task.setData("Monitoring Task " + (i + 1));
            
            // Разные сценарии для демонстрации
            if (i == 1) {
                task.setSimulateException(true);
            } else if (i == 2) {
                task.setTimeout(Duration.ofMillis(50));
                task.setSimulateTimeout(true);
            } else if (i == 3) {
                task.setSimulateCancellation(true);
            }
            
            System.out.println("Executing task " + (i + 1) + "...");
            task.executeWithExceptionHandling();
            
            // Показываем промежуточную статистику
            System.out.println("Task " + (i + 1) + " statistics:");
            System.out.println("  Executions: " + task.getExecutionCount());
            System.out.println("  Total time: " + task.getTotalExecutionTime() + "ms");
            System.out.println("  Average time: " + String.format("%.2f", task.getAverageExecutionTime()) + "ms");
            System.out.println("  Exceptions: " + task.getExceptionCount());
            System.out.println("  Timeouts: " + task.getTimeoutCount());
            System.out.println("  Cancellations: " + task.getCancellationCount());
            System.out.println("  Status: " + task.getStatus());
            System.out.println();
            
            pool.setFreeObject(task);
        }
        
        // Показываем итоговую статистику
        SimpleTask finalTask = pool.getFreeObject();
        finalTask.setData("Final Statistics Task");
        finalTask.executeWithExceptionHandling();
        
        System.out.println("Final detailed statistics:");
        System.out.println(finalTask.getDetailedStatistics());
        
        pool.setFreeObject(finalTask);
        System.out.println("Logging and monitoring example completed\n");
    }
    
    /**
     * Пример 6: Комбинированное использование всех возможностей
     */
    private static void example6_CombinedFeatures() {
        System.out.println("--- Example 6: Combined Features ---");
        
        // Создаем пул с AutoReturnSimpleTask
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setTaskName("CombinedFeaturesTask");
            task.setTaskDescription("Task demonstrating all enhanced features");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(3));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Создаем ExecutorService для параллельного выполнения
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // Запускаем задачи с разными сценариями
        for (int i = 0; i < 4; i++) {
            final int taskId = i + 1;
            executor.submit(() -> {
                try {
                    AutoReturnSimpleTask task = pool.getFreeObject();
                    if (task != null) {
                        task.setData("Combined Task " + taskId);
                        
                        // Настраиваем разные сценарии
                        switch (taskId) {
                            case 1:
                                // Нормальное выполнение
                                System.out.println("Task " + taskId + ": Normal execution");
                                break;
                            case 2:
                                // Симуляция исключения
                                task.setSimulateException(true);
                                System.out.println("Task " + taskId + ": Exception simulation");
                                break;
                            case 3:
                                // Симуляция таймаута
                                task.setSimulateTimeout(true);
                                System.out.println("Task " + taskId + ": Timeout simulation");
                                break;
                            case 4:
                                // Симуляция отмены
                                task.setSimulateCancellation(true);
                                System.out.println("Task " + taskId + ": Cancellation simulation");
                                break;
                        }
                        
                        // Выполняем задачу (автоматический возврат в пул)
                        task.executeWithExceptionHandling();
                        
                        // Показываем результат
                        System.out.println("Task " + taskId + " completed:");
                        System.out.println("  Status: " + task.getStatus());
                        System.out.println("  Execution time: " + task.getLastExecutionTime() + "ms");
                        System.out.println("  Has exception: " + task.hasException());
                        System.out.println("  Is cancelled: " + task.isCancelled());
                    }
                } catch (Exception e) {
                    System.err.println("Error in task " + taskId + ": " + e.getMessage());
                }
            });
        }
        
        // Ждем завершения всех задач
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("\nCombined features example completed");
        System.out.println("Final pool capacity: " + pool.getCapacity());
        System.out.println("Combined features example completed\n");
    }
    
    /**
     * Пример 7: Использование с фабриками
     */
    private static void example7_UsageWithFactories() {
        System.out.println("--- Example 7: Usage with Factories ---");
        
        // Создаем пул с использованием TaskFactory
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, TaskFactory.createSimpleTaskFactory(null));
        
        // Получаем задачу и настраиваем новую функциональность
        SimpleTask task = pool.getFreeObject();
        task.setTaskName("FactoryTask");
        task.setTaskDescription("Task created using TaskFactory");
        task.setLoggingEnabled(true);
        task.setTimeout(Duration.ofSeconds(2));
        task.setData("Factory Task Data");
        
        System.out.println("Task created with factory:");
        System.out.println("  Name: " + task.getTaskName());
        System.out.println("  Description: " + task.getTaskDescription());
        System.out.println("  Timeout: " + task.getTimeout().toSeconds() + "s");
        
        // Выполняем с новой функциональностью
        task.executeWithExceptionHandling();
        
        System.out.println("Factory task results:");
        System.out.println("  Status: " + task.getStatus());
        System.out.println("  Execution count: " + task.getExecutionCount());
        System.out.println("  Last execution time: " + task.getLastExecutionTime() + "ms");
        
        pool.setFreeObject(task);
        
        // Демонстрация с IntegratedObjectFactory
        System.out.println("\nUsing IntegratedObjectFactory...");
        IntegratedObjectFactory<SimpleTask> integratedFactory = 
            TaskFactory.createIntegratedSimpleTaskFactory(pool);
        
        SimpleTask integratedTask = integratedFactory.createObject();
        integratedTask.setTaskName("IntegratedTask");
        integratedTask.setLoggingEnabled(true);
        integratedTask.setData("Integrated Task Data");
        
        integratedTask.executeWithExceptionHandling();
        
        System.out.println("Integrated task results:");
        System.out.println("  Status: " + integratedTask.getStatus());
        System.out.println("  Pool reference: " + (integratedFactory.getPool() != null ? "Available" : "Not available"));
        
        pool.setFreeObject(integratedTask);
        System.out.println("Factory usage example completed\n");
    }
} 