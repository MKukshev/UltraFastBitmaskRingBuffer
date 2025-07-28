package com.ultrafast.pool.task;

import java.util.concurrent.Callable;

/**
 * Расширенный абстрактный класс с автоматическим возвратом в пул.
 * Реализует Runnable и Callable для использования в ExecutorService.
 * 
 * @param <T> Тип задачи (обычно сам класс)
 * @param <R> Тип результата для Callable (по умолчанию Void)
 */
public abstract class AutoReturnTask<T, R> extends BaseTask<T> implements Runnable, Callable<R> {
    
    @Override
    public void run() {
        try {
            // Используем новую функциональность с обработкой исключений
            executeWithExceptionHandling();
        } finally {
            free(); // Автоматический возврат в пул
        }
    }
    
    @Override
    public R call() throws Exception {
        try {
            // Проверяем отмену перед выполнением
            if (isCancelled()) {
                logWarning("Task cancelled before call execution: " + getTaskName());
                return null;
            }
            
            // Выполняем с обработкой исключений
            executeWithExceptionHandling();
            
            // Возвращаем результат
            return executeWithResult();
        } finally {
            free(); // Автоматический возврат в пул
        }
    }
    
    /**
     * Выполняет задачу и возвращает результат
     * По умолчанию вызывает execute() и возвращает null
     */
    @SuppressWarnings("unchecked")
    protected R executeWithResult() throws Exception {
        execute();
        return (R) null;
    }
    
    /**
     * Публичный метод для ручного возврата (если нужно)
     */
    public void returnToPool() {
        free();
    }
    
    /**
     * Проверяет, можно ли выполнить задачу
     */
    public boolean canExecute() {
        return !isReturned && pool != null && self != null;
    }
} 