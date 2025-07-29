package com.ultrafast.pool.smart;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import com.ultrafast.pool.ObjectPool;

/**
 * Умный пул задач с автоматическим управлением Future и интеграцией с ObjectPool.
 * Предоставляет элегантный API для работы с задачами, скрывая сложность управления Future.
 */
public class SmartTaskPool<T> {
    private final ObjectPool<T> pool;
    private final ExecutorService executorService;
    private final FutureRegistry futureRegistry;
    private final TaskLifecycleManager lifecycleManager;
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    
    /**
     * Создает новый SmartTaskPool с указанным пулом объектов и executor'ом.
     */
    public SmartTaskPool(ObjectPool<T> pool, ExecutorService executorService) {
        this.pool = pool;
        this.executorService = executorService;
        this.futureRegistry = new FutureRegistry();
        this.lifecycleManager = new TaskLifecycleManager(pool, futureRegistry);
    }
    
    /**
     * Простой API для отправки задачи.
     */
    public <R> Future<R> submit(Function<T, R> task) {
        return lifecycleManager.submitTask(task, executorService, generateTaskId());
    }
    
    /**
     * Расширенный API с конфигурацией.
     */
    public <R> Future<R> submit(TaskConfig config, Function<T, R> task) {
        return lifecycleManager.submitTask(task, executorService, generateTaskId(), config);
    }
    
    /**
     * Fluent API для создания задач с настройками.
     */
    public TaskBuilder<T> submit() {
        return new TaskBuilder<>(this);
    }
    
    /**
     * Batch API для отправки множественных задач.
     */
    public List<Future<?>> submitAll(List<Function<T, ?>> tasks) {
        List<Future<?>> futures = new ArrayList<>();
        for (Function<T, ?> task : tasks) {
            futures.add(submit(task));
        }
        return futures;
    }
    
    /**
     * Batch API с конфигурацией.
     */
    public List<Future<?>> submitAll(TaskConfig config, List<Function<T, ?>> tasks) {
        List<Future<?>> futures = new ArrayList<>();
        for (Function<T, ?> task : tasks) {
            futures.add(submit(config, task));
        }
        return futures;
    }
    
    /**
     * Отменяет конкретную задачу по ID.
     */
    public void cancelTask(String taskId) {
        futureRegistry.cancelTask(taskId);
    }
    
    /**
     * Отменяет все активные задачи.
     */
    public void cancelAllTasks() {
        futureRegistry.cancelAllTasks();
    }
    
    /**
     * Возвращает ID всех активных задач.
     */
    public Set<String> getActiveTaskIds() {
        return futureRegistry.getActiveTaskIds();
    }
    
    /**
     * Получает статистику пула задач.
     */
    public TaskPoolStatistics getStatistics() {
        return lifecycleManager.getStatistics();
    }
    
    /**
     * Возвращает базовый пул объектов для прямого доступа.
     */
    public ObjectPool<T> getPool() {
        return pool;
    }
    
    /**
     * Завершает работу пула, отменяя все задачи и закрывая executor.
     */
    public void shutdown() {
        cancelAllTasks();
        executorService.shutdown();
    }
    
    /**
     * Принудительно завершает работу пула.
     */
    public void shutdownNow() {
        cancelAllTasks();
        executorService.shutdownNow();
    }
    
    /**
     * Проверяет, завершена ли работа пула.
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }
    
    /**
     * Проверяет, завершена ли работа пула и все задачи выполнены.
     */
    public boolean isTerminated() {
        return executorService.isTerminated();
    }
    
    /**
     * Ожидает завершения работы пула с таймаутом.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }
    
    private String generateTaskId() {
        return "task_" + taskIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }
    
    // Внутренние классы для реализации
    
    /**
     * Fluent API builder для создания задач с настройками.
     */
    public class TaskBuilder<R> {
        private final SmartTaskPool<T> pool;
        private Duration timeout;
        private boolean autoCancelOnError = false;
        private Consumer<T> preProcessor;
        private Consumer<T> postProcessor;
        private String taskName;
        private boolean retryOnFailure = false;
        private int maxRetries = 3;
        
        public TaskBuilder(SmartTaskPool<T> pool) {
            this.pool = pool;
        }
        
        public TaskBuilder<R> withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public TaskBuilder<R> autoCancelOnError() {
            this.autoCancelOnError = true;
            return this;
        }
        
        public TaskBuilder<R> preProcess(Consumer<T> preProcessor) {
            this.preProcessor = preProcessor;
            return this;
        }
        
