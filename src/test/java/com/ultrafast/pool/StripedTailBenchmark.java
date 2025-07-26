package com.ultrafast.pool;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH-бенчмарк для сравнения striped tail оптимизации
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, warmups = 1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
public class StripedTailBenchmark {
    
    @Param({"100", "1000", "10000"})
    private int poolSize;
    
    private BitmaskRingBufferUltraVarHandleOptimized<ProcessTask> optimizedPool;
    private BitmaskRingBufferUltraVarHandleStriped<ProcessTask> stripedPool;
    
    @Setup
    public void setup() {
        optimizedPool = new BitmaskRingBufferUltraVarHandleOptimized<>(poolSize, 
            () -> new ProcessTask("Task-" + System.nanoTime()));
        stripedPool = new BitmaskRingBufferUltraVarHandleStriped<>(poolSize, 
            () -> new ProcessTask("Task-" + System.nanoTime()));
    }
    
    @TearDown
    public void tearDown() {
        optimizedPool.cleanup();
        stripedPool.cleanup();
    }
    
    @Benchmark
    @Threads(1)
    public void optimizedSingleThread(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            optimizedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void stripedSingleThread(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            stripedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void optimizedMultiThread(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            optimizedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void stripedMultiThread(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            stripedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void optimizedHighConcurrency(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            optimizedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void stripedHighConcurrency(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            stripedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(16)
    public void optimizedExtremeConcurrency(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            optimizedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(16)
    public void stripedExtremeConcurrency(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            stripedPool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void optimizedGetOnly(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void stripedGetOnly(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void optimizedGetOnlyMultiThread(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void stripedGetOnlyMultiThread(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void optimizedGetOnlyHighConcurrency(Blackhole bh) {
        ProcessTask task = optimizedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void stripedGetOnlyHighConcurrency(Blackhole bh) {
        ProcessTask task = stripedPool.getFreeObject();
        if (task != null) {
            bh.consume(task);
        }
    }
} 