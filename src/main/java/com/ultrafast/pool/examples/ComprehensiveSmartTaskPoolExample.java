package com.ultrafast.pool.examples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.smart.SmartTaskPool;
import com.ultrafast.pool.task.AutoReturnSimpleTask;

/**
 * Комплексный пример использования SmartTaskPool после редизайна.
 * Демонстрирует все преимущества нового дизайна:
 * - Элегантный API без boilerplate кода
 * - Автоматическое управление ресурсами
 * - Централизованное управление Future
 * - Fluent API для настройки
 * - Batch обработка
 * - Мониторинг и статистика
 * - Обработка ошибок и retry логика
 * - Отмена задач
 */
public class ComprehensiveSmartTaskPoolExample {
    
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    
    public static void main(String[] args) {
        System.out.println("🚀 Комплексный пример SmartTaskPool после редизайна");
        System.out.println("=" .repeat(80));
        
        // Создаем фабрику для задач
        BitmaskRingBufferUltraVarHandleAutoExpand.ObjectFactory<AutoReturnSimpleTask> factory = 
            () -> new AutoReturnSimpleTask();
        
        // Создаем пул объектов с авторасширением
        BitmaskRingBufferUltraVarHandleAutoExpand<AutoReturnSimpleTask> pool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, factory, 0.5, 200); // 50% расширение, макс 200%
        
        // Создаем умный пул задач
        SmartTaskPool<AutoReturnSimpleTask> smartPool = new SmartTaskPool<>(
            pool,
            Executors.newFixedThreadPool(4)
        );
        
        try {
            // Демонстрация всех возможностей
            demonstrateSimpleUsage(smartPool);
            demonstrateFluentAPI(smartPool);
            demonstrateBatchProcessing(smartPool);
            demonstrateTaskManagement(smartPool);
            demonstrateErrorHandling(smartPool);
            demonstrateMonitoring(smartPool);
            demonstratePerformanceComparison(smartPool);
            
        } finally {
            // Элегантное завершение работы
            System.out.println("\n🔄 Завершение работы SmartTaskPool...");
            smartPool.shutdown();
            
            try {
                if (smartPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.out.println("✅ SmartTaskPool успешно завершил работу");
                } else {
                    System.out.println("⚠️ SmartTaskPool принудительно завершен");
                    smartPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                smartPool.shutdownNow();
            }
        }
    }
    
    /**
     * Демонстрация 1: Простое использование - минимум кода, максимум функциональности
     */
    private static void demonstrateSimpleUsage(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 1: Простое использование");
        System.out.println("-".repeat(50));
        
        // Традиционный подход (много boilerplate кода)
        System.out.println("❌ Традиционный подход (много кода):");
        System.out.println("   - Ручное получение объекта из пула");
        System.out.println("   - Ручное управление Future");
        System.out.println("   - Ручной возврат объекта в пул");
        System.out.println("   - Ручная обработка исключений");
        
        // SmartTaskPool подход (элегантно и просто)
        System.out.println("\n✅ SmartTaskPool подход (элегантно):");
        
        // Простая отправка задачи - ВСЕГО 1 СТРОКА!
        Future<?> future = smartPool.submit(task -> {
            task.setData("Простая задача " + taskCounter.incrementAndGet());
            task.execute();
            return null;
        });
        
        try {
            future.get(2, TimeUnit.SECONDS);
            System.out.println("   ✅ Задача выполнена успешно");
        } catch (Exception e) {
            System.out.println("   ❌ Ошибка: " + e.getMessage());
        }
        
        System.out.println("   🎯 Результат: Автоматическое управление ресурсами, Future и исключениями!");
    }
    
