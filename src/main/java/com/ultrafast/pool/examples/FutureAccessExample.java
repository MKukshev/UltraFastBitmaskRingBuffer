package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Пример демонстрации различных способов физической отмены задач
 * и доступа к Future из задачи.
 */
public class FutureAccessExample {
    
    // Глобальный реестр Future для задач
    private static final Map<String, Future<?>> futureRegistry = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        System.out.println("=== Future Access Example ===\n");
        
        // Пример 1: Хранение Future в переменной
        example1_StoreFutureInVariable();
        
        // Пример 2: Реестр Future
        example2_FutureRegistry();
        
        // Пример 3: Комбинированный подход
        example3_CombinedApproach();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: Хранение Future в переменной
     */
    private static void example1_StoreFutureInVariable() {
        System.out.println("--- Example 1: Store Future in Variable ---");
        
        // Создаем пул
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setTaskName("VariableFutureTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(10));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        AutoReturnSimpleTask task = pool.getFreeObject();
        task.setData("Variable Future Test");
        
        // Запускаем и сохраняем Future в переменной
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Void> future = executor.submit((java.util.concurrent.Callable<Void>) task);
        
        System.out.println("Task submitted, Future stored in variable");
        
        // Даем время на начало выполнения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Физически отменяем через сохраненный Future
        System.out.println("Cancelling task via stored Future...");
        boolean cancelled = future.cancel(true);
        System.out.println("Future.cancel() result: " + cancelled);
        
        // Проверяем результат
        try {
            if (!future.isCancelled()) {
                Void result = future.get(2, TimeUnit.SECONDS);
                System.out.println("Task completed successfully");
            } else {
                System.out.println("Task was cancelled via Future");
            }
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
        }
        
        executor.shutdown();
        System.out.println("Variable Future example completed\n");
    }
    
    /**
     * Пример 2: Реестр Future
     */
    private static void example2_FutureRegistry() {
        System.out.println("--- Example 2: Future Registry ---");
        
        // Создаем пул
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setTaskName("RegistryTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(10));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        AutoReturnSimpleTask task = pool.getFreeObject();
        task.setData("Registry Test");
        
        // Запускаем и регистрируем Future
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Void> future = executor.submit((java.util.concurrent.Callable<Void>) task);
        
        // Регистрируем Future в глобальном реестре
        String taskId = task.getTaskName() + "_" + System.currentTimeMillis();
        futureRegistry.put(taskId, future);
        
        System.out.println("Task registered with ID: " + taskId);
        
        // Даем время на начало выполнения
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем через реестр
        System.out.println("Cancelling task via registry...");
        Future<?> registeredFuture = futureRegistry.get(taskId);
        if (registeredFuture != null) {
            boolean cancelled = registeredFuture.cancel(true);
            System.out.println("Registry Future.cancel() result: " + cancelled);
        }
        
        // Очищаем реестр
        futureRegistry.remove(taskId);
        
        // Проверяем результат
        try {
            if (!future.isCancelled()) {
                Void result = future.get(2, TimeUnit.SECONDS);
                System.out.println("Task completed successfully");
            } else {
                System.out.println("Task was cancelled via registry");
            }
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
        }
        
        executor.shutdown();
        System.out.println("Future Registry example completed\n");
    }
    
    /**
     * Пример 3: Комбинированный подход
     */
    private static void example3_CombinedApproach() {
        System.out.println("--- Example 3: Combined Approach ---");
        
        // Создаем пул
        IndependentObjectFactory<AutoReturnSimpleTask> factory = () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.setTaskName("CombinedTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(15));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Запускаем несколько задач
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        for (int i = 0; i < 3; i++) {
            final int taskId = i + 1;
            
            executor.submit(() -> {
                try {
                    AutoReturnSimpleTask task = pool.getFreeObject();
                    task.setData("Combined Task " + taskId);
                    
                    System.out.println("Starting task " + taskId);
                    Void result = task.call();
                    System.out.println("Task " + taskId + " completed successfully");
                    
                } catch (Exception e) {
                    System.err.println("Task " + taskId + " exception: " + e.getMessage());
                }
            });
        }
        
        // Даем время на выполнение
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем все задачи в пуле (кооперативная отмена)
        System.out.println("\nCancelling all tasks in pool (cooperative)...");
        for (int i = 0; i < 3; i++) {
            AutoReturnSimpleTask task = pool.getFreeObject();
            if (task != null) {
                task.cancel();
                System.out.println("Cancelled task: " + task.getTaskName());
                pool.setFreeObject(task);
            }
        }
        
        // Ждем завершения
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("Combined approach example completed\n");
    }
} 