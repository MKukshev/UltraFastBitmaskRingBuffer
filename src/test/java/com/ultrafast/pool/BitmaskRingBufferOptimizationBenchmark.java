package com.ultrafast.pool;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH-бенчмарк для сравнения производительности оригинальной и оптимизированной версий
 * BitmaskRingBuffer с ABA-safe lock-free stack.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class BitmaskRingBufferOptimizationBenchmark {

    @Param({"100", "1000", "10000"})
    private int capacity;

    @Param({"1", "2", "4", "8"})
    private int threadCount;

    private BitmaskRingBufferUltraVarHandle<TestObject> originalPool;
    private BitmaskRingBufferUltraVarHandleOptimized<TestObject> optimizedPool;
    private TestObject[] borrowedObjects;

    @Setup
    public void setup() {
        // Инициализация оригинального пула
        originalPool = new BitmaskRingBufferUltraVarHandle<>(
            capacity, 
            TestObject::new
        );

        // Инициализация оптимизированного пула
        optimizedPool = new BitmaskRingBufferUltraVarHandleOptimized<>(
            capacity, 
            TestObject::new
        );

        // Массив для хранения заимствованных объектов
        borrowedObjects = new TestObject[capacity];
    }

    @TearDown
    public void tearDown() {
        if (originalPool != null) {
            originalPool.cleanup();
        }
        if (optimizedPool != null) {
            optimizedPool.cleanup();
        }
    }

    /**
     * Бенчмарк для оригинальной версии - только получение объектов.
     */
    @Benchmark
    @Threads(1)
    public void originalGetOnly(Blackhole bh) {
        TestObject obj = originalPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            originalPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - только получение объектов.
     */
    @Benchmark
    @Threads(1)
    public void optimizedGetOnly(Blackhole bh) {
        TestObject obj = optimizedPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            optimizedPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оригинальной версии - получение и возврат объектов.
     */
    @Benchmark
    @Threads(1)
    public void originalGetAndReturn(Blackhole bh) {
        TestObject obj = originalPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            originalPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - получение и возврат объектов.
     */
    @Benchmark
    @Threads(1)
    public void optimizedGetAndReturn(Blackhole bh) {
        TestObject obj = optimizedPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            optimizedPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оригинальной версии - конкурентный доступ.
     */
    @Benchmark
    @Threads(4)
    public void originalConcurrent(Blackhole bh) {
        TestObject obj = originalPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            originalPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - конкурентный доступ.
     */
    @Benchmark
    @Threads(4)
    public void optimizedConcurrent(Blackhole bh) {
        TestObject obj = optimizedPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            optimizedPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оригинальной версии - стресс-тест с удержанием объектов.
     */
    @Benchmark
    @Threads(2)
    public void originalStressTest(Blackhole bh) {
        // Заимствуем несколько объектов
        for (int i = 0; i < Math.min(10, capacity / 10); i++) {
            TestObject obj = originalPool.getFreeObject();
            if (obj != null) {
                borrowedObjects[i] = obj;
                bh.consume(obj);
            }
        }

        // Возвращаем объекты
        for (int i = 0; i < Math.min(10, capacity / 10); i++) {
            if (borrowedObjects[i] != null) {
                originalPool.setFreeObject(borrowedObjects[i]);
                borrowedObjects[i] = null;
            }
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - стресс-тест с удержанием объектов.
     */
    @Benchmark
    @Threads(2)
    public void optimizedStressTest(Blackhole bh) {
        // Заимствуем несколько объектов
        for (int i = 0; i < Math.min(10, capacity / 10); i++) {
            TestObject obj = optimizedPool.getFreeObject();
            if (obj != null) {
                borrowedObjects[i] = obj;
                bh.consume(obj);
            }
        }

        // Возвращаем объекты
        for (int i = 0; i < Math.min(10, capacity / 10); i++) {
            if (borrowedObjects[i] != null) {
                optimizedPool.setFreeObject(borrowedObjects[i]);
                borrowedObjects[i] = null;
            }
        }
    }

    /**
     * Бенчмарк для оригинальной версии - тест ABA-проблемы.
     */
    @Benchmark
    @Threads(8)
    public void originalABATest(Blackhole bh) {
        TestObject obj = originalPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            // Быстрый возврат для создания ABA-сценария
            originalPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - тест ABA-проблемы.
     */
    @Benchmark
    @Threads(8)
    public void optimizedABATest(Blackhole bh) {
        TestObject obj = optimizedPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            // Быстрый возврат для создания ABA-сценария
            optimizedPool.setFreeObject(obj);
        }
    }

    /**
     * Бенчмарк для оригинальной версии - тест статистики.
     */
    @Benchmark
    @Threads(1)
    public void originalStatsTest(Blackhole bh) {
        TestObject obj = originalPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            originalPool.setFreeObject(obj);
            
            // Получаем статистику
            BitmaskRingBufferUltraVarHandle.PoolStats stats = originalPool.getStats();
            bh.consume(stats);
        }
    }

    /**
     * Бенчмарк для оптимизированной версии - тест статистики.
     */
    @Benchmark
    @Threads(1)
    public void optimizedStatsTest(Blackhole bh) {
        TestObject obj = optimizedPool.getFreeObject();
        if (obj != null) {
            bh.consume(obj);
            optimizedPool.setFreeObject(obj);
            
            // Получаем статистику
            BitmaskRingBufferUltraVarHandleOptimized.PoolStats stats = optimizedPool.getStats();
            bh.consume(stats);
        }
    }

    /**
     * Простой тестовый объект для бенчмарков.
     */
    public static class TestObject {
        private final int id;
        private final long timestamp;
        private volatile boolean active = true;

        public TestObject() {
            this.id = System.identityHashCode(this);
            this.timestamp = System.nanoTime();
        }

        public int getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        @Override
        public String toString() {
            return "TestObject{id=" + id + ", timestamp=" + timestamp + ", active=" + active + "}";
        }
    }

    /**
     * Запуск бенчмарка.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(BitmaskRingBufferOptimizationBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
} 