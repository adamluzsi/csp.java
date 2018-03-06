package com.github.adamluzsi.csp;

import java.util.concurrent.atomic.AtomicInteger;

public class WaitGroup {
    private final AtomicInteger counter = new AtomicInteger();

    private final static String NEGATIVE_WAITER_COUNTER_MESSAGE = "negative WaitGroup counter" +
            "This could happen if the program code have race condition";

    public void add(int n) {
        if ( size() + n < 0 ) {
            throw new IllegalArgumentException(NEGATIVE_WAITER_COUNTER_MESSAGE);
        }

        counter.addAndGet(n);
    }

    public void done() {
        add(-1);
    }

    public synchronized void hold() throws InterruptedException {
        while (size() != 0) {
            wait(1);
        }
    }

    public int size() {
        return counter.get();
    }
}
