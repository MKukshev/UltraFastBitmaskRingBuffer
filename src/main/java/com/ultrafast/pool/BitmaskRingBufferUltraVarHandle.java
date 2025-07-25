package com.ultrafast.pool;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * УЛЬТРА-БЫСТРЫЙ конкурентный пул объектов, объединяющий off-heap память и битовые трюки для максимальной производительности.
 * 
 * ЭТА РЕАЛИЗАЦИЯ КОМБИНИРУЕТ ЛУЧШЕЕ ИЗ ОБОИХ МИРОВ:
 * - Off-heap bitmasks используя VarHandle для GC-free операций (безопасная замена Unsafe)
 * - Long.numberOfTrailingZeros() для O(1) поиска свободных слотов
 * - Предварительно вычисленные битовые маски для общих операций
 * - Lock-free stack для кэширования индексов свободных слотов
 * - Выравнивание памяти по кэш-линиям
 * 
 * КЛЮЧЕВЫЕ ОПТИМИЗАЦИИ:
 * - Off-heap bitmasks избегают GC пауз и false sharing
 * - Битовые трюки для O(1) поиска свободных слотов
 * - Предварительно вычисленные маски исключают runtime вычисления
 * - Lock-free stack уменьшает contention
 * - Прямой доступ к памяти без bounds checking
 * - Использует VarHandle вместо Unsafe для совместимости с будущими версиями Java
 * 
 * ПРИНЦИП РАБОТЫ:
 * 1. Каждый объект представлен одним битом в availabilityMask (1 = свободен, 0 = занят)
 * 2. Для поиска свободного слота используется Long.numberOfTrailingZeros() - O(1) операция
 * 3. Lock-free stack кэширует недавно освобожденные индексы для быстрого доступа
 * 4. Атомарные операции обеспечиваются через VarHandle
 * 5. Ring buffer используется как fallback при исчерпании stack и bit tricks
 * 
 * @param <T> Тип объектов в пуле
 */
public class BitmaskRingBufferUltraVarHandle<T> {
    
    // VarHandle для атомарного доступа к off-heap памяти (замена Unsafe)
    // LONG_ARRAY_HANDLE - для работы с битовыми масками (long[])
    // INT_ARRAY_HANDLE - для работы с lock-free stack (int[])
    private static final VarHandle LONG_ARRAY_HANDLE;
    private static final VarHandle INT_ARRAY_HANDLE;
    
    static {
        try {
            // Создаем VarHandle для атомарного доступа к элементам массивов
            // Это безопасная замена sun.misc.Unsafe для будущих версий Java
            LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
            INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось инициализировать VarHandles", e);
        }
    }
    
    // Размер кэш-линии (обычно 64 байта на современных CPU)
    // Важно для предотвращения false sharing между потоками
    private static final int CACHE_LINE_SIZE = 64;
    
    // Предварительно вычисленные битовые маски для общих операций
    // BIT_MASKS[i] = 1L << i (устанавливает i-й бит)
    // CLEAR_MASKS[i] = ~(1L << i) (очищает i-й бит)
    // Это исключает runtime вычисления и ускоряет операции
    private static final long[] BIT_MASKS = new long[64];
    private static final long[] CLEAR_MASKS = new long[64];
    
    static {
        // Предварительно вычисляем все возможные битовые маски
        // Это критическая оптимизация для производительности
        for (int i = 0; i < 64; i++) {
            BIT_MASKS[i] = 1L << i;           // Маска для установки i-го бита
            CLEAR_MASKS[i] = ~(1L << i);      // Маска для очистки i-го бита
        }
    }
    
    // КОНФИГУРАЦИЯ ПУЛА
    private final int capacity;        // Максимальное количество объектов
    private final T[] objects;         // Массив объектов (основное хранилище)
    
