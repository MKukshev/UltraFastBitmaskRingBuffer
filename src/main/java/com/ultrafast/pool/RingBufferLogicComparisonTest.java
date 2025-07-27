package com.ultrafast.pool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Тест для сравнения логики ring buffer в разных классах
 */
public class RingBufferLogicComparisonTest {
    
    public static void main(String[] args) {
        System.out.println("=== СРАВНЕНИЕ ЛОГИКИ RING BUFFER В РАЗНЫХ КЛАССАХ ===\n");
        
        // Тест 1: Сравнение с очень маленьким пулом
        System.out.println("ТЕСТ 1: Сравнение с пулом емкостью 1");
        testWithTinyPool();
        
        // Тест 2: Сравнение с пулом емкостью 2
        System.out.println("\nТЕСТ 2: Сравнение с пулом емкостью 2");
        testWithSmallPool();
        
        // Тест 3: Многопоточный тест
        System.out.println("\nТЕСТ 3: Многопоточный тест с пулом емкостью 3");
        testMultithreaded();
        
        System.out.println("\n=== ТЕСТИРОВАНИЕ ЗАВЕРШЕНО ===");
    }
    
    private static void testWithTinyPool() {
        System.out.println("--- Пул емкостью 1 ---");
        
        // Тестируем разные классы
        testClass("BitmaskRingBufferUltraVarHandle", () -> 
            new BitmaskRingBufferUltraVarHandle<>(1, TestObject::new));
        
        testClass("BitmaskRingBufferUltra", () -> 
            new BitmaskRingBufferUltra<>(1, TestObject::new));
        
        testClass("BitmaskRingBufferOffHeap", () -> 
            new BitmaskRingBufferOffHeap<>(1, TestObject::new));
        

    }
    
    private static void testWithSmallPool() {
        System.out.println("--- Пул емкостью 2 ---");
        
        // Тестируем разные классы
        testClass("BitmaskRingBufferUltraVarHandle", () -> 
            new BitmaskRingBufferUltraVarHandle<>(2, TestObject::new));
        
        testClass("BitmaskRingBufferUltra", () -> 
            new BitmaskRingBufferUltra<>(2, TestObject::new));
        
        testClass("BitmaskRingBufferOffHeap", () -> 
            new BitmaskRingBufferOffHeap<>(2, TestObject::new));
        

    }
    
    private static void testMultithreaded() {
        System.out.println("--- Многопоточный тест с пулом емкостью 3 ---");
        
        // Тестируем разные классы
        testClassMultithreaded("BitmaskRingBufferUltraVarHandle", () -> 
            new BitmaskRingBufferUltraVarHandle<>(3, TestObject::new));
        
        testClassMultithreaded("BitmaskRingBufferUltra", () -> 
            new BitmaskRingBufferUltra<>(3, TestObject::new));
        
        testClassMultithreaded("BitmaskRingBufferOffHeap", () -> 
            new BitmaskRingBufferOffHeap<>(3, TestObject::new));
        

    }
    
    private static void testClass(String className, PoolFactory factory) {
        try {
            System.out.println("\n" + className + ":");
            
            Object pool = factory.createPool();
            int capacity = getCapacity(pool);
            
            System.out.println("  Емкость пула: " + capacity);
            
            // Пытаемся получить больше объектов, чем есть в пуле
            int attempts = capacity + 2;
            int successfulGets = 0;
            int nullReturns = 0;
            
            for (int i = 0; i < attempts; i++) {
                Object obj = getObject(pool);
                if (obj != null) {
                    successfulGets++;
                    returnObject(pool, obj);
                } else {
                    nullReturns++;
                }
            }
            
            System.out.println("  Успешных получений: " + successfulGets);
            System.out.println("  Возвратов null: " + nullReturns);
            System.out.println("  Соотношение: " + successfulGets + ":" + nullReturns);
            
            // Проверяем статистику
            Object stats = getStats(pool);
            System.out.println("  Статистика: " + stats);
            
        } catch (Exception e) {
            System.out.println("  ОШИБКА: " + e.getMessage());
        }
    }
    
    private static void testClassMultithreaded(String className, PoolFactory factory) {
        try {
            System.out.println("\n" + className + " (многопоточный):");
            
            Object pool = factory.createPool();
            int capacity = getCapacity(pool);
            
            System.out.println("  Емкость пула: " + capacity);
            
            int threadCount = 4;
            int operationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            AtomicInteger totalGets = new AtomicInteger(0);
            AtomicInteger totalNulls = new AtomicInteger(0);
            
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            Object obj = getObject(pool);
                            if (obj != null) {
                                totalGets.incrementAndGet();
                                // Небольшая задержка для увеличения contention
                                Thread.sleep(1);
                                returnObject(pool, obj);
                            } else {
                                totalNulls.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            executor.shutdown();
            
            System.out.println("  Всего получений: " + totalGets.get());
            System.out.println("  Всего null: " + totalNulls.get());
            System.out.println("  Соотношение: " + totalGets.get() + ":" + totalNulls.get());
            
            // Проверяем статистику
            Object stats = getStats(pool);
            System.out.println("  Статистика: " + stats);
            
        } catch (Exception e) {
            System.out.println("  ОШИБКА: " + e.getMessage());
        }
    }
    
    // Вспомогательные методы для работы с разными типами пулов
    private static int getCapacity(Object pool) throws Exception {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<?>) pool).getCapacity();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<?>) pool).getCapacity();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<?>) pool).getCapacity();
        }
        throw new IllegalArgumentException("Неизвестный тип пула: " + pool.getClass());
    }
    
    private static Object getObject(Object pool) throws Exception {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<?>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<?>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<?>) pool).getFreeObject();
        }
        throw new IllegalArgumentException("Неизвестный тип пула: " + pool.getClass());
    }
    
    private static void returnObject(Object pool, Object obj) throws Exception {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            ((BitmaskRingBufferUltraVarHandle<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltra) {
            ((BitmaskRingBufferUltra<Object>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            ((BitmaskRingBufferOffHeap<Object>) pool).setFreeObject(obj);
        } else {
            throw new IllegalArgumentException("Неизвестный тип пула: " + pool.getClass());
        }
    }
    
    private static Object getStats(Object pool) throws Exception {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferUltra) {
            return ((BitmaskRingBufferUltra<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferOffHeap) {
            return ((BitmaskRingBufferOffHeap<?>) pool).getStats();
        }
        throw new IllegalArgumentException("Неизвестный тип пула: " + pool.getClass());
    }
    
    @FunctionalInterface
    interface PoolFactory {
        Object createPool() throws Exception;
    }
    
    // Тестовый объект
    static class TestObject {
        private final int id;
        private static int counter = 0;
        
        public TestObject() {
            this.id = ++counter;
        }
        
        @Override
        public String toString() {
            return "TestObject[" + id + "]";
        }
    }
} 