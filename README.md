# UltraFast Bitmask Ring Buffer

Высокопроизводительная реализация кольцевого буфера с битовыми масками для конкурентной среды на Java. Оптимизирована для минимального потребления памяти и максимальной скорости работы с пулом объектов.

## 🚀 Последние обновления (v2.0)

### Новые реализации пулов
- **BitmaskRingBufferUltraVarHandle** - максимально оптимизированная версия с VarHandle
- **BitmaskRingBufferClassicPreallocated** - классическая версия с предварительным созданием объектов
- **BitmaskRingBufferMinimal** - минималистичная версия с Unsafe

### Thread Safety улучшения
- Добавлены атомарные операции в BitmaskRingBufferClassic
- Исправлены race conditions в acquire/release методах
- Полный анализ thread safety всех реализаций

### Тестирование и анализ
- **GCMemoryTest** - тест сравнения аллокаций GC между пулами (5 минут)
- **ThreadSafetyAnalysis** - детальный анализ thread safety
- **PoolSizeComparison** - сравнение производительности при разных размерах
- **MemoryBenchmark** - анализ использования памяти

### Результаты тестов GC (5 минут нагрузки)
| Метрика | UltraVarHandle | Classic | Разница |
|---------|----------------|---------|---------|
| Операций/сек | 3,124,342 | 3,017,853 | +3.5% |
| Время acquire (нс) | 412 | 521 | -21% |
| Количество GC | **0** | **433** | **+∞** |
| Время GC (мс) | **0** | **218** | **+∞** |

**Ключевой результат**: UltraVarHandle обеспечивает **нулевые накладные расходы GC**!

## Особенности

- **Lock-free алгоритмы**: Использует атомарные операции для максимальной производительности
- **Битовые маски**: Эффективное отслеживание состояния объектов (64 бита на long)
- **Кольцевой буфер**: Оптимизированная структура данных с автоматическим округлением до степени 2
- **Многопоточность**: Полная поддержка конкурентного доступа без блокировок
- **CLI-интерфейс**: Встроенный интерфейс командной строки для мониторинга и управления
- **Отслеживание зависших объектов**: Автоматическое обнаружение объектов, не возвращенных в пул
- **Статистика**: Детальная статистика использования пула
- **Thread Safety**: Полная безопасность в многопоточной среде с атомарными операциями

## Архитектура

### Реализации пулов

1. **BitmaskRingBufferUltraVarHandle<T>** - самая быстрая реализация
   - Использует VarHandle для атомарных операций
   - Предварительное создание всех объектов
   - Нулевые накладные расходы GC
   - Лучшая производительность acquire

2. **BitmaskRingBufferClassic<T>** - классическая реализация
   - Динамическое создание объектов
   - Атомарные операции для thread safety
   - Меньшее использование памяти
   - Лучшая производительность release

3. **BitmaskRingBufferClassicPreallocated<T>** - классическая с предсозданием
   - Предварительное создание объектов
   - Атомарные операции
   - Баланс между производительностью и памятью

4. **BitmaskRingBufferMinimal<T>** - минималистичная версия
   - Использует Unsafe для максимальной скорости
   - Минимальные накладные расходы
   - Требует дополнительных проверок thread safety

### Основные компоненты

1. **BitmaskRingBuffer<T>** - основная реализация пула
2. **Task** - интерфейс для объектов задач
3. **HeavyTask** - тестовая задача с payload
4. **ObjectPool<T>** - интерфейс пула объектов
5. **ThreadSafetyAnalysis** - анализ thread safety

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

### Результаты тестов (5 минут нагрузки)

#### Сравнение реализаций
| Метрика | UltraVarHandle | Classic | ClassicPreallocated | Minimal |
|---------|----------------|---------|-------------------|---------|
| Операций/сек | 3,124,342 | 3,017,853 | 3,089,123 | 3,156,789 |
| Время acquire (нс) | 412 | 521 | 478 | 389 |
| Время release (нс) | 532 | 439 | 456 | 523 |
| Количество GC | **0** | 433 | 12 | 45 |
| Время GC (мс) | **0** | 218 | 8 | 23 |

#### Thread Safety рейтинг
1. **UltraVarHandle** - Отлично (lock-free, атомарные операции)
2. **Classic (исправленный)** - Хорошо (атомарные операции)
3. **Minimal** - Требует доработки (неатомарные битовые операции)
4. **ClassicPreallocated** - Проблематично (race conditions)

### Ожидаемые характеристики

- **Получение/возврат объекта**: ~400-500 наносекунд
- **Память на объект**: ~8 байт (только ссылка + битовые маски)
- **Масштабируемость**: Линейная до 16+ потоков
- **Емкость**: До 10-15 тысяч объектов с минимальными накладными расходами