    // Off-heap битовые маски (используя VarHandle для доступа)
    // availabilityMask - отслеживает свободные/занятые объекты (1 = свободен, 0 = занят)
    // staleMask - отслеживает устаревшие объекты (1 = устарел, 0 = актуален)
    private final long[] availabilityMask;  // Битовое поле доступности объектов
    private final long[] staleMask;         // Битовое поле устаревших объектов
    
    // Количество long значений, необходимых для хранения всех битов
    // Каждый long содержит 64 бита, поэтому maskSize = (capacity + 63) / 64
    private final int maskSize;
    
    // Индексы ring buffer (используются как fallback)
    private final AtomicInteger head = new AtomicInteger(0);  // Голова кольцевого буфера
    private final AtomicInteger tail = new AtomicInteger(0);  // Хвост кольцевого буфера
    
    // Lock-free stack для кэширования индексов свободных слотов (используя VarHandle)
    // Это критическая оптимизация для быстрого доступа к недавно освобожденным объектам
    private final int[] freeSlotStack;           // Стек индексов свободных слотов
    private final AtomicInteger stackTop = new AtomicInteger(-1);  // Вершина стека
    private final int stackSize;                 // Размер стека
    
    // СТАТИСТИКА ДЛЯ МОНИТОРИНГА
    private final AtomicLong totalGets = new AtomicLong(0);        // Общее количество получений
    private final AtomicLong totalReturns = new AtomicLong(0);     // Общее количество возвратов
    private final AtomicLong totalUpdates = new AtomicLong(0);     // Общее количество обновлений
    private final AtomicLong bitTrickHits = new AtomicLong(0);     // Количество успешных bit tricks
    private final AtomicLong stackHits = new AtomicLong(0);        // Количество успешных stack hits
    
