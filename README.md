# UltraFast Bitmask Ring Buffer

Высокопроизводительная реализация кольцевого буфера с битовыми масками для конкурентной среды на Java. Оптимизирована для минимального потребления памяти и максимальной скорости работы с пулом объектов.

## Особенности

- **Lock-free алгоритмы**: Использует атомарные операции для максимальной производительности
- **Битовые маски**: Эффективное отслеживание состояния объектов (64 бита на long)
- **Кольцевой буфер**: Оптимизированная структура данных с автоматическим округлением до степени 2
- **Многопоточность**: Полная поддержка конкурентного доступа без блокировок
- **CLI-интерфейс**: Встроенный интерфейс командной строки для мониторинга и управления
- **Отслеживание зависших объектов**: Автоматическое обнаружение объектов, не возвращенных в пул
- **Статистика**: Детальная статистика использования пула

## Архитектура

### Основные компоненты

1. **BitmaskRingBuffer<T>** - основная реализация пула
2. **Task** - интерфейс для объектов задач
3. **ProcessTask** - пример реализации задачи
4. **PoolCLI** - CLI-интерфейс для управления
5. **Demo** - демонстрационное приложение

### Структура данных

```
BitmaskRingBuffer:
├── AtomicReferenceArray<T> objects     # Массив объектов
├── long[] availabilityMask             # Битовые маски занятости (64 бита/маска)
├── long[] updateMask                   # Битовые маски обновления
├── AtomicLong head                     # Позиция для получения
├── AtomicLong tail                     # Позиция для возврата
├── AtomicLong occupiedCount            # Счетчик занятых объектов
└── long[] lastUsedTimes               # Время последнего использования
```

### Алгоритм работы

1. **Получение объекта (getFreeObject)**:
   - Проверка доступности через head/tail
   - CAS-операция для атомарного получения
   - Установка бита в availabilityMask
   - Обновление времени использования

2. **Возврат объекта (setFreeObject)**:
   - Поиск индекса объекта
   - Сброс бита в availabilityMask
   - Увеличение tail

3. **Битовые операции**:
   - 64 бита на long для максимальной эффективности
   - Быстрые операции AND/OR для установки/сброса битов

## Производительность

### Ожидаемые характеристики

- **Получение/возврат объекта**: ~50-100 наносекунд
- **Память на объект**: ~8 байт (только ссылка + битовые маски)
- **Масштабируемость**: Линейная до 16+ потоков
- **Емкость**: До 10-15 тысяч объектов с минимальными накладными расходами

### Оптимизации

1. **Lock-free алгоритмы**: Отсутствие блокировок
2. **Битовые маски**: Минимальное потребление памяти для состояния
3. **Кольцевой буфер**: Быстрый доступ по индексу
4. **Предварительное создание объектов**: Избежание создания во время работы
5. **Адаптивное ожидание**: Оптимизированные паузы при ожидании

## Использование

### Базовое использование

```java
// Создание пула
BitmaskRingBuffer<Task> pool = new BitmaskRingBuffer<>(
    16384, 
    () -> new ProcessTask("MyProcess")
);

// Получение объекта
Task task = pool.getFreeObject();
if (task != null) {
    task.start();
    // ... работа с задачей ...
    task.stop();
    pool.setFreeObject(task);
}
```

### Многопоточное использование

```java
ExecutorService executor = Executors.newFixedThreadPool(8);
for (int i = 0; i < 1000; i++) {
    executor.submit(() -> {
        Task task = pool.getFreeObject();
        if (task != null) {
            try {
                task.start();
                // ... работа ...
            } finally {
                task.stop();
                pool.setFreeObject(task);
            }
        }
    });
}
```

### CLI-управление

```bash
# Запуск демо
mvn exec:java -Dexec.mainClass="com.ultrafast.pool.Demo"

# Доступные команды:
pool> stats                    # Статистика пула
pool> occupied                 # Список занятых объектов
pool> stale 30000             # Поиск зависших объектов (>30 сек)
pool> mark-update             # Пометить все для обновления
pool> stop-all                # Остановить все задачи
pool> get 10                  # Получить 10 объектов
pool> return 5                # Вернуть 5 объектов
pool> quit                    # Выход
```

## API

### BitmaskRingBuffer<T>

#### Основные методы

- `T getFreeObject()` - получить свободный объект
- `T getFreeObject(long maxWaitNanos)` - получить с ожиданием
- `boolean setFreeObject(T object)` - вернуть объект
- `T[] getOccupiedObjects()` - список занятых объектов
- `T[] findStaleObjects(long maxAgeMs)` - поиск зависших объектов
- `void markAllForUpdate()` - пометить все для обновления
- `void stopAllOccupied()` - остановить все занятые объекты
- `PoolStatistics getStatistics()` - получить статистику

#### Статистика

```java
public static class PoolStatistics {
    public final int capacity;        // Общая емкость
    public final long occupiedCount;  // Количество занятых
    public final long totalGets;      // Всего получений
    public final long totalReturns;   // Всего возвратов
    public final long totalWaits;     // Всего ожиданий
}
```

### Task

#### Интерфейс

```java
public interface Task {
    long getId();                    // Уникальный ID
    void start();                    // Запуск
    void stop();                     // Остановка
    boolean isRunning();             // Проверка состояния
    boolean needsUpdate();           // Требует обновления
    void markForUpdate();            // Пометить для обновления
    long getLastUsedTime();          // Время последнего использования
    String getStatus();              // Статус в виде строки
}
```

## Сборка и тестирование

### Сборка

```bash
mvn clean compile
```

### Запуск тестов

```bash
mvn test
```

### Запуск бенчмарков

```bash
mvn exec:java -Dexec.mainClass="org.openjdk.jmh.Main" -Dexec.args="BitmaskRingBufferBenchmark"
```

### Запуск демо

```bash
mvn exec:java -Dexec.mainClass="com.ultrafast.pool.Demo"
```

## Требования

- Java 17+
- Maven 3.6+

## Производительность

### Результаты бенчмарков (примерные)

| Операция | 1 поток | 4 потока | 8 потоков | 16 потоков |
|----------|---------|----------|-----------|------------|
| get/return | 2M ops/s | 6M ops/s | 8M ops/s | 10M ops/s |
| get only | 5M ops/s | 15M ops/s | 20M ops/s | 25M ops/s |
| return only | 4M ops/s | 12M ops/s | 16M ops/s | 20M ops/s |

### Потребление памяти

- **На объект**: ~8 байт (ссылка)
- **Битовые маски**: ~capacity/8 байт
- **Время использования**: ~capacity*8 байт
- **Общие накладные расходы**: ~2% от размера объектов

## Мониторинг и отладка

### Автоматический мониторинг

CLI автоматически отслеживает зависшие объекты каждые 30 секунд и выводит предупреждения.

### Ручной мониторинг

```java
// Получение статистики
PoolStatistics stats = pool.getStatistics();
System.out.println("Utilization: " + 
    (double) stats.occupiedCount / stats.capacity * 100 + "%");

// Поиск зависших объектов
Task[] stale = pool.findStaleObjects(60000); // старше 1 минуты
System.out.println("Stale objects: " + stale.length);
```

## Безопасность

- **Thread-safe**: Полная безопасность в многопоточной среде
- **Lock-free**: Отсутствие deadlock'ов
- **Memory-efficient**: Минимальное потребление памяти
- **Graceful degradation**: Корректная работа при переполнении

## Лицензия

MIT License 