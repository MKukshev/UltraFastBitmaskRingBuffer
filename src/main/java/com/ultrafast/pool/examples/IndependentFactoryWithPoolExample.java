package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.AutoReturnResultTask;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Пример использования IndependentObjectFactory напрямую с BitmaskRingBufferUltraVarHandleAutoExpand.
 * Демонстрирует, как IndependentObjectFactory теперь совместим с пулом благодаря наследованию.
 */
public class IndependentFactoryWithPoolExample {
    
    public static void main(String[] args) {
        System.out.println("=== Independent Factory with Pool Example ===\n");
        
        // Пример 1: IndependentObjectFactory с SimpleTask
        example1_IndependentFactoryWithSimpleTask();
        
        // Пример 2: IndependentObjectFactory с AutoReturnSimpleTask
        example2_IndependentFactoryWithAutoReturnTask();
        
        // Пример 3: IndependentObjectFactory с AutoReturnResultTask
        example3_IndependentFactoryWithResultTask();
        
        // Пример 4: Использование статических методов IndependentObjectFactory
        example4_StaticMethodsUsage();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: IndependentObjectFactory с SimpleTask
     */
    private static void example1_IndependentFactoryWithSimpleTask() {
        System.out.println("--- Example 1: IndependentObjectFactory with SimpleTask ---");
        
        // Создаем IndependentObjectFactory
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Independent Factory Task");
            System.out.println("Created task with data: " + task.getData());
            return task;
        };
        
        // Создаем пул с IndependentObjectFactory (теперь это работает!)
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        System.out.println("Pool created with IndependentObjectFactory");
        System.out.println("Initial pool capacity: " + pool.getCapacity());
        
        // Используем пул
        SimpleTask task1 = pool.getFreeObject();
        task1.execute();
        pool.setFreeObject(task1);
        
        SimpleTask task2 = pool.getFreeObject();
        task2.execute();
        pool.setFreeObject(task2);
        
        System.out.println("Task 1 execution count: " + task1.getExecutionCount());
        System.out.println("Task 2 execution count: " + task2.getExecutionCount());
        System.out.println("Final pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Пример 2: IndependentObjectFactory с AutoReturnSimpleTask
     */
    private static void example2_IndependentFactoryWithAutoReturnTask() {
        System.out.println("\n--- Example 2: IndependentObjectFactory with AutoReturnSimpleTask ---");
        
        // Создаем IndependentObjectFactory для AutoReturnSimpleTask
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setData("Auto Return Task from Independent Factory");
            System.out.println("Created auto-return task with data: " + task.getData());
            return task;
        };
        
        // Создаем пул с IndependentObjectFactory
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с автоматическим возвратом
        AutoReturnSimpleTask task1 = pool.getFreeObject();
        executor.submit((Runnable) task1);
        
        AutoReturnSimpleTask task2 = pool.getFreeObject();
        executor.submit((Runnable) task2);
        
        // Ждем завершения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("Auto-return tasks completed");
        System.out.println("Final pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Пример 3: IndependentObjectFactory с AutoReturnResultTask
     */
    private static void example3_IndependentFactoryWithResultTask() {
        System.out.println("\n--- Example 3: IndependentObjectFactory with AutoReturnResultTask ---");
        
        // Создаем IndependentObjectFactory для AutoReturnResultTask
        IndependentObjectFactory<AutoReturnResultTask> factory = () -> {
            AutoReturnResultTask task = new AutoReturnResultTask();
            task.setInputData("Default input from factory");
            System.out.println("Created result task with input: " + task.getInputData());
            return task;
        };
        
        // Создаем пул с IndependentObjectFactory
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnResultTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с результатами
        AutoReturnResultTask task1 = pool.getFreeObject();
        task1.setInputData("Custom Input 1");
        Future<String> future1 = executor.submit((java.util.concurrent.Callable<String>) task1);
        
        AutoReturnResultTask task2 = pool.getFreeObject();
        task2.setInputData("Custom Input 2");
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
        System.out.println("Result tasks completed");
        System.out.println("Final pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Пример 4: Использование статических методов IndependentObjectFactory
     */
    private static void example4_StaticMethodsUsage() {
        System.out.println("\n--- Example 4: Static Methods Usage ---");
        
        // Используем fromSupplier
        IndependentObjectFactory<SimpleTask> factory1 = IndependentObjectFactory.fromSupplier(() -> {
            SimpleTask task = new SimpleTask();
            task.setData("From Supplier");
            return task;
        });
        
        // Используем withInitializer
        IndependentObjectFactory<SimpleTask> factory2 = IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData("From Initializer");
                System.out.println("Task initialized with data: " + task.getData());
            }
        );
        
        // Создаем пулы с разными фабриками
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool1 = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory1);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool2 = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory2);
        
        // Используем первый пул
        SimpleTask task1 = pool1.getFreeObject();
        task1.execute();
        pool1.setFreeObject(task1);
        
        // Используем второй пул
        SimpleTask task2 = pool2.getFreeObject();
        task2.execute();
        pool2.setFreeObject(task2);
        
        System.out.println("Pool 1 task data: " + task1.getData());
        System.out.println("Pool 2 task data: " + task2.getData());
        System.out.println("Both pools work with IndependentObjectFactory!");
    }
} 