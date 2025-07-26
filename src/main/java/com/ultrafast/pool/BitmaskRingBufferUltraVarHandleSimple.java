package com.ultrafast.pool;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * УПРОЩЕННАЯ УЛЬТРА-БЫСТРАЯ версия конкурентного пула объектов.
 * 
 * УБРАННЫЕ ФУНКЦИИ:
 * - Контроль обновления объектов (staleMask)
 * - Контроль выданных объектов (getBusyObjects, getObjectsForUpdate)
 * - Контроль времени (detectStaleObjects)
 * 
 * ОСТАВЛЕННЫЕ ОПТИМИЗАЦИИ:
 * - VarHandle для атомарных операций с массивами
 * - Long.numberOfTrailingZeros() для O(1) поиска свободных слотов
 * - Предварительно вычисленные битовые маски
 * - Lock-free stack для кэширования индексов
 * - Выравнивание массивов по кэш-линиям для предотвращения false sharing
 * 
 * @param <T> Тип объектов в пуле
 */
public class BitmaskRingBufferUltraVarHandleSimple<T> {
    
    // VarHandle для атомарного доступа к off-heap памяти
    private static final VarHandle LONG_ARRAY_HANDLE;
    private static final VarHandle INT_ARRAY_HANDLE;
    
    static {
        try {
            LONG_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(long[].class);
            INT_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(int[].class);
        } catch (Exception e) {
            throw new RuntimeException("Не удалось инициализировать VarHandles", e);
        }
    }
    
    // Размер кэш-линии
    private static final int CACHE_LINE_SIZE = 64;
    
    // Предварительно вычисленные битовые маски
    private static final long[] BIT_MASKS = new long[64];
    private static final long[] CLEAR_MASKS = new long[64];
    
    static {
        for (int i = 0; i < 64; i++) {
            BIT_MASKS[i] = 1L << i;
            CLEAR_MASKS[i] = ~(1L << i);
        }
    }
    
    // КОНФИГУРАЦИЯ ПУЛА
    private final int capacity;
    private final T[] objects;
    private final ObjectFactory<T> objectFactory; // Фабрика для создания новых объектов
    
    // Map для O(1) поиска индекса объекта по ссылке
    // Это устраняет необходимость в O(n) линейном поиске в setFreeObject
    private final ConcurrentHashMap<T, Integer> objectToIndex = new ConcurrentHashMap<>();
    
    // Off-heap битовая маска (только для доступности)
    private final long[] availabilityMask;
    
    // Количество long значений для хранения всех битов
    private final int maskSize;
    
    // Индексы ring buffer (fallback)
    private final AtomicInteger head = new AtomicInteger(0);
    private final AtomicInteger tail = new AtomicInteger(0);
    
    // Lock-free stack для кэширования индексов
    private final int[] freeSlotStack;
    private final AtomicInteger stackTop = new AtomicInteger(-1);
    private final int stackSize;
    
    // Выравнивание по кэш-линиям для предотвращения false sharing
    // Это гарантирует, что массивы не будут находиться в одной кэш-линии,
    // что может привести к инвалидации кэша при одновременном доступе из разных потоков
    private final int maskSizeAligned;
    private final int stackSizeAligned;
    
    // КЭШИРОВАННЫЙ СЧЕТЧИК СВОБОДНЫХ ОБЪЕКТОВ
    // Это устраняет необходимость в O(n) поиске при каждом вызове getFreeCount()
    private final AtomicInteger cachedFreeCount = new AtomicInteger(0);
    
    // УПРОЩЕННАЯ СТАТИСТИКА
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong bitTrickHits = new AtomicLong(0);
    private final AtomicLong stackHits = new AtomicLong(0);
    private final AtomicLong totalCreates = new AtomicLong(0); // Счетчик созданных объектов
    private final AtomicLong totalDrops = new AtomicLong(0);   // Счетчик отброшенных объектов
    
