package com.ultrafast.pool;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.info.GraphLayout;

/**
 * Анализ структуры памяти различных реализаций пулов
 * для понимания причин разного overhead
 */
public class MemoryStructureAnalysis {
    
    public static void main(String[] args) {
        System.out.println("=== Анализ структуры памяти пулов ===\n");
        
        // Анализируем Classic
        System.out.println("--- BitmaskRingBufferClassic ---");
        BitmaskRingBufferClassic<HeavyTask> classic = 
            new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Test", 1024, 42.0), 1000, 50000, 1000);
        
        System.out.println("Classic object layout:");
        System.out.println(ClassLayout.parseInstance(classic).toPrintable());
        System.out.println("Classic total size: " + GraphLayout.parseInstance(classic).totalSize() + " bytes");
        
        // Анализируем UltraVarHandle
        System.out.println("\n--- BitmaskRingBufferUltraVarHandle ---");
        BitmaskRingBufferUltraVarHandle<HeavyTask> ultra = 
            new BitmaskRingBufferUltraVarHandle<>(50000, () -> new HeavyTask(0, "Test", 1024, 42.0));
        
        System.out.println("UltraVarHandle object layout:");
        System.out.println(ClassLayout.parseInstance(ultra).toPrintable());
        System.out.println("UltraVarHandle total size: " + GraphLayout.parseInstance(ultra).totalSize() + " bytes");
        
        // Сравнение
        System.out.println("\n=== Сравнение ===");
        long classicSize = GraphLayout.parseInstance(classic).totalSize();
        long ultraSize = GraphLayout.parseInstance(ultra).totalSize();
        
        System.out.printf("Classic total: %d bytes%n", classicSize);
        System.out.printf("UltraVarHandle total: %d bytes%n", ultraSize);
        System.out.printf("Difference: %d bytes (%.1fx)%n", ultraSize - classicSize, (double)ultraSize / classicSize);
        
        // Очистка
        ultra.cleanup();
    }
} 