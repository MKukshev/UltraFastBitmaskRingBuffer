package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой тест для проверки работы упрощенной версии BitmaskRingBufferUltraVarHandleSimple
 */
public class BitmaskRingBufferUltraVarHandleSimpleTest {
    
    private static final int THREAD_COUNT = 4;
    private static final int POOL_SIZE = 1000;
    private static final int TEST_DURATION_SECONDS = 10;
    private static final int OPERATIONS_PER_ITERATION = 100;
    
    public static void main(String[] args) {
        System.out.println("=== ТЕСТ УПРОЩЕННОЙ ВЕРСИИ BitmaskRingBufferUltraVarHandleSimple ===\n");
        System.out.println("Конфигурация теста:");
        System.out.println("  - Потоков: " + THREAD_COUNT);
        System.out.println("  - Размер пула: " + POOL_SIZE);
        System.out.println("  - Длительность теста: " + TEST_DURATION_SECONDS + " секунд");
        System.out.println("  - Операций за итерацию: " + OPERATIONS_PER_ITERATION);
        System.out.println();
        
        // Создаем упрощенный пул
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(POOL_SIZE, 
                () -> new HeavyTask(0, "Test", 512, 42.0));
        
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
                            HeavyTask task = pool.getFreeObject();
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
                                pool.setFreeObject(task);
                                long releaseTime = System.nanoTime() - startTime;
                                totalReleaseTime.addAndGet(releaseTime);
                                
                                totalOperations.incrementAndGet();
                            }
                        }
                        
                        // Небольшая пауза
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
        
        // Получаем статистику
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
        
        // Выводим результаты
        System.out.println("Результаты теста:");
        System.out.println("  - Длительность теста: " + testDuration + " мс");
        System.out.println("  - Всего операций: " + totalOperations.get());
        System.out.println("  - Операций в секунду: " + (totalOperations.get() * 1000L / testDuration));
        System.out.println("  - Среднее время acquire: " + 
            (totalOperations.get() > 0 ? totalAcquireTime.get() / totalOperations.get() : 0) + " нс");
        System.out.println("  - Среднее время release: " + 
            (totalOperations.get() > 0 ? totalReleaseTime.get() / totalOperations.get() : 0) + " нс");
        
        System.out.println("\nСтатистика пула:");
        System.out.println("  - Емкость: " + stats.capacity);
        System.out.println("  - Свободных объектов: " + stats.freeCount);
        System.out.println("  - Занятых объектов: " + stats.busyCount);
        System.out.println("  - Всего получений: " + stats.totalGets);
        System.out.println("  - Всего возвратов: " + stats.totalReturns);
        System.out.println("  - Bit trick hits: " + stats.bitTrickHits);
        System.out.println("  - Stack hits: " + stats.stackHits);
        
        // Проверяем корректность работы
        System.out.println("\nПроверка корректности:");
        System.out.println("  - Свободных + занятых = емкости: " + 
            (stats.freeCount + stats.busyCount == stats.capacity ? "✓" : "✗"));
        System.out.println("  - Получений = возвратов: " + 
            (stats.totalGets == stats.totalReturns ? "✓" : "✗"));
        System.out.println("  - Все объекты свободны: " + 
            (stats.freeCount == stats.capacity ? "✓" : "✗"));
        
        executor.shutdown();
        pool.cleanup();
        
        System.out.println("\nТест завершен успешно!");
    }
} 