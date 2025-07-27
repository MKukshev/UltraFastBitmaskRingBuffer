package com.ultrafast.pool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пример использования BitmaskRingBufferUltraVarHandleAutoExpand
 */
public class BitmaskRingBufferUltraVarHandleAutoExpandExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Пример использования BitmaskRingBufferUltraVarHandleAutoExpand ===\n");
        
        // Создаем пул с автоматическим расширением
        int initialCapacity = 10;
        double expansionPercentage = 0.3; // 30% расширение
        int maxExpansionPercentage = 200; // Максимум 200% от начальной емкости
        
        BitmaskRingBufferUltraVarHandleAutoExpand<WorkerTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(initialCapacity, WorkerTask::new, 
                                                          expansionPercentage, maxExpansionPercentage);
        
        System.out.printf("Создан пул с начальной емкостью: %d%n", pool.getInitialCapacity());
        System.out.printf("Процент расширения: %.1f%%%n", expansionPercentage * 100);
        System.out.printf("Максимальное расширение: %d%%%n", maxExpansionPercentage);
        System.out.printf("Максимальная емкость: %d%n", pool.getMaxAllowedCapacity());
        
        // Демонстрируем базовую функциональность
        demonstrateBasicFunctionality(pool);
        
        // Демонстрируем автоматическое расширение
        demonstrateAutoExpansion(pool);
        
        // Демонстрируем многопоточную работу
        demonstrateConcurrentAccess(pool);
        
        // Показываем финальную статистику
        showFinalStatistics(pool);
        
        // Очищаем ресурсы
        pool.cleanup();
        System.out.println("\n=== Пример завершен ===");
    }
    
    /**
     * Демонстрирует базовую функциональность пула
     */
    private static void demonstrateBasicFunctionality(BitmaskRingBufferUltraVarHandleAutoExpand<WorkerTask> pool) {
        System.out.println("\n--- Базовая функциональность ---");
        
        // Получаем несколько объектов
        WorkerTask task1 = pool.getFreeObject();
        WorkerTask task2 = pool.getFreeObject();
        WorkerTask task3 = pool.getFreeObject();
        
        System.out.printf("Получено объектов: 3%n");
        System.out.printf("Текущая емкость: %d%n", pool.getCapacity());
        
        // Выполняем работу
        task1.performTask("Задача 1");
        task2.performTask("Задача 2");
        task3.performTask("Задача 3");
        
        // Возвращаем объекты
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        pool.setFreeObject(task3);
        
        System.out.println("Объекты возвращены в пул");
        
        // Показываем статистику
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        System.out.printf("Статистика: %s%n", stats);
    }
    
    /**
     * Демонстрирует автоматическое расширение пула
     */
    private static void demonstrateAutoExpansion(BitmaskRingBufferUltraVarHandleAutoExpand<WorkerTask> pool) {
        System.out.println("\n--- Автоматическое расширение ---");
        
        int initialCapacity = pool.getCapacity();
        System.out.printf("Начальная емкость: %d%n", initialCapacity);
        
        // Получаем все объекты из начального пула
        WorkerTask[] tasks = new WorkerTask[initialCapacity];
        for (int i = 0; i < initialCapacity; i++) {
            tasks[i] = pool.getFreeObject();
            System.out.printf("Получен объект %d: %s%n", i + 1, tasks[i]);
        }
        
        System.out.printf("Текущая емкость после получения всех объектов: %d%n", pool.getCapacity());
        
        // Пытаемся получить еще один объект - должно произойти расширение
        WorkerTask expandedTask = pool.getFreeObject();
        System.out.printf("Получен объект после расширения: %s%n", expandedTask);
        System.out.printf("Новая емкость: %d%n", pool.getCapacity());
        
        // Показываем статистику расширения
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        System.out.printf("Расширений: %d%n", stats.totalExpansions);
        System.out.printf("Обращений к auto expansion: %d%n", stats.autoExpansionHits);
        
        // Возвращаем все объекты
        for (WorkerTask task : tasks) {
            pool.setFreeObject(task);
        }
        pool.setFreeObject(expandedTask);
        
        System.out.println("Все объекты возвращены в пул");
    }
    
    /**
     * Демонстрирует многопоточный доступ к пулу
     */
    private static void demonstrateConcurrentAccess(BitmaskRingBufferUltraVarHandleAutoExpand<WorkerTask> pool) 
            throws InterruptedException {
        System.out.println("\n--- Многопоточный доступ ---");
        
        int threadCount = 4;
        int operationsPerThread = 1000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulOperations = new AtomicInteger(0);
        
        System.out.printf("Запуск %d потоков, каждый выполняет %d операций%n", threadCount, operationsPerThread);
        
        long startTime = System.nanoTime();
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        WorkerTask task = pool.getFreeObject();
                        if (task != null) {
                            task.performTask("Поток " + threadId + ", операция " + j);
                            if (pool.setFreeObject(task)) {
                                successfulOperations.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.printf("Ошибка в потоке %d: %s%n", threadId, e.getMessage());
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        long duration = endTime - startTime;
        
        System.out.printf("Выполнено операций: %d%n", successfulOperations.get());
        System.out.printf("Время выполнения: %.2f мс%n", duration / 1_000_000.0);
        System.out.printf("Производительность: %.2f оп/мс%n", 
                         (double) successfulOperations.get() / (duration / 1_000_000.0));
    }
    
    /**
     * Показывает финальную статистику пула
     */
    private static void showFinalStatistics(BitmaskRingBufferUltraVarHandleAutoExpand<WorkerTask> pool) {
        System.out.println("\n--- Финальная статистика ---");
        
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        
        System.out.printf("Текущая емкость: %d%n", stats.capacity);
        System.out.printf("Свободных объектов: %d%n", stats.freeCount);
        System.out.printf("Занятых объектов: %d%n", stats.busyCount);
        System.out.printf("Общее количество получений: %d%n", stats.totalGets);
        System.out.printf("Общее количество возвратов: %d%n", stats.totalReturns);
        System.out.printf("Количество расширений: %d%n", stats.totalExpansions);
        System.out.printf("Обращений к auto expansion: %d%n", stats.autoExpansionHits);
        System.out.printf("Успешных bit tricks: %d%n", stats.bitTrickHits);
        System.out.printf("Успешных stack hits: %d%n", stats.stackHits);
        System.out.printf("Процент расширения: %.1f%%%n", stats.expansionPercentage * 100);
        System.out.printf("Максимальный процент расширения: %d%%%n", stats.maxExpansionPercentage);
        System.out.printf("Максимально допустимая емкость: %d%n", stats.maxAllowedCapacity);
        
        System.out.printf("\nПолная статистика: %s%n", stats);
    }
    
    /**
     * Рабочая задача для демонстрации
     */
    private static class WorkerTask {
        private final int id;
        private static int nextId = 0;
        private String lastTask;
        
        public WorkerTask() {
            this.id = nextId++;
        }
        
        public void performTask(String taskName) {
            this.lastTask = taskName;
            // Имитируем выполнение задачи
            try {
                Thread.sleep(1); // Небольшая задержка для имитации работы
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        public int getId() {
            return id;
        }
        
        public String getLastTask() {
            return lastTask;
        }
        
        @Override
        public String toString() {
            return String.format("WorkerTask{id=%d, lastTask='%s'}", id, lastTask);
        }
    }
} 