package com.ultrafast.pool;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Диагностический тест для проверки подсчета сборок GC
 */
public class GCDiagnosticTest {
    
    public static void main(String[] args) {
        System.out.println("=== ДИАГНОСТИКА СБОРЩИКОВ МУСОРА ===\n");
        
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        System.out.println("Найдено сборщиков мусора: " + gcBeans.size());
        System.out.println();
        
        for (int i = 0; i < gcBeans.size(); i++) {
            GarbageCollectorMXBean gcBean = gcBeans.get(i);
            System.out.println("Сборщик " + (i + 1) + ":");
            System.out.println("  - Имя: " + gcBean.getName());
            System.out.println("  - Количество сборок: " + gcBean.getCollectionCount());
            System.out.println("  - Время сборок: " + gcBean.getCollectionTime() + " мс");
            System.out.println("  - Валидный: " + gcBean.isValid());
            System.out.println();
        }
        
        // Текущая сумма
        long totalCount = gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
        
        long totalTime = gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
        
        System.out.println("ИТОГО:");
        System.out.println("  - Общее количество сборок: " + totalCount);
        System.out.println("  - Общее время сборок: " + totalTime + " мс");
        
        // Тест с принудительной сборкой
        System.out.println("\n=== ТЕСТ С ПРИНУДИТЕЛЬНОЙ СБОРКОЙ ===");
        
        long beforeCount = totalCount;
        long beforeTime = totalTime;
        
        System.gc();
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long afterCount = gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
        
        long afterTime = gcBeans.stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
        
        System.out.println("До System.gc():");
        System.out.println("  - Сборок: " + beforeCount);
        System.out.println("  - Время: " + beforeTime + " мс");
        
        System.out.println("После System.gc():");
        System.out.println("  - Сборок: " + afterCount);
        System.out.println("  - Время: " + afterTime + " мс");
        
        System.out.println("Разница:");
        System.out.println("  - Сборок: " + (afterCount - beforeCount));
        System.out.println("  - Время: " + (afterTime - beforeTime) + " мс");
        
        // Детальная информация по каждому сборщику
        System.out.println("\n=== ДЕТАЛЬНАЯ ИНФОРМАЦИЯ ПОСЛЕ System.gc() ===");
        for (int i = 0; i < gcBeans.size(); i++) {
            GarbageCollectorMXBean gcBean = gcBeans.get(i);
            System.out.println("Сборщик " + (i + 1) + " (" + gcBean.getName() + "):");
            System.out.println("  - Количество сборок: " + gcBean.getCollectionCount());
            System.out.println("  - Время сборок: " + gcBean.getCollectionTime() + " мс");
        }
    }
} 