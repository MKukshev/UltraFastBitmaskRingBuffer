# Элегантные решения для контроля Future в Callable Tasks

## 🎯 **Проблема и цели**

### **Текущие проблемы:**
1. Разработчик должен вручную хранить `Future` для отмены
2. Сложность управления множественными задачами
3. Отсутствие централизованного контроля отмены
4. Неудобство при работе с пулами задач

### **Цели решения:**
- ✅ Максимальная простота для разработчика
- ✅ Автоматическое управление Future
- ✅ Централизованный контроль отмены
- ✅ Интеграция с существующими пулами
- ✅ Минимальные изменения в API

## 🚀 **Решение 1: Future Registry в пуле**

### **Концепция:**
Встроить реестр Future прямо в пул, чтобы задачи автоматически регистрировались.

```java
public class FutureAwarePool<T> implements ObjectPool<T> {
    private final ObjectPool<T> delegate;
    private final Map<String, Future<?>> futureRegistry = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    
    public FutureAwarePool(ObjectPool<T> delegate) {
        this.delegate = delegate;
    }
    
    public <R> Future<R> submitTask(Callable<R> task) {
        String taskId = generateTaskId();
        
        // Создаем обертку, которая автоматически регистрирует Future
        Callable<R> wrappedTask = () -> {
            try {
                return task.call();
            } finally {
                // Автоматически удаляем из реестра при завершении
                futureRegistry.remove(taskId);
            }
        };
        
        // Отправляем в ExecutorService и регистрируем Future
        Future<R> future = executorService.submit(wrappedTask);
        futureRegistry.put(taskId, future);
        
        return new ManagedFuture<>(future, taskId, this);
    }
    
    public void cancelTask(String taskId) {
        Future<?> future = futureRegistry.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
    
    public void cancelAllTasks() {
        futureRegistry.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        futureRegistry.clear();
    }
    
    private String generateTaskId() {
        return "task_" + taskIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }
    
    // Делегируем методы ObjectPool
    @Override
    public T getFreeObject() {
        return delegate.getFreeObject();
    }
    
    @Override
    public void setFreeObject(T obj) {
        delegate.setFreeObject(obj);
    }
    
    // ... остальные методы
}

// Обертка для Future с автоматическим управлением
public class ManagedFuture<R> implements Future<R> {
    private final Future<R> delegate;
    private final String taskId;
    private final FutureAwarePool<?> pool;
    
    public ManagedFuture(Future<R> delegate, String taskId, FutureAwarePool<?> pool) {
        this.delegate = delegate;
        this.taskId = taskId;
        this.pool = pool;
    }
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean result = delegate.cancel(mayInterruptIfRunning);
        if (result) {
            pool.removeFromRegistry(taskId);
        }
        return result;
    }
    
    // Делегируем остальные методы
    @Override
    public boolean isCancelled() { return delegate.isCancelled(); }
    @Override
    public boolean isDone() { return delegate.isDone(); }
    @Override
    public R get() throws InterruptedException, ExecutionException { return delegate.get(); }
    @Override
    public R get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { 
        return delegate.get(timeout, unit); 
    }
}
```

### **Использование:**
```java
// Создаем пул с поддержкой Future
FutureAwarePool<MyTask> pool = new FutureAwarePool<>(new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory));

// Отправляем задачи - Future автоматически регистрируется
Future<String> future1 = pool.submitTask(() -> {
    MyTask task = pool.getFreeObject();
    try {
        return task.process("data1");
    } finally {
        pool.setFreeObject(task);
    }
});

Future<String> future2 = pool.submitTask(() -> {
    MyTask task = pool.getFreeObject();
    try {
        return task.process("data2");
    } finally {
        pool.setFreeObject(task);
    }
});

// Отменяем конкретную задачу
future1.cancel(true);

// Или отменяем все задачи
pool.cancelAllTasks();
```

---

## 🚀 **Решение 2: Task Executor с автоматической регистрацией**

### **Концепция:**
Создать специальный Executor, который автоматически управляет Future и интеграцией с пулом.

