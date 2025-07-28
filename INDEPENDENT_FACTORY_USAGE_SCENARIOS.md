# Сценарии использования IndependentObjectFactory

## Обзор методов создания

`IndependentObjectFactory<T>` предоставляет три способа создания фабрик:

1. **Обычный `new`** - прямое создание через лямбда-выражение
2. **`fromSupplier()`** - создание из существующего `Supplier<T>`
3. **`withInitializer()`** - создание с дополнительной инициализацией

## 1. 🎯 **Обычный `new` - Прямое создание**

### **Сценарий: Простое создание объектов**
```java
// Создание фабрики напрямую
IndependentObjectFactory<SimpleTask> factory = () -> {
    SimpleTask task = new SimpleTask();
    task.setData("Default Task");
    return task;
};
```

### **Когда использовать:**
- ✅ **Простая инициализация** - когда объект требует минимальной настройки
- ✅ **Единичное использование** - фабрика создается для конкретного случая
- ✅ **Быстрое прототипирование** - для тестирования или демонстрации
- ✅ **Кастомная логика** - когда нужна специфическая логика создания

### **Примеры сценариев:**

#### **Сценарий 1.1: Простая задача**
```java
// Создаем пул с простыми задачами
IndependentObjectFactory<SimpleTask> factory = () -> {
    SimpleTask task = new SimpleTask();
    task.setData("Task " + System.currentTimeMillis());
    return task;
};

BitmaskRingBufferUltraVarHandleAutoExpand<SimpleTask> pool = 
    new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory);
```

#### **Сценарий 1.2: Задача с уникальным ID**
```java
// Создаем задачи с уникальными идентификаторами
AtomicInteger idCounter = new AtomicInteger(0);
IndependentObjectFactory<SimpleTask> factory = () -> {
    SimpleTask task = new SimpleTask();
    task.setData("Task-" + idCounter.incrementAndGet());
    return task;
};
```

#### **Сценарий 1.3: Задача с контекстом**
```java
// Создаем задачи с контекстной информацией
String context = "Production";
IndependentObjectFactory<SimpleTask> factory = () -> {
    SimpleTask task = new SimpleTask();
    task.setData(context + " Task at " + LocalDateTime.now());
    return task;
};
```

---

## 2. 🔧 **`fromSupplier()` - Создание из Supplier**

### **Сценарий: Использование существующих Supplier'ов**
```java
// Создание фабрики из существующего Supplier
Supplier<SimpleTask> taskSupplier = SimpleTask::new;
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.fromSupplier(taskSupplier);
```

### **Когда использовать:**
- ✅ **Переиспользование кода** - когда уже есть готовый `Supplier<T>`
- ✅ **Интеграция с существующим кодом** - работа с библиотеками, которые возвращают `Supplier`
- ✅ **Чистый код** - когда логика создания уже инкапсулирована в `Supplier`
- ✅ **Тестирование** - легко подменять `Supplier` для тестов

### **Примеры сценариев:**

#### **Сценарий 2.1: Использование существующего Supplier**
```java
// У нас уже есть Supplier из другой части системы
Supplier<SimpleTask> existingSupplier = () -> {
    SimpleTask task = new SimpleTask();
    task.setData("Pre-configured task");
    return task;
};

// Создаем фабрику из существующего Supplier
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.fromSupplier(existingSupplier);
```

#### **Сценарий 2.2: Интеграция с библиотекой**
```java
// Библиотека возвращает Supplier
Supplier<SimpleTask> librarySupplier = TaskLibrary.createTaskSupplier();

// Создаем фабрику из библиотечного Supplier
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.fromSupplier(librarySupplier);
```

#### **Сценарий 2.3: Тестирование с Mock Supplier**
```java
// В тестах используем Mock Supplier
Supplier<SimpleTask> mockSupplier = () -> {
    SimpleTask mockTask = mock(SimpleTask.class);
    when(mockTask.getData()).thenReturn("Mock Task");
    return mockTask;
};

IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.fromSupplier(mockSupplier);
```

#### **Сценарий 2.4: Простой конструктор**
```java
// Когда нужен только простой конструктор без дополнительной логики
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.fromSupplier(SimpleTask::new);
```

---

## 3. ⚙️ **`withInitializer()` - Создание с инициализацией**

### **Сценарий: Сложная инициализация объектов**
```java
// Создание фабрики с отдельной логикой инициализации
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.withInitializer(
    SimpleTask::new,  // Supplier для создания
    task -> {         // Consumer для инициализации
        task.setData("Initialized Task");
        task.setPriority(5);
        System.out.println("Task initialized: " + task);
    }
);
```

### **Когда использовать:**
- ✅ **Сложная инициализация** - когда объект требует множественной настройки
- ✅ **Разделение ответственности** - отделение создания от инициализации
- ✅ **Переиспользование логики инициализации** - одна инициализация для разных Supplier'ов
- ✅ **Логирование и мониторинг** - добавление логики в процесс инициализации
- ✅ **Валидация** - проверка корректности инициализации

### **Примеры сценариев:**

