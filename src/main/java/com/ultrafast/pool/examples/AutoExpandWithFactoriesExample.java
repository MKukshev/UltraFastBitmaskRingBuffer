package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Пример использования BitmaskRingBufferUltraVarHandleAutoExpand с фабриками.
 * Демонстрирует различные способы создания и использования фабрик.
 */
public class AutoExpandWithFactoriesExample {
    
    public static void main(String[] args) {
        System.out.println("=== AutoExpand with Factories Example ===\n");
        
        // Пример 1: Независимая фабрика с SimpleTask
        example1_IndependentFactory();
        
        // Пример 2: Фабрика с AutoReturnSimpleTask
        example2_AutoReturnFactory();
        
        // Пример 3: Кастомная фабрика с дополнительной логикой
        example3_CustomFactory();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: Независимая фабрика с SimpleTask
     */
    private static void example1_IndependentFactory() {
        System.out.println("--- Example 1: Independent Factory with SimpleTask ---");
        
        // Создаем независимую фабрику
        IndependentObjectFactory<SimpleTask> factory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Independent Factory Task");
            return task;
        };
        
        // Создаем пул с IndependentObjectFactory (теперь это работает!)
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Используем пул напрямую
        SimpleTask task1 = pool.getFreeObject();
        task1.execute();
        pool.setFreeObject(task1);
        
        SimpleTask task2 = pool.getFreeObject();
        task2.execute();
        pool.setFreeObject(task2);
        
        System.out.println("Task 1 execution count: " + task1.getExecutionCount());
        System.out.println("Task 2 execution count: " + task2.getExecutionCount());
        System.out.println("Pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Пример 2: Фабрика с AutoReturnSimpleTask
     */
    private static void example2_AutoReturnFactory() {
        System.out.println("\n--- Example 2: Factory with AutoReturnSimpleTask ---");
        
        // Создаем фабрику
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setData("Factory Auto Task");
            return task;
        };
        
        // Создаем пул с IndependentObjectFactory
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем пул для получения задач
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
        System.out.println("Pool capacity: " + pool.getCapacity());
    }
    
    /**
     * Пример 3: Кастомная фабрика с дополнительной логикой
     */
    private static void example3_CustomFactory() {
        System.out.println("\n--- Example 3: Custom Factory with Additional Logic ---");
        
        // Создаем кастомную фабрику с дополнительной логикой
        IndependentObjectFactory<SimpleTask> customFactory = IndependentObjectFactory.withInitializer(
            () -> {
                SimpleTask task = new SimpleTask();
                return task;
            },
            task -> {
                // Дополнительная инициализация
                task.setData("Custom Factory Task");
                System.out.println("Custom factory initialized task: " + task);
            }
        );
        
        // Создаем пул с кастомной фабрикой
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, customFactory);
        
        // Используем пул
        SimpleTask task1 = pool.getFreeObject();
        task1.execute();
        pool.setFreeObject(task1);
        
        SimpleTask task2 = pool.getFreeObject();
        task2.execute();
        pool.setFreeObject(task2);
        
        System.out.println("Custom factory tasks completed");
        System.out.println("Task 1 execution count: " + task1.getExecutionCount());
        System.out.println("Task 2 execution count: " + task2.getExecutionCount());
        System.out.println("Pool capacity: " + pool.getCapacity());
    }
} 