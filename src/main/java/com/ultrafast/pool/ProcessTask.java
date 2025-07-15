package com.ultrafast.pool;

import java.util.concurrent.atomic.AtomicLong;

public class ProcessTask implements Task {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);
    private final long id;
    private final String name;
    private volatile boolean running = false;
    private volatile boolean needsUpdate = false;
    private volatile long lastUsedTime;

    public ProcessTask(String name) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.name = name;
        this.lastUsedTime = System.currentTimeMillis();
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void start() {
        running = true;
        lastUsedTime = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        running = false;
        lastUsedTime = System.currentTimeMillis();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean needsUpdate() {
        return needsUpdate;
    }

    @Override
    public void markForUpdate() {
        needsUpdate = true;
    }

    @Override
    public long getLastUsedTime() {
        return lastUsedTime;
    }

    @Override
    public String getStatus() {
        return name + " [id=" + id + "] " + (running ? "[RUNNING]" : "[STOPPED]");
    }

    public String getProcessName() {
        return name;
    }
} 