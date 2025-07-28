package com.ultrafast.pool.examples;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.SimpleTask;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Пример демонстрирующий новую функциональность задач:
 * - Поддержка исключений
 * - Таймауты выполнения
 * - Поддержка отмены задач
 * - Логирование выполнения
 */
public class EnhancedTaskFeaturesExample {
    
    public static void main(String[] args) {
        System.out.println("=== Enhanced Task Features Example ===\n");
        
        // Демонстрация 1: Обработка исключений
        demonstrateExceptionHandling();
        
        // Демонстрация 2: Таймауты выполнения
        demonstrateTimeouts();
        
        // Демонстрация 3: Отмена задач
        demonstrateCancellation();
        
        // Демонстрация 4: Логирование и статистика
        demonstrateLoggingAndStatistics();
        
        // Демонстрация 5: Комбинированный пример
        demonstrateCombinedFeatures();
        
        System.out.println("\n=== All demonstrations completed ===");
    }
    
    /**
     * Демонстрация 1: Обработка исключений
     */
    private static void demonstrateExceptionHandling() {
        System.out.println("--- Demonstration 1: Exception Handling ---");
        
        // Создаем пул с фабрикой
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("ExceptionTestTask");
            task.setTaskDescription("Task for testing exception handling");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу и настраиваем симуляцию исключения
        SimpleTask task = pool.getFreeObject();
        task.setData("Exception Test Task");
        task.setSimulateException(true);
        
        System.out.println("Executing task with exception simulation...");
        
        // Выполняем с обработкой исключений
        task.executeWithExceptionHandling();
        
        // Проверяем результат
        System.out.println("Task status: " + task.getStatus());
        System.out.println("Has exception: " + task.hasException());
        if (task.hasException()) {
            System.out.println("Exception: " + task.getLastException().getMessage());
        }
        System.out.println("Exception count: " + task.getExceptionCount());
        
        // Возвращаем в пул
        pool.setFreeObject(task);
        System.out.println("Exception handling demonstration completed\n");
    }
    
    /**
     * Демонстрация 2: Таймауты выполнения
     */
    private static void demonstrateTimeouts() {
        System.out.println("--- Demonstration 2: Timeout Handling ---");
        
        // Создаем пул с фабрикой
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("TimeoutTestTask");
            task.setTaskDescription("Task for testing timeout handling");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу и настраиваем таймаут
        SimpleTask task = pool.getFreeObject();
        task.setData("Timeout Test Task");
        task.setTimeout(Duration.ofMillis(500)); // 500ms таймаут
        task.setSimulateTimeout(true);
        
        System.out.println("Executing task with timeout simulation (500ms timeout)...");
        
        // Выполняем с обработкой исключений
        task.executeWithExceptionHandling();
        
        // Проверяем результат
        System.out.println("Task status: " + task.getStatus());
        System.out.println("Has exception: " + task.hasException());
        System.out.println("Timeout count: " + task.getTimeoutCount());
        System.out.println("Last execution time: " + task.getLastExecutionTime() + "ms");
        
        // Возвращаем в пул
        pool.setFreeObject(task);
        System.out.println("Timeout handling demonstration completed\n");
    }
    
    /**
     * Демонстрация 3: Отмена задач
     */
    private static void demonstrateCancellation() {
        System.out.println("--- Demonstration 3: Task Cancellation ---");
        
        // Создаем пул с фабрикой
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("CancellationTestTask");
            task.setTaskDescription("Task for testing cancellation");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        SimpleTask task = pool.getFreeObject();
        task.setData("Cancellation Test Task");
        
        // Создаем ExecutorService для демонстрации отмены
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        System.out.println("Starting task execution...");
        
        // Запускаем задачу в отдельном потоке
        Future<?> future = executor.submit(() -> {
            task.executeWithExceptionHandling();
        });
        
        // Даем задаче немного времени на выполнение
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем задачу
        System.out.println("Cancelling task...");
        task.cancel();
        
        // Ждем завершения
        try {
            future.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("Task execution completed with exception: " + e.getMessage());
        }
        
        // Проверяем результат
        System.out.println("Task status: " + task.getStatus());
        System.out.println("Is cancelled: " + task.isCancelled());
        System.out.println("Cancellation count: " + task.getCancellationCount());
        
        executor.shutdown();
        
        // Возвращаем в пул
        pool.setFreeObject(task);
        System.out.println("Cancellation demonstration completed\n");
    }
    
