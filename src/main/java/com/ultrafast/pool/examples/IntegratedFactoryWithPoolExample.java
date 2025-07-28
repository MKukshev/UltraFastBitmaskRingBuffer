package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.ObjectPool;
import com.ultrafast.pool.factory.IntegratedObjectFactory;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Пример использования IntegratedObjectFactory с BitmaskRingBufferUltraVarHandleAutoExpand.
 * Демонстрирует, как IntegratedObjectFactory теперь совместим с пулом благодаря наследованию.
 */
public class IntegratedFactoryWithPoolExample {
    
    public static void main(String[] args) {
        System.out.println("=== Integrated Factory with Pool Example ===\n");
        
        // Пример 1: Прямое использование IntegratedObjectFactory с пулом
        example1_DirectUsageWithPool();
        
        // Пример 2: Использование метода createPool()
        example2_CreatePoolMethod();
        
        // Пример 3: Интегрированная фабрика с AutoReturnSimpleTask
        example3_WithAutoReturnTask();
        
        // Пример 4: Сравнение с IndependentObjectFactory
        example4_ComparisonWithIndependentFactory();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: Прямое использование IntegratedObjectFactory с пулом
     */
    private static void example1_DirectUsageWithPool() {
        System.out.println("--- Example 1: Direct Usage with Pool ---");
        
        // Создаем базовый пул
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> basePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, SimpleTask::new);
        
        // Создаем интегрированную фабрику
        IntegratedObjectFactory<SimpleTask> factory = new IntegratedObjectFactory<>(
            basePool,
            () -> {
                SimpleTask task = new SimpleTask();
                task.setData("Integrated Factory Task");
                System.out.println("Created task with data: " + task.getData());
                return task;
            }
        );
        
        // Создаем новый пул с интегрированной фабрикой (теперь это работает!)
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> newPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        System.out.println("New pool created with IntegratedObjectFactory");
        System.out.println("New pool capacity: " + newPool.getCapacity());
        
        // Используем новый пул
        SimpleTask task1 = newPool.getFreeObject();
        task1.execute();
        newPool.setFreeObject(task1);
        
        SimpleTask task2 = newPool.getFreeObject();
        task2.execute();
        newPool.setFreeObject(task2);
        
        System.out.println("Task 1 execution count: " + task1.getExecutionCount());
        System.out.println("Task 2 execution count: " + task2.getExecutionCount());
        System.out.println("Final new pool capacity: " + newPool.getCapacity());
    }
    
    /**
     * Пример 2: Использование метода createPool()
     */
    private static void example2_CreatePoolMethod() {
        System.out.println("\n--- Example 2: Using createPool() Method ---");
        
        // Создаем базовый пул
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> basePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, SimpleTask::new);
        
        // Создаем интегрированную фабрику
        IntegratedObjectFactory<SimpleTask> factory = new IntegratedObjectFactory<SimpleTask>(
            basePool,
            () -> {
                SimpleTask task = new SimpleTask();
                task.setData("CreatePool Method Task");
                return task;
            }
        );
        
        // Используем метод createPool() (теперь это работает!)
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> createdPool = factory.createPool(4);
        
        System.out.println("Pool created using createPool() method");
        System.out.println("Created pool capacity: " + createdPool.getCapacity());
        
        // Используем созданный пул
        SimpleTask task = createdPool.getFreeObject();
        task.execute();
        createdPool.setFreeObject(task);
        
        System.out.println("Task execution count: " + task.getExecutionCount());
        System.out.println("Final created pool capacity: " + createdPool.getCapacity());
    }
    
    /**
     * Пример 3: Интегрированная фабрика с AutoReturnSimpleTask
     */
    private static void example3_WithAutoReturnTask() {
        System.out.println("\n--- Example 3: Integrated Factory with AutoReturnSimpleTask ---");
        
        // Создаем базовый пул
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> basePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, AutoReturnSimpleTask::new);
        
        // Создаем интегрированную фабрику
        IntegratedObjectFactory<AutoReturnSimpleTask> factory = new IntegratedObjectFactory<AutoReturnSimpleTask>(
            basePool,
            () -> {
                AutoReturnSimpleTask task = new AutoReturnSimpleTask();
                task.setData("Auto Return Task from Integrated Factory");
                System.out.println("Created auto-return task with data: " + task.getData());
                return task;
            }
        );
        
        // Создаем новый пул с интегрированной фабрикой
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> newPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с автоматическим возвратом
        AutoReturnSimpleTask task1 = newPool.getFreeObject();
        executor.submit((Runnable) task1);
        
        AutoReturnSimpleTask task2 = newPool.getFreeObject();
        executor.submit((Runnable) task2);
        
        // Ждем завершения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("Auto-return tasks completed");
        System.out.println("Final new pool capacity: " + newPool.getCapacity());
    }
    
    /**
     * Пример 4: Сравнение с IndependentObjectFactory
     */
    private static void example4_ComparisonWithIndependentFactory() {
        System.out.println("\n--- Example 4: Comparison with IndependentObjectFactory ---");
        
        // Создаем базовый пул
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> basePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, SimpleTask::new);
        
        // IndependentObjectFactory (независимая)
        com.ultrafast.pool.factory.IndependentObjectFactory<SimpleTask> independentFactory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Independent Factory Task");
            return task;
        };
        
        // IntegratedObjectFactory (интегрированная)
        IntegratedObjectFactory<SimpleTask> integratedFactory = new IntegratedObjectFactory<SimpleTask>(
            basePool,
            () -> {
                SimpleTask task = new SimpleTask();
                task.setData("Integrated Factory Task");
                return task;
            }
        );
        
        // Создаем пулы с разными фабриками
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> independentPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, independentFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> integratedPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, integratedFactory);
        
        // Используем независимую фабрику
        SimpleTask independentTask = independentPool.getFreeObject();
        independentTask.execute();
        independentPool.setFreeObject(independentTask);
        
        // Используем интегрированную фабрику
        SimpleTask integratedTask = integratedPool.getFreeObject();
        integratedTask.execute();
        integratedPool.setFreeObject(integratedTask);
        
        // Используем метод createAndReturn() интегрированной фабрики
        SimpleTask createdAndReturnedTask = integratedFactory.createAndReturn();
        createdAndReturnedTask.execute();
        
        System.out.println("Independent task data: " + independentTask.getData());
        System.out.println("Integrated task data: " + integratedTask.getData());
        System.out.println("Created and returned task data: " + createdAndReturnedTask.getData());
        System.out.println("Both factory types work with pools!");
        
        // Показываем разницу в архитектуре
        System.out.println("\n--- Architecture Differences ---");
        System.out.println("IndependentObjectFactory: Standalone, no pool dependency");
        System.out.println("IntegratedObjectFactory: Integrated with pool, has pool reference");
        System.out.println("Both: Compatible with BitmaskRingBufferUltraVarHandleAutoExpand");
    }
} 