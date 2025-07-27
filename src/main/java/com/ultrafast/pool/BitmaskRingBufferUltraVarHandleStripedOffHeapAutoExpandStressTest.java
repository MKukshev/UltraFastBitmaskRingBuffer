package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Стресс-тест для BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand
 * Принудительно вызывает расширение пула
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpandStressTest {
    
    /**
     * Тестовый объект для демонстрации
     */
    static class TestObject {
        private final int id;
        private final long creationTime;
        private volatile boolean inUse = false;
        
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
        
        public boolean isInUse() {
            return inUse;
        }
        
        public void setInUse(boolean inUse) {
            this.inUse = inUse;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + ", creationTime=" + creationTime + ", inUse=" + inUse + "}";
        }
    }
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== СТРЕСС-ТЕСТ Auto-Expanding Pool ===");
        
        // Создаем пул с очень маленькой начальной емкостью
        int initialCapacity = 5;
        AtomicInteger objectCounter = new AtomicInteger(0);
        
        BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<TestObject> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<>(
                initialCapacity, 
                () -> new TestObject(objectCounter.incrementAndGet())
            );
        
        System.out.println("Начальная емкость пула: " + pool.getInitialCapacity());
        System.out.println("Текущая емкость пула: " + pool.getCapacity());
        
        // Создаем множество потоков, которые будут интенсивно использовать пул
        int threadCount = 50; // Больше потоков
        int operationsPerThread = 200; // Меньше операций, но более агрессивно
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // Счетчики для отслеживания
        AtomicLong totalObjectsCreated = new AtomicLong(0);
        AtomicLong totalObjectsReturned = new AtomicLong(0);
        AtomicLong expansionEvents = new AtomicLong(0);
        
        System.out.println("\nЗапускаем " + threadCount + " потоков, каждый выполнит " + operationsPerThread + " операций...");
        System.out.println("Ожидаем принудительное расширение пула...");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Получаем объект из пула
                        TestObject obj = pool.getFreeObject();
                        
                        if (obj != null) {
                            obj.setInUse(true);
                            totalObjectsCreated.incrementAndGet();
                            
                            // Имитируем более длительную работу с объектом
                            // Это создаст больше конкуренции и принудит к расширению
                            Thread.sleep(5 + (j % 10)); // 5-15 мс
                            
                            obj.setInUse(false);
                            pool.setFreeObject(obj);
                            totalObjectsReturned.incrementAndGet();
                        } else {
                            System.err.println("Поток " + threadId + ": Не удалось получить объект!");
                        }
                        
                        // Периодически выводим статистику
                        if (j % 50 == 0) {
                            BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand.PoolStats stats = pool.getStats();
                            long currentExpansions = stats.totalExpansions;
                            if (currentExpansions > expansionEvents.get()) {
                                expansionEvents.set(currentExpansions);
                                System.out.printf("🔥 РАСШИРЕНИЕ! Поток %d, операция %d: емкость=%d, свободно=%d, занято=%d, autoExpansions=%d%n",
                                    threadId, j, stats.capacity, stats.freeCount, stats.busyCount, stats.autoExpansionHits);
                            } else {
                                System.out.printf("Поток %d, операция %d: емкость=%d, свободно=%d, занято=%d, autoExpansions=%d%n",
                                    threadId, j, stats.capacity, stats.freeCount, stats.busyCount, stats.autoExpansionHits);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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
        System.out.println("Свободных объектов: " + finalStats.freeCount);
        System.out.println("Занятых объектов: " + finalStats.busyCount);
        System.out.println("Hits по stack: " + finalStats.stackHits);
        System.out.println("Hits по striped tail: " + finalStats.stripedTailHits);
        System.out.println("Hits по bit tricks: " + finalStats.bitTrickHits);
        System.out.println("Наших счетчиков - создано: " + totalObjectsCreated.get() + ", возвращено: " + totalObjectsReturned.get());
        
        // Демонстрируем, что пул действительно расширился
        if (finalStats.totalExpansions > 0) {
            System.out.println("\n✅ СТРЕСС-ТЕСТ УСПЕШЕН!");
            System.out.println("✅ Пул успешно расширился с " + pool.getInitialCapacity() + 
                             " до " + pool.getCapacity() + " объектов!");
            System.out.println("✅ Auto-expansion сработал " + finalStats.autoExpansionHits + " раз");
            System.out.println("✅ Общее количество расширений: " + finalStats.totalExpansions);
        } else {
            System.out.println("\n⚠️ СТРЕСС-ТЕСТ НЕ ВЫЗВАЛ РАСШИРЕНИЯ");
            System.out.println("ℹ️ Пул не потребовал расширения - возможно, нужно больше нагрузки");
        }
        
        // Проверяем целостность пула
        System.out.println("\n=== ПРОВЕРКА ЦЕЛОСТНОСТИ ===");
        System.out.println("Все объекты возвращены: " + (finalStats.freeCount == pool.getCapacity()));
        System.out.println("Нет занятых объектов: " + (finalStats.busyCount == 0));
        System.out.println("Количество получений = количеству возвратов: " + (finalStats.totalGets == finalStats.totalReturns));
        
        // Очищаем ресурсы
        pool.cleanup();
        
        System.out.println("\n=== СТРЕСС-ТЕСТ ЗАВЕРШЕН ===");
    }
} 