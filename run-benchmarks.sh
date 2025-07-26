#!/bin/bash

echo "=== UltraFast BitmaskRingBuffer Optimization Benchmarks ==="
echo ""

# Компилируем проект
echo "1. Компилируем проект..."
mvn clean compile test-compile

if [ $? -ne 0 ]; then
    echo "Ошибка компиляции!"
    exit 1
fi

echo "✅ Компиляция завершена успешно"
echo ""

# Запускаем тесты ABA-безопасности
echo "2. Запускаем тесты ABA-безопасности..."
mvn test -Dtest=ABASafetyTest

if [ $? -ne 0 ]; then
    echo "❌ Тесты ABA-безопасности провалились!"
    exit 1
fi

echo "✅ Тесты ABA-безопасности пройдены"
echo ""

# Запускаем бенчмарк lock-free stack
echo "3. Запускаем бенчмарк lock-free stack..."
mvn exec:java -Dexec.mainClass="com.ultrafast.pool.LockFreeStackBenchmark" -Dexec.args=""

if [ $? -ne 0 ]; then
    echo "❌ Бенчмарк lock-free stack провалился!"
    exit 1
fi

echo "✅ Бенчмарк lock-free stack завершен"
echo ""

# Запускаем основной бенчмарк оптимизации
echo "4. Запускаем основной бенчмарк оптимизации..."
mvn exec:java -Dexec.mainClass="com.ultrafast.pool.BitmaskRingBufferOptimizationBenchmark" -Dexec.args=""

if [ $? -ne 0 ]; then
    echo "❌ Основной бенчмарк провалился!"
    exit 1
fi

echo "✅ Основной бенчмарк завершен"
echo ""

echo "=== Все бенчмарки и тесты завершены успешно! ==="
echo ""
echo "Результаты сохранены в target/benchmark-results/"
echo ""
echo "Основные оптимизации:"
echo "- ABA-safe lock-free stack с AtomicStampedReference"
echo "- Padding для предотвращения false sharing"
echo "- Thread.onSpinWait() вместо Thread.yield()"
echo "- Оптимизированная структура данных" 