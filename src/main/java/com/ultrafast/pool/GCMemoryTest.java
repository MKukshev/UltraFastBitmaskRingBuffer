package com.ultrafast.pool;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Тест для измерения разницы в аллокациях объектов GC между различными реализациями пулов
 * 
 * Этот тест сравнивает:
 * - BitmaskRingBufferUltraVarHandle (оптимизированная версия)
 * - BitmaskRingBufferClassic (классическая версия)
 * 
 * Метрики:
 * - Количество сборок мусора
 * - Время сборки мусора
 * - Использование памяти
 * - Количество аллокаций объектов
 */
public class GCMemoryTest {
    
    private static final int THREAD_COUNT = 8;
    private static final int POOL_SIZE = 10000;
    private static final int TEST_DURATION_SECONDS = 300; // 5 минут
    private static final int OPERATIONS_PER_ITERATION = 1000;
    private static final int PAYLOAD_SIZE = 1024; // 1KB на объект
    
    // JMX beans для мониторинга GC и памяти
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    
    public static void main(String[] args) {
        System.out.println("=== ТЕСТ СРАВНЕНИЯ АЛЛОКАЦИЙ GC МЕЖДУ ПУЛАМИ ===\n");
        System.out.println("Конфигурация теста:");
        System.out.println("  - Потоков: " + THREAD_COUNT);
        System.out.println("  - Размер пула: " + POOL_SIZE);
        System.out.println("  - Длительность теста: " + TEST_DURATION_SECONDS + " секунд");
        System.out.println("  - Операций за итерацию: " + OPERATIONS_PER_ITERATION);
        System.out.println("  - Размер payload: " + PAYLOAD_SIZE + " байт");
        System.out.println();
        
        // Запускаем тесты
        testUltraVarHandlePool("BitmaskRingBufferUltraVarHandle", 
            new BitmaskRingBufferUltraVarHandle<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0)));
        
        // Принудительная сборка мусора между тестами
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        testObjectPool("BitmaskRingBufferClassic", 
            new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0), 
                POOL_SIZE / 2, POOL_SIZE, 1000));
    }
    
    private static void testObjectPool(String poolName, ObjectPool<HeavyTask> pool) {
        testPool(poolName, pool, true);
    }
    
    private static void testUltraVarHandlePool(String poolName, BitmaskRingBufferUltraVarHandle<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testPool(String poolName, Object pool, boolean isObjectPool) {
        System.out.println("--- Тестирование " + poolName + " ---");
        
        // Записываем начальные метрики GC
        long initialGcCount = getTotalGcCount();
        long initialGcTime = getTotalGcTime();
        MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
        
        System.out.println("Начальные метрики:");
        System.out.println("  - Использование heap: " + formatBytes(initialHeap.getUsed()));
        System.out.println("  - Количество GC: " + initialGcCount);
        System.out.println("  - Время GC: " + initialGcTime + " мс");
        
        // Запускаем нагрузочный тест
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalAcquireTime = new AtomicLong(0);
        AtomicLong totalReleaseTime = new AtomicLong(0);
        
        // Запускаем потоки
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    long endTime = System.currentTimeMillis() + (TEST_DURATION_SECONDS * 1000L);
                    
                    while (System.currentTimeMillis() < endTime) {
                        // Выполняем операции с пулом
                        for (int j = 0; j < OPERATIONS_PER_ITERATION; j++) {
                            long startTime = System.nanoTime();
                            HeavyTask task;
                            if (isObjectPool) {
                                task = ((ObjectPool<HeavyTask>) pool).acquire();
                            } else {
                                task = ((BitmaskRingBufferUltraVarHandle<HeavyTask>) pool).getFreeObject();
                            }
                            long acquireTime = System.nanoTime() - startTime;
                            
                            if (task != null) {
                                totalAcquireTime.addAndGet(acquireTime);
                                
                                // Имитируем работу с объектом
                                byte[] payload = task.getPayload();
                                for (int k = 0; k < payload.length; k += 64) {
                                    payload[k] = (byte) (payload[k] + 1);
                                }
                                
                                // Возвращаем объект
                                startTime = System.nanoTime();
                                if (isObjectPool) {
                                    ((ObjectPool<HeavyTask>) pool).release(task);
                                } else {
                                    ((BitmaskRingBufferUltraVarHandle<HeavyTask>) pool).setFreeObject(task);
                                }
                                long releaseTime = System.nanoTime() - startTime;
                                totalReleaseTime.addAndGet(releaseTime);
                                
                                totalOperations.incrementAndGet();
                            }
                        }
                        
                        // Небольшая пауза для снижения нагрузки на CPU
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    System.err.println("Поток " + threadId + " завершился с ошибкой: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Запускаем тест
        long testStartTime = System.currentTimeMillis();
        startLatch.countDown();
        
        try {
            endLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long testEndTime = System.currentTimeMillis();
        long testDuration = testEndTime - testStartTime;
        
        // Записываем финальные метрики GC
        long finalGcCount = getTotalGcCount();
        long finalGcTime = getTotalGcTime();
        MemoryUsage finalHeap = memoryBean.getHeapMemoryUsage();
        
        // Принудительная сборка мусора для получения точных данных
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        MemoryUsage finalHeapAfterGc = memoryBean.getHeapMemoryUsage();
        
        // Вычисляем разницу
        long gcCountDiff = finalGcCount - initialGcCount;
        long gcTimeDiff = finalGcTime - initialGcTime;
        long heapUsedDiff = finalHeap.getUsed() - initialHeap.getUsed();
        long heapUsedAfterGc = finalHeapAfterGc.getUsed();
        
        // Выводим результаты
        System.out.println("\nРезультаты теста:");
        System.out.println("  - Длительность теста: " + testDuration + " мс");
        System.out.println("  - Всего операций: " + totalOperations.get());
        System.out.println("  - Операций в секунду: " + (totalOperations.get() * 1000L / testDuration));
        System.out.println("  - Среднее время acquire: " + 
            (totalOperations.get() > 0 ? totalAcquireTime.get() / totalOperations.get() : 0) + " нс");
        System.out.println("  - Среднее время release: " + 
            (totalOperations.get() > 0 ? totalReleaseTime.get() / totalOperations.get() : 0) + " нс");
        
        System.out.println("\nМетрики GC:");
        System.out.println("  - Количество сборок мусора: " + gcCountDiff);
        System.out.println("  - Время сборки мусора: " + gcTimeDiff + " мс");
        System.out.println("  - Среднее время на сборку: " + 
            (gcCountDiff > 0 ? gcTimeDiff / gcCountDiff : 0) + " мс");
        
        System.out.println("\nМетрики памяти:");
        System.out.println("  - Изменение использования heap: " + formatBytes(heapUsedDiff));
        System.out.println("  - Использование heap после GC: " + formatBytes(heapUsedAfterGc));
        System.out.println("  - Размер heap после GC: " + formatBytes(finalHeapAfterGc.getCommitted()));
        
        // Статистика пула
        if (isObjectPool) {
            ObjectPool.PoolStatistics stats = ((ObjectPool<HeavyTask>) pool).getStatistics();
            System.out.println("\nСтатистика пула:");
            System.out.println("  - Максимальный размер: " + stats.maxPoolSize);
            System.out.println("  - Доступных объектов: " + stats.availableObjects);
            System.out.println("  - Выданных объектов: " + stats.borrowedObjects);
            System.out.println("  - Всего получений: " + stats.totalAcquires);
            System.out.println("  - Всего возвратов: " + stats.totalReleases);
            System.out.println("  - Всего созданий: " + stats.totalCreates);
            System.out.println("  - Всего ожиданий: " + stats.totalWaits);
            System.out.println("  - Активных объектов: " + stats.activeObjects);
        } else {
            System.out.println("\nСтатистика пула (UltraVarHandle):");
            System.out.println("  - Размер пула: " + POOL_SIZE);
            System.out.println("  - Статистика недоступна для UltraVarHandle");
        }
        
        executor.shutdown();
        System.out.println();
    }
    
    /**
     * Получает общее количество сборок мусора
     */
    private static long getTotalGcCount() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Получает общее время сборки мусора в миллисекундах
     */
    private static long getTotalGcTime() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * Форматирует размер в байтах в читаемый вид
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
} 