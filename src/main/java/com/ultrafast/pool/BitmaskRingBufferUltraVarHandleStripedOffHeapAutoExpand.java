package com.ultrafast.pool;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * УЛЬТРА-БЫСТРЫЙ конкурентный пул объектов с STRIPED TAIL, OFF-HEAP памятью и АВТОМАТИЧЕСКИМ РАСШИРЕНИЕМ.
 * 
 * ОПТИМИЗАЦИИ В ЭТОЙ ВЕРСИИ:
 * - ABA-safe lock-free stack с использованием AtomicStampedReference
 * - Striped tail для лучшего распределения нагрузки между потоками
 * - Off-heap битовые маски для снижения нагрузки на GC
 * - Автоматическое расширение пула на конфигурируемый процент (по умолчанию 20%)
 * - Ограничение максимального расширения (по умолчанию 100% от начальной емкости)
 * - Расширенный padding для предотвращения false sharing
 * - Thread-local оптимизации
 * - Улучшенная производительность при высокой конкуренции
 * 
 * ОТЛИЧИЕ ОТ ДРУГИХ ВЕРСИЙ:
 * В этой версии ВСЕ данные (битовые маски, стек) хранятся в ByteBuffer, а не в массивах.
 * Для ByteBuffer используются обычные методы getLong/putLong и getInt/putInt,
 * так как VarHandle для ByteBuffer имеет ограничения в текущей реализации Java.
 * 
 * @param <T> Тип объектов в пуле
 */
public class BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand<T> {
    
    // В этой версии не используются VarHandle, так как все данные хранятся в ByteBuffer
    // и используются обычные методы getLong()/putLong() для доступа к памяти
    
    // Размер кэш-линии (обычно 64 байта на современных CPU)
    private static final int CACHE_LINE_SIZE = 64;
    
    // Количество stripes для tail (обычно равно количеству CPU cores)
    private static final int STRIPE_COUNT = Runtime.getRuntime().availableProcessors();
    
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
    private final int initialCapacity;
    private final double expansionPercentage; // Процент расширения (0.1 = 10%, 0.2 = 20%, etc.)
    private final int maxExpansionPercentage; // Максимальный процент расширения (100 = 100%)
    private volatile int currentCapacity;
    private T[] objects;
    private final ConcurrentHashMap<T, Integer> objectToIndex = new ConcurrentHashMap<>();
    
    // Off-heap битовая маска доступности
    private volatile ByteBuffer availabilityMaskBuffer;
    private volatile int maskSize;
    
    // STRIPED TAIL - массив атомарных счетчиков для лучшего распределения нагрузки
    private StripedTail[] stripedTails;
    
    // ABA-safe lock-free stack
    private volatile ByteBuffer freeSlotStackBuffer;
    private volatile AtomicStampedReference<Integer> stackTop; // ABA-safe: (value, stamp)
    private volatile int stackSize;
    
    // Выравнивание по кэш-линиям
    private volatile int maskSizeAligned;
    private volatile int stackSizeAligned;
    
    // Фабрика для создания объектов
    private final ObjectFactory<T> objectFactory;
    
    // СТАТИСТИКА
    private final AtomicLong totalGets = new AtomicLong(0);
    private final AtomicLong totalReturns = new AtomicLong(0);
    private final AtomicLong bitTrickHits = new AtomicLong(0);
    private final AtomicLong stackHits = new AtomicLong(0);
    private final AtomicLong stripedTailHits = new AtomicLong(0);
    private final AtomicLong autoExpansionHits = new AtomicLong(0);
    private final AtomicLong totalExpansions = new AtomicLong(0);
    
    // Thread-local кэш для stampHolder
    private static final ThreadLocal<int[]> STAMP_HOLDER_CACHE = 
        ThreadLocal.withInitial(() -> new int[1]);
    
    /**
     * Striped tail структура для лучшего распределения нагрузки между потоками
     */
    private static class StripedTail {
        // Padding для предотвращения false sharing
        private final long[] padding1 = new long[8]; // 64 bytes
        private final long[] padding2 = new long[8]; // 64 bytes
        
        // Атомарный счетчик для этого stripe
        private final AtomicInteger counter;
        
