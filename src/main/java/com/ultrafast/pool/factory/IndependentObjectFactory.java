package com.ultrafast.pool.factory;

import java.util.function.Supplier;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory;

/**
 * Независимая фабрика объектов, которая не зависит от пула.
 * Наследует от ObjectFactory для совместимости с пулом.
 * Следует принципу единственной ответственности (SRP).
 * 
 * @param <T> Тип создаваемых объектов
 */
@FunctionalInterface
public interface IndependentObjectFactory<T> extends ObjectFactory<T> {
    // Метод createObject() наследуется от ObjectFactory<T>
    
    /**
     * Создает фабрику из Supplier
     */
    static <T> IndependentObjectFactory<T> fromSupplier(Supplier<T> supplier) {
        return supplier::get;
    }
    
    /**
     * Создает фабрику с инициализацией объекта
     */
    static <T> IndependentObjectFactory<T> withInitializer(Supplier<T> supplier, java.util.function.Consumer<T> initializer) {
        return () -> {
            T obj = supplier.get();
            initializer.accept(obj);
            return obj;
        };
    }
} 