package com.ultrafast.pool.task;

/**
 * Задача с автоматическим возвратом в пул, реализующая AutoReturnTask.
 * Автоматически возвращается в пул после выполнения.
 * Поддерживает расширенную функциональность из BaseTask.
 */
public class AutoReturnSimpleTask extends AutoReturnTask<AutoReturnSimpleTask, Void> {
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
        logInfo("AutoReturnSimpleTask executing: " + data + " (local count: " + executionCount + ")");
        
        // Проверяем отмену во время выполнения
        if (isCancelled()) {
            logWarning("AutoReturnSimpleTask cancelled during execution: " + data);
            return;
        }
        
        // Симуляция различных сценариев
        try {
            // Симуляция исключения
            if (simulateException) {
                logError("Simulating exception for AutoReturnSimpleTask: " + data);
                throw new RuntimeException("Simulated exception for AutoReturnSimpleTask: " + data);
            }
            
            // Симуляция таймаута
            if (simulateTimeout) {
                logWarning("Simulating timeout for AutoReturnSimpleTask: " + data);
                Thread.sleep(getTimeout().toMillis() + 1000); // Превышаем таймаут
            }
            
            // Симуляция отмены
            if (simulateCancellation) {
                logWarning("Simulating cancellation for AutoReturnSimpleTask: " + data);
                cancel();
                return;
            }
            
            // Обычная работа
            long workTime = 50 + (long)(Math.random() * 150);
            logInfo("AutoReturnSimpleTask working for " + workTime + "ms: " + data);
            Thread.sleep(workTime);
            logInfo("AutoReturnSimpleTask completed successfully: " + data);
        } catch (InterruptedException e) {
            logError("AutoReturnSimpleTask interrupted: " + data, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("AutoReturnSimpleTask interrupted", e);
        } catch (Exception e) {
            logError("AutoReturnSimpleTask execution failed: " + data, e);
            throw new RuntimeException("AutoReturnSimpleTask execution failed", e);
        }
    }
    
    // ========== МЕТОДЫ ДЛЯ СИМУЛЯЦИИ ==========
    
    /**
     * Включает/выключает симуляцию исключения
     */
    public void setSimulateException(boolean simulateException) {
        this.simulateException = simulateException;
        logInfo("Exception simulation " + (simulateException ? "enabled" : "disabled") + " for AutoReturnSimpleTask: " + data);
    }
    
    /**
     * Включает/выключает симуляцию таймаута
     */
    public void setSimulateTimeout(boolean simulateTimeout) {
        this.simulateTimeout = simulateTimeout;
        logInfo("Timeout simulation " + (simulateTimeout ? "enabled" : "disabled") + " for AutoReturnSimpleTask: " + data);
    }
    
    /**
     * Включает/выключает симуляцию отмены
     */
    public void setSimulateCancellation(boolean simulateCancellation) {
        this.simulateCancellation = simulateCancellation;
        logInfo("Cancellation simulation " + (simulateCancellation ? "enabled" : "disabled") + " for AutoReturnSimpleTask: " + data);
    }
    
    @Override
    public String toString() {
        return "AutoReturnSimpleTask{data='" + data + "', executionCount=" + executionCount + ", returned=" + isReturned + "}";
    }
} 