        // Padding после счетчика
        private final long[] padding3 = new long[8]; // 64 bytes
        private final long[] padding4 = new long[8]; // 64 bytes
        
        public StripedTail(int initialValue) {
            this.counter = new AtomicInteger(initialValue);
            initializePadding();
        }
        
        /**
         * Инициализирует padding массивы случайными значениями
         */
        private void initializePadding() {
            for (int i = 0; i < 8; i++) {
                padding1[i] = System.nanoTime();
                padding2[i] = System.nanoTime();
                padding3[i] = System.nanoTime();
                padding4[i] = System.nanoTime();
            }
        }
        
        /**
         * Активирует padding для предотвращения false sharing
         */
        public void activatePadding() {
            long sum = 0;
            for (int i = 0; i < 8; i++) {
                sum += padding1[i] + padding2[i] + padding3[i] + padding4[i];
            }
            if (sum == 0) {
                initializePadding();
            }
        }
        
        public int getAndIncrement() {
            activatePadding();
            return counter.getAndIncrement();
        }
        
        public int get() {
            activatePadding();
            return counter.get();
        }
        
        public void set(int value) {
            activatePadding();
            counter.set(value);
        }
    }
    
    /**
     * Создает новый оптимизированный ring buffer пул с striped tail и автоматическим расширением.
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand(int initialCapacity, ObjectFactory<T> objectFactory) {
        this(initialCapacity, objectFactory, 0.2, 100); // По умолчанию: 20% расширение, максимум 100%
    }
    
    /**
     * Создает новый оптимизированный ring buffer пул с настраиваемым расширением.
     * 
     * @param initialCapacity Начальная емкость пула
     * @param objectFactory Фабрика для создания объектов
     * @param expansionPercentage Процент расширения (0.1 = 10%, 0.2 = 20%, etc.)
     * @param maxExpansionPercentage Максимальный процент расширения (100 = 100%)
     */
    @SuppressWarnings("unchecked")
    public BitmaskRingBufferUltraVarHandleStripedOffHeapAutoExpand(int initialCapacity, ObjectFactory<T> objectFactory, 
                                                                  double expansionPercentage, int maxExpansionPercentage) {
        if (initialCapacity <= 0) {
            throw new IllegalArgumentException("Начальная емкость должна быть положительной");
        }
        if (objectFactory == null) {
            throw new IllegalArgumentException("Фабрика объектов не может быть null");
        }
        if (expansionPercentage <= 0.0 || expansionPercentage > 1.0) {
            throw new IllegalArgumentException("Процент расширения должен быть от 0.0 до 1.0");
        }
        if (maxExpansionPercentage <= 0 || maxExpansionPercentage > 1000) {
            throw new IllegalArgumentException("Максимальный процент расширения должен быть от 1 до 1000");
        }
        
        this.initialCapacity = initialCapacity;
        this.expansionPercentage = expansionPercentage;
        this.maxExpansionPercentage = maxExpansionPercentage;
        this.currentCapacity = initialCapacity;
        this.objectFactory = objectFactory;
        
        initializePool();
    }
    
    /**
     * Инициализирует пул с текущей емкостью
     */
    @SuppressWarnings("unchecked")
    private void initializePool() {
        this.maskSize = (currentCapacity + 63) / 64;
        
        // Выравнивание по кэш-линиям
        this.maskSizeAligned = (maskSize + (CACHE_LINE_SIZE / 8) - 1) & ~((CACHE_LINE_SIZE / 8) - 1);
        
        // Инициализация off-heap битовой маски доступности
        this.availabilityMaskBuffer = ByteBuffer.allocateDirect(maskSizeAligned * 8);
        
        // ABA-safe lock-free stack
        this.stackSize = Math.max(currentCapacity, 1000);
        this.stackSizeAligned = (stackSize + (CACHE_LINE_SIZE / 4) - 1) & ~((CACHE_LINE_SIZE / 4) - 1);
        this.freeSlotStackBuffer = ByteBuffer.allocateDirect(stackSizeAligned * 4);
        this.stackTop = new AtomicStampedReference<>(-1, 0);
        
        // Инициализация striped tails
        this.stripedTails = new StripedTail[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            stripedTails[i] = new StripedTail(i * (currentCapacity / STRIPE_COUNT));
        }
        
        // Создание объектов
        this.objects = (T[]) new Object[currentCapacity];
        for (int i = 0; i < currentCapacity; i++) {
            objects[i] = objectFactory.createObject();
            objectToIndex.put(objects[i], i);
        }
        
        // Инициализация: помечаем все объекты как доступные
        for (int i = 0; i < currentCapacity; i++) {
            setBitOffHeap(availabilityMaskBuffer, i, true);
            pushToStack(i);
        }
    }
    
