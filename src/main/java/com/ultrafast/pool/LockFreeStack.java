package com.ultrafast.pool;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free stack (Treiber stack) для хранения индексов свободных объектов
 */
public class LockFreeStack {
    private static class Node {
        final int index;
        final AtomicReference<Node> next;
        
        Node(int index) {
            this.index = index;
            this.next = new AtomicReference<>(null);
        }
    }
    
    private final AtomicReference<Node> head = new AtomicReference<>(null);
    private final AtomicReference<Integer> size = new AtomicReference<>(0);
    
    /**
     * Добавляет индекс в стек
     */
    public void push(int index) {
        Node newNode = new Node(index);
        Node oldHead;
        do {
            oldHead = head.get();
            newNode.next.set(oldHead);
        } while (!head.compareAndSet(oldHead, newNode));
        
        size.updateAndGet(s -> s + 1);
    }
    
    /**
     * Извлекает индекс из стека
     * @return индекс или -1 если стек пуст
     */
    public int pop() {
        Node oldHead, newHead;
        do {
            oldHead = head.get();
            if (oldHead == null) {
                return -1; // Стек пуст
            }
            newHead = oldHead.next.get();
        } while (!head.compareAndSet(oldHead, newHead));
        
        size.updateAndGet(s -> Math.max(0, s - 1));
        return oldHead.index;
    }
    
    /**
     * Проверяет, пуст ли стек
     */
    public boolean isEmpty() {
        return head.get() == null;
    }
    
    /**
     * Возвращает размер стека (приблизительный)
     */
    public int size() {
        return size.get();
    }
    
    /**
     * Очищает стек
     */
    public void clear() {
        head.set(null);
        size.set(0);
    }
} 