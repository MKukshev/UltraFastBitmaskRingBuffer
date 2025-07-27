package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Агрессивный тест для BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand
 * Этот тест принудительно вызовет расширение пула
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpandAggressiveTest {
    
    static class TestObject {
        private final int id;
        private final long creationTime;
        
        public TestObject(int id) {
            this.id = id;
            this.creationTime = System.currentTimeMillis();
        }
        
        public int getId() {
            return id;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + ", created=" + creationTime + "}";
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== АГРЕССИВНЫЙ ТЕСТ Auto-Expanding Pool ===");
        
        // Создаем пул с очень маленькой начальной емкостью
        int initialCapacity = 1; // Всего 1 объект!
        AtomicInteger objectCounter = new AtomicInteger(0);
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectCounter.incrementAndGet())
            );
        
        System.out.println("Начальная емкость пула: " + pool.getInitialCapacity());
        System.out.println("Текущая емкость пула: " + pool.getCapacity());
        
        // Создаем много потоков, которые будут одновременно запрашивать объекты
        int threadCount = 50; // Много потоков
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Счетчики для отслеживания
        AtomicLong totalGets = new AtomicLong(0);
        AtomicLong totalReturns = new AtomicLong(0);
        AtomicLong failedGets = new AtomicLong(0);
        
        System.out.println("\nЗапускаем " + threadCount + " потоков, каждый выполнит " + operationsPerThread + " операций...");
        System.out.println("Начальная емкость: " + initialCapacity + " (должно вызвать расширение!)");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Получаем объект из пула
                        TestObject obj = pool.getFreeObject();
                        
                        if (obj != null) {
                            totalGets.incrementAndGet();
                            
                            // Минимальная работа с объектом (без sleep!)
                            int work = obj.getId() * 2;
                            
                            // Возвращаем объект в пул
                            if (pool.setFreeObject(obj)) {
                                totalReturns.incrementAndGet();
                            }
                        } else {
                            failedGets.incrementAndGet();
                            System.err.println("Поток " + threadId + ": Не удалось получить объект!");
                        }
                        
                        // Периодически выводим статистику
                        if (j % 20 == 0) {
                            BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
                            System.out.printf("Поток %d, операция %d: емкость=%d, свободно=%d, занято=%d, autoExpansions=%d, totalExpansions=%d%n",
                                threadId, j, stats.capacity, stats.freeCount, stats.busyCount, stats.autoExpansionHits, stats.totalExpansions);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Поток " + threadId + " завершился с ошибкой: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Ждем завершения всех потоков
        latch.await();
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        // Выводим финальную статистику
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats finalStats = pool.getStats();
        
        System.out.println("\n=== ФИНАЛЬНАЯ СТАТИСТИКА ===");
        System.out.println("Время выполнения: " + (endTime - startTime) + " мс");
        System.out.println("Начальная емкость: " + pool.getInitialCapacity());
        System.out.println("Финальная емкость: " + pool.getCapacity());
        System.out.println("Общее количество расширений: " + finalStats.totalExpansions);
        System.out.println("Количество обращений к auto-expansion: " + finalStats.autoExpansionHits);
        System.out.println("Общее количество получений: " + finalStats.totalGets);
        System.out.println("Общее количество возвратов: " + finalStats.totalReturns);
        System.out.println("Наши счетчики - получено: " + totalGets.get() + ", возвращено: " + totalReturns.get());
        System.out.println("Неудачных получений: " + failedGets.get());
        System.out.println("Свободных объектов: " + finalStats.freeCount);
        System.out.println("Занятых объектов: " + finalStats.busyCount);
        System.out.println("Hits по stack: " + finalStats.stackHits);
        System.out.println("Hits по striped tail: " + finalStats.stripedTailHits);
        System.out.println("Hits по bit tricks: " + finalStats.bitTrickHits);
        
        // Проверяем, что пул действительно расширился
        if (finalStats.totalExpansions > 0) {
            System.out.println("\n✅ Пул успешно расширился с " + pool.getInitialCapacity() + 
                             " до " + pool.getCapacity() + " объектов!");
            System.out.println("✅ Auto-expansion сработал " + finalStats.autoExpansionHits + " раз");
        } else {
            System.out.println("\n⚠️ Пул НЕ расширился - возможно, есть проблема с логикой расширения");
        }
        
        // Проверяем целостность
        System.out.println("\n=== ПРОВЕРКА ЦЕЛОСТНОСТИ ===");
        boolean allReturned = finalStats.totalGets == finalStats.totalReturns;
        boolean noBusyObjects = finalStats.busyCount == 0;
        boolean expansionWorked = finalStats.totalExpansions > 0 || finalStats.autoExpansionHits > 0;
        
        System.out.println("Все объекты возвращены: " + allReturned);
        System.out.println("Нет занятых объектов: " + noBusyObjects);
        System.out.println("Расширение сработало: " + expansionWorked);
        
        // Очищаем ресурсы
        pool.cleanup();
        
        System.out.println("\n=== АГРЕССИВНЫЙ ТЕСТ ЗАВЕРШЕН ===");
    }
} 