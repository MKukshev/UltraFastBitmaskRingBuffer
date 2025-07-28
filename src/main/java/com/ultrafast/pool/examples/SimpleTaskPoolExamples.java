package com.ultrafast.pool.examples;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Упрощенные примеры использования задач.
 */
public class SimpleTaskPoolExamples {
    
    public static void main(String[] args) {
        System.out.println("=== Simple Task Pool Examples ===\n");
        
        // Пример 1: SimpleTask с ручным возвратом
        example1_SimpleTask();
        
        // Пример 2: AutoReturnSimpleTask с автоматическим возвратом
        example2_AutoReturnTask();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: SimpleTask с ручным возвратом
     */
    private static void example1_SimpleTask() {
        System.out.println("--- Example 1: SimpleTask with Manual Return ---");
        
        // Создаем задачи напрямую
        SimpleTask task1 = new SimpleTask();
        task1.setData("Task 1");
        task1.execute();
        System.out.println("Task 1 executed: " + task1.getExecutionCount() + " times");
        
        SimpleTask task2 = new SimpleTask();
        task2.setData("Task 2");
        task2.execute();
        System.out.println("Task 2 executed: " + task2.getExecutionCount() + " times");
        
        System.out.println("SimpleTask examples completed");
    }
    
    /**
     * Пример 2: AutoReturnSimpleTask с автоматическим возвратом
     */
    private static void example2_AutoReturnTask() {
        System.out.println("\n--- Example 2: AutoReturnSimpleTask with Auto Return ---");
        
        // Создаем ExecutorService
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        // Используем задачи с автоматическим возвратом
        AutoReturnSimpleTask task1 = new AutoReturnSimpleTask();
        task1.setData("Auto Task 1");
        executor.submit((Runnable) task1); // Автоматически вернется в пул
        
        AutoReturnSimpleTask task2 = new AutoReturnSimpleTask();
        task2.setData("Auto Task 2");
        executor.submit((Runnable) task2); // Автоматически вернется в пул
        
        // Ждем завершения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("AutoReturnSimpleTask examples completed");
    }
} 