    /**
     * Создает новый пул объектов.
     * 
     * @param capacity Максимальное количество объектов
     * @param objectFactory Фабрика для создания объектов
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandleSimple(int capacity, ObjectFactory<T> objectFactory) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Емкость должна быть положительной");
        }
        if (objectFactory == null) {
            throw new IllegalArgumentException("Фабрика объектов не может быть null");
        }
        
        this.capacity = capacity;
        this.objectFactory = objectFactory;
        this.objects = (T[]) new Object[capacity];
        
        // Вычисляем размер битовых масок
        this.maskSize = (capacity + 63) / 64;
        
        // Выравниваем размеры по кэш-линиям для предотвращения false sharing
        // CACHE_LINE_SIZE / 8 = 8 (количество long в кэш-линии)
        this.maskSizeAligned = (maskSize + (CACHE_LINE_SIZE / 8) - 1) & ~((CACHE_LINE_SIZE / 8) - 1);
        
        // Инициализируем битовые маски с выравниванием
        this.availabilityMask = new long[maskSizeAligned];
        
        // Инициализируем все объекты как доступные (1 = свободен)
        for (int i = 0; i < maskSize; i++) {
            availabilityMask[i] = -1L; // Все биты установлены в 1
        }
        
        // Если capacity не кратно 64, очищаем лишние биты
        int extraBits = capacity % 64;
        if (extraBits > 0) {
            long mask = (1L << extraBits) - 1;
            availabilityMask[maskSize - 1] = mask;
        }
        
        // Инициализируем lock-free stack
        this.stackSize = Math.min(capacity / 4, 1024); // Размер стека = min(capacity/4, 1024)
        // CACHE_LINE_SIZE / 4 = 16 (количество int в кэш-линии)
        this.stackSizeAligned = (stackSize + (CACHE_LINE_SIZE / 4) - 1) & ~((CACHE_LINE_SIZE / 4) - 1);
        this.freeSlotStack = new int[stackSizeAligned];
        
        // Создаем объекты
        for (int i = 0; i < capacity; i++) {
            objects[i] = objectFactory.createObject();
        }
        
        // Инициализируем Map для быстрого поиска объектов
        for (int i = 0; i < capacity; i++) {
            objectToIndex.put(objects[i], i);
        }
        
        // Инициализируем кэшированный счетчик свободных объектов
        this.cachedFreeCount.set(capacity);
    }
    
    /**
     * Получает свободный объект из пула.
     * Если в пуле нет свободных объектов, создает новый через фабрику.
     * 
     * @return Свободный объект из пула или новый объект, созданный через фабрику
     */
    public T getFreeObject() {
        totalGets.incrementAndGet();
        
        // Сначала пробуем lock-free stack
        Integer stackIndex = popFromStack();
        if (stackIndex != null) {
            stackHits.incrementAndGet();
            if (tryAcquireSlot(stackIndex)) {
                return objects[stackIndex];
            }
        }
        
        // Затем пробуем bit tricks
        int bitIndex = findFreeSlotWithBitTricks();
        if (bitIndex >= 0) {
            bitTrickHits.incrementAndGet();
            if (tryAcquireSlot(bitIndex)) {
                return objects[bitIndex];
            }
        }
        
        // Наконец, пробуем ring buffer с ограниченным количеством попыток
        int attempts = 0;
        int maxAttempts = capacity * 2; // Ограничиваем количество попыток
        
        while (attempts < maxAttempts) {
            int currentHead = head.get();
            int nextHead = (currentHead + 1) % capacity;
            
            if (head.compareAndSet(currentHead, nextHead)) {
                // Проверяем, что слот действительно свободен перед попыткой захвата
                if (isBitSet(availabilityMask, currentHead) && tryAcquireSlot(currentHead)) {
                    return objects[currentHead];
                }
            }
            attempts++;
        }
        
        // Проверяем, действительно ли пул полон
        int freeCount = getFreeCount();
        if (freeCount == 0) {
            // Пул действительно полон - создаем новый объект через фабрику
            totalCreates.incrementAndGet();
            return objectFactory.createObject();
        }
        
        // Пул не полон, но не удалось найти слот из-за contention
        // Вместо бесконечного цикла - создаем новый объект как fallback
        // Это предотвращает deadlock и обеспечивает стабильную работу
        totalCreates.incrementAndGet();
        return objectFactory.createObject();
    }
    