```java
public class PoolTaskExecutor {
    private final ExecutorService executorService;
    private final ObjectPool<?> pool;
    private final Map<String, Future<?>> futureRegistry = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    
    public PoolTaskExecutor(ExecutorService executorService, ObjectPool<?> pool) {
        this.executorService = executorService;
        this.pool = pool;
    }
    
    public <T, R> Future<R> submitWithPool(Callable<R> task, Class<T> taskType) {
        String taskId = generateTaskId();
        
        Callable<R> wrappedTask = () -> {
            T poolObject = null;
            try {
                // Получаем объект из пула
                poolObject = (T) pool.getFreeObject();
                
                // Выполняем задачу
                return task.call();
            } finally {
                // Возвращаем объект в пул
                if (poolObject != null) {
                    pool.setFreeObject(poolObject);
                }
                // Удаляем из реестра
                futureRegistry.remove(taskId);
            }
        };
        
        Future<R> future = executorService.submit(wrappedTask);
        futureRegistry.put(taskId, future);
        
        return new ManagedFuture<>(future, taskId, this);
    }
    
    public <R> Future<R> submitWithPool(Supplier<T> taskSupplier, Function<T, R> taskExecutor, Class<T> taskType) {
        return submitWithPool(() -> {
            T task = taskSupplier.get();
            return taskExecutor.apply(task);
        }, taskType);
    }
    
    public void cancelTask(String taskId) {
        Future<?> future = futureRegistry.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
    
    public void cancelAllTasks() {
        futureRegistry.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        futureRegistry.clear();
    }
    
    public void shutdown() {
        cancelAllTasks();
        executorService.shutdown();
    }
    
    private String generateTaskId() {
        return "task_" + taskIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
    }
}
```

### **Использование:**
```java
// Создаем executor с пулом
PoolTaskExecutor executor = new PoolTaskExecutor(
    Executors.newFixedThreadPool(4),
    new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory)
);

// Простое использование
Future<String> future1 = executor.submitWithPool(
    () -> "Task 1 result",
    String.class
);

// Использование с объектами из пула
Future<String> future2 = executor.submitWithPool(
    () -> pool.getFreeObject(), // получение из пула
    task -> task.process("data"), // выполнение задачи
    MyTask.class
);

// Отмена
future1.cancel(true);
executor.cancelAllTasks();
```

---

## 🚀 **Решение 3: Future-Aware Tasks с встроенным контролем**

### **Концепция:**
Расширить задачи так, чтобы они сами знали о своем Future и могли управлять отменой.

```java
public abstract class FutureAwareTask<T extends FutureAwareTask<T, R>, R> 
    extends AutoReturnTask<T, R> {
    
    private Future<R> myFuture;
    private String taskId;
    private final FutureRegistry futureRegistry;
    
    public FutureAwareTask(FutureRegistry futureRegistry) {
        this.futureRegistry = futureRegistry;
    }
    
    public void setFuture(Future<R> future, String taskId) {
        this.myFuture = future;
        this.taskId = taskId;
    }
    
    public void selfCancel() {
        if (myFuture != null && !myFuture.isDone()) {
            myFuture.cancel(true);
        }
    }
    
    public boolean isSelfCancelled() {
        return myFuture != null && myFuture.isCancelled();
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    @Override
    public R call() throws Exception {
        try {
            return super.call();
        } finally {
            // Автоматически удаляем из реестра при завершении
            if (futureRegistry != null && taskId != null) {
                futureRegistry.removeTask(taskId);
            }
        }
    }
}

// Централизованный реестр Future
public class FutureRegistry {
    private final Map<String, Future<?>> registry = new ConcurrentHashMap<>();
    
    public void registerTask(String taskId, Future<?> future) {
        registry.put(taskId, future);
    }
    
    public void removeTask(String taskId) {
        registry.remove(taskId);
    }
    
    public void cancelTask(String taskId) {
        Future<?> future = registry.get(taskId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
    
    public void cancelAllTasks() {
        registry.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        registry.clear();
    }
    
    public Set<String> getActiveTaskIds() {
        return new HashSet<>(registry.keySet());
    }
}
```

### **Использование:**
```java
// Создаем реестр и пул
FutureRegistry registry = new FutureRegistry();
BitmaskRingBufferUltraVarHandleAutoExpand<MyFutureAwareTask> pool = 
    new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, () -> new MyFutureAwareTask(registry));

// Получаем задачу и отправляем в executor
MyFutureAwareTask task = pool.getFreeObject();
task.setData("test data");

Future<String> future = executor.submit(task);
String taskId = "task_" + System.currentTimeMillis();
registry.registerTask(taskId, future);
task.setFuture(future, taskId);

// Задача может сама себя отменить
task.selfCancel();

// Или отменяем через реестр
registry.cancelTask(taskId);
registry.cancelAllTasks();
```