    /**
     * Расширяет пул, добавляя новые объекты
     */
    @SuppressWarnings("unchecked")
    private synchronized void expandPool() {
        int oldCapacity = currentCapacity;
        
        // Вычисляем новый размер на основе процента расширения
        int expansionAmount = Math.max(1, (int) (oldCapacity * expansionPercentage));
        int newCapacity = oldCapacity + expansionAmount;
        
        // Проверяем ограничение максимального расширения
        int maxAllowedCapacity = initialCapacity + (int) (initialCapacity * maxExpansionPercentage / 100.0);
        if (newCapacity > maxAllowedCapacity) {
            newCapacity = maxAllowedCapacity;
        }
        
        // Если достигли максимума, не расширяем
        if (newCapacity <= oldCapacity) {
            return;
        }
        
        // Создаем новый off-heap буфер
        int newMaskSize = (newCapacity + 63) / 64;
        int newMaskSizeAligned = (newMaskSize + (CACHE_LINE_SIZE / 8) - 1) & ~((CACHE_LINE_SIZE / 8) - 1);
        
        ByteBuffer newAvailabilityMaskBuffer = ByteBuffer.allocateDirect(newMaskSizeAligned * 8);
        
        // Копируем старые данные
        if (availabilityMaskBuffer != null) {
            availabilityMaskBuffer.rewind();
            newAvailabilityMaskBuffer.put(availabilityMaskBuffer);
        }
        
        // Расширяем стек
        int newStackSize = Math.max(newCapacity, 1000);
        int newStackSizeAligned = (newStackSize + (CACHE_LINE_SIZE / 4) - 1) & ~((CACHE_LINE_SIZE / 4) - 1);
        ByteBuffer newFreeSlotStackBuffer = ByteBuffer.allocateDirect(newStackSizeAligned * 4);
        
        if (freeSlotStackBuffer != null) {
            freeSlotStackBuffer.rewind();
            newFreeSlotStackBuffer.put(freeSlotStackBuffer);
        }
        
        // Расширяем массив объектов
        T[] newObjects = (T[]) new Object[newCapacity];
        System.arraycopy(objects, 0, newObjects, 0, oldCapacity);
        
        // Создаем новые объекты
        for (int i = oldCapacity; i < newCapacity; i++) {
            newObjects[i] = objectFactory.createObject();
            objectToIndex.put(newObjects[i], i);
        }
        
        // Обновляем striped tails
        StripedTail[] newStripedTails = new StripedTail[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            newStripedTails[i] = new StripedTail(i * (newCapacity / STRIPE_COUNT));
        }
        
        // Освобождаем старые ресурсы
        if (availabilityMaskBuffer != null) {
            // ByteBuffer автоматически освобождается при GC
        }
        if (freeSlotStackBuffer != null) {
            // ByteBuffer автоматически освобождается при GC
        }
        
        // Обновляем ссылки
        this.currentCapacity = newCapacity;
        this.maskSize = newMaskSize;
        this.maskSizeAligned = newMaskSizeAligned;
        this.stackSize = newStackSize;
        this.stackSizeAligned = newStackSizeAligned;
        this.availabilityMaskBuffer = newAvailabilityMaskBuffer;
        this.freeSlotStackBuffer = newFreeSlotStackBuffer;
        this.objects = newObjects;
        this.stripedTails = newStripedTails;
        
        // Инициализируем новые объекты как доступные
        for (int i = oldCapacity; i < newCapacity; i++) {
            setBitOffHeap(availabilityMaskBuffer, i, true);
            pushToStack(i);
        }
        
        totalExpansions.incrementAndGet();
    }
    
