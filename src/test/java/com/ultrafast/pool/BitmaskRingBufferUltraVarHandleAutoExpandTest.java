package com.ultrafast.pool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Тесты для BitmaskRingBufferUltraVarHandleAutoExpand
 */
public class BitmaskRingBufferUltraVarHandleAutoExpandTest {
    
    private BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> pool;
    private static final int INITIAL_CAPACITY = 10;
    
    @BeforeEach
    void setUp() {
        pool = new BitmaskRingBufferUltraVarHandleAutoExpand<>(INITIAL_CAPACITY, TestObject::new);
    }
    
    @Test
    @DisplayName("Тест базовой функциональности получения и возврата объектов")
    void testBasicGetAndReturn() {
        // Получаем все объекты из начального пула
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < INITIAL_CAPACITY; i++) {
            TestObject obj = pool.getFreeObject();
            assertNotNull(obj, "Объект не должен быть null");
            objects.add(obj);
        }
        
        // Проверяем, что пул пуст (но может автоматически расшириться)
        TestObject extraObject = pool.getFreeObject();
        if (extraObject != null) {
            // Если пул автоматически расширился, возвращаем объект
            pool.setFreeObject(extraObject);
        }
        
        // Возвращаем все объекты
        for (TestObject obj : objects) {
            assertTrue(pool.setFreeObject(obj), "Объект должен быть успешно возвращен");
        }
        
