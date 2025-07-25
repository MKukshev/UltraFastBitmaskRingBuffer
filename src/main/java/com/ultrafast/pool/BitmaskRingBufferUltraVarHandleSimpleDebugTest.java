package com.ultrafast.pool;

import java.lang.reflect.Field;

/**
 * Отладочный тест для анализа внутреннего состояния пула
 */
public class BitmaskRingBufferUltraVarHandleSimpleDebugTest {
    
    public static void main(String[] args) {
        System.out.println("=== ОТЛАДОЧНЫЙ ТЕСТ BitmaskRingBufferUltraVarHandleSimple ===\n");
        
        // Тест 1: Анализ внутреннего состояния пула
        System.out.println("ТЕСТ 1: Анализ внутреннего состояния пула");
        testInternalStateAnalysis();
        
        // Тест 2: Принудительное создание ситуации с переполнением
        System.out.println("\nТЕСТ 2: Принудительное создание ситуации с переполнением");
        testForcedOverflow();
        
        System.out.println("\nОтладочный тест завершен!");
    }
    
    /**
     * Анализ внутреннего состояния пула
     */
    private static void testInternalStateAnalysis() {
        System.out.println("  - Создаем пул с 2 объектами...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(2, 
                () -> new HeavyTask(0, "DebugTest", 64, 42.0));
        
        // Анализируем начальное состояние
        System.out.println("  - Начальное состояние:");
        printPoolState(pool);
        
        // Забираем первый объект
        HeavyTask task1 = pool.getFreeObject();
        System.out.println("  - После получения первого объекта:");
        printPoolState(pool);
        
        // Забираем второй объект
        HeavyTask task2 = pool.getFreeObject();
        System.out.println("  - После получения второго объекта:");
        printPoolState(pool);
        
        // Пытаемся получить третий объект (должен быть создан через фабрику)
        HeavyTask task3 = pool.getFreeObject();
        System.out.println("  - После получения третьего объекта:");
        printPoolState(pool);
        
        // Возвращаем объекты
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        pool.setFreeObject(task3);
        
        System.out.println("  - После возврата всех объектов:");
        printPoolState(pool);
        
        pool.cleanup();
    }
    
    /**
     * Принудительное создание ситуации с переполнением
     */
    private static void testForcedOverflow() {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandleSimple<>(1, 
                () -> new HeavyTask(0, "OverflowDebugTest", 64, 42.0));
        
        // Забираем все доступные объекты
        HeavyTask[] tasks = new HeavyTask[5];
        
        for (int i = 0; i < 5; i++) {
            System.out.println("  - Забираем объект " + (i+1) + ":");
            tasks[i] = pool.getFreeObject();
            printPoolState(pool);
        }
        
        // Возвращаем все объекты
        System.out.println("  - Возвращаем все объекты:");
        for (int i = 0; i < 5; i++) {
            boolean returned = pool.setFreeObject(tasks[i]);
            System.out.println("    - Объект " + (i+1) + " возвращен: " + returned);
            printPoolState(pool);
        }
        
        pool.cleanup();
    }
    
    /**
     * Выводит внутреннее состояние пула
     */
    private static void printPoolState(BitmaskRingBufferUltraVarHandleSimple<HeavyTask> pool) {
        try {
            // Получаем доступ к приватным полям через reflection
            Field capacityField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("capacity");
            Field objectsField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("objects");
            Field availabilityMaskField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("availabilityMask");
            Field headField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("head");
            Field stackTopField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("stackTop");
            Field freeSlotStackField = BitmaskRingBufferUltraVarHandleSimple.class.getDeclaredField("freeSlotStack");
            
            capacityField.setAccessible(true);
            objectsField.setAccessible(true);
            availabilityMaskField.setAccessible(true);
            headField.setAccessible(true);
            stackTopField.setAccessible(true);
            freeSlotStackField.setAccessible(true);
            
            int capacity = (int) capacityField.get(pool);
            Object[] objects = (Object[]) objectsField.get(pool);
            long[] availabilityMask = (long[]) availabilityMaskField.get(pool);
            Object head = headField.get(pool);
            Object stackTop = stackTopField.get(pool);
            int[] freeSlotStack = (int[]) freeSlotStackField.get(pool);
            
            // Получаем статистику
            BitmaskRingBufferUltraVarHandleSimple.SimplePoolStats stats = pool.getStats();
            
            System.out.println("    - Емкость: " + capacity);
            System.out.println("    - Head: " + head);
            System.out.println("    - Stack top: " + stackTop);
            System.out.println("    - Stack: " + java.util.Arrays.toString(freeSlotStack));
            
            // Анализируем маску доступности
            System.out.println("    - Маска доступности:");
            for (int i = 0; i < availabilityMask.length; i++) {
                System.out.println("      [" + i + "]: " + Long.toBinaryString(availabilityMask[i]));
            }
            
            // Анализируем объекты
            System.out.println("    - Объекты:");
            for (int i = 0; i < Math.min(capacity, 10); i++) {
                boolean isAvailable = pool.isAvailable(i);
                System.out.println("      [" + i + "]: " + (objects[i] != null ? "✓" : "✗") + " (доступен: " + isAvailable + ")");
            }
            
            System.out.println("    - Статистика: создано=" + stats.totalCreates + ", отброшено=" + stats.totalDrops + 
                              ", получений=" + stats.totalGets + ", возвратов=" + stats.totalReturns);
            
        } catch (Exception e) {
            System.err.println("Ошибка при анализе состояния: " + e.getMessage());
        }
    }
} 