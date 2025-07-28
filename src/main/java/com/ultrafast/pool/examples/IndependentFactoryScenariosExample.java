package com.ultrafast.pool.examples;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.ultrafast.pool.BitmaskRingBufferUltraVarHandleAutoExpand;
import com.ultrafast.pool.factory.IndependentObjectFactory;
import com.ultrafast.pool.task.SimpleTask;

/**
 * Практический пример демонстрирующий все три сценария использования IndependentObjectFactory.
 * Показывает когда и как использовать обычный new, fromSupplier и withInitializer.
 */
public class IndependentFactoryScenariosExample {
    
    // Счетчик для уникальных ID
    private static final AtomicInteger idCounter = new AtomicInteger(0);
    
    public static void main(String[] args) {
        System.out.println("=== Independent Factory Scenarios Example ===\n");
        
        // Сценарий 1: Обычный new - Простое создание
        scenario1_SimpleCreation();
        
        // Сценарий 2: fromSupplier - Переиспользование существующих Supplier'ов
        scenario2_FromSupplier();
        
        // Сценарий 3: withInitializer - Сложная инициализация
        scenario3_WithInitializer();
        
        // Сценарий 4: Комбинирование подходов
        scenario4_CombinedApproach();
        
        System.out.println("\n=== All scenarios completed ===");
    }
    
    /**
     * Сценарий 1: Обычный new - Простое создание объектов
     * Используется для: простой инициализации, единичного использования, быстрого прототипирования
     */
    private static void scenario1_SimpleCreation() {
        System.out.println("--- Scenario 1: Simple Creation (new) ---");
        
        // 1.1: Простая задача
        IndependentObjectFactory<SimpleTask> simpleFactory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Simple Task");
            return task;
        };
        