        public TaskBuilder<R> postProcess(Consumer<T> postProcessor) {
            this.postProcessor = postProcessor;
            return this;
        }
        
        public TaskBuilder<R> withName(String taskName) {
            this.taskName = taskName;
            return this;
        }
        
        public TaskBuilder<R> retryOnFailure(int maxRetries) {
            this.retryOnFailure = true;
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Future<R> execute(Function<T, R> task) {
            TaskConfig config = new TaskConfig()
                .withTimeout(timeout)
                .preProcess(preProcessor)
                .postProcess(postProcessor)
                .withName(taskName)
                .retryOnFailure(maxRetries);
            
            if (autoCancelOnError) {
                config.autoCancelOnError();
            }
            
            return pool.submit(config, task);
        }
    }
    
    /**
     * Конфигурация задачи.
     */
    public static class TaskConfig {
        private Duration timeout;
        private boolean autoCancelOnError = false;
        private Consumer<?> preProcessor;
        private Consumer<?> postProcessor;
        private String taskName;
        private boolean retryOnFailure = false;
        private int maxRetries = 3;
        
        public TaskConfig withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public TaskConfig autoCancelOnError() {
            this.autoCancelOnError = true;
            return this;
        }
        
        public TaskConfig preProcess(Consumer<?> preProcessor) {
            this.preProcessor = preProcessor;
            return this;
        }
        
        public TaskConfig postProcess(Consumer<?> postProcessor) {
            this.postProcessor = postProcessor;
            return this;
        }
        
        public TaskConfig withName(String taskName) {
            this.taskName = taskName;
            return this;
        }
        
        public TaskConfig retryOnFailure(int maxRetries) {
            this.retryOnFailure = true;
            this.maxRetries = maxRetries;
            return this;
        }
        
        // Геттеры
        public Duration getTimeout() { return timeout; }
        public boolean isAutoCancelOnError() { return autoCancelOnError; }
        public Consumer<?> getPreProcessor() { return preProcessor; }
        public Consumer<?> getPostProcessor() { return postProcessor; }
        public String getTaskName() { return taskName; }
        public boolean isRetryOnFailure() { return retryOnFailure; }
        public int getMaxRetries() { return maxRetries; }
    }
    
    /**
     * Статистика пула задач.
     */
    public static class TaskPoolStatistics {
        private final int totalTasks;
        private final int activeTasks;
        private final int completedTasks;
        private final int cancelledTasks;
        private final int failedTasks;
        
        public TaskPoolStatistics(int totalTasks, int activeTasks, int completedTasks, 
                                int cancelledTasks, int failedTasks) {
            this.totalTasks = totalTasks;
            this.activeTasks = activeTasks;
            this.completedTasks = completedTasks;
            this.cancelledTasks = cancelledTasks;
            this.failedTasks = failedTasks;
        }
        
        public int getTotalTasks() { return totalTasks; }
        public int getActiveTasks() { return activeTasks; }
        public int getCompletedTasks() { return completedTasks; }
        public int getCancelledTasks() { return cancelledTasks; }
        public int getFailedTasks() { return failedTasks; }
        
        @Override
        public String toString() {
            return String.format("TaskPoolStatistics{total=%d, active=%d, completed=%d, cancelled=%d, failed=%d}",
                totalTasks, activeTasks, completedTasks, cancelledTasks, failedTasks);
        }
    }
    
    /**
     * Реестр Future для управления задачами.
     */
    private static class FutureRegistry {
        private final Map<String, Future<?>> registry = new ConcurrentHashMap<>();
        private final AtomicLong totalTasks = new AtomicLong(0);
        private final AtomicLong completedTasks = new AtomicLong(0);
        private final AtomicLong cancelledTasks = new AtomicLong(0);
        private final AtomicLong failedTasks = new AtomicLong(0);
        
        public void registerTask(String taskId, Future<?> future) {
            registry.put(taskId, future);
            totalTasks.incrementAndGet();
        }
        
        public void removeTask(String taskId) {
            registry.remove(taskId);
        }
        
        public void cancelTask(String taskId) {
            Future<?> future = registry.get(taskId);
            if (future != null && !future.isDone()) {
                if (future.cancel(true)) {
                    cancelledTasks.incrementAndGet();
                }
            }
        }
        
        public void cancelAllTasks() {
            registry.values().forEach(future -> {
                if (!future.isDone()) {
                    if (future.cancel(true)) {
                        cancelledTasks.incrementAndGet();
                    }
                }
            });
            registry.clear();
        }
        
        public Set<String> getActiveTaskIds() {
            return new HashSet<>(registry.keySet());
        }
        
