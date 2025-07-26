package com.ultrafast.pool;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * Специализированный JMH-бенчмарк для тестирования производительности lock-free stack
 * с ABA-safe оптимизациями.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class LockFreeStackBenchmark {

    @Param({"100", "1000", "10000"})
    private int stackSize;

    private OriginalLockFreeStack originalStack;
    private OptimizedLockFreeStack optimizedStack;
    private Integer[] testData;

    @Setup
    public void setup() {
        originalStack = new OriginalLockFreeStack(stackSize);
        optimizedStack = new OptimizedLockFreeStack(stackSize);
        
        // Подготавливаем тестовые данные
        testData = new Integer[stackSize];
        for (int i = 0; i < stackSize; i++) {
            testData[i] = i;
        }
        
        // Заполняем стеки
        for (int i = 0; i < stackSize; i++) {
            originalStack.push(i);
            optimizedStack.push(i);
        }
    }

    /**
     * Бенчмарк для оригинального lock-free stack - push операция.
     */
    @Benchmark
    @Threads(1)
    public void originalPush(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            originalStack.push(i);
            bh.consume(i);
        }
    }

    /**
     * Бенчмарк для оптимизированного lock-free stack - push операция.
     */
    @Benchmark
    @Threads(1)
    public void optimizedPush(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            optimizedStack.push(i);
            bh.consume(i);
        }
    }

    /**
     * Бенчмарк для оригинального lock-free stack - pop операция.
     */
    @Benchmark
    @Threads(1)
    public void originalPop(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            Integer value = originalStack.pop();
            bh.consume(value);
        }
    }

    /**
     * Бенчмарк для оптимизированного lock-free stack - pop операция.
     */
    @Benchmark
    @Threads(1)
    public void optimizedPop(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            Integer value = optimizedStack.pop();
            bh.consume(value);
        }
    }

    /**
     * Бенчмарк для оригинального lock-free stack - push/pop операции.
     */
    @Benchmark
    @Threads(1)
    public void originalPushPop(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            originalStack.push(i);
            Integer value = originalStack.pop();
            bh.consume(value);
        }
    }

    /**
     * Бенчмарк для оптимизированного lock-free stack - push/pop операции.
     */
    @Benchmark
    @Threads(1)
    public void optimizedPushPop(Blackhole bh) {
        for (int i = 0; i < 100; i++) {
            optimizedStack.push(i);
            Integer value = optimizedStack.pop();
            bh.consume(value);
        }
    }

    /**
     * Бенчмарк для оригинального lock-free stack - конкурентный доступ.
     */
    @Benchmark
    @Threads(4)
    public void originalConcurrent(Blackhole bh) {
        Integer value = originalStack.pop();
        if (value != null) {
            bh.consume(value);
            originalStack.push(value);
        }
    }

    /**
     * Бенчмарк для оптимизированного lock-free stack - конкурентный доступ.
     */
    @Benchmark
    @Threads(4)
    public void optimizedConcurrent(Blackhole bh) {
        Integer value = optimizedStack.pop();
        if (value != null) {
            bh.consume(value);
            optimizedStack.push(value);
        }
    }

    /**
     * Бенчмарк для оригинального lock-free stack - ABA-тест.
     */
    @Benchmark
    @Threads(8)
    public void originalABATest(Blackhole bh) {
        Integer value = originalStack.pop();
        if (value != null) {
            bh.consume(value);
            // Быстрый возврат для создания ABA-сценария
            originalStack.push(value);
        }
    }

    /**
     * Бенчмарк для оптимизированного lock-free stack - ABA-тест.
     */
    @Benchmark
    @Threads(8)
    public void optimizedABATest(Blackhole bh) {
        Integer value = optimizedStack.pop();
        if (value != null) {
            bh.consume(value);
            // Быстрый возврат для создания ABA-сценария
            optimizedStack.push(value);
        }
    }

    /**
     * Оригинальный lock-free stack (из BitmaskRingBufferUltraVarHandle).
     */
    public static class OriginalLockFreeStack {
        private final int[] stack;
        private final AtomicInteger top;
        private final int capacity;

        public OriginalLockFreeStack(int capacity) {
            this.capacity = capacity;
            this.stack = new int[capacity];
            this.top = new AtomicInteger(-1);
        }

        public void push(int value) {
            int currentTop = top.get();
            if (currentTop < capacity - 1) {
                if (top.compareAndSet(currentTop, currentTop + 1)) {
                    stack[currentTop + 1] = value;
                }
            }
        }

        public Integer pop() {
            int currentTop = top.get();
            if (currentTop >= 0) {
                if (top.compareAndSet(currentTop, currentTop - 1)) {
                    return stack[currentTop];
                }
            }
            return null;
        }
    }

    /**
     * Оптимизированный ABA-safe lock-free stack.
     */
    public static class OptimizedLockFreeStack {
        private final int[] stack;
        private final AtomicStampedReference<Integer> top; // ABA-safe: (value, stamp)
        private final int capacity;

        public OptimizedLockFreeStack(int capacity) {
            this.capacity = capacity;
            this.stack = new int[capacity];
            this.top = new AtomicStampedReference<>(-1, 0);
        }

        public void push(int value) {
            int[] stampHolder = new int[1];
            int currentTop = top.get(stampHolder);
            int currentStamp = stampHolder[0];
            
            if (currentTop < capacity - 1) {
                int newTop = currentTop + 1;
                int newStamp = currentStamp + 1;
                
                if (top.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                    stack[newTop] = value;
                }
            }
        }

        public Integer pop() {
            int[] stampHolder = new int[1];
            int currentTop = top.get(stampHolder);
            int currentStamp = stampHolder[0];
            
            if (currentTop >= 0) {
                int newTop = currentTop - 1;
                int newStamp = currentStamp + 1;
                
                if (top.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                    return stack[currentTop];
                }
            }
            return null;
        }
    }

    /**
     * Запуск бенчмарка.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(LockFreeStackBenchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
} 