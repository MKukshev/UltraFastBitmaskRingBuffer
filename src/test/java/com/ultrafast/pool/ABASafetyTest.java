package com.ultrafast.pool;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Тест для проверки корректности ABA-safe реализации lock-free stack.
 */
@DisplayName("ABA Safety Tests")
public class ABASafetyTest {

    private static final int CAPACITY = 1000;
    private static final int THREAD_COUNT = 8;
    private static final int OPERATIONS_PER_THREAD = 10000;

    private BitmaskRingBufferUltraVarHandle<TestObject> originalPool;
    private BitmaskRingBufferUltraVarHandleOptimized<TestObject> optimizedPool;

    @BeforeEach
    void setUp() {
        originalPool = new BitmaskRingBufferUltraVarHandle<>(CAPACITY, TestObject::new);
        optimizedPool = new BitmaskRingBufferUltraVarHandleOptimized<>(CAPACITY, TestObject::new);
    }

    @Test
    @DisplayName("Тест ABA-безопасности оригинального stack")
    void testOriginalABASafety() throws InterruptedException {
        testABASafety(originalPool, "Original");
    }

    @Test
    @DisplayName("Тест ABA-безопасности оптимизированного stack")
    void testOptimizedABASafety() throws InterruptedException {
        testABASafety(optimizedPool, "Optimized");
    }

    @Test
    @DisplayName("Сравнительный тест производительности при ABA-сценариях")
    void testABAPerformanceComparison() throws InterruptedException {
        long originalTime = measureABAPerformance(originalPool);
        long optimizedTime = measureABAPerformance(optimizedPool);

        System.out.println("Original ABA performance: " + originalTime + "ms");
        System.out.println("Optimized ABA performance: " + optimizedTime + "ms");
        System.out.println("Performance improvement: " + 
            String.format("%.2f%%", ((double)(originalTime - optimizedTime) / originalTime) * 100));
    }

    @Test
    @DisplayName("Тест корректности при высокой конкуренции")
    void testHighContentionCorrectness() throws InterruptedException {
        testHighContention(originalPool, "Original");
        testHighContention(optimizedPool, "Optimized");
    }