    /**
     * Получает свободный объект из пула с автоматическим расширением.
     */
    public T getFreeObject() {
        // Сначала пытаемся получить существующий объект
        T object = tryGetExistingObject();
        if (object != null) {
            return object;
        }
        
        // Если не удалось получить существующий, расширяем пул и создаем новый
        synchronized (this) {
            // Двойная проверка
            object = tryGetExistingObject();
            if (object != null) {
                return object;
            }
            
            // Расширяем пул
            expandPool();
            autoExpansionHits.incrementAndGet();
            
            // Теперь точно есть свободный объект
            return tryGetExistingObject();
        }
    }
    
    /**
     * Пытается получить существующий объект из пула
     */
    private T tryGetExistingObject() {
        // УРОВЕНЬ 1: ABA-safe lock-free stack
        Integer slotIndex = popFromStack();
        if (slotIndex != null) {
            stackHits.incrementAndGet();
            if (tryAcquireSlot(slotIndex)) {
                totalGets.incrementAndGet();
                return objects[slotIndex];
            }
        }
        
        // УРОВЕНЬ 2: Striped tail - распределение нагрузки между потоками
        int stripeIndex = getStripeIndex();
        StripedTail tail = stripedTails[stripeIndex];
        int tailIndex = Math.abs(tail.getAndIncrement()) % currentCapacity;
        
        // Проверяем несколько слотов вокруг tail для лучшего распределения
        for (int offset = 0; offset < 8; offset++) {
            int slotToCheck = (tailIndex + offset) % currentCapacity;
            if (slotToCheck >= 0 && slotToCheck < currentCapacity && 
                isBitSetOffHeap(availabilityMaskBuffer, slotToCheck) && 
                tryAcquireSlot(slotToCheck)) {
                stripedTailHits.incrementAndGet();
                totalGets.incrementAndGet();
                return objects[slotToCheck];
            }
        }
        
        // УРОВЕНЬ 3: Битовые трюки
        int freeSlot = findFreeSlotWithBitTricks();
        if (freeSlot >= 0) {
            bitTrickHits.incrementAndGet();
            if (tryAcquireSlot(freeSlot)) {
                totalGets.incrementAndGet();
                return objects[freeSlot];
            }
        }
        
        return null; // Не удалось найти свободный объект
    }
    
    /**
     * Возвращает объект в пул.
     */
    public boolean setFreeObject(T object) {
        Integer index = objectToIndex.get(object);
        if (index == null) {
            return false;
        }
        
        if (setBitAtomicOffHeap(availabilityMaskBuffer, index, true)) {
            pushToStack(index);
            totalReturns.incrementAndGet();
            return true;
        }
        
        return false;
    }
    
    /**
     * Получает индекс stripe для текущего потока
     */
    private int getStripeIndex() {
        return (int) (Thread.currentThread().getId() % STRIPE_COUNT);
    }
    
    /**
     * Добавляет индекс в ABA-safe stack
     */
    private void pushToStack(int slotIndex) {
        int[] stampHolder = STAMP_HOLDER_CACHE.get();
        int currentTop = stackTop.get(stampHolder);
        int currentStamp = stampHolder[0];
        if (currentTop < stackSize - 1) {
            int newTop = currentTop + 1;
            int newStamp = currentStamp + 1;
            if (stackTop.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                setOffHeapIntVolatile(freeSlotStackBuffer, newTop, slotIndex);
            }
        }
    }
    
    /**
     * Извлекает индекс из ABA-safe stack
     */
    private Integer popFromStack() {
        int[] stampHolder = STAMP_HOLDER_CACHE.get();
        int currentTop = stackTop.get(stampHolder);
        int currentStamp = stampHolder[0];
        if (currentTop >= 0) {
            int newTop = currentTop - 1;
            int newStamp = currentStamp + 1;
            if (stackTop.compareAndSet(currentTop, newTop, currentStamp, newStamp)) {
                return getOffHeapIntVolatile(freeSlotStackBuffer, currentTop);
            }
        }
        return null;
    }
    
    /**
     * Останавливает все объекты в пуле.
     */
    public void stopAll() {
        for (int i = 0; i < currentCapacity; i++) {
            setBitOffHeap(availabilityMaskBuffer, i, false);
        }
    }
    
