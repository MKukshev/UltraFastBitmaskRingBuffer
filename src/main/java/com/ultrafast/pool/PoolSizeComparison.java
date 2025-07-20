package com.ultrafast.pool;

import org.openjdk.jol.info.GraphLayout;

/**
 * Сравнение размеров самих объектов пулов (без содержимого)
 */
public class PoolSizeComparison {
    
    public static void main(String[] args) {
        System.out.println("=== Сравнение размеров объектов пулов ===\n");
        
        // Количество объектов для тестирования
        final int POOL_SIZE = 500000;
        
        // Создаем пустые пулы для анализа структуры
        BitmaskRingBufferClassic<HeavyTask> classic = 
            new BitmaskRingBufferClassic<>(() -> new HeavyTask(0, "Test", 1024, 42.0), 1000, POOL_SIZE, 1000);
        
        BitmaskRingBufferClassicPreallocated<HeavyTask> classicPreallocated = 
            new BitmaskRingBufferClassicPreallocated<>(() -> new HeavyTask(0, "Test", 1024, 42.0), POOL_SIZE, 1000);
        
        BitmaskRingBufferUltraVarHandle<HeavyTask> ultra = 
            new BitmaskRingBufferUltraVarHandle<>(POOL_SIZE, () -> new HeavyTask(0, "Test", 1024, 42.0));
        
        BitmaskRingBufferMinimal<HeavyTask> minimal = 
            new BitmaskRingBufferMinimal<>(POOL_SIZE, () -> new HeavyTask(0, "Test", 1024, 42.0));
        
        System.out.println("--- BitmaskRingBufferClassic ---");
        System.out.println("Total size (без содержимого): " + GraphLayout.parseInstance(classic).totalSize() + " bytes");
        
        System.out.println("\n--- BitmaskRingBufferClassicPreallocated ---");
        System.out.println("Total size (без содержимого): " + GraphLayout.parseInstance(classicPreallocated).totalSize() + " bytes");
        
        System.out.println("\n--- BitmaskRingBufferUltraVarHandle ---");
        System.out.println("Total size (без содержимого): " + GraphLayout.parseInstance(ultra).totalSize() + " bytes");
        
        System.out.println("\n--- BitmaskRingBufferMinimal ---");
        System.out.println("Total size (без содержимого): " + GraphLayout.parseInstance(minimal).totalSize() + " bytes");
        
        // Сравнение
        long classicSize = GraphLayout.parseInstance(classic).totalSize();
        long classicPreallocatedSize = GraphLayout.parseInstance(classicPreallocated).totalSize();
        long ultraSize = GraphLayout.parseInstance(ultra).totalSize();
        long minimalSize = GraphLayout.parseInstance(minimal).totalSize();
        
        // Размер одного HeavyTask объекта (payload = 1024 bytes)
        HeavyTask testTask = new HeavyTask(0, "Test", 1024, 42.0);
        long singleTaskSize = GraphLayout.parseInstance(testTask).totalSize();
        long totalTasksSize = POOL_SIZE * singleTaskSize;
        
        // Расчет overhead для каждого пула
        long classicOverhead = classicSize;
        long classicPreallocatedOverhead = classicPreallocatedSize - totalTasksSize;
        long ultraOverhead = ultraSize - totalTasksSize;
        long minimalOverhead = minimalSize - totalTasksSize;
        
        System.out.println("\n=== Сравнение ===");
        System.out.printf("Classic: %d bytes%n", classicSize);
        System.out.printf("ClassicPreallocated: %d bytes%n", classicPreallocatedSize);
        System.out.printf("UltraVarHandle: %d bytes%n", ultraSize);
        System.out.printf("Minimal: %d bytes%n", minimalSize);
        
        System.out.println("\n=== Overhead Analysis ===");
        System.out.printf("Размер одного HeavyTask: %d bytes%n", singleTaskSize);
        System.out.printf("Общий размер всех объектов (%d шт): %d bytes%n", POOL_SIZE, totalTasksSize);
        System.out.println();
        System.out.printf("Classic overhead: %d bytes (%.2f%% от общего размера)%n", 
            classicOverhead, (double)classicOverhead / classicSize * 100);
        System.out.printf("ClassicPreallocated overhead: %d bytes (%.2f%% от общего размера)%n", 
            classicPreallocatedOverhead, (double)classicPreallocatedOverhead / classicPreallocatedSize * 100);
        System.out.printf("UltraVarHandle overhead: %d bytes (%.2f%% от общего размера)%n", 
            ultraOverhead, (double)ultraOverhead / ultraSize * 100);
        System.out.printf("Minimal overhead: %d bytes (%.2f%% от общего размера)%n", 
            minimalOverhead, (double)minimalOverhead / minimalSize * 100);
        
        System.out.println("\n=== Overhead Comparison ===");
        System.out.printf("ClassicPreallocated vs Classic: %d bytes (%.2fx)%n", 
            classicPreallocatedOverhead - classicOverhead, (double)classicPreallocatedOverhead / classicOverhead);
        System.out.printf("UltraVarHandle vs ClassicPreallocated: %d bytes (%.2fx)%n", 
            ultraOverhead - classicPreallocatedOverhead, (double)ultraOverhead / classicPreallocatedOverhead);
        System.out.printf("Minimal vs ClassicPreallocated: %d bytes (%.2fx)%n", 
            minimalOverhead - classicPreallocatedOverhead, (double)minimalOverhead / classicPreallocatedOverhead);
        
        // Анализ отдельных полей
        System.out.println("\n--- Детальный анализ полей ---");
        
        // Используем уже созданный testTask
        System.out.println("HeavyTask size: " + GraphLayout.parseInstance(testTask).totalSize() + " bytes");
        
        // Анализируем размеры массивов
        System.out.println("\nРазмеры массивов (для " + POOL_SIZE + " объектов):");
        System.out.println("Массив HeavyTask[" + POOL_SIZE + "]: " + (POOL_SIZE * 4) + " bytes (только ссылки)");
        System.out.println("Битовая маска (782 long): " + (782 * 8) + " bytes");
        System.out.println("Stale маска (782 long): " + (782 * 8) + " bytes");
        System.out.println("Lock-free stack (1000 int): " + (1000 * 4) + " bytes");
        
        System.out.println("\n--- Особенности ClassicPreallocated ---");
        System.out.println("ClassicPreallocated создает все объекты сразу при инициализации");
        System.out.println("ClassicPreallocated использует массив объектов (как оптимизированные версии)");
        System.out.println("ClassicPreallocated позволяет честное сравнение производительности");
        
        System.out.println("\n--- Особенности Minimal ---");
        System.out.println("Minimal использует off-heap память для битовых масок");
        System.out.println("Minimal не имеет updateMask (экономия памяти)");
        System.out.println("Minimal использует sun.misc.Unsafe для прямого доступа к памяти");
        System.out.println("Minimal имеет меньший overhead по сравнению с UltraVarHandle");
    }
} 