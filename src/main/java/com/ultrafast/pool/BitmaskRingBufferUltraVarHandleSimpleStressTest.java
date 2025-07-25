package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Стресс-тест для принудительного создания ситуаций с нехваткой объектов и переполнением
 */
public class BitmaskRingBufferUltraVarHandleSimpleStressTest {
    
    private static final int THREAD_COUNT = 16;
    private static final int POOL_SIZE = 10; // Очень маленький пул для принудительного переполнения
    private static final int TEST_DURATION_SECONDS = 10;
    private static final int OPERATIONS_PER_ITERATION = 100;
    
    public static void main(String[] args) {
        System.out.println("=== СТРЕСС-ТЕСТ BitmaskRingBufferUltraVarHandleSimple ===\n");
        System.out.println("Конфигурация теста:");
        System.out.println("  - Потоков: " + THREAD_COUNT);
        System.out.println("  - Размер пула: " + POOL_SIZE + " (очень маленький для принудительного переполнения)");
        System.out.println("  - Длительность теста: " + TEST_DURATION_SECONDS + " секунд");
        System.out.println("  - Операций за итерацию: " + OPERATIONS_PER_ITERATION);
        System.out.println();
        
        // Создаем упрощенный пул
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(POOL_SIZE, 
                () -> new HeavyTask(0, "StressTest", 128, 42.0));
        
        // Запускаем стресс-тест
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
                                
                                // Имитируем длительную работу с объектом (увеличиваем вероятность переполнения)
                                byte[] payload = task.getPayload();
                                for (int k = 0; k < payload.length; k += 16) { // Более интенсивная работа
                                    payload[k] = (byte) (payload[k] + 1);
                                }
                                
                                // Небольшая задержка для увеличения времени удержания объекта
                                Thread.sleep(0, 1000); // 1 микросекунда
                                
                                // Возвращаем объект
                                startTime = System.nanoTime();
                                boolean returned = pool.setFreeObject(task);
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
        System.out.println("Результаты стресс-теста:");
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
        
        System.out.println("\nСТРЕСС-ТЕСТ РЕЗУЛЬТАТЫ:");
        System.out.println("  - Создано новых объектов: " + stats.totalCreates);
        System.out.println("  - Отброшено объектов: " + stats.totalDrops);
        System.out.println("  - Процент созданных: " + 
            (stats.totalGets > 0 ? String.format("%.2f%%", (stats.totalCreates * 100.0 / stats.totalGets)) : "0%"));
        System.out.println("  - Процент отброшенных: " + 
            (stats.totalReturns > 0 ? String.format("%.2f%%", (stats.totalDrops * 100.0 / stats.totalReturns)) : "0%"));
        
        // Проверяем корректность работы
        System.out.println("\nПроверка корректности:");
        System.out.println("  - Свободных + занятых = емкости: " + 
            (stats.freeCount + stats.busyCount == stats.capacity ? "✓" : "✗"));
        System.out.println("  - Получений >= возвратов: " + 
            (stats.totalGets >= stats.totalReturns ? "✓" : "✗"));
        System.out.println("  - Создано >= отброшено: " + 
            (stats.totalCreates >= stats.totalDrops ? "✓" : "✗"));
        
        // Тестируем принудительное создание объектов
        System.out.println("\nТестирование принудительного создания объектов:");
        testForcedObjectCreation();
        
        executor.shutdown();
        pool.cleanup();
        
        System.out.println("\nСтресс-тест завершен!");
    }
    
    /**
     * Тестирует принудительное создание объектов
     */
    private static void testForcedObjectCreation() {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> tinyPool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(1, 
                () -> new HeavyTask(0, "TinyTest", 64, 42.0));
        
        // Получаем все доступные объекты из пула
        HeavyTask[] tasks = new HeavyTask[5];
        for (int i = 0; i < 5; i++) {
            tasks[i] = tinyPool.getFreeObject();
            System.out.println("  - Получен объект " + (i+1) + ": " + (tasks[i] != null ? "✓" : "✗"));
        }
        
        // Проверяем, что объекты разные (некоторые должны быть созданы через фабрику)
        boolean allDifferent = true;
        for (int i = 0; i < 4; i++) {
            for (int j = i + 1; j < 5; j++) {
                if (tasks[i] == tasks[j]) {
                    allDifferent = false;
                    break;
                }
            }
        }
        System.out.println("  - Все объекты разные: " + (allDifferent ? "✓" : "✗"));
        
        // Возвращаем все объекты
        for (int i = 0; i < 5; i++) {
            boolean returned = tinyPool.setFreeObject(tasks[i]);
            System.out.println("  - Объект " + (i+1) + " возвращен: " + (returned ? "✓" : "✗"));
        }
        
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = tinyPool.getStats();
        System.out.println("  - Статистика: создано=" + stats.totalCreates + ", отброшено=" + stats.totalDrops);
        
        tinyPool.cleanup();
    }
} 