package com.ultrafast.pool;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Модернизированная классическая реализация пула объектов с предварительным созданием всех объектов
 * 
 * Основные компоненты:
 * - ConcurrentLinkedQueue<T> - основная очередь для хранения доступных объектов
 * - ConcurrentHashMap<T, ObjectMetadata> - трекинг выданных объектов
 * - T[] objects - массив всех созданных объектов (для честного сравнения с оптимизированными версиями)
 * - AtomicLong - счетчик статистики
 * - AtomicInteger - счетчик активных объектов
 * 
 * Эта реализация создает все объекты сразу при инициализации, что позволяет
 * провести честное сравнение производительности с оптимизированными версиями,
 * исключив разницу в стратегии создания объектов.
 */
public class BitmaskRingBufferClassicPreallocated<T> implements ObjectPool<T> {
    
    /**
     * Метаданные объекта для трекинга
     */
    private static class ObjectMetadata {
        final long acquireTime;
        final long threadId;
        
        ObjectMetadata(long acquireTime, long threadId) {
            this.acquireTime = acquireTime;
            this.threadId = threadId;
        }
    }
    
    // Основная очередь для хранения доступных объектов
    private final ConcurrentLinkedQueue<T> availableQueue;
    
    // Трекинг выданных объектов
    private final ConcurrentHashMap<T, ObjectMetadata> borrowedObjects;
    
    // Массив всех созданных объектов (для честного сравнения с оптимизированными версиями)
    private final T[] objects;
    
    // Фабрика для создания новых объектов
    private final Supplier<T> objectFactory;
    
    // Размер пула
    private final int poolSize;
    
    // Счетчики статистики
    private final AtomicLong totalAcquires = new AtomicLong(0);
    private final AtomicLong totalReleases = new AtomicLong(0);
    private final AtomicLong totalCreates = new AtomicLong(0);
    private final AtomicLong totalWaits = new AtomicLong(0);
    private final AtomicInteger activeObjects = new AtomicInteger(0);
    
    // Таймаут для ожидания объекта (в наносекундах)
    private final long acquireTimeoutNanos;
    
    /**
     * Конструктор модернизированного классического пула объектов
     * 
     * @param objectFactory фабрика для создания новых объектов
     * @param poolSize размер пула (все объекты создаются сразу)
     * @param acquireTimeoutMs таймаут ожидания объекта в миллисекундах
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferClassicPreallocated(Supplier<T> objectFactory, int poolSize, long acquireTimeoutMs) {
        this.objectFactory = objectFactory;
        this.poolSize = poolSize;
        this.acquireTimeoutNanos = acquireTimeoutMs * 1_000_000L;
        
        // Инициализация коллекций
        this.availableQueue = new ConcurrentLinkedQueue<>();
        this.borrowedObjects = new ConcurrentHashMap<>();
        
        // Создаем массив объектов (как в оптимизированных версиях)
        this.objects = (T[]) new Object[poolSize];
        
        // Предварительное создание ВСЕХ объектов
        for (int i = 0; i < poolSize; i++) {
            T obj = objectFactory.get();
            objects[i] = obj;
            availableQueue.offer(obj);
            totalCreates.incrementAndGet();
        }
        
        activeObjects.set(poolSize);
    }
    
    /**
     * Получение объекта из пула
     * 
     * Алгоритм:
     * 1. Пытаемся получить объект из очереди
     * 2. Если очередь пуста - ждем освобождения объекта
     * 3. Трекируем выданный объект
     * 
     * @return объект из пула или null при таймауте
     */
    @Override
    public T acquire() {
        long startTime = System.nanoTime();
        
        while (true) {
            // Пытаемся получить объект из очереди
            T obj = availableQueue.poll();
            
            if (obj != null) {
                // Объект найден, трекируем его
                trackBorrowedObject(obj, startTime);
                totalAcquires.incrementAndGet();
                return obj;
            }
            
            // Очередь пуста, проверяем таймаут
            long elapsed = System.nanoTime() - startTime;
            if (elapsed >= acquireTimeoutNanos) {
                totalWaits.incrementAndGet();
                return null; // Таймаут
            }
            
            // Ждем немного перед следующей попыткой
            try {
                Thread.sleep(0, 1000); // 1 микросекунда
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }
    
    /**
     * Возврат объекта в пул
     * 
     * Алгоритм:
     * 1. Удаляем объект из трекинга
     * 2. Возвращаем в очередь доступных объектов
     * 3. Обновляем статистику
     * 
     * @param obj объект для возврата
     */
    @Override
    public boolean release(T obj) {
        if (obj == null) {
            return false;
        }
        
        // Удаляем из трекинга
        ObjectMetadata metadata = borrowedObjects.remove(obj);
        if (metadata != null) {
            // Возвращаем в очередь
            availableQueue.offer(obj);
            totalReleases.incrementAndGet();
            return true;
        }
        return false;
    }
    
    /**
     * Трекинг выданного объекта
     * 
     * @param obj объект для трекинга
     * @param acquireTime время получения
     */
    private void trackBorrowedObject(T obj, long acquireTime) {
        long threadId = Thread.currentThread().getId();
        ObjectMetadata metadata = new ObjectMetadata(acquireTime, threadId);
        borrowedObjects.put(obj, metadata);
    }
    
    /**
     * Получение статистики пула
     * 
     * @return статистика пула
     */
    public ObjectPool.PoolStatistics getStatistics() {
        return new ObjectPool.PoolStatistics(
            poolSize,
            availableQueue.size(),
            borrowedObjects.size(),
            totalAcquires.get(),
            totalReleases.get(),
            totalCreates.get(),
            totalWaits.get(),
            activeObjects.get()
        );
    }
    
    /**
     * Очистка ресурсов пула
     * 
     * В модернизированной реализации очищаем коллекции
     */
    public void close() {
        availableQueue.clear();
        borrowedObjects.clear();
    }
    
    /**
     * Строковое представление пула
     * 
     * @return строковое представление с статистикой
     */
    @Override
    public String toString() {
        ObjectPool.PoolStatistics stats = getStatistics();
        return String.format(
            "BitmaskRingBufferClassicPreallocated{poolSize=%d, available=%d, borrowed=%d, " +
            "acquires=%d, releases=%d, creates=%d, waits=%d, active=%d}",
            stats.maxPoolSize,
            stats.availableObjects,
            stats.borrowedObjects,
            stats.totalAcquires,
            stats.totalReleases,
            stats.totalCreates,
            stats.totalWaits,
            stats.activeObjects
        );
    }
    
    /**
     * Получение текущей емкости пула
     * 
     * @return емкость пула
     */
    @Override
    public int getCapacity() {
        return poolSize;
    }
    
    /**
     * Получает свободный объект из пула (алиас для acquire)
     * 
     * @return свободный объект
     */
    @Override
    public T getFreeObject() {
        return acquire();
    }
    
    /**
     * Возвращает объект в пул (алиас для release)
     * 
     * @param object объект для возврата
     * @return true если объект успешно возвращен
     */
    @Override
    public boolean setFreeObject(T object) {
        return release(object);
    }
    
    /**
     * Получение размера пула
     * 
     * @return размер пула
     */
    public int getPoolSize() {
        return poolSize;
    }
    
    /**
     * Получение массива объектов (для анализа памяти)
     * 
     * @return массив всех объектов
     */
    public T[] getObjects() {
        return objects;
    }
} 