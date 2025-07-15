package com.ultrafast.pool;

/**
 * Интерфейс для пула объектов
 * 
 * Определяет стандартный контракт для всех реализаций пулов объектов,
 * включая классические и оптимизированные версии.
 * 
 * @param <T> тип объектов в пуле
 */
public interface ObjectPool<T> {
    
    /**
     * Получить объект из пула
     * 
     * @return объект из пула или null при таймауте
     */
    T acquire();
    
    /**
     * Вернуть объект в пул
     * 
     * @param obj объект для возврата
     */
    void release(T obj);
    
    /**
     * Получить статистику пула
     * 
     * @return статистика пула
     */
    PoolStatistics getStatistics();
    
    /**
     * Закрыть пул и освободить ресурсы
     */
    void close();
    
    /**
     * Статистика пула объектов
     */
    class PoolStatistics {
        public final int maxPoolSize;
        public final long availableObjects;
        public final long borrowedObjects;
        public final long totalAcquires;
        public final long totalReleases;
        public final long totalCreates;
        public final long totalWaits;
        public final long activeObjects;
        
        public PoolStatistics(int maxPoolSize, long availableObjects, long borrowedObjects,
                            long totalAcquires, long totalReleases, long totalCreates,
                            long totalWaits, long activeObjects) {
            this.maxPoolSize = maxPoolSize;
            this.availableObjects = availableObjects;
            this.borrowedObjects = borrowedObjects;
            this.totalAcquires = totalAcquires;
            this.totalReleases = totalReleases;
            this.totalCreates = totalCreates;
            this.totalWaits = totalWaits;
            this.activeObjects = activeObjects;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStatistics{maxSize=%d, available=%d, borrowed=%d, " +
                "acquires=%d, releases=%d, creates=%d, waits=%d, active=%d}",
                maxPoolSize, availableObjects, borrowedObjects,
                totalAcquires, totalReleases, totalCreates, totalWaits, activeObjects
            );
        }
    }
} 