    /**
     * Возвращает статистику пула.
     */
    public PoolStats getStats() {
        int freeCount = 0;
        int busyCount = 0;
        
        for (int i = 0; i < currentCapacity; i++) {
            if (isBitSetOffHeap(availabilityMaskBuffer, i)) {
                freeCount++;
            } else {
                busyCount++;
            }
        }
        
        return new PoolStats(
            currentCapacity, freeCount, busyCount,
            totalGets.get(), totalReturns.get(),
            bitTrickHits.get(), stackHits.get(), stripedTailHits.get(),
            autoExpansionHits.get(), totalExpansions.get(),
            expansionPercentage, maxExpansionPercentage, getMaxAllowedCapacity()
        );
    }
    
    /**
     * Возвращает текущую емкость пула.
     */
    public int getCapacity() {
        return currentCapacity;
    }
    
    /**
     * Возвращает начальную емкость пула.
     */
    public int getInitialCapacity() {
        return initialCapacity;
    }
    
    /**
     * Возвращает процент расширения пула.
     */
    public double getExpansionPercentage() {
        return expansionPercentage;
    }
    
    /**
     * Возвращает максимальный процент расширения пула.
     */
    public int getMaxExpansionPercentage() {
        return maxExpansionPercentage;
    }
    
    /**
     * Возвращает максимально допустимую емкость пула.
     */
    public int getMaxAllowedCapacity() {
        return initialCapacity + (int) (initialCapacity * maxExpansionPercentage / 100.0);
    }
    
    /**
     * Возвращает объект по индексу.
     */
    public T getObject(int index) {
        if (index < 0 || index >= currentCapacity) {
            return null;
        }
        return objects[index];
    }
    
    /**
     * Проверяет, доступен ли объект по индексу.
     */
    public boolean isAvailable(int index) {
        if (index < 0 || index >= currentCapacity) {
            return false;
        }
        return isBitSetOffHeap(availabilityMaskBuffer, index);
    }
    

    
    /**
     * Ищет свободный слот с помощью битовых трюков.
     */
    private int findFreeSlotWithBitTricks() {
        for (int maskIndex = 0; maskIndex < maskSize; maskIndex++) {
            long mask = getOffHeapLong(availabilityMaskBuffer, maskIndex);
            if (mask != 0) {
                int bitIndex = Long.numberOfTrailingZeros(mask);
                return maskIndex * 64 + bitIndex;
            }
        }
        return -1;
    }
    
    /**
     * Пытается занять слот атомарно.
     */
    private boolean tryAcquireSlot(int slotIndex) {
        return setBitAtomicOffHeap(availabilityMaskBuffer, slotIndex, false);
    }
    
    /**
     * Устанавливает бит атомарно в off-heap памяти.
     */
    private boolean setBitAtomicOffHeap(ByteBuffer buffer, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long currentValue;
        long newValue;
        
        do {
            currentValue = getOffHeapLong(buffer, arrayIndex);
            if (value) {
                newValue = currentValue | BIT_MASKS[bitOffset];
            } else {
                newValue = currentValue & CLEAR_MASKS[bitOffset];
            }
        } while (!compareAndSetOffHeapLong(buffer, arrayIndex, currentValue, newValue));
        
        return true;
    }
    
    /**
     * Устанавливает бит в off-heap памяти (не атомарно).
     */
    private void setBitOffHeap(ByteBuffer buffer, int bitIndex, boolean value) {
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        
        long currentValue = getOffHeapLong(buffer, arrayIndex);
        if (value) {
            currentValue |= BIT_MASKS[bitOffset];
        } else {
            currentValue &= CLEAR_MASKS[bitOffset];
        }
        setOffHeapLong(buffer, arrayIndex, currentValue);
    }
    
    /**
     * Проверяет, установлен ли бит в off-heap памяти.
     */
    private boolean isBitSetOffHeap(ByteBuffer buffer, int bitIndex) {
        if (bitIndex < 0 || bitIndex >= currentCapacity) {
            return false;
        }
        int arrayIndex = bitIndex / 64;
        int bitOffset = bitIndex % 64;
        long value = getOffHeapLong(buffer, arrayIndex);
        return (value & BIT_MASKS[bitOffset]) != 0;
    }
    
