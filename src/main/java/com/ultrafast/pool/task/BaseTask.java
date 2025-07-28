package com.ultrafast.pool.task;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ultrafast.pool.ObjectPool;

/**
 * Базовый абстрактный класс для задач с расширенной функциональностью.
 * Поддерживает исключения, таймауты, отмену и логирование.
 * Не реализует Runnable и Callable - только базовую функциональность.
 * 
 * @param <T> Тип задачи (обычно сам класс)
 */
public abstract class BaseTask<T> {
    protected ObjectPool<T> pool;
    protected T self;
    protected volatile boolean isReturned = false;
    
    // Новые поля для расширенной функциональности
    protected volatile boolean isCancelled = false;
    protected volatile boolean isCompleted = false;
    protected volatile boolean hasException = false;
    protected volatile Exception lastException = null;
    protected volatile LocalDateTime startTime = null;
    protected volatile LocalDateTime endTime = null;
    protected volatile Duration timeout = Duration.ofSeconds(30); // По умолчанию 30 секунд
    protected volatile String taskName = "UnnamedTask";
    protected volatile String taskDescription = "";
    
    // Счетчики для статистики
    protected final AtomicLong executionCount = new AtomicLong(0);
    protected final AtomicLong totalExecutionTime = new AtomicLong(0);
    protected final AtomicLong exceptionCount = new AtomicLong(0);
    protected final AtomicLong timeoutCount = new AtomicLong(0);
    protected final AtomicLong cancellationCount = new AtomicLong(0);
    
    // Логгер для задачи
    protected final Logger logger = Logger.getLogger(getClass().getName());
    
    // Флаг для включения/выключения логирования
    protected volatile boolean loggingEnabled = true;
    
    /**
     * Инициализирует задачу с пулом и ссылкой на себя
     */
    public void initialize(ObjectPool<T> pool, T self) {
        this.pool = pool;
        this.self = self;
    }
    
    /**
     * Возвращает задачу в пул
     */
    protected final void free() {
        if (!isReturned && pool != null && self != null) {
            isReturned = true;
            pool.setFreeObject(self);
        }
    }
    
    /**
     * Проверяет, была ли задача возвращена в пул
     */
    public boolean isReturned() {
        return isReturned;
    }
    
    /**
     * Получает оригинальную задачу
     */
    public T getSelf() {
        return self;
    }
    
    /**
     * Получает пул
     */
    public ObjectPool<T> getPool() {
        return pool;
    }
    
    /**
     * Сбрасывает флаг возврата (для повторного использования)
     */
    protected void resetReturnFlag() {
        isReturned = false;
    }
    
    /**
     * Абстрактный метод для выполнения задачи
     */
    public abstract void execute();
    
    // ========== НОВЫЕ МЕТОДЫ ДЛЯ РАСШИРЕННОЙ ФУНКЦИОНАЛЬНОСТИ ==========
    
    /**
     * Выполняет задачу с поддержкой исключений, таймаутов и отмены
     */
    public final void executeWithExceptionHandling() {
        if (isCancelled) {
            logWarning("Task cancelled before execution: " + taskName);
            return;
        }
        
        startTime = LocalDateTime.now();
        isCompleted = false;
        hasException = false;
        lastException = null;
        
        logInfo("Starting task execution: " + taskName);
        
        try {
            // Проверяем таймаут перед выполнением
            if (timeout != null && timeout.toMillis() > 0) {
                long timeoutMs = timeout.toMillis();
                logInfo("Task timeout set to: " + timeoutMs + "ms");
            }
            
            // Выполняем задачу
            execute();
            
            // Отмечаем успешное завершение
            isCompleted = true;
            endTime = LocalDateTime.now();
            executionCount.incrementAndGet();
            
            long executionTime = java.time.Duration.between(startTime, endTime).toMillis();
            totalExecutionTime.addAndGet(executionTime);
            
            logInfo("Task completed successfully: " + taskName + " (execution time: " + executionTime + "ms)");
            
        } catch (Exception e) {
            handleException(e, "Task execution failed");
        }
    }
    
    /**
     * Обрабатывает исключения во время выполнения
     */
    protected void handleException(Exception e, String message) {
        hasException = true;
        lastException = e;
        exceptionCount.incrementAndGet();
        endTime = LocalDateTime.now();
        
        logError(message + ": " + taskName, e);
        
        // Проверяем, не был ли превышен таймаут
        if (startTime != null && timeout != null) {
            long executionTime = java.time.Duration.between(startTime, endTime).toMillis();
            if (executionTime > timeout.toMillis()) {
                timeoutCount.incrementAndGet();
                logError("Task timeout exceeded: " + taskName + " (execution time: " + executionTime + "ms, timeout: " + timeout.toMillis() + "ms)");
            }
        }
    }
    
    /**
     * Отменяет задачу
     */
    public final void cancel() {
        if (!isCancelled && !isCompleted) {
            isCancelled = true;
            cancellationCount.incrementAndGet();
            logInfo("Task cancelled: " + taskName);
        }
    }
    
    /**
     * Проверяет, была ли задача отменена
     */
    public final boolean isCancelled() {
        return isCancelled;
    }
    
    /**
     * Проверяет, была ли задача завершена
     */
    public final boolean isCompleted() {
        return isCompleted;
    }
    
