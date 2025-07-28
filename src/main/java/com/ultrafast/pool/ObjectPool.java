package com.ultrafast.pool;

/**
 * Интерфейс для работы с пулами объектов.
 * 
 * @param <T> Тип объектов в пуле
 */
public interface ObjectPool<T> {
    /**
     * Получает свободный объект из пула
     */
    T getFreeObject();
    
    /**
     * Возвращает объект в пул
     */
    boolean setFreeObject(T object);
    
    /**
     * @deprecated Используйте getFreeObject()
     */
    @Deprecated
    default T acquire() {
        return getFreeObject();
    }
    
    /**
     * @deprecated Используйте setFreeObject(T)
     */
    @Deprecated
    default boolean release(T object) {
        return setFreeObject(object);
    }
    
    /**
     * Получает текущую емкость пула
     */
    int getCapacity();
    
    /**
     * Очищает ресурсы пула
     */
    default void cleanup() {
        // По умолчанию ничего не делаем
    }
    
    /**
     * Получает статистику пула
     */
    default PoolStatistics getStatistics() {
        return new PoolStatistics(0, 0, 0, 0, 0);
    }
    
    /**
     * @deprecated Используйте getStatistics()
     */
    @Deprecated
    default PoolStatistics getPoolStatistics() {
        return getStatistics();
    }
    
    /**
     * Статистика пула объектов
     */
    class PoolStatistics {
        public final int capacity;
        public final long occupiedCount;
        public final long totalGets;
        public final long totalReturns;
        public final long totalWaits;
        
        // Старые поля для совместимости
        @Deprecated
        public final int maxPoolSize;
        @Deprecated
        public final int availableObjects;
        @Deprecated
        public final int borrowedObjects;
        @Deprecated
        public final long totalAcquires;
        @Deprecated
        public final long totalReleases;
        @Deprecated
        public final long totalCreates;
        @Deprecated
        public final int activeObjects;
        
        public PoolStatistics(int capacity, long occupiedCount, long totalGets, long totalReturns, long totalWaits) {
            this.capacity = capacity;
            this.occupiedCount = occupiedCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalWaits = totalWaits;
            
            // Инициализация старых полей для совместимости
            this.maxPoolSize = capacity;
            this.availableObjects = (int) (capacity - occupiedCount);
            this.borrowedObjects = (int) occupiedCount;
            this.totalAcquires = totalGets;
            this.totalReleases = totalReturns;
            this.totalCreates = totalGets; // Приблизительно
            this.activeObjects = (int) occupiedCount;
        }
        
        // Конструктор для совместимости со старыми вызовами
        @Deprecated
        public PoolStatistics(int maxPoolSize, int availableObjects, int borrowedObjects, 
                            long totalAcquires, long totalReleases, long totalCreates, long totalWaits, int activeObjects) {
            this.capacity = maxPoolSize;
            this.occupiedCount = borrowedObjects;
            this.totalGets = totalAcquires;
            this.totalReturns = totalReleases;
            this.totalWaits = totalWaits;
            
            // Старые поля
            this.maxPoolSize = maxPoolSize;
            this.availableObjects = availableObjects;
            this.borrowedObjects = borrowedObjects;
            this.totalAcquires = totalAcquires;
            this.totalReleases = totalReleases;
            this.totalCreates = totalCreates;
            this.activeObjects = activeObjects;
        }
        
        @Override
        public String toString() {
            return String.format("PoolStatistics{capacity=%d, occupied=%d, gets=%d, returns=%d, waits=%d}",
                    capacity, occupiedCount, totalGets, totalReturns, totalWaits);
        }
    }
} 