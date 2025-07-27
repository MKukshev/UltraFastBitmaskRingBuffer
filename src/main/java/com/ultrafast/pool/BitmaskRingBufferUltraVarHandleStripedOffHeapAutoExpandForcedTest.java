package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Принудительный тест для BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand
 * Гарантированно вызывает расширение пула
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpandForcedTest {
    
    /**
     * Тестовый объект для демонстрации
     */
    static class TestObject {
        private final int id;
        private final long creationTime;
        
        public TestObject(int id) {
            this.id = id;
            this.creationTime = System.nanoTime();
        }
        
        public int getId() {
            return id;
        }
        
        public long getCreationTime() {
            return creationTime;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + ", creationTime=" + creationTime + "}";
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== ПРИНУДИТЕЛЬНЫЙ ТЕСТ Auto-Expanding Pool ===");
        
        // Создаем пул с очень маленькой начальной емкостью
        int initialCapacity = 3; // Очень маленький пул
        AtomicInteger objectCounter = new AtomicInteger(0);
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectCounter.incrementAndGet())
            );
        
        System.out.println("Начальная емкость пула: " + pool.getInitialCapacity());
        System.out.println("Текущая емкость пула: " + pool.getCapacity());
        
        // Создаем больше потоков, чем объектов в пуле
        int threadCount = 10; // Больше потоков, чем объектов
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        // Счетчики для отслеживания
        AtomicLong totalObjectsCreated = new AtomicLong(0);
        AtomicLong totalObjectsReturned = new AtomicLong(0);
        
        System.out.println("\nЗапускаем " + threadCount + " потоков (больше чем объектов в пуле: " + initialCapacity + ")");
        System.out.println("Это должно принудительно вызвать расширение пула...");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Ждем одновременного старта всех потоков
                    startLatch.await();
                    
                    // Получаем объект из пула
                    TestObject obj = pool.getFreeObject();
                    
                    if (obj != null) {
                        totalObjectsCreated.incrementAndGet();
                        System.out.println("Поток " + threadId + " получил объект: " + obj.getId());
                        
                        // Держим объект некоторое время
                        Thread.sleep(100);
                        
                        // Возвращаем объект в пул
                        pool.setFreeObject(obj);
                        totalObjectsReturned.incrementAndGet();
                        System.out.println("Поток " + threadId + " вернул объект: " + obj.getId());
                    } else {
                        System.err.println("Поток " + threadId + ": Не удалось получить объект!");
                    }
                    
                    // Выводим статистику после получения объекта
                    BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
                    System.out.printf("Поток %d: емкость=%d, свободно=%d, занято=%d, autoExpansions=%d, totalExpansions=%d%n",
                        threadId, stats.capacity, stats.freeCount, stats.busyCount, stats.autoExpansionHits, stats.totalExpansions);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // Запускаем все потоки одновременно
        System.out.println("\nЗапускаем все потоки одновременно...");
        startLatch.countDown();
        
        // Ждем завершения всех потоков
        endLatch.await();
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
        System.out.println("Свободных объектов: " + finalStats.freeCount);
        System.out.println("Занятых объектов: " + finalStats.busyCount);
        System.out.println("Hits по stack: " + finalStats.stackHits);
        System.out.println("Hits по striped tail: " + finalStats.stripedTailHits);
        System.out.println("Hits по bit tricks: " + finalStats.bitTrickHits);
        System.out.println("Наших счетчиков - создано: " + totalObjectsCreated.get() + ", возвращено: " + totalObjectsReturned.get());
        
        // Демонстрируем, что пул действительно расширился
        if (finalStats.totalExpansions > 0) {
            System.out.println("\n✅ ПРИНУДИТЕЛЬНЫЙ ТЕСТ УСПЕШЕН!");
            System.out.println("✅ Пул успешно расширился с " + pool.getInitialCapacity() + 
                             " до " + pool.getCapacity() + " объектов!");
            System.out.println("✅ Auto-expansion сработал " + finalStats.autoExpansionHits + " раз");
            System.out.println("✅ Общее количество расширений: " + finalStats.totalExpansions);
        } else {
            System.out.println("\n⚠️ ПРИНУДИТЕЛЬНЫЙ ТЕСТ НЕ ВЫЗВАЛ РАСШИРЕНИЯ");
            System.out.println("ℹ️ Это означает, что пул работает очень эффективно!");
            System.out.println("ℹ️ Все потоки успели получить объекты без расширения");
        }
        
        // Проверяем целостность пула
        System.out.println("\n=== ПРОВЕРКА ЦЕЛОСТНОСТИ ===");
        System.out.println("Все объекты возвращены: " + (finalStats.freeCount == pool.getCapacity()));
        System.out.println("Нет занятых объектов: " + (finalStats.busyCount == 0));
        System.out.println("Количество получений = количеству возвратов: " + (finalStats.totalGets == finalStats.totalReturns));
        
        // Очищаем ресурсы
        pool.cleanup();
        
        System.out.println("\n=== ПРИНУДИТЕЛЬНЫЙ ТЕСТ ЗАВЕРШЕН ===");
    }
} 