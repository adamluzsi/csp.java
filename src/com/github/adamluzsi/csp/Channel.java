package com.github.adamluzsi.csp;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

interface Func<T> {
    T call() throws InterruptedException;
}

interface VoidFunc {
    void call() throws InterruptedException;
}

public class Channel<E> implements Closeable, BlockingQueue<E> {
    private final static Thread mainThread = Thread.currentThread();
    private final SynchronousQueue<E> queue = new SynchronousQueue<>();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final List<Thread> threads = new ArrayList<>();
    private final ReentrantLock mutex = new ReentrantLock();

    public static void close(Channel<?> chan) throws IllegalStateException {
        try {
            chan.close();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void close(BlockingQueue<?> queue) throws IllegalStateException {
        close((Channel<?>) queue);
    }

    @Override
    public void close() throws IOException {
        try {
            withLock(() -> {

                if (!ok()) {
                    throwInterruptedException(null);
                }

                this.open.set(false);
                threads.forEach(Thread::interrupt);

            });
        } catch (InterruptedException ex) {
            throw new IOException(ex);

        }
    }

    @Override
    public void put(E e) throws InterruptedException {
        synchronize(() -> {
            queue.put(e);
            return null;
        });
    }

    @Override
    public E take() throws InterruptedException {
        return (E) synchronize(queue::take);
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        try {
            while (ok()) {
                action.accept(take());
            }
        } catch (InterruptedException e) {
            Thread.interrupted();
        }
    }

    @Override
    public <T> T[] toArray(T[] a) {
        ArrayList<E> buffer = new ArrayList<>();
        forEach(buffer::add);
        return buffer.toArray(a);
    }

    @Override
    public Object[] toArray() {
        return toArray(new Object[0]);
    }

    @Override
    public boolean add(E e) {
        try {
            put(e);

            return true;
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean offer(E e) {
        try {
            return (Boolean) synchronize(() -> queue.offer(e));
        } catch (InterruptedException ex) {
            return false;
        }
    }


    //////////////////////////////// dummy methods to implement BlockingQueue interface ////////////////////////////////

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }


    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        try {
            put(e);

            return true;
        } catch (ChannelIsClosed closed) {
            return false;
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return null;
    }

    @Override
    public int remainingCapacity() {
        return 0;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return 0;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        return 0;
    }

    @Override
    public E remove() {
        return null;
    }

    @Override
    public E poll() {
        return null;
    }

    @Override
    public E element() {
        return null;
    }

    @Override
    public E peek() {
        return null;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        return false;
    }

    @Override
    public Spliterator<E> spliterator() {
        return null;
    }

    @Override
    public Stream<E> stream() {
        return null;
    }

    @Override
    public Stream<E> parallelStream() {
        return null;
    }

    //
    // [CORE]
    //
    private Object synchronize(Func<Object> fn) throws InterruptedException {
        try {
            withLock(() -> {
                if (!ok()) {
                    throwInterruptedException(null);
                }

                threads.add(Thread.currentThread());
            });

            return fn.call();
        } catch (InterruptedException ex) {
            throwInterruptedException(ex);
        } finally {
            withLock(() -> threads.remove(Thread.currentThread()));
        }

        throwInterruptedException(null);
        return null;
    }

    private void withLock(VoidFunc func) throws InterruptedException {
        try {
            mutex.lock();

            func.call();
        } finally {
            mutex.unlock();
        }
    }

    private boolean ok() {
        return open.get() && !Thread.currentThread().isInterrupted();
    }

    private void throwInterruptedException(InterruptedException ex) throws InterruptedException {
        if (!open.get()) {
            throw new ChannelIsClosed();
        }

        if (ex != null) {
            throw ex;
        }

        if (Thread.interrupted()) { // Clear interrupt state
            throw new InterruptedException();
        }

        throw new IllegalStateException();
    }
}
