package com.ultrafast.pool.task;

/**
 * Задача с автоматическим возвратом в пул и возвращаемым результатом.
 * Реализует Callable для возврата результата.
 */
public class AutoReturnResultTask extends AutoReturnTask<AutoReturnResultTask, String> {
    private String inputData;
    private int executionCount = 0;
    
    public void setInputData(String inputData) {
        this.inputData = inputData;
    }
    
    public String getInputData() {
        return inputData;
    }
    
    public int getLocalExecutionCount() {
        return executionCount;
    }
    
    @Override
    public void execute() {
        executionCount++;
        System.out.println("AutoReturnResultTask executing: " + inputData + " (count: " + executionCount + ")");
        // Симуляция работы
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    protected String executeWithResult() throws Exception {
        execute();
        return "Processed: " + inputData + " (execution #" + executionCount + ")";
    }
    
    @Override
    public String toString() {
        return "AutoReturnResultTask{inputData='" + inputData + "', executionCount=" + executionCount + ", returned=" + isReturned + "}";
    }
} 