    @Test
    @DisplayName("Тест статистики при ABA-сценариях")
    void testStatsUnderABA() throws InterruptedException {
        // Запускаем ABA-тест
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        TestObject obj = originalPool.getFreeObject();
                        if (obj != null) {
                            originalPool.setFreeObject(obj);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Проверяем статистику
        BitmaskRingBufferUltraVarHandle.PoolStats originalStats = originalPool.getStats();
        System.out.println("Original stats under ABA: " + originalStats);

        // Проверяем, что все объекты вернулись в пул
        assertEquals(CAPACITY, originalStats.freeCount);
        assertEquals(0, originalStats.busyCount);
    }

    private void testABASafety(Object pool, String poolName) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        Set<TestObject> borrowedObjects = ConcurrentHashMap.newKeySet();
        AtomicInteger successfulOperations = new AtomicInteger(0);
        AtomicInteger failedOperations = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        TestObject obj = getObjectFromPool(pool);
                        if (obj != null) {
                            borrowedObjects.add(obj);
                            successfulOperations.incrementAndGet();
                            
                            // Имитируем ABA-сценарий: быстро возвращаем объект
                            if (returnObjectToPool(pool, obj)) {
                                borrowedObjects.remove(obj);
                            } else {
                                failedOperations.incrementAndGet();
                            }
                        } else {
                            failedOperations.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Проверяем, что все объекты вернулись в пул
        assertEquals(0, borrowedObjects.size(), 
            poolName + ": Все объекты должны быть возвращены в пул");

        // Проверяем статистику
        Object stats = getStatsFromPool(pool);
        if (stats instanceof BitmaskRingBufferUltraVarHandle.PoolStats) {
            BitmaskRingBufferUltraVarHandle.PoolStats originalStats = (BitmaskRingBufferUltraVarHandle.PoolStats) stats;
            assertEquals(CAPACITY, originalStats.freeCount, 
                poolName + ": Все объекты должны быть свободны");
            assertEquals(0, originalStats.busyCount, 
                poolName + ": Не должно быть занятых объектов");
        } else if (stats instanceof BitmaskRingBufferUltraVarHandleOptimized.PoolStats) {
            BitmaskRingBufferUltraVarHandleOptimized.PoolStats optimizedStats = (BitmaskRingBufferUltraVarHandleOptimized.PoolStats) stats;
            assertEquals(CAPACITY, optimizedStats.freeCount, 
                poolName + ": Все объекты должны быть свободны");
            assertEquals(0, optimizedStats.busyCount, 
                poolName + ": Не должно быть занятых объектов");
        }

        System.out.println(poolName + " - Successful operations: " + successfulOperations.get());
        System.out.println(poolName + " - Failed operations: " + failedOperations.get());
        System.out.println(poolName + " - Final stats: " + stats);
    }

    private long measureABAPerformance(Object pool) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        TestObject obj = getObjectFromPool(pool);
                        if (obj != null) {
                            returnObjectToPool(pool, obj);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        return System.currentTimeMillis() - startTime;
    }

    private void testHighContention(Object pool, String poolName) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT * 2);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT * 2);
        Set<TestObject> activeObjects = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < THREAD_COUNT * 2; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < OPERATIONS_PER_THREAD / 2; j++) {
                        TestObject obj = getObjectFromPool(pool);
                        if (obj != null) {
                            activeObjects.add(obj);
                            
                            // Имитируем работу с объектом
                            obj.setValue(j);
                            
                            // Возвращаем объект
                            if (returnObjectToPool(pool, obj)) {
                                activeObjects.remove(obj);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Проверяем, что все объекты вернулись в пул
        assertEquals(0, activeObjects.size(), 
            poolName + ": Все объекты должны быть возвращены в пул при высокой конкуренции");

        Object stats = getStatsFromPool(pool);
        if (stats instanceof BitmaskRingBufferUltraVarHandle.PoolStats) {
            BitmaskRingBufferUltraVarHandle.PoolStats originalStats = (BitmaskRingBufferUltraVarHandle.PoolStats) stats;
            assertEquals(CAPACITY, originalStats.freeCount, 
                poolName + ": Все объекты должны быть свободны при высокой конкуренции");
        } else if (stats instanceof BitmaskRingBufferUltraVarHandleOptimized.PoolStats) {
            BitmaskRingBufferUltraVarHandleOptimized.PoolStats optimizedStats = (BitmaskRingBufferUltraVarHandleOptimized.PoolStats) stats;
            assertEquals(CAPACITY, optimizedStats.freeCount, 
                poolName + ": Все объекты должны быть свободны при высокой конкуренции");
        }
    }

    @SuppressWarnings("unchecked")
    private TestObject getObjectFromPool(Object pool) {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<TestObject>) pool).getFreeObject();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleOptimized) {
            return ((BitmaskRingBufferUltraVarHandleOptimized<TestObject>) pool).getFreeObject();
        }
        throw new IllegalArgumentException("Unknown pool type");
    }

    @SuppressWarnings("unchecked")
    private boolean returnObjectToPool(Object pool, TestObject obj) {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<TestObject>) pool).setFreeObject(obj);
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleOptimized) {
            return ((BitmaskRingBufferUltraVarHandleOptimized<TestObject>) pool).setFreeObject(obj);
        }
        throw new IllegalArgumentException("Unknown pool type");
    }

    private Object getStatsFromPool(Object pool) {
        if (pool instanceof BitmaskRingBufferUltraVarHandle) {
            return ((BitmaskRingBufferUltraVarHandle<?>) pool).getStats();
        } else if (pool instanceof BitmaskRingBufferUltraVarHandleOptimized) {
            return ((BitmaskRingBufferUltraVarHandleOptimized<?>) pool).getStats();
        }
        throw new IllegalArgumentException("Unknown pool type");
    }

    /**
     * Тестовый объект для проверки ABA-безопасности.
     */
    public static class TestObject {
        private final int id;
        private volatile int value;
        private volatile long lastAccessTime;

        public TestObject() {
            this.id = System.identityHashCode(this);
            this.value = 0;
            this.lastAccessTime = System.nanoTime();
        }

        public int getId() {
            return id;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
            this.lastAccessTime = System.nanoTime();
        }

        public long getLastAccessTime() {
            return lastAccessTime;
        }

        @Override
        public String toString() {
            return "TestObject{id=" + id + ", value=" + value + ", lastAccess=" + lastAccessTime + "}";
        }
    }

} 