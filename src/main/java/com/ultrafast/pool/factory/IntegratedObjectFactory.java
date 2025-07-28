package com.ultrafast.pool.factory;

import java.util.function.Supplier;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory;

/**
 * Интегрированная фабрика объектов, совмещенная с пулом.
 * Наследует от ObjectFactory для совместимости с пулом.
 * Нарушает принцип единственной ответственности, но упрощает использование.
 * 
 * @param <T> Тип создаваемых объектов
 */
public class IntegratedObjectFactory<T> implements ObjectFactory<T> {
    private final BitmaskRingBufferUltraVarHandleAutoExpand<T> pool;
    private final Supplier<T> objectSupplier;
    private final java.util.function.Consumer<T> initializer;
    
    public IntegratedObjectFactory(BitmaskRingBufferUltraVarHandleAutoExpand<T> pool, Supplier<T> objectSupplier) {
        this(pool, objectSupplier, null);
    }
    
    public IntegratedObjectFactory(BitmaskRingBufferUltraVarHandleAutoExpand<T> pool, Supplier<T> objectSupplier, 
                                 java.util.function.Consumer<T> initializer) {
        this.pool = pool;
        this.objectSupplier = objectSupplier;
        this.initializer = initializer;
    }
    
    public T createObject() {
        T obj = objectSupplier.get();
        if (initializer != null) {
            initializer.accept(obj);
        }
        return obj;
    }
    
    public BitmaskRingBufferUltraVarHandleAutoExpand<T> getPool() {
        return pool;
    }
    
    /**
     * Создает объект и сразу возвращает его в пул (для тестирования)
     */
    public T createAndReturn() {
        T obj = createObject();
        pool.setFreeObject(obj);
        return obj;
    }
    
    /**
     * Создает пул с этой фабрикой
     */
    public com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand<T> createPool(int capacity) {
        return new com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand<>(capacity, this);
    }
} 