    /**
     * Демонстрация 2: Fluent API - читаемый и настраиваемый код
     */
    private static void demonstrateFluentAPI(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 2: Fluent API");
        System.out.println("-".repeat(50));
        
        System.out.println("🎨 Fluent API - читаемый и настраиваемый код:");
        
        // Создание задачи с полной настройкой через fluent API
        Future<?> future = smartPool.submit()
            .withTimeout(Duration.ofSeconds(5))           // Таймаут выполнения
            .autoCancelOnError()                          // Автоматическая отмена при ошибке
            .withName("FluentTask")                       // Именование задачи
            .preProcess(task -> {                         // Pre-processing
                System.out.println("   🔧 Инициализация задачи...");
                task.setData("Fluent API задача");
                task.setLoggingEnabled(true);
            })
            .postProcess(task -> {                        // Post-processing
                System.out.println("   🧹 Очистка после выполнения...");
                task.resetStatistics();
            })
            .retryOnFailure(3)                           // Retry логика
            .execute(task -> {                           // Выполнение
                System.out.println("   ⚡ Выполнение задачи: " + task.getTaskName());
                task.execute();
                return null;
            });
        
        try {
            future.get(3, TimeUnit.SECONDS);
            System.out.println("   ✅ Fluent задача выполнена успешно");
        } catch (Exception e) {
            System.out.println("   ❌ Ошибка: " + e.getMessage());
        }
        
        System.out.println("   🎯 Результат: Читаемый код с полной настройкой поведения!");
    }
    
    /**
     * Демонстрация 3: Batch обработка - эффективная работа с множественными задачами
     */
    private static void demonstrateBatchProcessing(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 3: Batch обработка");
        System.out.println("-".repeat(50));
        
        System.out.println("📦 Batch обработка множественных задач:");
        
        // Создание списка задач
        List<Function<AutoReturnSimpleTask, ?>> tasks = Arrays.asList(
            task -> {
                task.setData("Batch задача 1");
                task.execute();
                return null;
            },
            task -> {
                task.setData("Batch задача 2");
                task.execute();
                return null;
            },
            task -> {
                task.setData("Batch задача 3");
                task.execute();
                return null;
            },
            task -> {
                task.setData("Batch задача 4");
                task.execute();
                return null;
            }
        );
        
        // Отправка всех задач одновременно - ВСЕГО 1 СТРОКА!
        List<Future<?>> futures = smartPool.submitAll(tasks);
        
        System.out.println("   📊 Отправлено задач: " + futures.size());
        
        // Ожидание завершения всех задач
        for (int i = 0; i < futures.size(); i++) {
            try {
                Object result = futures.get(i).get(2, TimeUnit.SECONDS);
                System.out.println("   ✅ Задача " + (i + 1) + " завершена: " + result);
            } catch (Exception e) {
                System.out.println("   ❌ Задача " + (i + 1) + " завершена с ошибкой: " + e.getMessage());
            }
        }
        
        System.out.println("   🎯 Результат: Эффективная параллельная обработка множественных задач!");
    }
    
