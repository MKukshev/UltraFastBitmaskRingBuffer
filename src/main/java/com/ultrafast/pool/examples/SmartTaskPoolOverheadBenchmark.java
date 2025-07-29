package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.smart.SmartTaskPool;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Бенчмарк для измерения оверхеда SmartTaskPool по сравнению с прямым использованием пула и задач.
 * Сравнивает:
 * 1. Прямое использование пула + ExecutorService
 * 2. SmartTaskPool с базовым API
 * 3. SmartTaskPool с расширенным API (конфигурация)
 * 4. SmartTaskPool с Fluent API
 */
public class SmartTaskPoolOverheadBenchmark {
    
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int THREAD_POOL_SIZE = 4;
    
    public static void main(String[] args) {
        System.out.println("🚀 Бенчмарк оверхеда SmartTaskPool");
        System.out.println("=" .repeat(80));
        
        // Создаем фабрику для задач
        BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory<AutoReturnSimpleTask> factory = 
            () -> new AutoReturnSimpleTask();
        
        // Создаем пул объектов с авторасширением
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(100, factory, 0.5, 1000);
        
        // Создаем ExecutorService для прямого использования
        ExecutorService directExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        // Создаем SmartTaskPool
        SmartTaskPool<AutoReturnSimpleTask> smartPool = new SmartTaskPool<>(
            pool,
            Executors.newFixedThreadPool(THREAD_POOL_SIZE)
        );
        
        try {
            // Прогрев
            System.out.println("🔥 Прогрев системы...");
            warmup(directExecutor, smartPool, pool);
            
            // Бенчмарки
            System.out.println("\n📊 Запуск бенчмарков...");
            
            // Тест 1: Прямое использование пула + ExecutorService
            benchmarkDirectUsage(directExecutor, pool);
            
            // Тест 2: SmartTaskPool с базовым API
            benchmarkSmartTaskPoolBasic(smartPool);
            
            // Тест 3: SmartTaskPool с расширенным API
            benchmarkSmartTaskPoolExtended(smartPool);
            
            // Тест 4: SmartTaskPool с Fluent API
            benchmarkSmartTaskPoolFluent(smartPool);
            
            // Тест 5: Batch операции
            benchmarkBatchOperations(directExecutor, smartPool, pool);
            
            // Тест 6: Долгие задачи (закомментирован для ускорения)
            // benchmarkLongRunningTasks(directExecutor, smartPool, pool);
            
        } finally {
            // Очистка ресурсов
            System.out.println("\n🔄 Завершение работы...");
            directExecutor.shutdown();
            smartPool.shutdown();
            
            try {
                directExecutor.awaitTermination(5, TimeUnit.SECONDS);
                smartPool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Прогрев системы
     */
    private static void warmup(ExecutorService directExecutor, SmartTaskPool<AutoReturnSimpleTask> smartPool, 
                              BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            final int warmupIndex = i;
            // Прямое использование
            AutoReturnSimpleTask task1 = pool.getFreeObject();
            task1.setData("Warmup " + warmupIndex);
            Future<?> future1 = directExecutor.submit(() -> {
                task1.execute();
                return null;
            });
            try {
                future1.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки в прогреве
            } finally {
                pool.setFreeObject(task1);
            }
            
            // SmartTaskPool
            smartPool.submit(task -> {
                task.setData("Warmup " + warmupIndex);
                task.execute();
                return null;
            });
        }
    }
    
    /**
     * Бенчмарк 1: Прямое использование пула + ExecutorService
     */
    private static void benchmarkDirectUsage(ExecutorService executor, 
                                           BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool) {
        System.out.println("\n📋 Тест 1: Прямое использование пула + ExecutorService");
        System.out.println("-".repeat(50));
        
        long startTime = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            final AutoReturnSimpleTask task = pool.getFreeObject();
            if (task == null) {
                System.out.println("   ⚠️ Пул исчерпан на итерации " + i);
                break;
            }
            task.setData("Direct " + i);
            
            Future<?> future = executor.submit(() -> {
                try {
                    task.execute();
                    return null;
                } finally {
                    pool.setFreeObject(task);
                }
            });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
        
        System.out.println("   ⏱️ Время выполнения: " + duration + "ms");
        System.out.println("   📊 Среднее время на задачу: " + (duration / (double)BENCHMARK_ITERATIONS) + "ms");
        System.out.println("   🎯 Задач в секунду: " + (BENCHMARK_ITERATIONS * 1000 / duration));
    }
    
    /**
     * Бенчмарк 2: SmartTaskPool с базовым API
     */
    private static void benchmarkSmartTaskPoolBasic(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Тест 2: SmartTaskPool с базовым API");
        System.out.println("-".repeat(50));
        
        long startTime = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            final int taskIndex = i;
            Future<?> future = smartPool.submit(task -> {
                task.setData("SmartBasic " + taskIndex);
                task.execute();
                return null;
            });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
        
        System.out.println("   ⏱️ Время выполнения: " + duration + "ms");
        System.out.println("   📊 Среднее время на задачу: " + (duration / (double)BENCHMARK_ITERATIONS) + "ms");
        System.out.println("   🎯 Задач в секунду: " + (BENCHMARK_ITERATIONS * 1000 / duration));
    }
    
    /**
     * Бенчмарк 3: SmartTaskPool с расширенным API
     */
    private static void benchmarkSmartTaskPoolExtended(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Тест 3: SmartTaskPool с расширенным API (конфигурация)");
        System.out.println("-".repeat(50));
        
        long startTime = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            final int taskIndex = i;
            SmartTaskPool.TaskConfig config = new SmartTaskPool.TaskConfig()
                .withTimeout(Duration.ofSeconds(10))
                .withName("ExtendedTask_" + taskIndex);
            
            Future<?> future = smartPool.submit(config, task -> {
                task.setData("SmartExtended " + taskIndex);
                task.execute();
                return null;
            });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
        
        System.out.println("   ⏱️ Время выполнения: " + duration + "ms");
        System.out.println("   📊 Среднее время на задачу: " + (duration / (double)BENCHMARK_ITERATIONS) + "ms");
        System.out.println("   🎯 Задач в секунду: " + (BENCHMARK_ITERATIONS * 1000 / duration));
    }
    
    /**
     * Бенчмарк 4: SmartTaskPool с Fluent API
     */
    private static void benchmarkSmartTaskPoolFluent(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Тест 4: SmartTaskPool с Fluent API");
        System.out.println("-".repeat(50));
        
        long startTime = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            final int taskIndex = i;
            Future<?> future = smartPool.submit()
                .withTimeout(Duration.ofSeconds(10))
                .withName("FluentTask_" + taskIndex)
                .execute(task -> {
                    task.setData("SmartFluent " + taskIndex);
                    task.execute();
                    return null;
                });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1_000_000; // в миллисекундах
        
        System.out.println("   ⏱️ Время выполнения: " + duration + "ms");
        System.out.println("   📊 Среднее время на задачу: " + (duration / (double)BENCHMARK_ITERATIONS) + "ms");
        System.out.println("   🎯 Задач в секунду: " + (BENCHMARK_ITERATIONS * 1000 / duration));
    }
    
    /**
     * Бенчмарк 5: Batch операции
     */
    private static void benchmarkBatchOperations(ExecutorService executor, 
                                               SmartTaskPool<AutoReturnSimpleTask> smartPool,
                                               BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool) {
        System.out.println("\n📋 Тест 5: Batch операции");
        System.out.println("-".repeat(50));
        
        int batchSize = 100;
        int batches = BENCHMARK_ITERATIONS / batchSize;
        
        // Прямое использование
        long startTime = System.nanoTime();
        for (int batch = 0; batch < batches; batch++) {
            final int batchIndex = batch;
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                final AutoReturnSimpleTask task = pool.getFreeObject();
                final int taskIndex = i;
                task.setData("BatchDirect_" + batchIndex + "_" + taskIndex);
                
                Future<?> future = executor.submit(() -> {
                    try {
                        task.execute();
                        return null;
                    } finally {
                        pool.setFreeObject(task);
                    }
                });
                futures.add(future);
            }
            
            // Ждем завершения batch
            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        }
        long endTime = System.nanoTime();
        long directDuration = (endTime - startTime) / 1_000_000;
        
        // SmartTaskPool
        startTime = System.nanoTime();
        for (int batch = 0; batch < batches; batch++) {
            final int batchIndex = batch;
            List<Function<AutoReturnSimpleTask, ?>> tasks = new ArrayList<>();
            for (int i = 0; i < batchSize; i++) {
                final int taskIndex = i;
                tasks.add(task -> {
                    task.setData("BatchSmart_" + batchIndex + "_" + taskIndex);
                    task.execute();
                    return null;
                });
            }
            
            List<Future<?>> futures = smartPool.submitAll(tasks);
            
            // Ждем завершения batch
            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Игнорируем ошибки
                }
            }
        }
        endTime = System.nanoTime();
        long smartDuration = (endTime - startTime) / 1_000_000;
        
