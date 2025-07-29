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

import com.ultrafast.pool.smart.SmartTaskPool;

/**
 * Тест для измерения разницы в аллокациях объектов GC между различными реализациями пулов
 * 
 * Этот тест сравнивает:
 * - BitmaskRingBufferUltraVarHandle (оптимизированная версия)
 * - BitmaskRingBufferUltraVarHandleStriped (striped tail версия)
 * - BitmaskRingBufferUltraVarHandleStripedOffHeap (off-heap padding версия)
 * - BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand (auto-expanding версия с off-heap)
 * - BitmaskRingBufferUltraVarHandleAutoExpand (auto-expanding версия на базе UltraVarHandle)
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
    private static final int POOL_SIZE = 100000;
    private static final int TEST_DURATION_SECONDS = 180; // 30 секунд для тестирования
    private static final int OPERATIONS_PER_ITERATION = 1000;
    private static final int PAYLOAD_SIZE = 4096; // 1KB на объект
    
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
        Object pool5 = new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0));
        testUltraVarHandleStripedOffHeapAutoExpandPool("BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand", (BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<HeavyTask>) pool5);        
        
        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool5);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Object pool1 = new BitmaskRingBufferUltraVarHandle<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0));
        testUltraVarHandlePool("BitmaskRingBufferUltraVarHandle", (BitmaskRingBufferUltraVarHandle<HeavyTask>) pool1);

        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool1);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Object pool4 = new BitmaskRingBufferUltraVarHandleStripedOffHeap<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0));
        testUltraVarHandleStripedOffHeapPool("BitmaskRingBufferUltraVarHandleStripedOffHeap", (BitmaskRingBufferUltraVarHandleStripedOffHeap<HeavyTask>) pool4);        
        
        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool4);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Object pool6 = new BitmaskRingBufferUltraVarHandleAutoExpand<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0));
        testUltraVarHandleAutoExpandPool("BitmaskRingBufferUltraVarHandleAutoExpand", (BitmaskRingBufferUltraVarHandleAutoExpand<HeavyTask>) pool6);        
        
        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool6);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Object pool3 = new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0), 
            POOL_SIZE / 2, POOL_SIZE, 1000);
        testObjectPool("BitmaskRingBufferClassic", (ObjectPool<HeavyTask>) pool3);

        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool3);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Object pool2 = new BitmaskRingBufferUltraVarHandleStriped<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0));
        testUltraVarHandleStripedPool("BitmaskRingBufferUltraVarHandleStriped", (BitmaskRingBufferUltraVarHandleStriped<HeavyTask>) pool2);
        
        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(pool2);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Тест SmartTaskPool
        SmartTaskPool<HeavyTask> smartPool = new SmartTaskPool<>(
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(POOL_SIZE, () -> new HeavyTask(0, "Test", PAYLOAD_SIZE, 42.0)),
            Executors.newFixedThreadPool(THREAD_COUNT)
        );
        testSmartTaskPool("SmartTaskPool", smartPool);
        
        // Cleanup и принудительная сборка мусора между тестами
        cleanupPool(smartPool);
        System.gc();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Выполняет cleanup для пула в зависимости от его типа
     */
    private static void cleanupPool(Object pool) {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<?>) pool).cleanup();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStriped) {
            ((BitmaskRingBufferUltraVarHandleStriped<?>) pool).cleanup();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeap) {
            ((BitmaskRingBufferUltraVarHandleStripedOffHeap<?>) pool).cleanup();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand) {
            ((BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<?>) pool).cleanup();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleAutoExpand) {
            ((BitmaskRingBufferUltraVarHandleAutoExpand<?>) pool).cleanup();
        } else if (pool instanceof SmartTaskPool) {
            ((SmartTaskPool<?>) pool).shutdown();
        }
        // ObjectPool не требует cleanup
    }
    
    private static void testObjectPool(String poolName, ObjectPool<HeavyTask> pool) {
        testPool(poolName, pool, true);
    }
    
    private static void testUltraVarHandlePool(String poolName, BitmaskRingBufferUltraVarHandle<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testUltraVarHandleStripedPool(String poolName, BitmaskRingBufferUltraVarHandleStriped<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testUltraVarHandleStripedOffHeapPool(String poolName, BitmaskRingBufferUltraVarHandleStripedOffHeap<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testUltraVarHandleStripedOffHeapAutoExpandPool(String poolName, BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testUltraVarHandleAutoExpandPool(String poolName, BitmaskRingBufferUltraVarHandleAutoExpand<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }

    private static void testSmartTaskPool(String poolName, SmartTaskPool<HeavyTask> pool) {
        testPool(poolName, pool, false);
    }
    
    private static void testPool(String poolName, Object pool, boolean isObjectPool) {
        System.out.println("--- Тестирование " + poolName + " ---");
        
        // Записываем начальные метрики GC
        long initialYoungGcCount = getYoungGcCount();
        long initialYoungGcTime = getYoungGcTime();
        long initialTotalGcCount = getTotalGcCount();
        long initialTotalGcTime = getTotalGcTime();
        MemoryUsage initialHeap = memoryBean.getHeapMemoryUsage();
        
        System.out.println("Начальные метрики:");
        System.out.println("  - Использование heap: " + formatBytes(initialHeap.getUsed()));
        System.out.println("  - Young GC: " + initialYoungGcCount + " сборок, " + initialYoungGcTime + " мс");
        System.out.println("  - Всего GC: " + initialTotalGcCount + " сборок, " + initialTotalGcTime + " мс");
        
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
                            } else if (pool instanceof BitmaskRingBufferUltraVarHandleStriped) {
                                task = ((BitmaskRingBufferUltraVarHandleStriped<HeavyTask>) pool).getFreeObject();
                            } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeap) {
                                task = ((BitmaskRingBufferUltraVarHandleStripedOffHeap<HeavyTask>) pool).getFreeObject();
                            } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand) {
                                task = ((BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<HeavyTask>) pool).getFreeObject();
                            } else if (pool instanceof BitmaskRingBufferUltraVarHandleAutoExpand) {
                                task = ((BitmaskRingBufferUltraVarHandleAutoExpand<HeavyTask>) pool).getFreeObject();
                            } else if (pool instanceof SmartTaskPool) {
                                task = ((SmartTaskPool<HeavyTask>) pool).getPool().getFreeObject();
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
                                } else if (pool instanceof BitmaskRingBufferUltraVarHandleStriped) {
                                    ((BitmaskRingBufferUltraVarHandleStriped<HeavyTask>) pool).setFreeObject(task);
                                } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeap) {
                                    ((BitmaskRingBufferUltraVarHandleStripedOffHeap<HeavyTask>) pool).setFreeObject(task);
                                } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand) {
                                    ((BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<HeavyTask>) pool).setFreeObject(task);
                                } else if (pool instanceof BitmaskRingBufferUltraVarHandleAutoExpand) {
                                    ((BitmaskRingBufferUltraVarHandleAutoExpand<HeavyTask>) pool).setFreeObject(task);
                                } else if (pool instanceof SmartTaskPool) {
                                    ((SmartTaskPool<HeavyTask>) pool).getPool().setFreeObject(task);
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
        long finalYoungGcCount = getYoungGcCount();
        long finalYoungGcTime = getYoungGcTime();
        long finalTotalGcCount = getTotalGcCount();
        long finalTotalGcTime = getTotalGcTime();
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
        long youngGcCountDiff = finalYoungGcCount - initialYoungGcCount;
        long youngGcTimeDiff = finalYoungGcTime - initialYoungGcTime;
        long totalGcCountDiff = finalTotalGcCount - initialTotalGcCount;
        long totalGcTimeDiff = finalTotalGcTime - initialTotalGcTime;
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
        System.out.println("  - Young GC сборок: " + youngGcCountDiff + " (влияют на производительность)");
        System.out.println("  - Время Young GC: " + youngGcTimeDiff + " мс");
        System.out.println("  - Всего GC сборок: " + totalGcCountDiff + " (все сборщики)");
        System.out.println("  - Время всех GC: " + totalGcTimeDiff + " мс");
        System.out.println("  - Среднее время на Young GC: " + 
            (youngGcCountDiff > 0 ? youngGcTimeDiff / youngGcCountDiff : 0) + " мс");
        
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
        } else if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            BitmaskRingBufferUltraVarHandle.PoolStats stats = ((BitmaskRingBufferUltraVarHandle<HeavyTask>) pool).getStats();
            System.out.println("\nСтатистика пула (UltraVarHandle):");
            System.out.println("  - Размер пула: " + stats.capacity);
            System.out.println("  - Свободных объектов: " + stats.freeCount);
            System.out.println("  - Занятых объектов: " + stats.busyCount);
            System.out.println("  - Объектов для обновления: " + stats.updateCount);
            System.out.println("  - Всего получений: " + stats.totalGets);
            System.out.println("  - Всего возвратов: " + stats.totalReturns);
            System.out.println("  - Всего обновлений: " + stats.totalUpdates);
            System.out.println("  - Bit trick hits: " + stats.bitTrickHits);
            System.out.println("  - Stack hits: " + stats.stackHits);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStriped) {
            BitmaskRingBufferUltraVarHandleStriped.PoolStats stats = ((BitmaskRingBufferUltraVarHandleStriped<HeavyTask>) pool).getStats();
            System.out.println("\nСтатистика пула (UltraVarHandleStriped):");
            System.out.println("  - Размер пула: " + stats.capacity);
            System.out.println("  - Свободных объектов: " + stats.freeCount);
            System.out.println("  - Занятых объектов: " + stats.busyCount);
            System.out.println("  - Объектов для обновления: " + stats.updateCount);
            System.out.println("  - Всего получений: " + stats.totalGets);
            System.out.println("  - Всего возвратов: " + stats.totalReturns);
            System.out.println("  - Всего обновлений: " + stats.totalUpdates);
            System.out.println("  - Bit trick hits: " + stats.bitTrickHits);
            System.out.println("  - Stack hits: " + stats.stackHits);
            System.out.println("  - Striped tail hits: " + stats.stripedTailHits);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeap) {
            BitmaskRingBufferUltraVarHandleStripedOffHeap.PoolStats stats = ((BitmaskRingBufferUltraVarHandleStripedOffHeap<HeavyTask>) pool).getStats();
            System.out.println("\nСтатистика пула (UltraVarHandleStripedOffHeap):");
            System.out.println("  - Размер пула: " + stats.capacity);
            System.out.println("  - Свободных объектов: " + stats.freeCount);
            System.out.println("  - Занятых объектов: " + stats.busyCount);
            System.out.println("  - Объектов для обновления: " + stats.updateCount);
            System.out.println("  - Всего получений: " + stats.totalGets);
            System.out.println("  - Всего возвратов: " + stats.totalReturns);
            System.out.println("  - Всего обновлений: " + stats.totalUpdates);
            System.out.println("  - Bit trick hits: " + stats.bitTrickHits);
            System.out.println("  - Stack hits: " + stats.stackHits);
            System.out.println("  - Striped tail hits: " + stats.stripedTailHits);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand) {
            BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = ((BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<HeavyTask>) pool).getStats();
            System.out.println("\nСтатистика пула (UltraVarHandleStripedOffHeapAutoExpand):");
            System.out.println("  - Размер пула: " + stats.capacity);
            System.out.println("  - Свободных объектов: " + stats.freeCount);
            System.out.println("  - Занятых объектов: " + stats.busyCount);
            System.out.println("  - Всего получений: " + stats.totalGets);
            System.out.println("  - Всего возвратов: " + stats.totalReturns);
            System.out.println("  - Bit trick hits: " + stats.bitTrickHits);
            System.out.println("  - Stack hits: " + stats.stackHits);
            System.out.println("  - Striped tail hits: " + stats.stripedTailHits);
            System.out.println("  - Auto expansion hits: " + stats.autoExpansionHits);
            System.out.println("  - Total expansions: " + stats.totalExpansions);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleAutoExpand) {
            BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = ((BitmaskRingBufferUltraVarHandleAutoExpand<HeavyTask>) pool).getStats();
            System.out.println("\nСтатистика пула (UltraVarHandleAutoExpand):");
            System.out.println("  - Размер пула: " + stats.capacity);
            System.out.println("  - Свободных объектов: " + stats.freeCount);
            System.out.println("  - Занятых объектов: " + stats.busyCount);
            System.out.println("  - Всего получений: " + stats.totalGets);
            System.out.println("  - Всего возвратов: " + stats.totalReturns);
            System.out.println("  - Auto expansion hits: " + stats.autoExpansionHits);
            System.out.println("  - Total expansions: " + stats.totalExpansions);
        } else {
            System.out.println("\nСтатистика пула:");
            System.out.println("  - Размер пула: " + POOL_SIZE);
            System.out.println("  - Статистика недоступна для данного типа пула");
        }
        
        executor.shutdown();
        System.out.println();
    }
    
    /**
     * Получает количество сборок Young Generation (основные сборки, влияющие на производительность)
     */
    private static long getYoungGcCount() {
        return gcBeans.stream()
            .filter(bean -> bean.getName().contains("Young"))
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Получает время сборок Young Generation
     */
    private static long getYoungGcTime() {
        return gcBeans.stream()
            .filter(bean -> bean.getName().contains("Young"))
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * Получает общее количество сборок мусора (все сборщики)
     */
    private static long getTotalGcCount() {
        return gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Получает общее время сборки мусора в миллисекундах (все сборщики)
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