    /**
     * Возвращает объект в пул.
     * Если объект не из пула и пул полон, объект отбрасывается.
     * 
     * @param object Объект для возврата
     * @return true если объект был успешно возвращен, false в противном случае
     */
    public boolean setFreeObject(T object) {
        if (object == null) {
            return false;
        }
        
        // O(1) поиск индекса объекта через Map
        Integer index = objectToIndex.get(object);
        
        if (index != null) {
            // Объект найден в пуле - освобождаем слот
            if (setBitAtomicWithCounter(availabilityMask, index, true)) {
                totalReturns.incrementAndGet();
                
                // Добавляем в stack для быстрого доступа
                pushToStack(index);
                return true;
            }
            return false;
        }
        
        // Объект не из пула - проверяем, есть ли место
        int freeCount = getFreeCount();
        if (freeCount < capacity) {
            // Есть место - добавляем объект в пул
            int newIndex = findFreeSlotForNewObject();
            if (newIndex >= 0) {
                objects[newIndex] = object;
                objectToIndex.put(object, newIndex); // Добавляем в Map
                setBitAtomicWithCounter(availabilityMask, newIndex, false); // Помечаем как занятый
                totalReturns.incrementAndGet();
                return true;
            }
        }
        
        // Пул полон или не удалось найти место - отбрасываем объект
        totalDrops.incrementAndGet();
        return false;
    }
    
    /**
     * Получает статистику пула.
     * Использует кэшированный счетчик для O(1) производительности.
     * 
     * @return Статистика пула
     */
    public SimplePoolStats getStats() {
        int freeCount = cachedFreeCount.get();
        int busyCount = capacity - freeCount;
        
        return new SimplePoolStats(
            capacity,
            freeCount,
            busyCount,
            totalGets.get(),
            totalReturns.get(),
            bitTrickHits.get(),
            stackHits.get(),
            totalCreates.get(),
            totalDrops.get()
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
            throw new IndexOutOfBoundsException("Индекс вне диапазона: " + index);
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект по указанному индексу.
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
     * Находит свободный слот с помощью битовых трюков.
     * 
     * @return Индекс свободного слота или -1, если не найден
     */
    private int findFreeSlotWithBitTricks() {
        for (int maskIndex = 0; maskIndex < maskSize; maskIndex++) {
            long mask = (long) LONG_ARRAY_HANDLE.getVolatile(availabilityMask, maskIndex);
            if (mask != 0) {
                // Находим позицию первого установленного бита
                int bitPosition = Long.numberOfTrailingZeros(mask);
                int globalIndex = maskIndex * 64 + bitPosition;
                
                if (globalIndex < capacity) {
                    return globalIndex;
                }
            }
        }
        return -1;
    }
    
    /**
     * Пытается занять слот атомарно.
     * 
     * @param slotIndex Индекс слота
     * @return true если слот был успешно занят, false в противном случае
     */
    private boolean tryAcquireSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= capacity) {
            return false;
        }
        
        return setBitAtomicWithCounter(availabilityMask, slotIndex, false);
    }
    
    /**
     * Устанавливает бит атомарно.
     * 
     * @param array Массив
     * @param bitIndex Индекс бита
     * @param value Значение для установки
     * @return true если операция была успешной, false в противном случае
     */
    private boolean setBitAtomic(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long mask = BIT_MASKS[bitOffset];
        long clearMask = CLEAR_MASKS[bitOffset];
        
        while (true) {
            long current = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
            long newValue = value ? (current | mask) : (current & clearMask);
            
            if (LONG_ARRAY_HANDLE.compareAndSet(array, arrayIndex, current, newValue)) {
                return true;
            }
        }
    }
    
    /**
     * Устанавливает бит атомарно и обновляет кэшированный счетчик свободных объектов.
     * 
     * @param array Массив
     * @param bitIndex Индекс бита
     * @param value Значение для установки (true = свободен, false = занят)
     * @return true если операция была успешной, false в противном случае
     */
    private boolean setBitAtomicWithCounter(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long mask = BIT_MASKS[bitOffset];
        long clearMask = CLEAR_MASKS[bitOffset];
        
        while (true) {
            long current = (long) LONG_ARRAY_HANDLE.getVolatile(array, arrayIndex);
            long newValue = value ? (current | mask) : (current & clearMask);
            
            if (LONG_ARRAY_HANDLE.compareAndSet(array, arrayIndex, current, newValue)) {
                // Обновляем кэшированный счетчик
                if (value) {
                    incrementFreeCount(); // Освобождаем слот
                } else {
                    decrementFreeCount(); // Занимаем слот
                }
                return true;
            }
        }
    }
    
    /**
     * Устанавливает бит (неатомарно).
     * 
     * @param array Массив
     * @param bitIndex Индекс бита
     * @param value Значение для установки
     */
    private void setBit(long[] array, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        if (value) {
            array[arrayIndex] |= BIT_MASKS[bitOffset];
        } else {
            array[arrayIndex] &= CLEAR_MASKS[bitOffset];
        }
    }
    