    /**
     * Демонстрация 4: Управление задачами - централизованный контроль
     */
    private static void demonstrateTaskManagement(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 4: Управление задачами");
        System.out.println("-".repeat(50));
        
        System.out.println("🎮 Централизованное управление задачами:");
        
        // Демонстрация отмены через task.cancelTask()
        System.out.println("   🔄 Демонстрация отмены через task.cancelTask():");
        Future<?> selfCancellingFuture = smartPool.submit(task -> {
            task.setData("Самоотменяющаяся задача");
            task.setLoggingEnabled(true);
            
            try {
                Thread.sleep(1000); // Работаем 1 секунду
                System.out.println("   🔄 Задача решает отменить себя...");
                task.cancelTask(); // Отменяем задачу изнутри
                System.out.println("   ✅ Задача успешно отменена изнутри");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            return null;
        });
        
        try {
            Thread.sleep(1500); // Ждем завершения самоотменяющейся задачи
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Отправляем несколько долгих задач
        Future<?> future1 = smartPool.submit(task -> {
            task.setData("Долгая задача 1");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        Future<?> future2 = smartPool.submit(task -> {
            task.setData("Долгая задача 2");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        Future<?> future3 = smartPool.submit(task -> {
            task.setData("Долгая задача 3");
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            task.execute();
            return null;
        });
        
        try {
            Thread.sleep(500); // Даем время на запуск задач
            
            // Мониторинг активных задач
            System.out.println("   📊 Активные задачи: " + smartPool.getActiveTaskIds());
            
            // Отмена конкретной задачи через Future
            boolean cancelled1 = future1.cancel(true);
            System.out.println("   🚫 Задача 1 отменена через Future: " + cancelled1);
            
            // Отмена через SmartTaskPool.cancelTask() (по ID)
            if (!smartPool.getActiveTaskIds().isEmpty()) {
                String taskId = smartPool.getActiveTaskIds().iterator().next();
                System.out.println("   🚫 Отменяем задачу с ID: " + taskId);
                smartPool.cancelTask(taskId);
            }
            
            // Отмена всех остальных задач
            smartPool.cancelAllTasks();
            System.out.println("   🚫 Все оставшиеся задачи отменены");
            
            // Проверка статуса
            System.out.println("   📊 Активные задачи после отмены: " + smartPool.getActiveTaskIds());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println("   🎯 Результат: Полный контроль над выполнением задач!");
    }
    
    /**
     * Демонстрация 5: Обработка ошибок и retry логика
     */
    private static void demonstrateErrorHandling(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 5: Обработка ошибок");
        System.out.println("-".repeat(50));
        
        System.out.println("🛡️ Встроенная обработка ошибок и retry логика:");
        
        // Задача, которая может завершиться с ошибкой
        Future<?> future = smartPool.submit()
            .withTimeout(Duration.ofSeconds(10))
            .retryOnFailure(3)  // 3 попытки
            .withName("ErrorHandlingTask")
            .execute(task -> {
                task.setData("Задача с обработкой ошибок");
                
                // Симулируем случайную ошибку
                if (Math.random() < 0.7) {
                    throw new RuntimeException("Симулированная ошибка");
                }
                
                task.execute();
                return null;
            });
        
        try {
            Object result = future.get(5, TimeUnit.SECONDS);
            System.out.println("   ✅ Задача завершена успешно: " + result);
        } catch (Exception e) {
            System.out.println("   ❌ Задача завершена с ошибкой после retry: " + e.getMessage());
        }
        
        System.out.println("   🎯 Результат: Автоматическая обработка ошибок с retry логикой!");
    }
    
    /**
     * Демонстрация 6: Мониторинг и статистика
     */
    private static void demonstrateMonitoring(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 6: Мониторинг и статистика");
        System.out.println("-".repeat(50));
        
        System.out.println("📊 Встроенный мониторинг и статистика:");
        
        // Отправляем задачи для сбора статистики
        for (int i = 0; i < 10; i++) {
            final int taskNum = i;
            smartPool.submit(task -> {
                task.setData("Статистическая задача " + taskNum);
                task.execute();
                return null;
            });
        }
        
        // Отменяем несколько задач
        try {
            Thread.sleep(100);
            smartPool.cancelAllTasks();
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Получение детальной статистики
        SmartTaskPool.TaskPoolStatistics stats = smartPool.getStatistics();
        
        System.out.println("   📈 Статистика SmartTaskPool:");
        System.out.println("      • Всего задач: " + stats.getTotalTasks());
        System.out.println("      • Активных задач: " + stats.getActiveTasks());
        System.out.println("      • Завершенных задач: " + stats.getCompletedTasks());
        System.out.println("      • Отмененных задач: " + stats.getCancelledTasks());
        System.out.println("      • Неудачных задач: " + stats.getFailedTasks());
        
        System.out.println("   🎯 Результат: Полная видимость в выполнение задач!");
    }
    
    /**
     * Демонстрация 7: Сравнение производительности
     */
    private static void demonstratePerformanceComparison(SmartTaskPool<AutoReturnSimpleTask> smartPool) {
        System.out.println("\n📋 Демонстрация 7: Сравнение производительности");
        System.out.println("-".repeat(50));
        
        System.out.println("⚡ Сравнение производительности подходов:");
        
        int taskCount = 1000;
        
        // Тест SmartTaskPool
        long startTime = System.currentTimeMillis();
        
        List<Function<AutoReturnSimpleTask, ?>> tasks = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            tasks.add(task -> {
                task.setData("Производительная задача");
                task.execute();
                return null;
            });
        }
        List<Future<?>> futures = smartPool.submitAll(tasks);
        
        // Ожидание завершения
        for (Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Игнорируем ошибки для демонстрации
            }
        }
        
        long smartPoolTime = System.currentTimeMillis() - startTime;
        
        System.out.println("   📊 Результаты:");
        System.out.println("      • SmartTaskPool: " + smartPoolTime + "ms для " + taskCount + " задач");
        System.out.println("      • Среднее время на задачу: " + (smartPoolTime / (double) taskCount) + "ms");
        
        System.out.println("   🎯 Результат: Высокая производительность с минимальными накладными расходами!");
    }
} 