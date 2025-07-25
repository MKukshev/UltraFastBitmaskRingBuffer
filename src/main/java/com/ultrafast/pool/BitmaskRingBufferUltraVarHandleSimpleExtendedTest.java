package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Расширенный тест для проверки новой функциональности BitmaskRingBufferUltraVarHandleSimple:
 * - Создание новых объектов через фабрику при нехватке
 * - Защита от переполнения при возврате объектов
 */
public class BitmaskRingBufferUltraVarHandleSimpleExtendedTest {
    
    private static final int THREAD_COUNT = 8;
    private static final int POOL_SIZE = 100; // Маленький пул для тестирования переполнения
    private static final int TEST_DURATION_SECONDS = 15;
    private static final int OPERATIONS_PER_ITERATION = 50;
    
    public static void main(String[] args) {
        System.out.println("=== РАСШИРЕННЫЙ ТЕСТ BitmaskRingBufferUltraVarHandleSimple ===\n");
        System.out.println("Конфигурация теста:");
        System.out.println("  - Потоков: " + THREAD_COUNT);
        System.out.println("  - Размер пула: " + POOL_SIZE + " (маленький для тестирования переполнения)");
        System.out.println("  - Длительность теста: " + TEST_DURATION_SECONDS + " секунд");
        System.out.println("  - Операций за итерацию: " + OPERATIONS_PER_ITERATION);
        System.out.println();
        
        // Создаем упрощенный пул
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(POOL_SIZE, 
                () -> new HeavyTask(0, "Test", 256, 42.0));
        
        // Запускаем нагрузочный тест
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(THREAD_COUNT);
        
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalAcquireTime = new AtomicLong(0);
        AtomicLong totalReleaseTime = new AtomicLong(0);
        AtomicLong totalCreatedObjects = new AtomicLong(0);
        AtomicLong totalDroppedObjects = new AtomicLong(0);
        
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
                                boolean returned = pool.setFreeObject(task);
                                long releaseTime = System.nanoTime() - startTime;
                                totalReleaseTime.addAndGet(releaseTime);
                                
                                if (!returned) {
                                    totalDroppedObjects.incrementAndGet();
                                }
                                
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
        
        System.out.println("\nНОВАЯ ФУНКЦИОНАЛЬНОСТЬ:");
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
        
        // Тестируем создание объекта при пустом пуле
        System.out.println("\nТестирование создания объекта при пустом пуле:");
        testEmptyPoolCreation(pool);
        
        executor.shutdown();
        pool.cleanup();
        
        System.out.println("\nРасширенный тест завершен успешно!");
    }
    
    /**
     * Тестирует создание объекта при пустом пуле
     */
    private static void testEmptyPoolCreation(BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool) {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> smallPool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(1, 
                () -> new HeavyTask(0, "SmallTest", 128, 42.0));
        
        // Получаем единственный объект
        HeavyTask task1 = smallPool.getFreeObject();
        System.out.println("  - Получен объект: " + (task1 != null ? "✓" : "✗"));
        
        // Пытаемся получить еще один - должен создаться новый
        HeavyTask task2 = smallPool.getFreeObject();
        System.out.println("  - Получен второй объект (создан через фабрику): " + (task2 != null ? "✓" : "✗"));
        System.out.println("  - Объекты разные: " + (task1 != task2 ? "✓" : "✗"));
        
        // Возвращаем оба объекта
        boolean returned1 = smallPool.setFreeObject(task1);
        boolean returned2 = smallPool.setFreeObject(task2);
        System.out.println("  - Первый объект возвращен: " + (returned1 ? "✓" : "✗"));
        System.out.println("  - Второй объект возвращен: " + (returned2 ? "✓" : "✗"));
        
        BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = smallPool.getStats();
        System.out.println("  - Статистика: создано=" + stats.totalCreates + ", отброшено=" + stats.totalDrops);
        
        smallPool.cleanup();
    }
} 