        public TaskPoolStatistics getStatistics() {
            int active = registry.size();
            int total = (int) totalTasks.get();
            int completed = (int) completedTasks.get();
            int cancelled = (int) cancelledTasks.get();
            int failed = (int) failedTasks.get();
            
            return new TaskPoolStatistics(total, active, completed, cancelled, failed);
        }
        
        public void markCompleted(String taskId) {
            completedTasks.incrementAndGet();
            removeTask(taskId);
        }
        
        public void markFailed(String taskId) {
            failedTasks.incrementAndGet();
            removeTask(taskId);
        }
    }
    
    /**
     * Менеджер жизненного цикла задач.
     */
    private static class TaskLifecycleManager {
        private final ObjectPool<?> pool;
        private final FutureRegistry futureRegistry;
        
        public TaskLifecycleManager(ObjectPool<?> pool, FutureRegistry futureRegistry) {
            this.pool = pool;
            this.futureRegistry = futureRegistry;
        }
        
        public <T, R> Future<R> submitTask(Function<T, R> task, ExecutorService executor, String taskId) {
            return submitTask(task, executor, taskId, new TaskConfig());
        }
        
        public <T, R> Future<R> submitTask(Function<T, R> task, ExecutorService executor, 
                                         String taskId, TaskConfig config) {
            Callable<R> wrappedTask = createWrappedTask(task, taskId, config);
            
            Future<R> future = executor.submit(wrappedTask);
            futureRegistry.registerTask(taskId, future);
            
            return new ManagedFuture<>(future, taskId, futureRegistry);
        }
        
        private <T, R> Callable<R> createWrappedTask(Function<T, R> task, String taskId, TaskConfig config) {
            return () -> {
                Object poolObject = null;
                int retryCount = 0;
                
                while (retryCount <= config.getMaxRetries()) {
                    try {
                        poolObject = pool.getFreeObject();
                        
                        // Pre-processing
                        if (config.getPreProcessor() != null) {
                            @SuppressWarnings("unchecked")
                            Consumer<T> preProcessor = (Consumer<T>) config.getPreProcessor();
                            preProcessor.accept((T) poolObject);
                        }
                        
                        // Execute task
                        R result = task.apply((T) poolObject);
                        
                        // Post-processing
                        if (config.getPostProcessor() != null) {
                            @SuppressWarnings("unchecked")
                            Consumer<T> postProcessor = (Consumer<T>) config.getPostProcessor();
                            postProcessor.accept((T) poolObject);
                        }
                        
                        futureRegistry.markCompleted(taskId);
                        return result;
                        
                    } catch (Exception e) {
                        retryCount++;
                        
                        if (config.isAutoCancelOnError() || retryCount > config.getMaxRetries()) {
                            futureRegistry.markFailed(taskId);
                            throw e;
                        }
                        
                        // Retry logic
                        if (config.isRetryOnFailure() && retryCount <= config.getMaxRetries()) {
                            try {
                                Thread.sleep(100 * retryCount); // Exponential backoff
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException("Task interrupted during retry", ie);
                            }
                            continue;
                        }
                        
                        futureRegistry.markFailed(taskId);
                        throw e;
                        
                    } finally {
                        if (poolObject != null) {
                            @SuppressWarnings("unchecked")
                            ObjectPool<Object> typedPool = (ObjectPool<Object>) pool;
                            typedPool.setFreeObject(poolObject);
                        }
                    }
                }
                
                throw new RuntimeException("Max retries exceeded");
            };
        }
        
        public TaskPoolStatistics getStatistics() {
            return futureRegistry.getStatistics();
        }
    }
    
    /**
     * Обертка для Future с автоматическим управлением.
     */
    private static class ManagedFuture<R> implements Future<R> {
        private final Future<R> delegate;
        private final String taskId;
        private final FutureRegistry futureRegistry;
        
        public ManagedFuture(Future<R> delegate, String taskId, FutureRegistry futureRegistry) {
            this.delegate = delegate;
            this.taskId = taskId;
            this.futureRegistry = futureRegistry;
        }
        
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean result = delegate.cancel(mayInterruptIfRunning);
            if (result) {
                futureRegistry.removeTask(taskId);
            }
            return result;
        }
        
        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }
        
        @Override
        public boolean isDone() {
            return delegate.isDone();
        }
        
        @Override
        public R get() throws InterruptedException, ExecutionException {
            return delegate.get();
        }
        
        @Override
        public R get(long timeout, TimeUnit unit) 
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
        
        public String getTaskId() {
            return taskId;
        }
    }
} 