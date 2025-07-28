package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.AutoReturnResultTask;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Полный пример использования BitmaskRingBufferUltraVarHandleAutoExpand с задачами и фабриками.
 * Демонстрирует все возможности системы в одном месте.
 */
public class CompleteAutoExpandExample {
    
    public static void main(String[] args) {
        System.out.println("=== Complete AutoExpand Example ===\n");
        
        // Демонстрация 1: Базовое использование пула
        demonstrateBasicUsage();
        
        // Демонстрация 2: Автоматическое расширение
        demonstrateAutoExpansion();
        
        // Демонстрация 3: Многопоточность
        demonstrateMultithreading();
        
        // Демонстрация 4: Фабрики и задачи
        demonstrateFactoriesAndTasks();
        
        // Демонстрация 5: Статистика и мониторинг
        demonstrateStatistics();
        
        System.out.println("\n=== Complete Example Finished ===");
    }
    
    /**
     * Демонстрация 1: Базовое использование пула
     */
    private static void demonstrateBasicUsage() {
        System.out.println("--- Demonstration 1: Basic Pool Usage ---");
        
        // Создаем пул с начальной емкостью 3
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, SimpleTask::new);
        
        System.out.println("Initial pool capacity: " + pool.getCapacity());
        
        // Получаем объекты из пула
        SimpleTask task1 = pool.getFreeObject();
        SimpleTask task2 = pool.getFreeObject();
        SimpleTask task3 = pool.getFreeObject();
        
        // Используем задачи
        task1.setData("Task 1");
        task2.setData("Task 2");
        task3.setData("Task 3");
        
        task1.execute();
        task2.execute();
        task3.execute();
        
        // Возвращаем в пул
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        pool.setFreeObject(task3);
        
        System.out.println("Tasks executed successfully");
        System.out.println("Final pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Демонстрация 2: Автоматическое расширение
     */
    private static void demonstrateAutoExpansion() {
        System.out.println("\n--- Demonstration 2: Auto Expansion ---");
        
        // Создаем пул с автоматическим расширением
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, SimpleTask::new, 0.5, 200);
        
        System.out.println("Initial capacity: " + pool.getCapacity());
        System.out.println("Max allowed capacity: " + pool.getMaxAllowedCapacity());
        
        // Получаем больше объектов, чем начальная емкость
        SimpleTask[] tasks = new SimpleTask[10];
        for (int i = 0; i < 10; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].setData("Expansion Task " + i);
                tasks[i].execute();
            } else {
                System.out.println("Warning: Could not get task " + i + " from pool");
            }
        }
        
        System.out.println("Current capacity after expansion: " + pool.getCapacity());
        
        // Возвращаем все задачи
        for (SimpleTask task : tasks) {
            pool.setFreeObject(task);
        }
        
        // Показываем статистику
        var stats = pool.getStats();
        System.out.println("Total expansions: " + stats.totalExpansions);
        System.out.println("Auto expansion hits: " + stats.autoExpansionHits);
    }
    
    /**
     * Демонстрация 3: Многопоточность
     */
    private static void demonstrateMultithreading() {
        System.out.println("\n--- Demonstration 3: Multithreading ---");
        
        // Создаем пул для многопоточного использования
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, AutoReturnSimpleTask::new, 0.2, 100);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // Запускаем множество задач в разных потоках
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    AutoReturnSimpleTask task = pool.getFreeObject();
                    task.setData("Thread Task " + taskId + " from " + Thread.currentThread().getName());
                    task.execute();
                    // Автоматический возврат в пул
                } catch (Exception e) {
                    System.err.println("Error in task " + taskId + ": " + e.getMessage());
                }
            });
        }
        
        // Ждем завершения
        executor.shutdown();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Multithreaded execution completed");
        System.out.println("Final pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Демонстрация 4: Фабрики и задачи
     */
    private static void demonstrateFactoriesAndTasks() {
        System.out.println("\n--- Demonstration 4: Factories and Tasks ---");
        
        // Создаем пул
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnResultTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, AutoReturnResultTask::new);
        
        // Создаем фабрику с дополнительной логикой
        IndependentObjectFactory<AutoReturnResultTask> factory = IndependentObjectFactory.withInitializer(
            () -> new AutoReturnResultTask(),
            task -> {
                task.setInputData("Factory initialized task");
                System.out.println("Factory created task with input: " + task.getInputData());
            }
        );
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем фабрику для создания задач с результатами
        AutoReturnResultTask task1 = factory.createObject();
        task1.setInputData("Input Data 1");
        Future<String> future1 = executor.submit((java.util.concurrent.Callable<String>) task1);
        
        AutoReturnResultTask task2 = factory.createObject();
        task2.setInputData("Input Data 2");
        Future<String> future2 = executor.submit((java.util.concurrent.Callable<String>) task2);
        
        // Получаем результаты
        try {
            String result1 = future1.get();
            String result2 = future2.get();
            System.out.println("Result 1: " + result1);
            System.out.println("Result 2: " + result2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        executor.shutdown();
        System.out.println("Factory and tasks demonstration completed");
    }
    
    /**
     * Демонстрация 5: Статистика и мониторинг
     */
    private static void demonstrateStatistics() {
        System.out.println("\n--- Demonstration 5: Statistics and Monitoring ---");
        
        // Создаем пул с мониторингом
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, SimpleTask::new, 0.3, 150);
        
        // Выполняем операции
        SimpleTask[] tasks = new SimpleTask[15];
        for (int i = 0; i < 15; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].setData("Stats Task " + i);
                tasks[i].execute();
            } else {
                System.out.println("Warning: Could not get stats task " + i + " from pool");
            }
        }
        
        // Возвращаем задачи
        for (SimpleTask task : tasks) {
            if (task != null) {
                pool.setFreeObject(task);
            }
        }
        
        // Показываем детальную статистику
        var stats = pool.getStats();
        System.out.println("=== Pool Statistics ===");
        System.out.println("Current capacity: " + stats.capacity);
        System.out.println("Free objects: " + stats.freeCount);
        System.out.println("Busy objects: " + stats.busyCount);
        System.out.println("Total gets: " + stats.totalGets);
        System.out.println("Total returns: " + stats.totalReturns);
        System.out.println("Bit trick hits: " + stats.bitTrickHits);
        System.out.println("Stack hits: " + stats.stackHits);
        System.out.println("Auto expansion hits: " + stats.autoExpansionHits);
        System.out.println("Total expansions: " + stats.totalExpansions);
        System.out.println("Expansion percentage: " + (stats.expansionPercentage * 100) + "%");
        System.out.println("Max expansion percentage: " + stats.maxExpansionPercentage + "%");
        System.out.println("Max allowed capacity: " + stats.maxAllowedCapacity);
        
        // Показываем производительность
        double bitTrickRatio = stats.totalGets > 0 ? (double) stats.bitTrickHits / stats.totalGets : 0;
        double stackRatio = stats.totalGets > 0 ? (double) stats.stackHits / stats.totalGets : 0;
        double expansionRatio = stats.totalGets > 0 ? (double) stats.autoExpansionHits / stats.totalGets : 0;
        
        System.out.println("\n=== Performance Metrics ===");
        System.out.printf("Bit trick efficiency: %.2f%%\n", bitTrickRatio * 100);
        System.out.printf("Stack efficiency: %.2f%%\n", stackRatio * 100);
        System.out.printf("Expansion frequency: %.2f%%\n", expansionRatio * 100);
        System.out.printf("Average expansions per operation: %.2f\n", 
            stats.totalGets > 0 ? (double) stats.totalExpansions / stats.totalGets : 0);
    }
} 