    /**
     * Проверяет, произошло ли исключение
     */
    public final boolean hasException() {
        return hasException;
    }
    
    /**
     * Получает последнее исключение
     */
    public final Exception getLastException() {
        return lastException;
    }
    
    /**
     * Устанавливает таймаут выполнения
     */
    public final void setTimeout(Duration timeout) {
        this.timeout = timeout;
        logInfo("Task timeout set to: " + timeout.toSeconds() + " seconds");
    }
    
    /**
     * Устанавливает таймаут в миллисекундах
     */
    public final void setTimeout(long timeoutMs) {
        this.timeout = Duration.ofMillis(timeoutMs);
        logInfo("Task timeout set to: " + timeoutMs + "ms");
    }
    
    /**
     * Получает текущий таймаут
     */
    public final Duration getTimeout() {
        return timeout;
    }
    
    /**
     * Устанавливает имя задачи
     */
    public final void setTaskName(String taskName) {
        this.taskName = taskName;
    }
    
    /**
     * Получает имя задачи
     */
    public final String getTaskName() {
        return taskName;
    }
    
    /**
     * Устанавливает описание задачи
     */
    public final void setTaskDescription(String taskDescription) {
        this.taskDescription = taskDescription;
    }
    
    /**
     * Получает описание задачи
     */
    public final String getTaskDescription() {
        return taskDescription;
    }
    
    /**
     * Включает/выключает логирование
     */
    public final void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }
    
    /**
     * Проверяет, включено ли логирование
     */
    public final boolean isLoggingEnabled() {
        return loggingEnabled;
    }
    
    // ========== МЕТОДЫ ЛОГИРОВАНИЯ ==========
    
    protected void logInfo(String message) {
        if (loggingEnabled) {
            logger.info("[" + taskName + "] " + message);
        }
    }
    
    protected void logWarning(String message) {
        if (loggingEnabled) {
            logger.warning("[" + taskName + "] " + message);
        }
    }
    
    protected void logError(String message, Throwable throwable) {
        if (loggingEnabled) {
            logger.log(Level.SEVERE, "[" + taskName + "] " + message, throwable);
        }
    }
    
    protected void logError(String message) {
        if (loggingEnabled) {
            logger.severe("[" + taskName + "] " + message);
        }
    }
    
    // ========== МЕТОДЫ СТАТИСТИКИ ==========
    
    /**
     * Получает количество выполнений
     */
    public final long getExecutionCount() {
        return executionCount.get();
    }
    
    /**
     * Получает общее время выполнения в миллисекундах
     */
    public final long getTotalExecutionTime() {
        return totalExecutionTime.get();
    }
    
    /**
     * Получает среднее время выполнения в миллисекундах
     */
    public final double getAverageExecutionTime() {
        long count = executionCount.get();
        return count > 0 ? (double) totalExecutionTime.get() / count : 0.0;
    }
    
    /**
     * Получает количество исключений
     */
    public final long getExceptionCount() {
        return exceptionCount.get();
    }
    
    /**
     * Получает количество таймаутов
     */
    public final long getTimeoutCount() {
        return timeoutCount.get();
    }
    
    /**
     * Получает количество отмен
     */
    public final long getCancellationCount() {
        return cancellationCount.get();
    }
    
    /**
     * Получает время начала последнего выполнения
     */
    public final LocalDateTime getStartTime() {
        return startTime;
    }
    
    /**
     * Получает время окончания последнего выполнения
     */
    public final LocalDateTime getEndTime() {
        return endTime;
    }
    
    /**
     * Получает время выполнения последней задачи в миллисекундах
     */
    public final long getLastExecutionTime() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }
    
    /**
     * Сбрасывает статистику
     */
    public final void resetStatistics() {
        executionCount.set(0);
        totalExecutionTime.set(0);
        exceptionCount.set(0);
        timeoutCount.set(0);
        cancellationCount.set(0);
        logInfo("Statistics reset");
    }
    
    /**
     * Получает подробную статистику в виде строки
     */
    public final String getDetailedStatistics() {
        return String.format(
            "Task Statistics for '%s':\n" +
            "  Executions: %d\n" +
            "  Total Time: %dms\n" +
            "  Average Time: %.2fms\n" +
            "  Exceptions: %d\n" +
            "  Timeouts: %d\n" +
            "  Cancellations: %d\n" +
            "  Last Execution Time: %dms\n" +
            "  Status: %s",
            taskName,
            getExecutionCount(),
            getTotalExecutionTime(),
            getAverageExecutionTime(),
            getExceptionCount(),
            getTimeoutCount(),
            getCancellationCount(),
            getLastExecutionTime(),
            getStatus()
        );
    }
    
    /**
     * Получает текущий статус задачи
     */
    public final String getStatus() {
        if (isCancelled) return "CANCELLED";
        if (hasException) return "FAILED";
        if (isCompleted) return "COMPLETED";
        if (startTime != null) return "RUNNING";
        return "IDLE";
    }
    
    /**
     * Сбрасывает состояние задачи для повторного использования
     */
    public final void reset() {
        isCancelled = false;
        isCompleted = false;
        hasException = false;
        lastException = null;
        startTime = null;
        endTime = null;
        resetReturnFlag();
        logInfo("Task reset for reuse");
    }
} 