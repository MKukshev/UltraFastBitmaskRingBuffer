package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.task.AutoReturnResultTask;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Пример использования BitmaskRingBufferUltraVarHandleAutoExpand с задачами.
 * Демонстрирует различные подходы к созданию и использованию пулов задач.
 */
public class AutoExpandTaskPoolExample {
    
    public static void main(String[] args) {
        System.out.println("=== AutoExpand Task Pool Example ===\n");
        
        // Пример 1: SimpleTask с ручным возвратом
        example1_SimpleTaskWithManualReturn();
        
        // Пример 2: AutoReturnSimpleTask с автоматическим возвратом
        example2_AutoReturnSimpleTask();
        
        // Пример 3: AutoReturnResultTask с результатами
        example3_AutoReturnResultTask();
        
        // Пример 4: Стресс-тест с множественными потоками
        example4_StressTest();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: SimpleTask с ручным возвратом
     */
    private static void example1_SimpleTaskWithManualReturn() {
        System.out.println("--- Example 1: SimpleTask with Manual Return ---");
        
        // Создаем пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> taskPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, SimpleTask::new);
        
        // Используем задачи
        SimpleTask task1 = taskPool.getFreeObject();
        task1.setData("Manual Task 1");
        task1.execute();
        task1.returnToPool();
        
        SimpleTask task2 = taskPool.getFreeObject();
        task2.setData("Manual Task 2");
        task2.execute();
        task2.returnToPool();
        
        System.out.println("Pool capacity: " + taskPool.getCapacity());
        System.out.println("Task 1 execution count: " + task1.getExecutionCount());
        System.out.println("Task 2 execution count: " + task2.getExecutionCount());
    }
    
    /**
     * Пример 2: AutoReturnSimpleTask с автоматическим возвратом
     */
    private static void example2_AutoReturnSimpleTask() {
        System.out.println("\n--- Example 2: AutoReturnSimpleTask with Auto Return ---");
        
        // Создаем пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> taskPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, AutoReturnSimpleTask::new);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с автоматическим возвратом
        AutoReturnSimpleTask task1 = taskPool.getFreeObject();
        task1.setData("Auto Task 1");
        executor.submit((Runnable) task1);
        
        AutoReturnSimpleTask task2 = taskPool.getFreeObject();
        task2.setData("Auto Task 2");
        executor.submit((Runnable) task2);
        
        // Ждем завершения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("Pool capacity: " + taskPool.getCapacity());
    }
    
    /**
     * Пример 3: AutoReturnResultTask с результатами
     */
    private static void example3_AutoReturnResultTask() {
        System.out.println("\n--- Example 3: AutoReturnResultTask with Results ---");
        
        // Создаем пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnResultTask> taskPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, AutoReturnResultTask::new);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с результатами
        AutoReturnResultTask task1 = taskPool.getFreeObject();
        task1.setInputData("Input Data 1");
        Future<String> future1 = executor.submit((java.util.concurrent.Callable<String>) task1);
        
        AutoReturnResultTask task2 = taskPool.getFreeObject();
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
        System.out.println("Pool capacity: " + taskPool.getCapacity());
    }
    
    /**
     * Пример 4: Стресс-тест с множественными потоками
     */
    private static void example4_StressTest() {
        System.out.println("\n--- Example 4: Stress Test with Multiple Threads ---");
        
        // Создаем пул задач
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> taskPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, AutoReturnSimpleTask::new);
        
        // Создаем ExecutorService с множественными потоками
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        // Запускаем множество задач
        for (int i = 0; i < 20; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    AutoReturnSimpleTask task = taskPool.getFreeObject();
                    task.setData("Stress Task " + taskId);
                    task.execute();
                    // Автоматический возврат в пул
                } catch (Exception e) {
                    System.err.println("Error in task " + taskId + ": " + e.getMessage());
                }
            });
        }
        
        // Ждем завершения
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("Final pool capacity: " + taskPool.getCapacity());
        System.out.println("Stress test completed successfully!");
    }
} 