package com.ultrafast.pool;

/**
 * Простой тест для проверки работы BitmaskRingBufferUltraVarHandleStripedOffHeap с off-heap padding
 */
public class OffHeapPaddingTest {
    
    public static void main(String[] args) {
        System.out.println("=== Тест Off-Heap Padding ===");
        
        // Создаем пул
        BitmaskRingBufferUltraVarHandleStripedOffHeap<ProcessTask> pool = 
            new BitmaskRingBufferUltraVarHandleStripedOffHeap<>(1000, () -> new ProcessTask("Task"));
        
        try {
            // Получаем статистику
            BitmaskRingBufferUltraVarHandleStripedOffHeap.PoolStats stats = pool.getStats();
            System.out.println("Начальная статистика: " + stats);
            
            // Тестируем получение объектов
            ProcessTask task1 = pool.getFreeObject();
            ProcessTask task2 = pool.getFreeObject();
            ProcessTask task3 = pool.getFreeObject();
            
            System.out.println("Получено объектов: " + (task1 != null ? 1 : 0) + 
                             ", " + (task2 != null ? 1 : 0) + 
                             ", " + (task3 != null ? 1 : 0));
            
            // Тестируем возврат объектов
            if (task1 != null) {
                pool.setFreeObject(task1);
                System.out.println("Объект 1 возвращен в пул");
            }
            if (task2 != null) {
                pool.setFreeObject(task2);
                System.out.println("Объект 2 возвращен в пул");
            }
            if (task3 != null) {
                pool.setFreeObject(task3);
                System.out.println("Объект 3 возвращен в пул");
            }
            
            // Получаем финальную статистику
            stats = pool.getStats();
            System.out.println("Финальная статистика: " + stats);
            
            System.out.println("Тест завершен успешно!");
            
        } catch (Exception e) {
            System.err.println("Ошибка в тесте: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Очищаем ресурсы
            pool.cleanup();
        }
    }
} 