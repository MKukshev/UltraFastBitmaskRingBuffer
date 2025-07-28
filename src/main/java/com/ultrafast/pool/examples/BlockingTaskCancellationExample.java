package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.BlockingCallableTask;

/**
 * Пример демонстрации отмены блокирующих задач,
 * реализующих AutoReturnTask и Callable.
 */
public class BlockingTaskCancellationExample {
    
    public static void main(String[] args) {
        System.out.println("=== Blocking Task Cancellation Example ===\n");
        
        // Пример 1: Отмена через cancel()
        example1_CancelMethod();
        
        // Пример 2: Отмена через stop() метод
        example2_StopMethod();
        
        // Пример 3: Отмена через Future.cancel()
        example3_FutureCancel();
        
        // Пример 4: Отмена через таймаут
        example4_TimeoutCancellation();
        
        // Пример 5: Комбинированная отмена
        example5_CombinedCancellation();
        
        System.out.println("\n=== All examples completed ===");
    }
    
    /**
     * Пример 1: Отмена через метод cancel()
     */
    private static void example1_CancelMethod() {
        System.out.println("--- Example 1: Cancel Method ---");
        
        // Создаем пул
        IndependentObjectFactory<BlockingCallableTask> factory = () -> {
            BlockingCallableTask task = new BlockingCallableTask();
            task.setTaskName("CancelMethodTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(30));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<BlockingCallableTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        BlockingCallableTask task = pool.getFreeObject();
        task.setInputData("Cancel Method Test");
        
        // Запускаем в отдельном потоке
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit((java.util.concurrent.Callable<String>) task);
        
        // Даем время на начало выполнения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем задачу
        System.out.println("Cancelling task via cancel() method...");
        task.cancel();
        
        // Ждем завершения
        try {
            String result = future.get(5, TimeUnit.SECONDS);
            System.out.println("Task result: " + result);
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
        }
        
        executor.shutdown();
        System.out.println("Cancel method example completed\n");
    }
    
    /**
     * Пример 2: Отмена через метод stop()
     */
    private static void example2_StopMethod() {
        System.out.println("--- Example 2: Stop Method ---");
        
        // Создаем пул
        IndependentObjectFactory<BlockingCallableTask> factory = () -> {
            BlockingCallableTask task = new BlockingCallableTask();
            task.setTaskName("StopMethodTask");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<BlockingCallableTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        BlockingCallableTask task = pool.getFreeObject();
        task.setInputData("Stop Method Test");
        
        // Добавляем данные в очередь
        task.addData("Test Data 1");
        task.addData("Test Data 2");
        
        // Запускаем в отдельном потоке
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit((java.util.concurrent.Callable<String>) task);
        
        // Даем время на начало выполнения
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Останавливаем задачу через stop()
        System.out.println("Stopping task via stop() method...");
        task.stop();
        
        // Ждем завершения
        try {
            String result = future.get(5, TimeUnit.SECONDS);
            System.out.println("Task result: " + result);
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
        }
        
        executor.shutdown();
        System.out.println("Stop method example completed\n");
    }
    
    /**
     * Пример 3: Отмена через Future.cancel()
     */
    private static void example3_FutureCancel() {
        System.out.println("--- Example 3: Future Cancel ---");
        
        // Создаем пул
        IndependentObjectFactory<BlockingCallableTask> factory = () -> {
            BlockingCallableTask task = new BlockingCallableTask();
            task.setTaskName("FutureCancelTask");
            task.setLoggingEnabled(true);
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<BlockingCallableTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        BlockingCallableTask task = pool.getFreeObject();
        task.setInputData("Future Cancel Test");
        
        // Запускаем в отдельном потоке
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit((java.util.concurrent.Callable<String>) task);
        
        // Даем время на начало выполнения
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отменяем через Future
        System.out.println("Cancelling task via Future.cancel()...");
        boolean cancelled = future.cancel(true); // true = interrupt if running
        System.out.println("Future.cancel() result: " + cancelled);
        
        // Проверяем результат
        try {
            if (!future.isCancelled()) {
                String result = future.get(2, TimeUnit.SECONDS);
                System.out.println("Task result: " + result);
            } else {
                System.out.println("Task was cancelled via Future");
            }
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
        }
        
        executor.shutdown();
        System.out.println("Future cancel example completed\n");
    }
    
    /**
     * Пример 4: Отмена через таймаут
     */
    private static void example4_TimeoutCancellation() {
        System.out.println("--- Example 4: Timeout Cancellation ---");
        
        // Создаем пул
        IndependentObjectFactory<BlockingCallableTask> factory = () -> {
            BlockingCallableTask task = new BlockingCallableTask();
            task.setTaskName("TimeoutTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(3)); // Короткий таймаут
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<BlockingCallableTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Получаем задачу
        BlockingCallableTask task = pool.getFreeObject();
        task.setInputData("Timeout Test");
        
        // Запускаем в отдельном потоке
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit((java.util.concurrent.Callable<String>) task);
        
        // Ждем с таймаутом
        try {
            System.out.println("Waiting for task with timeout...");
            String result = future.get(5, TimeUnit.SECONDS);
            System.out.println("Task result: " + result);
        } catch (Exception e) {
            System.out.println("Task execution exception: " + e.getMessage());
            
            // Пытаемся отменить задачу
            if (!future.isDone()) {
                System.out.println("Cancelling task due to timeout...");
                future.cancel(true);
            }
        }
        
        executor.shutdown();
        System.out.println("Timeout cancellation example completed\n");
    }
    
    /**
     * Пример 5: Комбинированная отмена
     */
    private static void example5_CombinedCancellation() {
        System.out.println("--- Example 5: Combined Cancellation ---");
        
        // Создаем пул
        IndependentObjectFactory<BlockingCallableTask> factory = () -> {
            BlockingCallableTask task = new BlockingCallableTask();
            task.setTaskName("CombinedTask");
            task.setLoggingEnabled(true);
            task.setTimeout(Duration.ofSeconds(10));
            return task;
        };
        
        BitmaskRingBufferUltraVarHandleAutoExpand<BlockingCallableTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, factory);
        
        // Запускаем несколько задач
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        for (int i = 0; i < 3; i++) {
            final int taskId = i + 1;
            
            executor.submit(() -> {
                try {
                    BlockingCallableTask task = pool.getFreeObject();
                    task.setInputData("Combined Task " + taskId);
                    
                    // Добавляем данные для некоторых задач
                    if (taskId == 2) {
                        task.addData("Data for task " + taskId);
                    }
                    
                    System.out.println("Starting task " + taskId);
                    String result = task.call(); // Прямой вызов call()
                    System.out.println("Task " + taskId + " result: " + result);
                    
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
        
        // Отменяем все задачи в пуле
        System.out.println("\nCancelling all tasks in pool...");
        for (int i = 0; i < 3; i++) {
            BlockingCallableTask task = pool.getFreeObject();
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
        
        System.out.println("Combined cancellation example completed\n");
    }
} 