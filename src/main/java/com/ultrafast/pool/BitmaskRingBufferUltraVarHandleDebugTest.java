package com.ultrafast.pool;

import java.lang.reflect.Field;

/**
 * Отладочный тест для анализа логики доступности слотов в оригинальном классе
 */
public class BitmaskRingBufferUltraVarHandleDebugTest {
    
    public static void main(String[] args) {
        System.out.println("=== ОТЛАДОЧНЫЙ ТЕСТ BitmaskRingBufferUltraVarHandle ===\n");
        
        // Тест 1: Анализ внутреннего состояния пула
        System.out.println("ТЕСТ 1: Анализ внутреннего состояния пула");
        testInternalStateAnalysis();
        
        // Тест 2: Тест переполнения с очень маленьким пулом
        System.out.println("\nТЕСТ 2: Тест переполнения с очень маленьким пулом");
        testOverflowWithTinyPool();
        
        System.out.println("\nОтладочный тест завершен!");
    }
    
    /**
     * Анализ внутреннего состояния пула
     */
    private static void testInternalStateAnalysis() {
        System.out.println("  - Создаем пул с 2 объектами...");
        
        BitmaskRingBufferUltraVarHandle<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandle<>(2, 
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
        
        // Пытаемся получить третий объект (должен вернуть null)
        HeavyTask task3 = pool.getFreeObject();
        System.out.println("  - После попытки получения третьего объекта:");
        System.out.println("    - Получен объект: " + (task3 != null ? "✓" : "✗ (null)"));
        printPoolState(pool);
        
        // Возвращаем объекты
        pool.setFreeObject(task1);
        pool.setFreeObject(task2);
        
        System.out.println("  - После возврата всех объектов:");
        printPoolState(pool);
        
        pool.cleanup();
    }
    
    /**
     * Тест переполнения с очень маленьким пулом
     */
    private static void testOverflowWithTinyPool() {
        System.out.println("  - Создаем пул с 1 объектом...");
        
        BitmaskRingBufferUltraVarHandle<HeavyTask> pool = 
            new BitmaskRingBufferUltraVarHandle<>(1, 
                () -> new HeavyTask(0, "OverflowDebugTest", 64, 42.0));
        
        // Забираем все доступные объекты
        HeavyTask[] tasks = new HeavyTask[5];
        
        for (int i = 0; i < 5; i++) {
            System.out.println("  - Забираем объект " + (i+1) + ":");
            tasks[i] = pool.getFreeObject();
            System.out.println("    - Получен объект: " + (tasks[i] != null ? "✓" : "✗ (null)"));
            printPoolState(pool);
        }
        
        // Возвращаем все объекты
        System.out.println("  - Возвращаем все объекты:");
        for (int i = 0; i < 5; i++) {
            if (tasks[i] != null) {
                boolean returned = pool.setFreeObject(tasks[i]);
                System.out.println("    - Объект " + (i+1) + " возвращен: " + returned);
                printPoolState(pool);
            }
        }
        
        pool.cleanup();
    }
    
    /**
     * Выводит внутреннее состояние пула
     */
    private static void printPoolState(BitmaskRingBufferUltraVarHandle<HeavyTask> pool) {
        try {
            // Получаем доступ к приватным полям через reflection
            Field capacityField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("capacity");
            Field objectsField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("objects");
            Field availabilityMaskField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("availabilityMask");
            Field tailField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("tail");
            Field stackTopField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("stackTop");
            Field freeSlotStackField = BitmaskRingBufferUltraVarHandle.class.getDeclaredField("freeSlotStack");
            
            capacityField.setAccessible(true);
            objectsField.setAccessible(true);
            availabilityMaskField.setAccessible(true);
            tailField.setAccessible(true);
            stackTopField.setAccessible(true);
            freeSlotStackField.setAccessible(true);
            
            int capacity = (int) capacityField.get(pool);
            Object[] objects = (Object[]) objectsField.get(pool);
            long[] availabilityMask = (long[]) availabilityMaskField.get(pool);
            Object tail = tailField.get(pool);
            Object stackTop = stackTopField.get(pool);
            int[] freeSlotStack = (int[]) freeSlotStackField.get(pool);
            
            // Получаем статистику
            BitmaskRingBufferUltraVarHandle.PoolStats stats = pool.getStats();
            
            System.out.println("    - Емкость: " + capacity);
            System.out.println("    - Tail: " + tail);
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
            
            System.out.println("    - Статистика: получений=" + stats.totalGets + ", возвратов=" + stats.totalReturns + 
                              ", stackHits=" + stats.stackHits + ", bitTrickHits=" + stats.bitTrickHits);
            
        } catch (Exception e) {
            System.err.println("Ошибка при анализе состояния: " + e.getMessage());
        }
    }
} 