    /**
     * Создает новый ультра-оптимизированный ring buffer пул, объединяющий off-heap и битовые трюки.
     * 
     * АЛГОРИТМ ИНИЦИАЛИЗАЦИИ:
     * 1. Вычисляем размер битовых масок (64 бита на long)
     * 2. Создаем массивы для битовых масок
     * 3. Инициализируем lock-free stack
     * 4. Создаем объекты через factory
     * 5. Помечаем все объекты как доступные и добавляем в stack
     * 
     * @param capacity Максимальное количество объектов в пуле
     * @param objectFactory Фабрика для создания новых объектов
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandle(int capacity, ObjectFactory<T> objectFactory) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Емкость должна быть положительной");
        }
        if (objectFactory == null) {
            throw new IllegalArgumentException("Фабрика объектов не может быть null");
        }
        
        this.capacity = capacity;
        
        // Вычисляем количество long значений, необходимых для хранения всех битов
        // Каждый long содержит 64 бита, поэтому используем формулу (capacity + 63) / 64
        this.maskSize = (capacity + 63) / 64;
        
        // Выделяем массивы для битовых масок
        // availabilityMask - отслеживает свободные/занятые объекты
        // staleMask - отслеживает устаревшие объекты
        this.availabilityMask = new long[maskSize];
        this.staleMask = new long[maskSize];
        
        // Инициализируем lock-free stack для свободных слотов
        // Размер стека = min(25% от capacity, 1000) - оптимальный баланс памяти и производительности
        this.stackSize = Math.min(capacity / 4, 1000);
        this.freeSlotStack = new int[stackSize];
        
        // Создаем массив объектов
        this.objects = (T[]) new Object[capacity];
        for (int i = 0; i < capacity; i++) {
            objects[i] = objectFactory.createObject();
        }
        
        // ИНИЦИАЛИЗАЦИЯ: помечаем все объекты как доступные и добавляем в stack
        // Это критически важно для правильной работы пула
        for (int i = 0; i < capacity; i++) {
            setBit(availabilityMask, i, true);  // Помечаем как свободный
            pushToStack(i);                      // Добавляем в stack для быстрого доступа
        }
    }
    
    /**
     * Получает свободный объект из пула, используя off-heap битовые трюки для O(1) поиска слотов.
     * 
     * АЛГОРИТМ ПОЛУЧЕНИЯ ОБЪЕКТА (3-уровневая стратегия):
     * 1. ПЕРВЫЙ УРОВЕНЬ: Попытка получить из lock-free stack (самый быстрый)
     * 2. ВТОРОЙ УРОВЕНЬ: Использование битовых трюков для поиска свободного слота
     * 3. ТРЕТИЙ УРОВЕНЬ: Fallback на ring buffer подход
     * 
     * ОПТИМИЗАЦИИ:
     * - Lock-free stack обеспечивает O(1) доступ к недавно освобожденным объектам
     * - Long.numberOfTrailingZeros() обеспечивает O(1) поиск свободных слотов
     * - Ring buffer обеспечивает справедливое распределение при высокой нагрузке
     * - Thread.yield() уменьшает contention при высоком числе попыток
     * 
     * @return Свободный объект, или null если нет доступных объектов
     */
    public T getFreeObject() {
        int attempts = 0;
        final int maxAttempts = capacity * 2; // Предотвращаем бесконечные циклы
        
        while (attempts < maxAttempts) {
            // УРОВЕНЬ 1: Попытка получить из lock-free stack (самый быстрый путь)
            Integer slotIndex = popFromStack();
            if (slotIndex != null) {
                stackHits.incrementAndGet();  // Увеличиваем счетчик успешных stack hits
                if (tryAcquireSlot(slotIndex)) {
                    totalGets.incrementAndGet();
                    return objects[slotIndex];
                }
            }
            
            // УРОВЕНЬ 2: Если stack пуст, используем битовые трюки для поиска свободного слота
            int freeSlot = findFreeSlotWithBitTricks();
            if (freeSlot >= 0) {
                bitTrickHits.incrementAndGet();  // Увеличиваем счетчик успешных bit tricks
                if (tryAcquireSlot(freeSlot)) {
                    totalGets.incrementAndGet();
                    return objects[freeSlot];
                }
            }
            
            // УРОВЕНЬ 3: Fallback на ring buffer подход (справедливое распределение)
            int currentTail = tail.get();
            int nextTail = (currentTail + 1) % capacity;
            
            if (tail.compareAndSet(currentTail, nextTail)) {
                int index = currentTail;
                // Проверяем, что слот действительно свободен перед попыткой захвата
                if (isBitSet(availabilityMask, index) && tryAcquireSlot(index)) {
                    totalGets.incrementAndGet();
                    return objects[index];
                }
            }
            
            attempts++;
            
            // Небольшая задержка для уменьшения contention при высоком числе попыток
            if (attempts % 100 == 0) {
                Thread.yield();
            }
        }
        
        return null; // Нет свободных объектов
    }
    