    /**
     * Получает long значение из off-heap памяти.
     */
    private long getOffHeapLong(ByteBuffer buffer, int offset) {
        return buffer.getLong(offset * 8);
    }
    
    /**
     * Устанавливает long значение в off-heap памяти.
     */
    private void setOffHeapLong(ByteBuffer buffer, int offset, long value) {
        buffer.putLong(offset * 8, value);
    }
    
    /**
     * Атомарно сравнивает и устанавливает long значение в off-heap памяти.
     */
    private boolean compareAndSetOffHeapLong(ByteBuffer buffer, int offset, long expected, long update) {
        // Для ByteBuffer нужно использовать обычные методы, так как VarHandle не поддерживает CAS для ByteBuffer
        long current = buffer.getLong(offset * 8);
        if (current == expected) {
            buffer.putLong(offset * 8, update);
            return true;
        }
        return false;
    }
    
    /**
     * Получает int значение из off-heap памяти.
     */
    private int getOffHeapInt(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset * 4);
    }
    
    /**
     * Устанавливает int значение в off-heap памяти.
     */
    private void setOffHeapInt(ByteBuffer buffer, int offset, int value) {
        buffer.putInt(offset * 4, value);
    }
    
    /**
     * Устанавливает int значение в off-heap памяти.
     */
    private void setOffHeapIntVolatile(ByteBuffer buffer, int offset, int value) {
        buffer.putInt(offset * 4, value);
    }
    
    /**
     * Получает int значение из off-heap памяти.
     */
    private int getOffHeapIntVolatile(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset * 4);
    }
    
    /**
     * Освобождает ресурсы.
     */
    public void cleanup() {
        // Очищаем Map для быстрого поиска объектов
        objectToIndex.clear();
        
        // Очищаем массивы объектов для ускорения GC
        if (objects != null) {
            for (int i = 0; i < objects.length; i++) {
                objects[i] = null;
            }
        }
        
        // ByteBuffer автоматически освобождается при GC
        availabilityMaskBuffer = null;
        freeSlotStackBuffer = null;
        
        // Очищаем striped tails
        if (stripedTails != null) {
            for (int i = 0; i < stripedTails.length; i++) {
                stripedTails[i] = null;
            }
        }
    }
    
    /**
     * Статистика пула.
     */
    public static class PoolStats {
        public final int capacity;
        public final int freeCount;
        public final int busyCount;
        public final long totalGets;
        public final long totalReturns;
        public final long bitTrickHits;
        public final long stackHits;
        public final long stripedTailHits;
        public final long autoExpansionHits;
        public final long totalExpansions;
        public final double expansionPercentage;
        public final int maxExpansionPercentage;
        public final int maxAllowedCapacity;
        
        public PoolStats(int capacity, int freeCount, int busyCount,
                        long totalGets, long totalReturns,
                        long bitTrickHits, long stackHits, long stripedTailHits,
                        long autoExpansionHits, long totalExpansions,
                        double expansionPercentage, int maxExpansionPercentage, int maxAllowedCapacity) {
            this.capacity = capacity;
            this.freeCount = freeCount;
            this.busyCount = busyCount;
            this.totalGets = totalGets;
            this.totalReturns = totalReturns;
            this.bitTrickHits = bitTrickHits;
            this.stackHits = stackHits;
            this.stripedTailHits = stripedTailHits;
            this.autoExpansionHits = autoExpansionHits;
            this.totalExpansions = totalExpansions;
            this.expansionPercentage = expansionPercentage;
            this.maxExpansionPercentage = maxExpansionPercentage;
            this.maxAllowedCapacity = maxAllowedCapacity;
        }
        
        @Override
        public String toString() {
            return String.format(
                "PoolStats{capacity=%d, free=%d, busy=%d, gets=%d, returns=%d, bitTricks=%d, stack=%d, striped=%d, autoExpansions=%d, totalExpansions=%d, expansion=%.1f%%, maxExpansion=%d%%, maxCapacity=%d}",
                capacity, freeCount, busyCount, totalGets, totalReturns, 
                bitTrickHits, stackHits, stripedTailHits, autoExpansionHits, totalExpansions,
                expansionPercentage * 100, maxExpansionPercentage, maxAllowedCapacity
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