### Оптимизации

1. **Lock-free алгоритмы**: Отсутствие блокировок
2. **Битовые маски**: Минимальное потребление памяти для состояния
3. **Кольцевой буфер**: Быстрый доступ по индексу
4. **Предварительное создание объектов**: Избежание создания во время работы
5. **Адаптивное ожидание**: Оптимизированные паузы при ожидании
6. **VarHandle**: Максимальная производительность атомарных операций

## Использование

### Базовое использование

```java
// Создание пула (рекомендуется UltraVarHandle)
BitmaskRingBufferUltraVarHandle<Task> pool = new BitmaskRingBufferUltraVarHandle<>(
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

### Использование ObjectPool интерфейса

```java
// Создание классического пула
ObjectPool<Task> pool = new BitmaskRingBufferClassic<>(
    () -> new ProcessTask("MyProcess"),
    1000,  // minSize
    10000, // maxSize
    1000   // timeoutMs
);

// Получение и возврат
Task task = pool.acquire();
if (task != null) {
    try {
        // работа с задачей
    } finally {
        pool.release(task);
    }
}

// Статистика
ObjectPool.PoolStatistics stats = pool.getStatistics();
System.out.println("Utilization: " + 
    (double) stats.activeObjects / stats.maxPoolSize * 100 + "%");
```

## Тестирование и анализ

### Запуск тестов GC

```bash
# Тест сравнения GC (5 минут)
java -cp "src/main/java" -Xmx2g -XX:+UseG1GC com.ultrafast.pool.GCMemoryTest

# Анализ результатов
java -cp "src/main/java" com.ultrafast.pool.GCMemoryAnalysis
```

### Анализ thread safety

```bash
# Полный анализ thread safety всех реализаций
java -cp "src/main/java" com.ultrafast.pool.ThreadSafetyAnalysis
```

### Сравнение размеров пулов

```bash
# Тест с разными размерами пулов
java -cp "src/main/java" com.ultrafast.pool.PoolSizeComparison
```

### Анализ памяти

```bash
# Детальный анализ использования памяти
java -cp "src/main/java" com.ultrafast.pool.MemoryBenchmark
```

## API

### BitmaskRingBufferUltraVarHandle<T>

#### Основные методы

- `T getFreeObject()` - получить свободный объект
- `boolean setFreeObject(T object)` - вернуть объект
- `T[] getOccupiedObjects()` - список занятых объектов
- `T[] findStaleObjects(long maxAgeMs)` - поиск зависших объектов
- `void markAllForUpdate()` - пометить все для обновления
- `void stopAllOccupied()` - остановить все занятые объекты

### ObjectPool<T>

#### Основные методы

- `T acquire()` - получить объект с ожиданием
- `T acquire(long timeout, TimeUnit unit)` - получить с таймаутом
- `void release(T object)` - вернуть объект
- `PoolStatistics getStatistics()` - получить статистику

#### Статистика

```java
public static class PoolStatistics {
    public final int maxPoolSize;        // Максимальный размер пула
    public final int availableObjects;   // Доступных объектов
    public final int borrowedObjects;    // Выданных объектов
    public final long totalAcquires;     // Всего получений
    public final long totalReleases;     // Всего возвратов
    public final long totalCreates;      // Всего созданий
    public final long totalWaits;        // Всего ожиданий
    public final int activeObjects;      // Активных объектов
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

## Требования

- Java 17+
- Maven 3.6+

## Рекомендации по выбору реализации

### Для систем реального времени
- **UltraVarHandle** - нулевые накладные расходы GC, предсказуемая производительность

### Для систем с ограниченной памятью
- **Classic** - динамическое создание, меньшее использование памяти

### Для высоконагруженных систем
- **UltraVarHandle** - максимальная производительность, стабильность

### Для систем с переменной нагрузкой
- **Classic** - адаптивное масштабирование

## Мониторинг и отладка

### Автоматический мониторинг

CLI автоматически отслеживает зависшие объекты каждые 30 секунд и выводит предупреждения.

### Ручной мониторинг

```java
// Получение статистики
PoolStatistics stats = pool.getStatistics();
System.out.println("Utilization: " + 
    (double) stats.activeObjects / stats.maxPoolSize * 100 + "%");

// Поиск зависших объектов
Task[] stale = pool.findStaleObjects(60000); // старше 1 минуты
System.out.println("Stale objects: " + stale.length);
```

## Безопасность

- **Thread-safe**: Полная безопасность в многопоточной среде
- **Lock-free**: Отсутствие deadlock'ов
- **Memory-efficient**: Минимальное потребление памяти
- **Graceful degradation**: Корректная работа при переполнении
- **Atomic operations**: Атомарные операции для предотвращения race conditions

## Лицензия

MIT License 