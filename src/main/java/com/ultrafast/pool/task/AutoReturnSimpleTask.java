package com.ultrafast.pool.task;

/**
 * Задача с автоматическим возвратом в пул, реализующая AutoReturnTask.
 * Автоматически возвращается в пул после выполнения.
 * Поддерживает расширенную функциональность из BaseTask.
 * Поддерживает отмену через task.cancel() и проверку отмены во время выполнения.
 */
public class AutoReturnSimpleTask extends AutoReturnTask<AutoReturnSimpleTask, Void> {
    private String data;
    private int executionCount = 0;
    private boolean simulateException = false;
    private boolean simulateTimeout = false;
    private boolean simulateCancellation = false;
    private boolean longRunningTask = false;
    private int cancellationCheckInterval = 100; // мс между проверками отмены
    
    public void setData(String data) {
        this.data = data;
    }
    
    public String getData() {
        return data;
    }
    
    public int getLocalExecutionCount() {
        return executionCount;
    }
    
    /**
     * Устанавливает режим длительной задачи с периодическими проверками отмены
     */
    public void setLongRunningTask(boolean longRunningTask) {
        this.longRunningTask = longRunningTask;
        logInfo("Long running task mode " + (longRunningTask ? "enabled" : "disabled") + " for: " + data);
    }
    
    /**
     * Устанавливает интервал проверки отмены (в миллисекундах)
     */
    public void setCancellationCheckInterval(int cancellationCheckInterval) {
        this.cancellationCheckInterval = cancellationCheckInterval;
        logInfo("Cancellation check interval set to " + cancellationCheckInterval + "ms for: " + data);
    }
    
    /**
     * Отменяет задачу. Может быть вызван извне или из самой задачи.
     */
    public void cancelTask() {
        cancel();
        logInfo("AutoReturnSimpleTask cancelled: " + data);
    }
    
    /**
     * Проверяет, была ли задача отменена
     */
    public boolean isTaskCancelled() {
        return isCancelled();
    }
    
    @Override
    public void execute() {
        executionCount++;
        logInfo("AutoReturnSimpleTask executing: " + data + " (local count: " + executionCount + ")");
        
        // Проверяем отмену в начале выполнения
        if (isCancelled()) {
            logWarning("AutoReturnSimpleTask cancelled before execution: " + data);
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
            
            // Обычная работа или длительная задача
            if (longRunningTask) {
                executeLongRunningTask();
            } else {
                executeNormalTask();
            }
            
        } catch (InterruptedException e) {
            logError("AutoReturnSimpleTask interrupted: " + data, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("AutoReturnSimpleTask interrupted", e);
        } catch (Exception e) {
            logError("AutoReturnSimpleTask execution failed: " + data, e);
            throw new RuntimeException("AutoReturnSimpleTask execution failed", e);
        }
    }
    
    /**
     * Выполнение обычной задачи
     */
    private void executeNormalTask() throws InterruptedException {
        long workTime = 50 + (long)(Math.random() * 150);
        logInfo("AutoReturnSimpleTask working for " + workTime + "ms: " + data);
        Thread.sleep(workTime);
        
        // Проверяем отмену после работы
        if (isCancelled()) {
            logWarning("AutoReturnSimpleTask cancelled after work: " + data);
            return;
        }
        
        logInfo("AutoReturnSimpleTask completed successfully: " + data);
    }
    
    /**
     * Выполнение длительной задачи с периодическими проверками отмены
     */
    private void executeLongRunningTask() throws InterruptedException {
        logInfo("AutoReturnSimpleTask starting long running task: " + data);
        
        long totalWorkTime = 5000; // 5 секунд работы
        long startTime = System.currentTimeMillis();
        long elapsedTime = 0;
        
        while (elapsedTime < totalWorkTime) {
            // Проверяем отмену каждые cancellationCheckInterval миллисекунд
            if (isCancelled()) {
                logWarning("AutoReturnSimpleTask cancelled during long running task: " + data + 
                          " (elapsed: " + elapsedTime + "ms)");
                return;
            }
            
            // Выполняем небольшую порцию работы
            long workChunk = Math.min(cancellationCheckInterval, totalWorkTime - elapsedTime);
            Thread.sleep(workChunk);
            
            elapsedTime = System.currentTimeMillis() - startTime;
            
            // Логируем прогресс каждые 500мс
            if (elapsedTime % 500 < workChunk) {
                logInfo("AutoReturnSimpleTask progress: " + data + 
                       " (" + (elapsedTime * 100 / totalWorkTime) + "% complete)");
            }
        }
        
        // Финальная проверка отмены
        if (isCancelled()) {
            logWarning("AutoReturnSimpleTask cancelled at the end of long running task: " + data);
            return;
        }
        
        logInfo("AutoReturnSimpleTask completed long running task successfully: " + data);
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
        return "AutoReturnSimpleTask{data='" + data + "', executionCount=" + executionCount + 
               ", returned=" + isReturned + ", cancelled=" + isCancelled() + 
               ", longRunning=" + longRunningTask + "}";
    }
} 