---

## 🚀 **Решение 4: Fluent API с автоматическим управлением**

### **Концепция:**
Создать fluent API, который скрывает всю сложность управления Future.

```java
public class TaskManager<T> {
    private final ObjectPool<T> pool;
    private final ExecutorService executorService;
    private final Map<String, Future<?>> futureRegistry = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    
    public TaskManager(ObjectPool<T> pool, ExecutorService executorService) {
        this.pool = pool;
        this.executorService = executorService;
    }
    
    public TaskBuilder<T> submit() {
        return new TaskBuilder<>(this);
    }
    
    public void cancelAll() {
        futureRegistry.values().forEach(future -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        futureRegistry.clear();
    }
    
    public void shutdown() {
        cancelAll();
        executorService.shutdown();
    }
    
    // Внутренний класс для fluent API
    public class TaskBuilder<R> {
        private final TaskManager<T> manager;
        private Duration timeout;
        private boolean autoCancelOnError = false;
        private Consumer<T> preProcessor;
        private Consumer<T> postProcessor;
        
        public TaskBuilder(TaskManager<T> manager) {
            this.manager = manager;
        }
        
        public TaskBuilder<R> withTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public TaskBuilder<R> autoCancelOnError() {
            this.autoCancelOnError = true;
            return this;
        }
        
        public TaskBuilder<R> preProcess(Consumer<T> preProcessor) {
            this.preProcessor = preProcessor;
            return this;
        }
        
        public TaskBuilder<R> postProcess(Consumer<T> postProcessor) {
            this.postProcessor = postProcessor;
            return this;
        }
        
        public <V> Future<V> execute(Function<T, V> task) {
            String taskId = generateTaskId();
            
            Callable<V> wrappedTask = () -> {
                T poolObject = null;
                try {
                    poolObject = pool.getFreeObject();
                    
                    if (preProcessor != null) {
                        preProcessor.accept(poolObject);
                    }
                    
                    V result = task.apply(poolObject);
                    
                    if (postProcessor != null) {
                        postProcessor.accept(poolObject);
                    }
                    
                    return result;
                } catch (Exception e) {
                    if (autoCancelOnError) {
                        manager.futureRegistry.get(taskId).cancel(true);
                    }
                    throw e;
                } finally {
                    if (poolObject != null) {
                        pool.setFreeObject(poolObject);
                    }
                    manager.futureRegistry.remove(taskId);
                }
            };
            
            Future<V> future = executorService.submit(wrappedTask);
            manager.futureRegistry.put(taskId, future);
            
            return new ManagedFuture<>(future, taskId, manager);
        }
        
        private String generateTaskId() {
            return "task_" + taskIdCounter.incrementAndGet() + "_" + System.currentTimeMillis();
        }
    }
}
```

### **Использование:**
```java
// Создаем менеджер задач
TaskManager<MyTask> taskManager = new TaskManager<>(
    new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory),
    Executors.newFixedThreadPool(4)
);

// Fluent API - максимально простое использование
Future<String> future1 = taskManager.submit()
    .withTimeout(Duration.ofSeconds(30))
    .autoCancelOnError()
    .preProcess(task -> task.initialize())
    .postProcess(task -> task.cleanup())
    .execute(task -> task.process("data1"));

Future<Integer> future2 = taskManager.submit()
    .withTimeout(Duration.ofMinutes(5))
    .execute(task -> task.calculate("data2"));

// Отмена
future1.cancel(true);
taskManager.cancelAll();
taskManager.shutdown();
```

---

## 🚀 **Решение 5: Комбинированный подход (Рекомендуемый)**

### **Концепция:**
Объединить лучшие части всех решений в единую систему.