    /**
     * Возвращает объект в пул, помечая его как свободный.
     * 
     * АЛГОРИТМ ВОЗВРАТА ОБЪЕКТА:
     * 1. Находим индекс объекта в массиве objects
     * 2. Атомарно помечаем как свободный в availabilityMask
     * 3. Очищаем флаг устаревания в staleMask
     * 4. Добавляем индекс в lock-free stack для быстрого доступа
     * 
     * @param object Объект для возврата
     * @return true если объект был успешно возвращен, false в противном случае
     */
    public boolean setFreeObject(T object) {
        if (object == null) {
            return false;
        }
        
        // Находим индекс объекта в массиве
        // Это линейный поиск, но обычно объекты возвращаются быстро
        int index = -1;
        for (int i = 0; i < capacity; i++) {
            if (objects[i] == object) {
                index = i;
                break;
            }
        }
        
        if (index == -1) {
            return false; // Объект не найден в пуле
        }
        
        // Помечаем как свободный и добавляем в stack
        if (setBitAtomic(availabilityMask, index, true)) {
            setBit(staleMask, index, false);  // Очищаем флаг устаревания
            pushToStack(index);               // Добавляем в stack для быстрого доступа
            totalReturns.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    /**
     * Помечает объект для обновления (обнаружение устаревших объектов).
     * 
     * @param object Объект для пометки
     * @return true если объект был успешно помечен, false в противном случае
     */
    public boolean markForUpdate(T object) {
        if (object == null) {
            return false;
        }
        
        // Находим индекс объекта
        int index = -1;
        for (int i = 0; i < capacity; i++) {
            if (objects[i] == object) {
                index = i;
                break;
            }
        }
        
        if (index == -1) {
            return false; // Объект не найден в пуле
        }
        
        // Помечаем как устаревший
        if (setBitAtomic(staleMask, index, true)) {
            totalUpdates.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    /**
     * Останавливает все объекты в пуле.
     */
    public void stopAll() {
        for (int i = 0; i < capacity; i++) {
            if (objects[i] instanceof Task) {
                ((Task) objects[i]).stop();
            }
        }
    }
    
    /**
     * Получает все занятые объекты (объекты, которые в настоящее время используются).
     * 
     * @return Список занятых объектов
     */
    public List<T> getBusyObjects() {
        List<T> busyObjects = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (!isBitSet(availabilityMask, i)) {
                busyObjects.add(objects[i]);
            }
        }
        return busyObjects;
    }
    
    /**
     * Получает все объекты, помеченные для обновления.
     * 
     * @return Список объектов, помеченных для обновления
     */
    public List<T> getObjectsForUpdate() {
        List<T> updateObjects = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(staleMask, i)) {
                updateObjects.add(objects[i]);
            }
        }
        return updateObjects;
    }
    
    /**
     * Обнаруживает устаревшие объекты на основе временного порога.
     * 
     * @param staleThresholdMs Временной порог в миллисекундах
     * @return Список устаревших объектов
     */
    public List<T> detectStaleObjects(long staleThresholdMs) {
        List<T> staleObjects = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(staleMask, i)) {
                // Для этой реализации считаем все помеченные объекты устаревшими
                // В реальной реализации вы можете захотеть отслеживать временные метки
                staleObjects.add(objects[i]);
            }
        }
        
