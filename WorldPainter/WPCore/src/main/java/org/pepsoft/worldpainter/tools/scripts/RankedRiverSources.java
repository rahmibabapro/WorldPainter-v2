package org.pepsoft.worldpainter.tools.scripts;

import java.util.NoSuchElementException;
import java.util.function.IntBinaryOperator;

/** Fixed-size primitive heap: retains every eligible source, not whole route lists. */
final class RankedRiverSources {
    RankedRiverSources(int capacity, IntBinaryOperator order) {
        heap = new int[capacity]; this.order = order;
    }
    void add(int source) {
        if(size==heap.length)throw new IllegalStateException("Source heap capacity exceeded");
        int i=size++;
        while(i>0) {
            int p=(i-1)/2;
            if(order.applyAsInt(source,heap[p])<=0)break;
            heap[i]=heap[p];i=p;
        }
        heap[i]=source;
    }
    boolean isEmpty(){return size==0;}
    int remove() {
        if(isEmpty())throw new NoSuchElementException();
        int result=heap[0],last=heap[--size],i=0;
        while(i*2+1<size) {
            int child=i*2+1;
            if(child+1<size&&order.applyAsInt(heap[child+1],heap[child])>0)child++;
            if(order.applyAsInt(last,heap[child])>=0)break;
            heap[i]=heap[child];i=child;
        }
        if(size>0)heap[i]=last;
        return result;
    }
    private final int[] heap;
    private final IntBinaryOperator order;
    private int size;
}
