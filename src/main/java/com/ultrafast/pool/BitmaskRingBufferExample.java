package com.ultrafast.pool;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пример использования BitmaskRingBuffer
 * Демонстрирует различные сценарии работы с пулом объектов
 */
public class BitmaskRingBufferExample {
    
    public static void main(String[] args) {
        System.out.println("=== BitmaskRingBuffer Example ===");
        
        // Пример 1: Базовое использование
        basicUsageExample();
        
        // Пример 2: Многопоточное использование
        concurrentUsageExample();
        
        // Пример 3: Управление объектами
        objectManagementExample();
        
        // Пример 4: Статистика и мониторинг
        statisticsExample();
        
        System.out.println("\n=== Все примеры завершены ===");
    }
    
    /**
     * Пример 1: Базовое использование пула
     */
    private static void basicUsageExample() {
        System.out.println("\n--- Пример 1: Базовое использование ---");
        
        // Создаем пул на 1024 объекта
        BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
            1024, 
            () -> new ProcessTask("Task-" + System.nanoTime())
        );
        
        System.out.println("Создан пул с емкостью: " + pool.getStatistics().capacity);
        
        // Получаем несколько объектов
        Task task1 = pool.getFreeObject();
        Task task2 = pool.getFreeObject();
        Task task3 = pool.getFreeObject();
        
        System.out.println("Получено объектов: " + pool.getStatistics().occupiedCount);
        System.out.println("Свободно объектов: " + (pool.getStatistics().capacity - pool.getStatistics().occupiedCount));
        
        // Работаем с объектами
        if (task1 != null) {
            task1.start();
            System.out.println("Запущена задача: " + ((ProcessTask)task1).getProcessName());
        }
        
        if (task2 != null) {
            task2.start();
            task2.markForUpdate();
            System.out.println("Запущена и помечена для обновления задача: " + ((ProcessTask)task2).getProcessName());
        }
        
        // Возвращаем объекты в пул
        if (task1 != null) {
            task1.stop();
            pool.setFreeObject(task1);
            System.out.println("Задача возвращена в пул: " + ((ProcessTask)task1).getProcessName());
        }
        
        if (task2 != null) {
            task2.stop();
            pool.setFreeObject(task2);
            System.out.println("Задача возвращена в пул: " + ((ProcessTask)task2).getProcessName());
        }
        
        if (task3 != null) {
            task3.stop();
            pool.setFreeObject(task3);
            System.out.println("Задача возвращена в пул: " + ((ProcessTask)task3).getProcessName());
        }
        
        System.out.println("Финальная статистика: " + pool.getStatistics());
    }
    
    /**
     * Пример 2: Многопоточное использование
     */
    private static void concurrentUsageExample() {
        System.out.println("\n--- Пример 2: Многопоточное использование ---");
        
        // Создаем пул на 4096 объектов
        BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
            4096, 
            () -> new ProcessTask("ConcurrentTask-" + System.nanoTime())
        );
        
        int threadCount = 4;
        int operationsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger totalGets = new AtomicInteger(0);
        AtomicInteger totalReturns = new AtomicInteger(0);
        
        long startTime = System.nanoTime();
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // Получаем объект
                        Task task = pool.getFreeObject();
                        if (task != null) {
                            totalGets.incrementAndGet();
                            
                            // Имитируем работу
                            task.start();
                            Thread.sleep(1); // 1ms работы
                            
                            // Возвращаем объект
                            task.stop();
                            if (pool.setFreeObject(task)) {
                                totalReturns.incrementAndGet();
                            }
                        }
                        
                        // Пауза каждые 100 операций
                        if (j % 100 == 0) {
                            Thread.sleep(1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
            long endTime = System.nanoTime();
            long duration = endTime - startTime;
            
            System.out.println("Многопоточный тест завершен:");
            System.out.println("  Потоков: " + threadCount);
            System.out.println("  Операций на поток: " + operationsPerThread);
            System.out.println("  Всего операций: " + (threadCount * operationsPerThread));
            System.out.println("  Время выполнения: " + (duration / 1_000_000) + "ms");
            System.out.println("  Операций в секунду: " + 
                (threadCount * operationsPerThread * 1_000_000_000L / duration));
            System.out.println("  Всего получено: " + totalGets.get());
            System.out.println("  Всего возвращено: " + totalReturns.get());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Пример 3: Управление объектами
     */
    private static void objectManagementExample() {
        System.out.println("\n--- Пример 3: Управление объектами ---");
        
        // Создаем пул на 512 объектов
        BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
            512, 
            () -> new ProcessTask("ManagedTask-" + System.nanoTime())
        );
        
        // Получаем несколько объектов
        Task[] tasks = new Task[10];
        for (int i = 0; i < 10; i++) {
            tasks[i] = pool.getFreeObject();
            if (tasks[i] != null) {
                tasks[i].start();
                System.out.println("Запущена задача " + i + ": " + ((ProcessTask)tasks[i]).getProcessName());
            }
        }
        
        // Показываем статистику
        System.out.println("Статистика после запуска задач: " + pool.getStatistics());
        
        // Получаем список занятых объектов
        List<Task> busyTasks = pool.getOccupiedObjects();
        System.out.println("Количество занятых объектов: " + busyTasks.size());
        
        // Останавливаем некоторые задачи
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                tasks[i].stop();
                pool.setFreeObject(tasks[i]);
                System.out.println("Остановлена и возвращена задача " + i);
            }
        }
        
        // Показываем обновленную статистику
        System.out.println("Статистика после остановки части задач: " + pool.getStatistics());
        
        // Останавливаем все оставшиеся задачи
        pool.stopAllOccupied();
        System.out.println("Все задачи остановлены");
        
        // Возвращаем все объекты в пул
        for (int i = 5; i < 10; i++) {
            if (tasks[i] != null) {
                pool.setFreeObject(tasks[i]);
            }
        }
        
        System.out.println("Финальная статистика: " + pool.getStatistics());
    }
    
    /**
     * Пример 4: Статистика и мониторинг
     */
    private static void statisticsExample() {
        System.out.println("\n--- Пример 4: Статистика и мониторинг ---");
        
        // Создаем пул на 2048 объектов
        BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
            2048, 
            () -> new ProcessTask("StatsTask-" + System.nanoTime())
        );
        
        System.out.println("Начальная статистика: " + pool.getStatistics());
        
        // Получаем объекты и показываем статистику
        for (int i = 0; i < 100; i++) {
            Task task = pool.getFreeObject();
            if (task != null) {
                task.start();
                
                // Показываем статистику каждые 20 операций
                if (i % 20 == 0) {
                    BitmaskRingBuffer.PoolStatistics stats = pool.getStatistics();
                    System.out.println("После " + (i + 1) + " операций: " + stats);
                }
            }
        }
        
        // Показываем детальную статистику
        BitmaskRingBuffer.PoolStatistics finalStats = pool.getStatistics();
        System.out.println("\nДетальная статистика:");
        System.out.println("  Емкость пула: " + finalStats.capacity);
        System.out.println("  Занято объектов: " + finalStats.occupiedCount);
        System.out.println("  Свободно объектов: " + (finalStats.capacity - finalStats.occupiedCount));
        System.out.println("  Утилизация: " + String.format("%.2f%%", 
            (double) finalStats.occupiedCount / finalStats.capacity * 100));
        
        // Останавливаем все задачи
        pool.stopAllOccupied();
        System.out.println("\nВсе задачи остановлены");
        System.out.println("Финальная статистика: " + pool.getStatistics());
    }
} 