package com.ultrafast.pool;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
public class BitmaskRingBufferBenchmark {
    
    @Param({"1024", "8192", "16384"})
    private int capacity;
    
    private BitmaskRingBuffer<Task> pool;
    private AtomicInteger taskIdCounter;
    
    @Setup
    public void setup() {
        taskIdCounter = new AtomicInteger(1);
        pool = new BitmaskRingBuffer<>(capacity, () -> 
            new ProcessTask("BenchmarkTask-" + taskIdCounter.getAndIncrement()));
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadGetReturn(Blackhole bh) {
        Task task = pool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(4)
    public void fourThreadsGetReturn(Blackhole bh) {
        Task task = pool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(8)
    public void eightThreadsGetReturn(Blackhole bh) {
        Task task = pool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(16)
    public void sixteenThreadsGetReturn(Blackhole bh) {
        Task task = pool.getFreeObject();
        if (task != null) {
            bh.consume(task);
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadGetOnly(Blackhole bh) {
        Task task = pool.getFreeObject();
        bh.consume(task);
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadReturnOnly(Blackhole bh) {
        // Предварительно получаем объект
        Task task = pool.getFreeObject();
        if (task != null) {
            bh.consume(pool.setFreeObject(task));
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadGetOccupiedObjects(Blackhole bh) {
        // Получаем несколько объектов
        for (int i = 0; i < 10; i++) {
            Task task = pool.getFreeObject();
            bh.consume(task);
        }
        
        // Измеряем получение списка занятых объектов
        List<Task> occupied = pool.getOccupiedObjects();
        bh.consume(occupied);
        
        // Возвращаем объекты
        for (Task task : occupied) {
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadFindStaleObjects(Blackhole bh) {
        // Получаем несколько объектов
        for (int i = 0; i < 10; i++) {
            Task task = pool.getFreeObject();
            bh.consume(task);
        }
        
        // Измеряем поиск зависших объектов
        List<Task> stale = pool.findStaleObjects(1000);
        bh.consume(stale);
        
        // Возвращаем объекты
        List<Task> occupied = pool.getOccupiedObjects();
        for (Task task : occupied) {
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadMarkForUpdate(Blackhole bh) {
        // Получаем несколько объектов
        for (int i = 0; i < 10; i++) {
            Task task = pool.getFreeObject();
            bh.consume(task);
        }

        
        // Возвращаем объекты
        List<Task> occupied = pool.getOccupiedObjects();
        for (Task task : occupied) {
            pool.setFreeObject(task);
        }
    }
    
    @Benchmark
    @Threads(1)
    public void singleThreadGetStatistics(Blackhole bh) {
        // Получаем несколько объектов
        for (int i = 0; i < 10; i++) {
            Task task = pool.getFreeObject();
            bh.consume(task);
        }
        
        // Измеряем получение статистики
        BitmaskRingBuffer.PoolStatistics stats = pool.getStatistics();
        bh.consume(stats);
        
        // Возвращаем объекты
        List<Task> occupied = pool.getOccupiedObjects();
        for (Task task : occupied) {
            pool.setFreeObject(task);
        }
    }
} 