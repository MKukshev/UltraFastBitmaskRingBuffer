package com.ultrafast.pool.factory;

import com.ultrafast.pool.ObjectPool;
import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.task.AutoReturnResultTask;
import com.ultrafast.pool.task.AutoReturnSimpleTask;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Фабрика для создания задач с автоматической инициализацией пула.
 */
public class TaskFactory {
    
    /**
     * Создает фабрику для SimpleTask
     */
    public static IndependentObjectFactory<SimpleTask> createSimpleTaskFactory(ObjectPool<SimpleTask> pool) {
        return () -> {
            SimpleTask task = new SimpleTask();
            task.initialize(pool, task);
            return task;
        };
    }
    
    /**
     * Создает фабрику для AutoReturnSimpleTask
     */
    public static IndependentObjectFactory<AutoReturnSimpleTask> createAutoReturnSimpleTaskFactory(ObjectPool<AutoReturnSimpleTask> pool) {
        return () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            task.initialize(pool, task);
            return task;
        };
    }
    
    /**
     * Создает фабрику для AutoReturnResultTask
     */
    public static IndependentObjectFactory<AutoReturnResultTask> createAutoReturnResultTaskFactory(ObjectPool<AutoReturnResultTask> pool) {
        return () -> {
            AutoReturnResultTask task = new AutoReturnResultTask();
            task.initialize(pool, task);
            return task;
        };
    }
    
    /**
     * Создает интегрированную фабрику для SimpleTask
     */
    public static IntegratedObjectFactory<SimpleTask> createIntegratedSimpleTaskFactory(BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool) {
        return new IntegratedObjectFactory<SimpleTask>(pool, () -> {
            SimpleTask task = new SimpleTask();
            return task;
        });
    }
    
    /**
     * Создает интегрированную фабрику для AutoReturnSimpleTask
     */
    public static IntegratedObjectFactory<AutoReturnSimpleTask> createIntegratedAutoReturnSimpleTaskFactory(BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool) {
        return new IntegratedObjectFactory<AutoReturnSimpleTask>(pool, () -> {
            AutoReturnSimpleTask task = new AutoReturnSimpleTask();
            return task;
        });
    }
} 