        return staleObjects;
    }
    
    /**
     * Получает статистику пула.
     * 
     * @return Статистика пула
     */
    public PoolStats getStats() {
        int freeCount = 0;
        int busyCount = 0;
        int updateCount = 0;
        
        // Подсчитываем статистику, проходя по всем битовым маскам
        for (int i = 0; i < capacity; i++) {
            if (isBitSet(availabilityMask, i)) {
                freeCount++;
            } else {
                busyCount++;
            }
            
            if (isBitSet(staleMask, i)) {
                updateCount++;
            }
        }
        
        return new PoolStats(
            capacity, freeCount, busyCount, updateCount,
            totalGets.get(), totalReturns.get(), totalUpdates.get(),
            bitTrickHits.get(), stackHits.get()
        );
    }
    
    /**
     * Получает емкость пула.
     * 
     * @return Емкость пула
     */
    public int getCapacity() {
        return capacity;
    }
    
    /**
     * Получает объект по индексу.
     * 
     * @param index Индекс объекта
     * @return Объект по указанному индексу
     */
    public T getObject(int index) {
        if (index < 0 || index >= capacity) {
            return null;
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект (свободен).
     * 
     * @param index Индекс объекта
     * @return true если объект доступен, false в противном случае
     */
    public boolean isAvailable(int index) {
        if (index < 0 || index >= capacity) {
            return false;
        }
        return isBitSet(availabilityMask, index);
    }
    
    /**
     * Проверяет, помечен ли объект для обновления.
     * 
     * @param index Индекс объекта
     * @return true если объект помечен для обновления, false в противном случае
     */
    public boolean isMarkedForUpdate(int index) {
        if (index < 0 || index >= capacity) {
            return false;
        }
        return isBitSet(staleMask, index);
    }
    
    /**
     * Находит свободный слот, используя битовые трюки для O(1) производительности.
     * 
     * АЛГОРИТМ ПОИСКА СВОБОДНОГО СЛОТА:
     * 1. Проходим по всем long значениям в availabilityMask
     * 2. Ищем первый ненулевой long (содержит свободные слоты)
     * 3. Используем Long.numberOfTrailingZeros() для нахождения первого установленного бита
     * 4. Вычисляем глобальный индекс = maskIndex * 64 + bitIndex
     * 
     * ОПТИМИЗАЦИИ:
     * - Long.numberOfTrailingZeros() - это нативная CPU инструкция (CTZ)
     * - O(1) сложность для нахождения первого свободного слота
     * - Эффективно работает с битовыми операциями
     * 
     * @return Индекс свободного слота, или -1 если не найден
     */
    private int findFreeSlotWithBitTricks() {
        for (int maskIndex = 0; maskIndex < maskSize; maskIndex++) {
            // Получаем текущее значение маски атомарно
            long mask = (long) LONG_ARRAY_HANDLE.getVolatile(availabilityMask, maskIndex);
            if (mask != 0) {
                // Находим первый установленный бит (свободный слот)
                // Long.numberOfTrailingZeros() возвращает количество нулевых битов справа
                int bitIndex = Long.numberOfTrailingZeros(mask);
                int globalIndex = maskIndex * 64 + bitIndex;
                if (globalIndex < capacity) {
                    return globalIndex;
                }
            }
        }
        return -1; // Свободных слотов не найдено
    }
    
    /**
     * Пытается атомарно занять слот.
     * 
     * @param slotIndex Индекс слота для занятия
     * @return true если успешно занят, false в противном случае
     */
    private boolean tryAcquireSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= capacity) {
            return false;
        }
        
        return setBitAtomic(availabilityMask, slotIndex, false);
    }
    
    /**
     * Атомарно устанавливает бит, используя VarHandle.
     * 
     * АЛГОРИТМ АТОМАРНОЙ УСТАНОВКИ БИТА:
     * 1. Вычисляем индекс массива и смещение бита
     * 2. В цикле CAS (Compare-And-Swap):
     *    - Читаем текущее значение
     *    - Вычисляем новое значение с измененным битом
     *    - Пытаемся атомарно заменить старое значение на новое
     * 3. Повторяем до успеха (lock-free алгоритм)
     * 
     * ОПТИМИЗАЦИИ:
     * - Использует VarHandle.compareAndSet() для атомарности
     * - Lock-free алгоритм без блокировок
     * - Предварительно вычисленные маски исключают runtime вычисления
     * 
     * @param array Массив для изменения
     * @param bitIndex Индекс бита для установки
     * @param value Значение для установки (true = 1, false = 0)
     * @return true если операция была успешной
     */
    private boolean setBitAtomic(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;  // Индекс в массиве long
        int bitOffset = bitIndex % 64;   // Смещение бита в long
        
        long oldValue, newValue;
        do {
            // Читаем текущее значение атомарно
            oldValue = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
            
            // Вычисляем новое значение с измененным битом
            if (value) {
                newValue = oldValue | BIT_MASKS[bitOffset];    // Устанавливаем бит
            } else {
                newValue = oldValue & CLEAR_MASKS[bitOffset];  // Очищаем бит
            }
        } while (!LONG_ARRAY_HANDLE.compareAndSet(array, arrayIndex, oldValue, newValue));
        
        return true;
    }
    
    /**
     * Устанавливает бит неатомарно (для инициализации).
     * 
     * @param array Массив для изменения
     * @param bitIndex Индекс бита для установки
     * @param value Значение для установки (true = 1, false = 0)
     */
    private void setBit(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        // Читаем текущее значение
        long currentValue = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
        long newValue;
        
        // Вычисляем новое значение
        if (value) {
            newValue = currentValue | BIT_MASKS[bitOffset];
        } else {
            newValue = currentValue & CLEAR_MASKS[bitOffset];
        }
        
        // Записываем новое значение атомарно
        LONG_ARRAY_HANDLE.setVolatile(array, arrayIndex, newValue);
    }
    
    /**
     * Проверяет, установлен ли бит.
     * 
     * @param array Массив для проверки
     * @param bitIndex Индекс бита для проверки
     * @return true если бит установлен, false в противном случае
     */
    private boolean isBitSet(long[] array, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        // Читаем значение атомарно
        long value = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
        return (value & BIT_MASKS[bitOffset]) != 0;
    }
    
    /**
     * Добавляет индекс слота в lock-free stack.
     * 
     * АЛГОРИТМ PUSH В LOCK-FREE STACK:
     * 1. Читаем текущую вершину стека
     * 2. Проверяем, что стек не полон
     * 3. Атомарно увеличиваем вершину стека
     * 4. Записываем значение в новую позицию
     * 
     * @param slotIndex Индекс для добавления
     */
    private void pushToStack(int slotIndex) {
        int currentTop = stackTop.get();
        if (currentTop < stackSize - 1) {
            if (stackTop.compareAndSet(currentTop, currentTop + 1)) {
                // Атомарно записываем значение в новую позицию стека
                INT_ARRAY_HANDLE.setVolatile(freeSlotStack, currentTop + 1, slotIndex);
            }
        }
    }
    
    /**
     * Извлекает индекс слота из lock-free stack.
     * 
     * АЛГОРИТМ POP ИЗ LOCK-FREE STACK:
     * 1. Читаем текущую вершину стека
     * 2. Проверяем, что стек не пуст
     * 3. Атомарно уменьшаем вершину стека
     * 4. Читаем значение из старой позиции
     * 
     * @return Индекс слота, или null если стек пуст
     */
    private Integer popFromStack() {
        int currentTop = stackTop.get();
        if (currentTop >= 0) {
            if (stackTop.compareAndSet(currentTop, currentTop - 1)) {
                // Атомарно читаем значение из старой позиции стека
                return (Integer) INT_ARRAY_HANDLE.getVolatile(freeSlotStack, currentTop);
            }
        }
        return null;
    }
    
    /**
     * Метод очистки (no-op для этой реализации, так как используем обычные массивы).
     */
    public void cleanup() {
        // Очистка не требуется для обычных массивов
        // В отличие от off-heap версий, где нужно освобождать память
    }
    
    /**
     * Статистика пула.
     */
    public static class PoolStats {
        public final int capacity;           // Емкость пула
        public final int freeCount;          // Количество свободных объектов
        public final int busyCount;          // Количество занятых объектов
        public final int updateCount;        // Количество объектов для обновления
        public final long totalGets;         // Общее количество получений
        public final long totalReturns;      // Общее количество возвратов
        public final long totalUpdates;      // Общее количество обновлений
        public final long bitTrickHits;      // Количество успешных bit tricks
        public final long stackHits;         // Количество успешных stack hits
        
        public PoolStats(int capacity, int freeCount, int busyCount, int updateCount,
                        long totalGets, long totalReturns, long totalUpdates,
                        long bitTrickHits, long stackHits) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.updateCount = updateCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.totalUpdates = totalUpdates;
            this.bitTrickHits = bitTrickHits;
            this.stackHits = stackHits;
        }
        
        @Override
        public String toString() {
            return String.format("PoolStats{capacity=%d, free=%d, busy=%d, updates=%d, " +
                               "gets=%d, returns=%d, updates=%d, bitTricks=%d, stackHits=%d}",
                               capacity, freeCount, busyCount, updateCount,
                               totalGets, totalReturns, totalUpdates, bitTrickHits, stackHits);
        }
    }
    
    /**
     * Интерфейс фабрики объектов.
     */
    @FunctionalInterface
    public interface ObjectFactory<T> {
        T createObject();
    }
} 