        System.out.println("   📊 Прямое использование: " + directDuration + "ms");
        System.out.println("   📊 SmartTaskPool: " + smartDuration + "ms");
        System.out.println("   📈 Оверхед: " + String.format("%.2f", (smartDuration - directDuration) / (double)directDuration * 100) + "%");
    }
    
    /**
     * Бенчмарк 6: Долгие задачи
     */
    private static void benchmarkLongRunningTasks(ExecutorService executor, 
                                                SmartTaskPool<AutoReturnSimpleTask> smartPool,
                                                BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool) {
        System.out.println("\n📋 Тест 6: Долгие задачи (с реальной работой)");
        System.out.println("-".repeat(50));
        
        int iterations = BENCHMARK_ITERATIONS / 10; // Меньше итераций для долгих задач
        
        // Прямое использование
        long startTime = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            final AutoReturnSimpleTask task = pool.getFreeObject();
            final int taskIndex = i;
            task.setData("LongDirect " + taskIndex);
            task.setLongRunningTask(true);
            
            Future<?> future = executor.submit(() -> {
                try {
                    task.execute();
                    return null;
                } finally {
                    pool.setFreeObject(task);
                }
            });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        long endTime = System.nanoTime();
        long directDuration = (endTime - startTime) / 1_000_000;
        
        // SmartTaskPool
        startTime = System.nanoTime();
        futures.clear();
        
        for (int i = 0; i < iterations; i++) {
            final int taskIndex = i;
            Future<?> future = smartPool.submit(task -> {
                task.setData("LongSmart " + taskIndex);
                task.setLongRunningTask(true);
                task.execute();
                return null;
            });
            futures.add(future);
        }
        
        // Ждем завершения всех задач
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки
            }
        }
        
        endTime = System.nanoTime();
        long smartDuration = (endTime - startTime) / 1_000_000;
        
        System.out.println("   📊 Прямое использование: " + directDuration + "ms");
        System.out.println("   📊 SmartTaskPool: " + smartDuration + "ms");
        System.out.println("   📈 Оверхед: " + String.format("%.2f", (smartDuration - directDuration) / (double)directDuration * 100) + "%");
    }
} 