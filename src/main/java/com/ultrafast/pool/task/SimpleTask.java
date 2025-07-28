package com.ultrafast.pool.task;

/**
 * Простая задача с расширенной функциональностью.
 * Демонстрирует поддержку исключений, таймаутов, отмены и логирования.
 * Требует ручного возврата в пул.
 */
public class SimpleTask extends BaseTask<SimpleTask> {
    private String data;
    private int executionCount = 0;
    private boolean simulateException = false;
    private boolean simulateTimeout = false;
    private boolean simulateCancellation = false;
    
    public void setData(String data) {
        this.data = data;
    }
    
    public String getData() {
        return data;
    }
    
    public int getLocalExecutionCount() {
        return executionCount;
    }
    
    @Override
    public void execute() {
        executionCount++;
        logInfo("Starting execution: " + data + " (local count: " + executionCount + ")");
        
        // Проверяем отмену во время выполнения
        if (isCancelled()) {
            logWarning("Task cancelled during execution: " + data);
            return;
        }
        
        // Симуляция различных сценариев
        try {
            // Симуляция исключения
            if (simulateException) {
                logError("Simulating exception for task: " + data);
                throw new RuntimeException("Simulated exception for task: " + data);
            }
            
            // Симуляция таймаута
            if (simulateTimeout) {
                logWarning("Simulating timeout for task: " + data);
                Thread.sleep(getTimeout().toMillis() + 1000); // Превышаем таймаут
            }
            
            // Симуляция отмены
            if (simulateCancellation) {
                logWarning("Simulating cancellation for task: " + data);
                cancel();
                return;
            }
            
            // Обычная работа
            long workTime = 50 + (long)(Math.random() * 150);
            logInfo("Working for " + workTime + "ms: " + data);
            Thread.sleep(workTime);
            
            logInfo("Task completed successfully: " + data);
            
        } catch (InterruptedException e) {
            logError("Task interrupted: " + data, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task interrupted", e);
        } catch (Exception e) {
            logError("Task execution failed: " + data, e);
            throw new RuntimeException("Task execution failed", e);
        }
    }
    
    // ========== МЕТОДЫ ДЛЯ СИМУЛЯЦИИ ==========
    
    /**
     * Включает/выключает симуляцию исключения
     */
    public void setSimulateException(boolean simulateException) {
        this.simulateException = simulateException;
        logInfo("Exception simulation " + (simulateException ? "enabled" : "disabled") + " for task: " + data);
    }
    
    /**
     * Включает/выключает симуляцию таймаута
     */
    public void setSimulateTimeout(boolean simulateTimeout) {
        this.simulateTimeout = simulateTimeout;
        logInfo("Timeout simulation " + (simulateTimeout ? "enabled" : "disabled") + " for task: " + data);
    }
    
    /**
     * Включает/выключает симуляцию отмены
     */
    public void setSimulateCancellation(boolean simulateCancellation) {
        this.simulateCancellation = simulateCancellation;
        logInfo("Cancellation simulation " + (simulateCancellation ? "enabled" : "disabled") + " for task: " + data);
    }
    
    /**
     * Публичный метод для ручного возврата в пул
     */
    public void returnToPool() {
        free();
    }
    
    @Override
    public String toString() {
        return "SimpleTask{data='" + data + "', executionCount=" + executionCount + ", returned=" + isReturned + "}";
    }
} 