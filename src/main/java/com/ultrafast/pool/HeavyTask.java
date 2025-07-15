package com.ultrafast.pool;

/**
 * Класс для имитации "тяжелого" объекта в пуле.
 * Содержит массив байт, строку, int и double.
 */
public class HeavyTask {
    private final byte[] payload;
    private final String name;
    private final int id;
    private final double value;

    public HeavyTask(int id, String name, int payloadSize, double value) {
        this.id = id;
        this.name = name;
        this.payload = new byte[payloadSize];
        this.value = value;
        // Заполняем массив для имитации реальных данных
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public byte[] getPayload() { return payload; }
    public double getValue() { return value; }
} 