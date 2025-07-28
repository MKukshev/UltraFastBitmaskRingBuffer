package com.ultrafast.pool.task;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Пример блокирующей задачи с поддержкой отмены.
 * Демонстрирует различные способы реализации отмены для Callable задач.
 */
public class BlockingCallableTask extends AutoReturnTask<BlockingCallableTask, String> {
    
    private String inputData;
    private int executionCount = 0;
    
    // Очередь для получения данных (блокирующая операция)
    private BlockingQueue<String> dataQueue;
    
    // Флаг для контроля блокирующих операций
    private volatile boolean shouldStop = false;
    
    public BlockingCallableTask() {
        this.dataQueue = new LinkedBlockingQueue<>();
    }
    
    public void setInputData(String inputData) {
        this.inputData = inputData;
    }
    
    public String getInputData() {
        return inputData;
    }
    
    public int getLocalExecutionCount() {
        return executionCount;
    }
    
    /**
     * Добавляет данные в очередь для обработки
     */
    public void addData(String data) {
        dataQueue.offer(data);
    }
    
    /**
     * Сигнализирует о необходимости остановки
     */
    public void stop() {
        shouldStop = true;
        // Добавляем специальный маркер для разблокировки очереди
        dataQueue.offer("STOP_MARKER");
    }
    
    @Override
    public void execute() {
        executionCount++;
        logInfo("BlockingCallableTask executing: " + inputData + " (count: " + executionCount + ")");
        
        try {
            // Способ 1: Блокирующая операция с проверкой отмены
            processDataWithCancellationCheck();
            
            // Способ 2: Блокирующая операция с таймаутом
            processDataWithTimeout();
            
            // Способ 3: Блокирующая операция с прерыванием
            processDataWithInterruption();
            
            logInfo("BlockingCallableTask completed successfully: " + inputData);
            
        } catch (InterruptedException e) {
            logError("BlockingCallableTask interrupted: " + inputData, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task interrupted", e);
        } catch (Exception e) {
            logError("BlockingCallableTask execution failed: " + inputData, e);
            throw new RuntimeException("Task execution failed", e);
        }
    }
    
    /**
     * Способ 1: Блокирующая операция с периодической проверкой отмены
     */
    private void processDataWithCancellationCheck() throws InterruptedException {
        logInfo("Processing data with cancellation check...");
        
        for (int i = 0; i < 10; i++) {
            // Проверяем отмену перед каждой итерацией
            if (isCancelled()) {
                logWarning("Task cancelled during data processing (iteration " + i + ")");
                return;
            }
            
            // Проверяем флаг остановки
            if (shouldStop) {
                logInfo("Task stopped by stop flag (iteration " + i + ")");
                return;
            }
            
            // Симулируем работу
            logInfo("Processing iteration " + i + " for: " + inputData);
            Thread.sleep(100); // Короткая блокирующая операция
        }
    }
    
    /**
     * Способ 2: Блокирующая операция с таймаутом
     */
    private void processDataWithTimeout() throws InterruptedException {
        logInfo("Processing data with timeout...");
        
        // Получаем данные из очереди с таймаутом
        String data = dataQueue.poll(2, TimeUnit.SECONDS); // 2 секунды таймаут
        
        if (data == null) {
            logWarning("Timeout waiting for data from queue");
            return;
        }
        
        if ("STOP_MARKER".equals(data)) {
            logInfo("Received stop marker from queue");
            return;
        }
        
        logInfo("Processing data from queue: " + data);
        
        // Обрабатываем данные с проверкой отмены
        for (int i = 0; i < 5; i++) {
            if (isCancelled()) {
                logWarning("Task cancelled during queue processing");
                return;
            }
            
            logInfo("Processing queue data iteration " + i + ": " + data);
            Thread.sleep(200);
        }
    }
    
    /**
     * Способ 3: Блокирующая операция с прерыванием потока
     */
    private void processDataWithInterruption() throws InterruptedException {
        logInfo("Processing data with interruption support...");
        
        try {
            // Долгая блокирующая операция
            for (int i = 0; i < 20; i++) {
                // Проверяем прерывание потока
                if (Thread.currentThread().isInterrupted()) {
                    logWarning("Thread interrupted during processing");
                    throw new InterruptedException("Thread interrupted");
                }
                
                // Проверяем отмену задачи
                if (isCancelled()) {
                    logWarning("Task cancelled during interruption processing");
                    return;
                }
                
                logInfo("Processing with interruption check " + i + ": " + inputData);
                Thread.sleep(150);
            }
        } catch (InterruptedException e) {
            logWarning("Interrupted during data processing: " + e.getMessage());
            throw e; // Перебрасываем исключение
        }
    }
    
    @Override
    protected String executeWithResult() throws Exception {
        execute();
        return "Processed: " + inputData + " (executions: " + executionCount + ", cancelled: " + isCancelled() + ")";
    }
    
    @Override
    public String toString() {
        return "BlockingCallableTask{inputData='" + inputData + "', executionCount=" + executionCount + 
               ", cancelled=" + isCancelled() + ", returned=" + isReturned + "}";
    }
} 