#### **Сценарий 3.1: Сложная инициализация**
```java
// Создаем задачи с комплексной инициализацией
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.withInitializer(
    SimpleTask::new,
    task -> {
        task.setData("Complex Task");
        task.setPriority(ThreadLocalRandom.current().nextInt(1, 11));
        task.setCreatedAt(LocalDateTime.now());
        task.setStatus("READY");
        
        // Логирование
        System.out.println("Created task: " + task.getData() + " with priority: " + task.getPriority());
    }
);
```

#### **Сценарий 3.2: Переиспользование инициализации**
```java
// Общая логика инициализации для разных типов задач
Consumer<SimpleTask> commonInitializer = task -> {
    task.setData("Common Task");
    task.setPriority(5);
    task.setCreatedAt(LocalDateTime.now());
};

// Используем одну инициализацию для разных Supplier'ов
IndependentObjectFactory<SimpleTask> factory1 = IndependentObjectFactory.withInitializer(
    SimpleTask::new, commonInitializer
);

IndependentObjectFactory<AutoReturnSimpleTask> factory2 = IndependentObjectFactory.withInitializer(
    AutoReturnSimpleTask::new, 
    task -> {
        commonInitializer.accept(task);
        task.setAutoReturn(true);
    }
);
```

#### **Сценарий 3.3: Валидация и логирование**
```java
// Создаем задачи с валидацией и логированием
IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.withInitializer(
    SimpleTask::new,
    task -> {
        // Инициализация
        task.setData("Validated Task");
        task.setPriority(5);
        
        // Валидация
        if (task.getPriority() < 1 || task.getPriority() > 10) {
            throw new IllegalArgumentException("Invalid priority: " + task.getPriority());
        }
        
        // Логирование
        System.out.println("Task validated and ready: " + task.getData());
    }
);
```

#### **Сценарий 3.4: Конфигурация из внешних источников**
```java
// Инициализация на основе конфигурации
Configuration config = loadConfiguration();
Consumer<SimpleTask> configBasedInitializer = task -> {
    task.setData(config.getDefaultTaskName());
    task.setPriority(config.getDefaultPriority());
    task.setTimeout(config.getDefaultTimeout());
};

IndependentObjectFactory<SimpleTask> factory = IndependentObjectFactory.withInitializer(
    SimpleTask::new, configBasedInitializer
);
```

---

## 📊 **Сравнительная таблица сценариев**

| Метод | Сценарий использования | Сложность | Переиспользование | Гибкость |
|-------|----------------------|-----------|------------------|----------|
| **Обычный `new`** | Простая инициализация, единичное использование | Низкая | Низкое | Высокая |
| **`fromSupplier()`** | Переиспользование существующих Supplier'ов | Низкая | Высокое | Средняя |
| **`withInitializer()`** | Сложная инициализация, разделение ответственности | Высокая | Высокое | Очень высокая |

## 🎯 **Рекомендации по выбору**

### **Выбирайте обычный `new` когда:**
- Нужна простая инициализация
- Фабрика используется только в одном месте
- Требуется максимальная гибкость
- Быстрое прототипирование

### **Выбирайте `fromSupplier()` когда:**
- Уже есть готовый `Supplier<T>`
- Нужно интегрироваться с существующим кодом
- Требуется переиспользование логики создания
- Работаете с библиотеками

### **Выбирайте `withInitializer()` когда:**
- Объект требует сложной инициализации
- Нужно разделить создание и инициализацию
- Требуется переиспользование логики инициализации
- Нужны валидация, логирование или мониторинг
- Инициализация зависит от конфигурации

## 🚀 **Практический пример комбинирования**

```java
// Создаем различные фабрики для разных сценариев
public class TaskFactoryManager {
    
    // Простая фабрика для базовых задач
    public static IndependentObjectFactory<SimpleTask> createBasicFactory() {
        return () -> {
            SimpleTask task = new SimpleTask();
            task.setData("Basic Task");
            return task;
        };
    }
    
    // Фабрика из существующего Supplier
    public static IndependentObjectFactory<SimpleTask> createFromSupplier(Supplier<SimpleTask> supplier) {
        return IndependentObjectFactory.fromSupplier(supplier);
    }
    
    // Фабрика с сложной инициализацией
    public static IndependentObjectFactory<SimpleTask> createAdvancedFactory() {
        return IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData("Advanced Task");
                task.setPriority(ThreadLocalRandom.current().nextInt(1, 11));
                task.setCreatedAt(LocalDateTime.now());
                System.out.println("Advanced task created: " + task);
            }
        );
    }
    
    // Фабрика с конфигурацией
    public static IndependentObjectFactory<SimpleTask> createConfiguredFactory(Configuration config) {
        return IndependentObjectFactory.withInitializer(
            SimpleTask::new,
            task -> {
                task.setData(config.getTaskName());
                task.setPriority(config.getPriority());
                task.setTimeout(config.getTimeout());
            }
        );
    }
}
```

Этот подход позволяет гибко создавать фабрики для различных сценариев использования, выбирая наиболее подходящий метод в зависимости от требований. 