# Как работает метод `cancel()` в системе задач

## 🎯 **Обзор механизма отмены**

Метод `cancel()` реализует **кооперативную отмену задач** (cooperative cancellation), что означает, что задача сама проверяет флаг отмены и корректно завершает работу.

## 🔧 **Архитектура отмены**

### **1. Состояние отмены**
```java
protected volatile boolean isCancelled = false;
```
- `volatile` обеспечивает видимость изменений между потоками
- Флаг устанавливается в `true` при вызове `cancel()`

### **2. Счетчик отмен**
```java
protected final AtomicLong cancellationCount = new AtomicLong(0);
```
- Атомарный счетчик для отслеживания количества отмен
- Потокобезопасный подсчет статистики

## 📋 **Реализация метода `cancel()`**

```java
public final void cancel() {
    if (!isCancelled && !isCompleted) {
        isCancelled = true;
        cancellationCount.incrementAndGet();
        logInfo("Task cancelled: " + taskName);
    }
}
```

### **Логика работы:**
1. **Проверка состояния** - отмена возможна только если задача не отменена и не завершена
2. **Установка флага** - `isCancelled = true`
3. **Обновление статистики** - увеличение счетчика отмен
4. **Логирование** - запись события отмены

## 🔄 **Интеграция с выполнением**

### **1. Проверка перед выполнением**
```java
public final void executeWithExceptionHandling() {
    if (isCancelled) {
        logWarning("Task cancelled before execution: " + taskName);
        return;
    }
    // ... выполнение задачи
}
```

### **2. Проверка во время выполнения**
```java
@Override
public void execute() {
    executionCount++;
    logInfo("Starting execution: " + data + " (local count: " + executionCount + ")");
    
    // Проверяем отмену во время выполнения
    if (isCancelled()) {
        logWarning("Task cancelled during execution: " + data);
        return;
    }
    
    // ... основная логика задачи
}
```

## 🎭 **Сценарии использования**

### **Сценарий 1: Отмена до выполнения**
```java
SimpleTask task = pool.getFreeObject();
task.cancel(); // Отменяем до выполнения
task.executeWithExceptionHandling(); // Задача не выполнится
// Результат: "Task cancelled before execution"
```

### **Сценарий 2: Отмена во время выполнения**
```java
SimpleTask task = pool.getFreeObject();
task.setSimulateCancellation(true); // Включаем симуляцию отмены
task.executeWithExceptionHandling(); // Задача отменится во время выполнения
// Результат: "Task cancelled during execution"
```

### **Сценарий 3: Внешняя отмена**
```java
SimpleTask task = pool.getFreeObject();
// Запускаем в отдельном потоке
executor.submit(() -> task.executeWithExceptionHandling());

// Отменяем из другого потока
task.cancel();
```

## 🔍 **Проверка состояния отмены**

### **Методы для проверки:**
```java
public final boolean isCancelled() {
    return isCancelled;
}

public final String getStatus() {
    if (isCancelled) return "CANCELLED";
    if (hasException) return "FAILED";
    if (isCompleted) return "COMPLETED";
    return "PENDING";
}

public final long getCancellationCount() {
    return cancellationCount.get();
}
```

## ⚡ **Особенности реализации**

### **1. Кооперативная отмена**
- Задача должна сама проверять флаг отмены
- Нет принудительной остановки выполнения
- Позволяет корректно освободить ресурсы

### **2. Потокобезопасность**
- `volatile` для флага отмены
- `AtomicLong` для счетчика
- Безопасная работа в многопоточной среде

### **3. Идемпотентность**
- Множественные вызовы `cancel()` безопасны
- Флаг устанавливается только один раз
- Счетчик увеличивается только при первой отмене

### **4. Логирование**
- Все события отмены логируются
- Контекстная информация (имя задачи)
- Различные уровни логирования

## 🚨 **Ограничения**

### **1. Кооперативность**
```java
// ❌ Плохо - задача не проверяет отмену
public void execute() {
    while (true) {
        // Бесконечный цикл без проверки отмены
        doWork();
    }
}

// ✅ Хорошо - задача проверяет отмену
public void execute() {
    while (!isCancelled()) {
        doWork();
    }
}
```

### **2. Нет принудительной остановки**
- Нельзя принудительно остановить блокирующие операции
- Задача должна сама реагировать на отмену
- Долгие операции должны периодически проверять флаг

## 📊 **Примеры из тестов**

### **Пример 1: Отмена в многопоточной среде**
```java
// Запускаем задачи
for (int i = 0; i < 3; i++) {
    executor.submit(() -> {
        SimpleTask task = pool.getFreeObject();
        task.executeWithExceptionHandling();
        pool.setFreeObject(task);
    });
}

// Отменяем все задачи в пуле
for (int i = 0; i < 3; i++) {
    SimpleTask task = pool.getFreeObject();
    if (task != null) {
        task.cancel();
        pool.setFreeObject(task);
    }
}
```

### **Пример 2: Симуляция отмены**
```java
SimpleTask task = pool.getFreeObject();
task.setSimulateCancellation(true); // Включаем симуляцию
task.executeWithExceptionHandling(); // Задача отменится сама
```

## 🎯 **Практические рекомендации**

### **1. Регулярные проверки**
```java
public void execute() {
    for (int i = 0; i < 1000; i++) {
        if (isCancelled()) {
            logInfo("Task cancelled at iteration " + i);
            return;
        }
        doWork(i);
    }
}
```

### **2. Проверка в критических точках**
```java
public void execute() {
    if (isCancelled()) return;
    
    // Подготовка данных
    prepareData();
    
    if (isCancelled()) return;
    
    // Основная работа
    processData();
    
    if (isCancelled()) return;
    
    // Завершение
    cleanup();
}
```

### **3. Обработка блокирующих операций**
```java
public void execute() {
    while (!isCancelled()) {
        try {
            // Блокирующая операция с таймаутом
            if (blockingOperation(100)) {
                break;
            }
        } catch (InterruptedException e) {
            if (isCancelled()) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

## 🚀 **Заключение**

Метод `cancel()` реализует **элегантный и безопасный механизм отмены задач**:

- ✅ **Кооперативная отмена** - задачи корректно завершают работу
- ✅ **Потокобезопасность** - безопасная работа в многопоточной среде
- ✅ **Статистика** - отслеживание количества отмен
- ✅ **Логирование** - детальное логирование событий
- ✅ **Идемпотентность** - безопасные множественные вызовы

Этот подход обеспечивает **надежную и предсказуемую отмену задач** в production-среде! 🎉 