        // Проверяем, что все объекты снова доступны
        for (int i = 0; i < INITIAL_CAPACITY; i++) {
            assertNotNull(pool.getFreeObject(), "Объект должен быть доступен после возврата");
        }
    }
    
    @Test
    @DisplayName("Тест автоматического расширения пула")
    void testAutoExpansion() {
        // Получаем все объекты из начального пула
        List<TestObject> initialObjects = new ArrayList<>();
        for (int i = 0; i < INITIAL_CAPACITY; i++) {
            TestObject obj = pool.getFreeObject();
            assertNotNull(obj, "Объект не должен быть null");
            initialObjects.add(obj);
        }
        
        // Проверяем, что пул пуст (но может автоматически расшириться)
        TestObject extraObject = pool.getFreeObject();
        if (extraObject != null) {
            // Если пул автоматически расширился, возвращаем объект
            pool.setFreeObject(extraObject);
        }
        
        // Пытаемся получить еще один объект - должно произойти расширение
        TestObject expandedObject = pool.getFreeObject();
        assertNotNull(expandedObject, "Объект должен быть получен после расширения");
        
        // Проверяем, что емкость увеличилась
        assertTrue(pool.getCapacity() > INITIAL_CAPACITY, "Емкость должна увеличиться");
        
        // Проверяем статистику расширения
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        assertEquals(1, stats.totalExpansions, "Должно произойти одно расширение");
        assertEquals(1, stats.autoExpansionHits, "Должно быть одно обращение к auto expansion");
        
        // Возвращаем все объекты
        for (TestObject obj : initialObjects) {
            assertTrue(pool.setFreeObject(obj), "Объект должен быть успешно возвращен");
        }
        assertTrue(pool.setFreeObject(expandedObject), "Расширенный объект должен быть возвращен");
    }
    
    @Test
    @DisplayName("Тест настройки параметров расширения")
    void testExpansionParameters() {
        // Создаем пул с настраиваемыми параметрами
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> customPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, TestObject::new, 0.5, 200);
        
        assertEquals(10, customPool.getInitialCapacity(), "Начальная емкость должна быть 10");
        assertEquals(0.5, customPool.getExpansionPercentage(), "Процент расширения должен быть 0.5");
        assertEquals(200, customPool.getMaxExpansionPercentage(), "Максимальный процент расширения должен быть 200");
        assertEquals(30, customPool.getMaxAllowedCapacity(), "Максимальная емкость должна быть 30");
    }
    
    @Test
    @DisplayName("Тест ограничения максимального расширения")
    void testMaxExpansionLimit() {
        // Создаем пул с ограниченным расширением
        BitmaskRingBufferUltraVarHandleAutoExpand<TestObject> limitedPool = 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(5, TestObject::new, 0.2, 20); // Максимум 20%
        
        assertEquals(6, limitedPool.getMaxAllowedCapacity(), "Максимальная емкость должна быть 6");
        
        // Получаем все объекты
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            objects.add(limitedPool.getFreeObject());
        }
        
        // Расширяем до максимума
        for (int i = 0; i < 10; i++) {
            TestObject obj = limitedPool.getFreeObject();
            if (obj != null) {
                objects.add(obj);
            }
        }
        
        // Проверяем, что не превысили максимум
        assertTrue(limitedPool.getCapacity() <= limitedPool.getMaxAllowedCapacity(), 
                  "Емкость не должна превышать максимум");
    }
    
    @Test
    @DisplayName("Тест многопоточного доступа")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 4;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulGets = new AtomicInteger(0);
        AtomicInteger successfulReturns = new AtomicInteger(0);
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        TestObject obj = pool.getFreeObject();
                        if (obj != null) {
                            successfulGets.incrementAndGet();
                            // Небольшая задержка для имитации работы
                            Thread.sleep(1);
                            if (pool.setFreeObject(obj)) {
                                successfulReturns.incrementAndGet();
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
        
        latch.await();
        executor.shutdown();
        
        // Проверяем, что все операции были успешными
        assertEquals(threadCount * operationsPerThread, successfulGets.get(), 
                    "Все попытки получения должны быть успешными");
        assertEquals(threadCount * operationsPerThread, successfulReturns.get(), 
                    "Все попытки возврата должны быть успешными");
        
        // Проверяем статистику
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats stats = pool.getStats();
        // При высокой нагрузке могут произойти расширения, но это не обязательно
        System.out.printf("Статистика после многопоточного теста: %s%n", stats);
    }
    
    @Test
    @DisplayName("Тест статистики пула")
    void testPoolStatistics() {
        // Получаем начальную статистику
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats initialStats = pool.getStats();
        assertEquals(INITIAL_CAPACITY, initialStats.capacity, "Начальная емкость должна быть " + INITIAL_CAPACITY);
        assertEquals(INITIAL_CAPACITY, initialStats.freeCount, "Все объекты должны быть свободны");
        assertEquals(0, initialStats.busyCount, "Нет занятых объектов");
        assertEquals(0, initialStats.totalGets, "Нет получений");
        assertEquals(0, initialStats.totalReturns, "Нет возвратов");
        
        // Получаем несколько объектов
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            objects.add(pool.getFreeObject());
        }
        
        // Проверяем статистику после получения
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats afterGetStats = pool.getStats();
        assertEquals(5, afterGetStats.totalGets, "Должно быть 5 получений");
        assertEquals(5, afterGetStats.busyCount, "Должно быть 5 занятых объектов");
        assertEquals(INITIAL_CAPACITY - 5, afterGetStats.freeCount, "Должно быть 5 свободных объектов");
        
        // Возвращаем объекты
        for (TestObject obj : objects) {
            pool.setFreeObject(obj);
        }
        
        // Проверяем статистику после возврата
        BitmaskRingBufferUltraVarHandleAutoExpand.PoolStats afterReturnStats = pool.getStats();
        assertEquals(5, afterReturnStats.totalReturns, "Должно быть 5 возвратов");
        assertEquals(0, afterReturnStats.busyCount, "Нет занятых объектов");
        assertEquals(INITIAL_CAPACITY, afterReturnStats.freeCount, "Все объекты должны быть свободны");
    }
    

    
    @Test
    @DisplayName("Тест очистки ресурсов")
    void testCleanup() {
        // Получаем несколько объектов
        List<TestObject> objects = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            objects.add(pool.getFreeObject());
        }
        
        // Выполняем очистку
        pool.cleanup();
        
        // Проверяем, что пул все еще работает
        TestObject obj = pool.getFreeObject();
        assertNotNull(obj, "Пул должен продолжать работать после очистки");
        
        // Возвращаем объект
        assertTrue(pool.setFreeObject(obj), "Объект должен быть возвращен после очистки");
    }
    
    @Test
    @DisplayName("Тест граничных случаев")
    void testEdgeCases() {
        // Тест с нулевой емкостью
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(0, TestObject::new),
            "Должно быть исключение при нулевой емкости");
        
        // Тест с null фабрикой
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, null),
            "Должно быть исключение при null фабрике");
        
        // Тест с некорректным процентом расширения
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, TestObject::new, -0.1, 100),
            "Должно быть исключение при отрицательном проценте расширения");
        
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, TestObject::new, 1.5, 100),
            "Должно быть исключение при проценте расширения больше 1.0");
        
        // Тест с некорректным максимальным процентом расширения
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, TestObject::new, 0.2, 0),
            "Должно быть исключение при нулевом максимальном проценте расширения");
        
        assertThrows(IllegalArgumentException.class, () -> 
            new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, TestObject::new, 0.2, 1500),
            "Должно быть исключение при максимальном проценте расширения больше 1000");
    }
    
    /**
     * Тестовый объект для пула
     */
    private static class TestObject {
        private final int id;
        private static int nextId = 0;
        
        public TestObject() {
            this.id = nextId++;
        }
        
        public int getId() {
            return id;
        }
        
        @Override
        public String toString() {
            return "TestObject{id=" + id + "}";
        }
    }
} 