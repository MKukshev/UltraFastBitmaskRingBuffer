package com.ultrafast.pool;

/**
 * Отладочный тест для понимания проблемы с off-heap версией
 */
public class OffHeapStatsDebugTest {
    
    public static void main(String[] args) {
        System.out.println("=== Отладочный тест Off-heap статистики ===");
        
        try {
            // Создаем пул
            BitmaskRingBufferUltraVarHandleStripedOffHeap<ProcessTask> pool = 
                new BitmaskRingBufferUltraVarHandleStripedOffHeap<>(5, () -> new ProcessTask("TestTask"));
            
            System.out.println("Пул создан успешно");
            System.out.println("Емкость пула: " + pool.getCapacity());
            
            // Проверяем доступность слотов
            System.out.println("\n=== Проверка доступности слотов ===");
            for (int i = 0; i < pool.getCapacity(); i++) {
                boolean available = pool.isAvailable(i);
                ProcessTask obj = pool.getObject(i);
                System.out.printf("Слот %d: доступен=%s, объект=%s%n", i, available, obj != null ? obj.getStatus() : "null");
            }
            
            // Получаем статистику
            BitmaskRingBufferUltraVarHandleStripedOffHeap.PoolStats stats = pool.getStats();
            System.out.println("\nНачальная статистика: " + stats);
            
            // Тестируем получение объектов
            System.out.println("\n=== Тестирование получения объектов ===");
            for (int i = 0; i < pool.getCapacity(); i++) {
                ProcessTask task = pool.getFreeObject();
                if (task != null) {
                    System.out.printf("Получен объект %d: %s%n", i, task.getStatus());
                    pool.setFreeObject(task);
                } else {
                    System.out.printf("Не удалось получить объект %d%n", i);
                }
            }
            
            // Пробуем получить объект
            System.out.println("\n=== Попытка получения объекта ===");
            ProcessTask task = pool.getFreeObject();
            if (task != null) {
                System.out.println("✅ Получен объект: " + task.getStatus());
                
                // Используем объект
                task.start();
                task.stop();
                
                // Возвращаем объект
                boolean returned = pool.setFreeObject(task);
                System.out.println("Объект возвращен: " + returned);
                
                // Получаем обновленную статистику
                stats = pool.getStats();
                System.out.println("Обновленная статистика: " + stats);
                
                System.out.println("✅ Тест прошел успешно!");
            } else {
                System.out.println("❌ Не удалось получить объект из пула");
                
                // Дополнительная диагностика
                System.out.println("\n=== Дополнительная диагностика ===");
                for (int i = 0; i < pool.getCapacity(); i++) {
                    boolean available = pool.isAvailable(i);
                    ProcessTask obj = pool.getObject(i);
                    System.out.printf("После попытки получения - Слот %d: доступен=%s, объект=%s%n", 
                        i, available, obj != null ? obj.getStatus() : "null");
                }
            }
            
            // Очищаем ресурсы
            pool.cleanup();
            
        } catch (Exception e) {
            System.err.println("❌ Ошибка в тесте: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 