        // 1.2: Задача с уникальным ID
        IndependentObjectFactory<SimpleTask> idFactory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Task-" + idCounter.incrementAndGet());
            return task;
        };
        
        // 1.3: Задача с контекстом времени
        IndependentObjectFactory<SimpleTask> timeFactory = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Time Task at " + LocalDateTime.now());
            return task;
        };
        
        // Создаем пулы с разными фабриками
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> simplePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, simpleFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> idPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, idFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> timePool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(3, timeFactory);
        
        // Используем пулы
        SimpleTask simpleTask = simplePool.getFreeObject();
        SimpleTask idTask = idPool.getFreeObject();
        SimpleTask timeTask = timePool.getFreeObject();
        
        simpleTask.execute();
        idTask.execute();
        timeTask.execute();
        
        System.out.println("Simple task data: " + simpleTask.getData());
        System.out.println("ID task data: " + idTask.getData());
        System.out.println("Time task data: " + timeTask.getData());
        
        // Возвращаем в пулы
        simplePool.setFreeObject(simpleTask);
        idPool.setFreeObject(idTask);
        timePool.setFreeObject(timeTask);
        
        System.out.println("Scenario 1 completed - Simple creation works!");
    }
    
    /**
     * Сценарий 2: fromSupplier - Переиспользование существующих Supplier'ов
     * Используется для: интеграции с существующим кодом, переиспользования логики, тестирования
     */
    private static void scenario2_FromSupplier() {
        System.out.println("\n--- Scenario 2: From Supplier ---");
        
        // 2.1: Существующий Supplier из другой части системы
        Supplier<SimpleTask> existingSupplier = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Pre-configured task from supplier");
            return task;
        };
        
        // 2.2: Простой конструктор Supplier
        Supplier<SimpleTask> constructorSupplier = SimpleTask::new;
        
        // 2.3: Supplier с логикой
        Supplier<SimpleTask> logicSupplier = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Logic-based task #" + System.currentTimeMillis());
            return task;
        };
        
        // Создаем фабрики из Supplier'ов
        IndependentObjectFactory<SimpleTask> existingFactory = IndependentObjectFactory.fromSupplier(existingSupplier);
        IndependentObjectFactory<SimpleTask> constructorFactory = IndependentObjectFactory.fromSupplier(constructorSupplier);
        IndependentObjectFactory<SimpleTask> logicFactory = IndependentObjectFactory.fromSupplier(logicSupplier);
        
        // Создаем пулы
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> existingPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, existingFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> constructorPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, constructorFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> logicPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, logicFactory);
        
        // Используем пулы
        SimpleTask existingTask = existingPool.getFreeObject();
        SimpleTask constructorTask = constructorPool.getFreeObject();
        SimpleTask logicTask = logicPool.getFreeObject();
        
        existingTask.execute();
        constructorTask.execute();
        logicTask.execute();
        
        System.out.println("Existing supplier task: " + existingTask.getData());
        System.out.println("Constructor supplier task: " + constructorTask.getData());
        System.out.println("Logic supplier task: " + logicTask.getData());
        
        // Возвращаем в пулы
        existingPool.setFreeObject(existingTask);
        constructorPool.setFreeObject(constructorTask);
        logicPool.setFreeObject(logicTask);
        
        System.out.println("Scenario 2 completed - From supplier works!");
    }
    
    /**
     * Сценарий 3: withInitializer - Сложная инициализация объектов
     * Используется для: сложной инициализации, разделения ответственности, валидации, логирования
     */
    private static void scenario3_WithInitializer() {
        System.out.println("\n--- Scenario 3: With Initializer ---");
        
        // 3.1: Сложная инициализация с множественными полями
        IndependentObjectFactory<SimpleTask> complexFactory = IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData("Complex Task");
                // Симулируем установку дополнительных полей
                System.out.println("Setting complex properties for task: " + task.getData());
                System.out.println("  - Priority: HIGH");
                System.out.println("  - Created at: " + LocalDateTime.now());
                System.out.println("  - Status: READY");
            }
        );
        
        // 3.2: Инициализация с валидацией
        IndependentObjectFactory<SimpleTask> validatedFactory = IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData("Validated Task");
                
                // Валидация
                if (task.getData() == null || task.getData().isEmpty()) {
                    throw new IllegalArgumentException("Task data cannot be null or empty");
                }
                
                System.out.println("Task validated successfully: " + task.getData());
            }
        );
        
        // 3.3: Инициализация с логированием
        IndependentObjectFactory<SimpleTask> loggedFactory = IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData("Logged Task #" + idCounter.incrementAndGet());
                
                // Логирование
                System.out.println("=== Task Creation Log ===");
                System.out.println("Task created: " + task.getData());
                System.out.println("Creation time: " + LocalDateTime.now());
                System.out.println("Thread: " + Thread.currentThread().getName());
                System.out.println("=========================");
            }
        );
        
        // Создаем пулы
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> complexPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, complexFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> validatedPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, validatedFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> loggedPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, loggedFactory);
        
        // Используем пулы
        SimpleTask complexTask = complexPool.getFreeObject();
        SimpleTask validatedTask = validatedPool.getFreeObject();
        SimpleTask loggedTask = loggedPool.getFreeObject();
        
        complexTask.execute();
        validatedTask.execute();
        loggedTask.execute();
        
        System.out.println("Complex task: " + complexTask.getData());
        System.out.println("Validated task: " + validatedTask.getData());
        System.out.println("Logged task: " + loggedTask.getData());
        
        // Возвращаем в пулы
        complexPool.setFreeObject(complexTask);
        validatedPool.setFreeObject(validatedTask);
        loggedPool.setFreeObject(loggedTask);
        
        System.out.println("Scenario 3 completed - With initializer works!");
    }
    
    /**
     * Сценарий 4: Комбинирование подходов
     * Демонстрирует как можно комбинировать разные подходы в реальном приложении
     */
    private static void scenario4_CombinedApproach() {
        System.out.println("\n--- Scenario 4: Combined Approach ---");
        
        // Создаем различные фабрики для разных типов задач
        TaskFactoryManager factoryManager = new TaskFactoryManager();
        
        // Базовые задачи - обычный new
        IndependentObjectFactory<SimpleTask> basicFactory = factoryManager.createBasicFactory();
        
        // Задачи из существующего Supplier
        Supplier<SimpleTask> existingSupplier = () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Existing supplier task");
            return task;
        };
        IndependentObjectFactory<SimpleTask> supplierFactory = factoryManager.createFromSupplier(existingSupplier);
        
        // Продвинутые задачи - withInitializer
        IndependentObjectFactory<SimpleTask> advancedFactory = factoryManager.createAdvancedFactory();
        
        // Создаем пулы
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> basicPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, basicFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> supplierPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, supplierFactory);
        
        BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> advancedPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(2, advancedFactory);
        
        // Используем все пулы в многопоточной среде
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        // Запускаем задачи из разных пулов
        executor.submit(() -> {
            SimpleTask task = basicPool.getFreeObject();
            task.execute();
            System.out.println("Basic task executed: " + task.getData());
            basicPool.setFreeObject(task);
        });
        
        executor.submit(() -> {
            SimpleTask task = supplierPool.getFreeObject();
            task.execute();
            System.out.println("Supplier task executed: " + task.getData());
            supplierPool.setFreeObject(task);
        });
        
        executor.submit(() -> {
            SimpleTask task = advancedPool.getFreeObject();
            task.execute();
            System.out.println("Advanced task executed: " + task.getData());
            advancedPool.setFreeObject(task);
        });
        
        // Ждем завершения
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        System.out.println("Scenario 4 completed - Combined approach works!");
    }
    
    /**
     * Менеджер фабрик - демонстрирует практическое использование
     */
    private static class TaskFactoryManager {
        
        // Простая фабрика для базовых задач
        public IndependentObjectFactory<SimpleTask> createBasicFactory() {
            return () -> {
                SimpleTask task = new SimpleTask();
                task.setData("Basic Task");
                return task;
            };
        }
        
        // Фабрика из существующего Supplier
        public IndependentObjectFactory<SimpleTask> createFromSupplier(Supplier<SimpleTask> supplier) {
            return IndependentObjectFactory.fromSupplier(supplier);
        }
        
        // Фабрика с сложной инициализацией
        public IndependentObjectFactory<SimpleTask> createAdvancedFactory() {
            return IndependentObjectFactory.withInitializer(
                SimpleTask::new,
                task -> {
                    task.setData("Advanced Task");
                    System.out.println("Advanced task created with complex initialization");
                    System.out.println("  - Task: " + task.getData());
                    System.out.println("  - Time: " + LocalDateTime.now());
                    System.out.println("  - Thread: " + Thread.currentThread().getName());
                }
            );
        }
    }
} 