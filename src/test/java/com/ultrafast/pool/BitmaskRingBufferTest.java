package com.ultrafast.pool;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BitmaskRingBufferTest {
    
    private BitmaskRingBuffer<Task> pool;
    private AtomicInteger taskIdCounter;
    
    @BeforeEach
    void setUp() {
        taskIdCounter = new AtomicInteger(1);
        pool = new BitmaskRingBuffer<>(100, () -> 
            new ProcessTask("TestTask-" + taskIdCounter.getAndIncrement()));
    }
    
    @Test
    void testBasicGetAndReturn() {
        // Получаем объект
        Task task = pool.getFreeObject();
        assertNotNull(task);
        assertTrue(task instanceof ProcessTask);
        
        // Проверяем, что объект помечен как занятый
        List<Task> occupied = pool.getOccupiedObjects();
        assertEquals(1, occupied.size());
        assertEquals(task, occupied.get(0));
        
        // Возвращаем объект
        assertTrue(pool.setFreeObject(task));
        
        // Проверяем, что объект больше не занят
        occupied = pool.getOccupiedObjects();
        assertEquals(0, occupied.size());
    }
    
    @Test
    void testCapacityLimit() {
        int capacity = pool.getStatistics().capacity;
        
        // Получаем все объекты
        Task[] tasks = new Task[capacity];
        for (int i = 0; i < capacity; i++) {
            tasks[i] = pool.getFreeObject();
            assertNotNull(tasks[i]);
        }
        
        // Пытаемся получить еще один - должен вернуть null
        Task extraTask = pool.getFreeObject();
        assertNull(extraTask);
        
        // Возвращаем все объекты
        for (Task task : tasks) {
            assertTrue(pool.setFreeObject(task));
        }
        
        // Теперь снова можем получить объект
        Task newTask = pool.getFreeObject();
        assertNotNull(newTask);
    }
    
    @Test
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successfulGets = new AtomicInteger(0);
        AtomicInteger successfulReturns = new AtomicInteger(0);
        
        // Запускаем потоки
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        Task task = pool.getFreeObject();
                        if (task != null) {
                            successfulGets.incrementAndGet();
                            Thread.sleep(1); // Имитируем работу
                            if (pool.setFreeObject(task)) {
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
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Проверяем, что все операции завершились успешно
        assertEquals(successfulGets.get(), successfulReturns.get());
        assertEquals(0, pool.getOccupiedObjects().size());
    }
    
    @Test
    @Timeout(5)
    void testStaleObjectDetection() throws InterruptedException {
        // Получаем объект
        Task task = pool.getFreeObject();
        assertNotNull(task);
        
        // Ждем немного
        Thread.sleep(100);
        
        // Ищем зависшие объекты старше 50ms
        List<Task> stale = pool.findStaleObjects(50);
        assertEquals(1, stale.size());
        assertEquals(task, stale.get(0));
        
        // Возвращаем объект
        pool.setFreeObject(task);
        
        // Теперь зависших объектов не должно быть
        stale = pool.findStaleObjects(50);
        assertEquals(0, stale.size());
    }
    
    @Test
    void testMarkForUpdate() {
        // Получаем несколько объектов
        Task task1 = pool.getFreeObject();
        Task task2 = pool.getFreeObject();
        Task task3 = pool.getFreeObject();
        
        assertNotNull(task1);
        assertNotNull(task2);
        assertNotNull(task3);
        
        // В упрощенной версии объекты не помечаются автоматически для обновления
        // Проверяем, что объекты можно пометить вручную
        task1.markForUpdate();
        task2.markForUpdate();
        task3.markForUpdate();
        
        assertTrue(task1.needsUpdate());
        assertTrue(task2.needsUpdate());
        assertTrue(task3.needsUpdate());
        
        // Возвращаем объекты
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        pool.setFreeObject(task3);
    }
    
    @Test
    void testStopAllOccupied() {
        // Получаем несколько объектов
        Task task1 = pool.getFreeObject();
        Task task2 = pool.getFreeObject();
        Task task3 = pool.getFreeObject();
        
        assertNotNull(task1);
        assertNotNull(task2);
        assertNotNull(task3);
        
        // Запускаем задачи
        task1.start();
        task2.start();
        task3.start();
        
        assertTrue(task1.isRunning());
        assertTrue(task2.isRunning());
        assertTrue(task3.isRunning());
        
        // Останавливаем все занятые объекты
        pool.stopAllOccupied();
        
        // Проверяем, что все задачи остановлены
        assertFalse(task1.isRunning());
        assertFalse(task2.isRunning());
        assertFalse(task3.isRunning());
        
        // Возвращаем объекты
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        pool.setFreeObject(task3);
    }
    
    @Test
    void testStatistics() {
        BitmaskRingBuffer.PoolStatistics initialStats = pool.getStatistics();
        assertEquals(0, initialStats.occupiedCount);
        assertEquals(0, initialStats.totalGets);
        assertEquals(0, initialStats.totalReturns);
        
        // Получаем объект
        Task task = pool.getFreeObject();
        assertNotNull(task);
        
        BitmaskRingBuffer.PoolStatistics afterGetStats = pool.getStatistics();
        assertEquals(1, afterGetStats.occupiedCount);
        assertEquals(1, afterGetStats.totalGets);
        assertEquals(0, afterGetStats.totalReturns);
        
        // Возвращаем объект
        pool.setFreeObject(task);
        
        BitmaskRingBuffer.PoolStatistics afterReturnStats = pool.getStatistics();
        assertEquals(0, afterReturnStats.occupiedCount);
        assertEquals(1, afterReturnStats.totalGets);
        assertEquals(1, afterReturnStats.totalReturns);
    }
    
    @Test
    void testPowerOfTwoCapacity() {
        // Тестируем, что емкость всегда степень 2
        BitmaskRingBuffer<Task> pool100 = new BitmaskRingBuffer<>(100, () -> 
            new ProcessTask("Test"));
        assertEquals(128, pool100.getStatistics().capacity); // 2^7
        
        BitmaskRingBuffer<Task> pool1000 = new BitmaskRingBuffer<>(1000, () -> 
            new ProcessTask("Test"));
        assertEquals(1024, pool1000.getStatistics().capacity); // 2^10
        
        BitmaskRingBuffer<Task> pool16384 = new BitmaskRingBuffer<>(16384, () -> 
            new ProcessTask("Test"));
        assertEquals(16384, pool16384.getStatistics().capacity); // 2^14
    }
} 