    /**
     * Демонстрация 4: Логирование и статистика
     */
    private static void demonstrateLoggingAndStatistics() {
        System.out.println("--- Demonstration 4: Logging and Statistics ---");
        
        // Создаем пул с фабрикой
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setTaskName("StatisticsTestTask");
            task.setTaskDescription("Task for testing logging and statistics");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Выполняем несколько задач для накопления статистики
        for (int i = 0; i < 3; i++) {
            SimpleTask task = pool.getFreeObject();
            task.setData("Statistics Test Task " + (i + 1));
            
            System.out.println("Executing task " + (i + 1) + "...");
            task.executeWithExceptionHandling();
            
            // Показываем статистику после каждого выполнения
            System.out.println("Task " + (i + 1) + " statistics:");
            System.out.println("  Executions: " + task.getExecutionCount());
            System.out.println("  Total time: " + task.getTotalExecutionTime() + "ms");
            System.out.println("  Average time: " + String.format("%.2f", task.getAverageExecutionTime()) + "ms");
            System.out.println("  Last execution time: " + task.getLastExecutionTime() + "ms");
            System.out.println("  Status: " + task.getStatus());
            
            pool.setFreeObject(task);
        }
        
        // Получаем задачу для показа подробной статистики
        SimpleTask task = pool.getFreeObject();
        task.setData("Final Statistics Task");
        task.executeWithExceptionHandling();
        
        System.out.println("\nDetailed statistics:");
        System.out.println(task.getDetailedStatistics());
        
        pool.setFreeObject(task);
        System.out.println("Logging and statistics demonstration completed\n");
    }
    
    /**
     * Демонстрация 5: Комбинированный пример
     */
    private static void demonstrateCombinedFeatures() {
        System.out.println("--- Demonstration 5: Combined Features ---");
        
        // Создаем пул с AutoReturnSimpleTask
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setTaskName("CombinedTestTask");
            task.setTaskDescription("Task demonstrating all enhanced features");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(2)); // 2 секунды таймаут
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // Запускаем несколько задач с разными сценариями
        for (int i = 0; i < 5; i++) {
            final int taskId = i + 1;
            executor.submit(() -> {
                try {
                    AutoReturnSimpleTask task = pool.getFreeObject();
                    if (task != null) {
                        task.setData("Combined Test Task " + taskId);
                        
                        // Разные сценарии для разных задач
                        if (taskId == 1) {
                            // Обычное выполнение
                            System.out.println("Task " + taskId + ": Normal execution");
                        } else if (taskId == 2) {
                            // Симуляция исключения
                            task.setSimulateException(true);
                            System.out.println("Task " + taskId + ": Exception simulation");
                        } else if (taskId == 3) {
                            // Симуляция таймаута
                            task.setSimulateTimeout(true);
                            System.out.println("Task " + taskId + ": Timeout simulation");
                        } else if (taskId == 4) {
                            // Симуляция отмены
                            task.setSimulateCancellation(true);
                            System.out.println("Task " + taskId + ": Cancellation simulation");
                        } else {
                            // Долгое выполнение
                            task.setTimeout(Duration.ofMillis(100));
                            System.out.println("Task " + taskId + ": Long execution");
                        }
                        
                        // Выполняем задачу (автоматический возврат в пул)
                        task.executeWithExceptionHandling();
                        
                        // Показываем результат
                        System.out.println("Task " + taskId + " completed - Status: " + task.getStatus());
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
        
        System.out.println("\nCombined features demonstration completed");
        System.out.println("Final pool capacity: " + pool.getCapacity());
        System.out.println("Combined features demonstration completed\n");
    }
} 