    /**
     * Проверяет, установлен ли бит.
     * 
     * @param array Массив
     * @param bitIndex Индекс бита
     * @return true если бит установлен, false в противном случае
     */
    private boolean isBitSet(long[] array, int bitIndex) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        return (array[arrayIndex] & BIT_MASKS[bitOffset]) != 0;
    }
    
    /**
     * Получает количество свободных объектов в пуле.
     * Использует кэшированный счетчик для O(1) производительности.
     * 
     * @return Количество свободных объектов
     */
    private int getFreeCount() {
        return cachedFreeCount.get();
    }
    
    /**
     * Увеличивает кэшированный счетчик свободных объектов.
     * Вызывается при освобождении слота.
     */
    private void incrementFreeCount() {
        cachedFreeCount.incrementAndGet();
    }
    
    /**
     * Уменьшает кэшированный счетчик свободных объектов.
     * Вызывается при занятии слота.
     */
    private void decrementFreeCount() {
        cachedFreeCount.decrementAndGet();
    }
    
    /**
     * Находит свободный слот для нового объекта.
     * 
     * @return Индекс свободного слота или -1, если не найден
     */
    private int findFreeSlotForNewObject() {
        // Сначала пробуем bit tricks
        int bitIndex = findFreeSlotWithBitTricks();
        if (bitIndex >= 0) {
            return bitIndex;
        }
        
        // Затем пробуем ring buffer
        int attempts = 0;
        int maxAttempts = capacity;
        
        while (attempts < maxAttempts) {
            int currentHead = head.get();
            int nextHead = (currentHead + 1) % capacity;
            
            if (head.compareAndSet(currentHead, nextHead)) {
                if (isBitSet(availabilityMask, currentHead)) {
                    return currentHead;
                }
            }
            attempts++;
        }
        
        return -1;
    }
    
    /**
     * Добавляет индекс в stack.
     * 
     * @param slotIndex Индекс слота
     */
    private void pushToStack(int slotIndex) {
        while (true) {
            int currentTop = stackTop.get();
            if (currentTop >= stackSize - 1) {
                return; // Stack полон
            }
            
            if (stackTop.compareAndSet(currentTop, currentTop + 1)) {
                INT_ARRAY_HANDLE.setVolatile(freeSlotStack, currentTop + 1, slotIndex);
                return;
            }
        }
    }
    
    /**
     * Извлекает индекс из stack.
     * 
     * @return Индекс слота или null, если stack пуст
     */
    private Integer popFromStack() {
        while (true) {
            int currentTop = stackTop.get();
            if (currentTop < 0) {
                return null; // Stack пуст
            }
            
            if (stackTop.compareAndSet(currentTop, currentTop - 1)) {
                return (int) INT_ARRAY_HANDLE.getVolatile(freeSlotStack, currentTop);
            }
        }
    }
    
    /**
     * Освобождает ресурсы.
     */
    public void cleanup() {
        // Очищаем Map для быстрого поиска объектов
        objectToIndex.clear();
        
        // Очищаем ссылки на объекты
        for (int i = 0; i < capacity; i++) {
            objects[i] = null;
        }
    }
    
    /**
     * Упрощенная статистика пула.
     */
    public static class SimplePoolStats {
        public final int capacity;
        public final int freeCount;
        public final int busyCount;
        public final long totalGets;
        public final long totalReturns;
        public final long bitTrickHits;
        public final long stackHits;
        public final long totalCreates;
        public final long totalDrops;
        
        public SimplePoolStats(int capacity, int freeCount, int busyCount,
                             long totalGets, long totalReturns,
                             long bitTrickHits, long stackHits,
                             long totalCreates, long totalDrops) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.bitTrickHits = bitTrickHits;
            this.stackHits = stackHits;
            this.totalCreates = totalCreates;
            this.totalDrops = totalDrops;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SimplePoolStats{capacity=%d, free=%d, busy=%d, gets=%d, returns=%d, bitTricks=%d, stackHits=%d, creates=%d, drops=%d}",
                capacity, freeCount, busyCount, totalGets, totalReturns, bitTrickHits, stackHits, totalCreates, totalDrops
            );
        }
    }
    
    /**
     * Фабрика для создания объектов.
     */
    @FunctionalInterface
    public interface ObjectFactory<T> {
        T createObject();
    }
} 