```java
public class SmartTaskPool<T> {
    private final ObjectPool<T> pool;
    private final ExecutorService executorService;
    private final FutureRegistry futureRegistry;
    private final TaskLifecycleManager lifecycleManager;
    
    public SmartTaskPool(ObjectPool<T> pool, ExecutorService executorService) {
        this.pool = pool;
        this.executorService = executorService;
        this.futureRegistry = new FutureRegistry();
        this.lifecycleManager = new TaskLifecycleManager(pool, futureRegistry);
    }
    
    // Простой API для быстрого использования
    public <R> Future<R> submit(Function<T, R> task) {
        return lifecycleManager.submitTask(task, executorService);
    }
    
    // Расширенный API с настройками
    public <R> Future<R> submit(TaskConfig config, Function<T, R> task) {
        return lifecycleManager.submitTask(task, executorService, config);
    }
    
    // Batch API для множественных задач
    public List<Future<?>> submitAll(List<Function<T, ?>> tasks) {
        return lifecycleManager.submitTasks(tasks, executorService);
    }
    
    // Управление задачами
    public void cancelTask(String taskId) {
        futureRegistry.cancelTask(taskId);
    }
    
    public void cancelAllTasks() {
        futureRegistry.cancelAllTasks();
    }
    
    public Set<String> getActiveTaskIds() {
        return futureRegistry.getActiveTaskIds();
    }
    
    public void shutdown() {
        cancelAllTasks();
        executorService.shutdown();
    }
    
    // Статистика
    public TaskPoolStatistics getStatistics() {
        return lifecycleManager.getStatistics();
    }
}

// Конфигурация задач
public class TaskConfig {
    private Duration timeout;
    private boolean autoCancelOnError = false;
    private Consumer<T> preProcessor;
    private Consumer<T> postProcessor;
    private String taskName;
    
    // Builder методы
    public TaskConfig withTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }
    
    public TaskConfig autoCancelOnError() {
        this.autoCancelOnError = true;
        return this;
    }
    
    public TaskConfig withName(String taskName) {
        this.taskName = taskName;
        return this;
    }
    
    // ... остальные методы
}
```

### **Использование:**
```java
// Создаем умный пул
SmartTaskPool<MyTask> smartPool = new SmartTaskPool<>(
    new BitmaskRingBufferUltraVarHandleAutoExpand<>(10, factory),
    Executors.newFixedThreadPool(4)
);

// Простое использование
Future<String> future1 = smartPool.submit(task -> task.process("data1"));

// Расширенное использование
TaskConfig config = new TaskConfig()
    .withTimeout(Duration.ofSeconds(30))
    .autoCancelOnError()
    .withName("DataProcessing");

Future<String> future2 = smartPool.submit(config, task -> task.process("data2"));

// Batch обработка
List<Function<MyTask, ?>> tasks = Arrays.asList(
    task -> task.process("data3"),
    task -> task.process("data4"),
    task -> task.process("data5")
);

List<Future<?>> futures = smartPool.submitAll(tasks);

// Управление
smartPool.cancelTask("task_123");
smartPool.cancelAllTasks();

// Мониторинг
Set<String> activeTasks = smartPool.getActiveTaskIds();
TaskPoolStatistics stats = smartPool.getStatistics();
```

## 📊 **Сравнение решений**

| Решение | Простота | Гибкость | Производительность | Интеграция |
|---------|----------|----------|-------------------|------------|
| **Future Registry в пуле** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Task Executor** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Future-Aware Tasks** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ |
| **Fluent API** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Комбинированный** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |

## 🎯 **Рекомендации**

### **Для простых случаев:**
```java
// Рекомендация: Future Registry в пуле
FutureAwarePool<MyTask> pool = new FutureAwarePool<>(basePool);
Future<String> future = pool.submitTask(() -> processData());
future.cancel(true);
```

### **Для сложных систем:**
```java
// Рекомендация: Комбинированный подход
SmartTaskPool<MyTask> smartPool = new SmartTaskPool<>(basePool, executor);
Future<String> future = smartPool.submit(task -> task.process("data"));
smartPool.cancelAllTasks();
```

### **Для максимальной гибкости:**
```java
// Рекомендация: Fluent API
TaskManager<MyTask> manager = new TaskManager<>(pool, executor);
Future<String> future = manager.submit()
    .withTimeout(Duration.ofSeconds(30))
    .autoCancelOnError()
    .execute(task -> task.process("data"));
```

## 🚀 **Заключение**

**Лучшее решение - Комбинированный подход**, который предоставляет:

1. **Максимальную простоту** для разработчиков
2. **Автоматическое управление** Future и пулом
3. **Гибкость** в настройке поведения
4. **Централизованный контроль** отмены
5. **Мониторинг** и статистику

Это решение скрывает всю сложность от разработчика, предоставляя простой и интуитивный API! 🎉 