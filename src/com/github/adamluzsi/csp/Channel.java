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
    private final BlockingQueue<E> queue = new SynchronousQueue<>();
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

    ////////////////////////////////////////////////// proxy  methods //////////////////////////////////////////////////

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
    public boolean add(E e) {
        try {
            return (boolean) synchronize(() -> queue.add(e));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean offer(E e) {
        try {
            return (boolean) synchronize(() -> queue.offer(e));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        return (boolean) synchronize(() -> queue.offer(e, timeout, unit));
    }

    @Override
    public Iterator<E> iterator() {
        try {
            return (Iterator<E>) synchronize(queue::iterator);
        } catch (InterruptedException ex) {
            return Collections.emptyIterator();
        }
    }

    @Override
    public E poll() {
        try {
            return (E) synchronize(queue::poll);
        } catch (InterruptedException ex) {
            return null;
        }
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        return (E) synchronize(() -> queue.poll(timeout, unit));
    }

    @Override
    public int remainingCapacity() {
        try {
            return (int) synchronize(queue::remainingCapacity);
        } catch (InterruptedException ex) {
            return 0;
        }
    }

    @Override
    public boolean remove(Object o) {
        try {
            return (boolean) synchronize(() -> queue.remove(o));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean contains(Object o) {
        try {
            return (boolean) synchronize(() -> queue.contains(o));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        try {
            return (int) synchronize(() -> queue.drainTo(c));
        } catch (InterruptedException ex) {
            return 0;
        }
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        try {
            return (int) synchronize(() -> queue.drainTo(c, maxElements));
        } catch (InterruptedException ex) {
            return 0;
        }
    }

    @Override
    public E remove() {
        try {
            return (E) synchronize(queue::remove);
        } catch (InterruptedException ex) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public E element() {
        try {
            return (E) synchronize(queue::element);
        } catch (InterruptedException ex) {
            throw new NoSuchElementException();
        }
    }

    @Override
    public E peek() {
        try {
            return (E) synchronize(queue::peek);
        } catch (InterruptedException ex) {
            return null;
        }
    }

    @Override
    public int size() {
        try {
            return (int) synchronize(queue::size);
        } catch (InterruptedException ex) {
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        try {
            return (boolean) synchronize(queue::isEmpty);
        } catch (InterruptedException ex) {
            return true;
        }
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        try {
            return (boolean) synchronize(() -> queue.containsAll(c));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        try {
            return (boolean) synchronize(() -> queue.addAll(c));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        try {
            return (boolean) synchronize(() -> queue.removeAll(c));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        try {
            return (boolean) synchronize(() -> queue.retainAll(c));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            synchronize(() -> {
                queue.clear();

                return null;
            });
        } catch (InterruptedException ex) {
            // ignore
        }
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        try {
            return (boolean) synchronize(() -> queue.removeIf(filter));
        } catch (InterruptedException ex) {
            return false;
        }
    }

    @Override
    public Spliterator<E> spliterator() {
        try {
            return (Spliterator<E>) synchronize(queue::spliterator);
        } catch (InterruptedException ex) {
            return null;
        }
    }

    @Override
    public Stream<E> stream() {
        try {
            return (Stream<E>) synchronize(queue::stream);
        } catch (InterruptedException ex) {
            return null;
        }
    }

    @Override
    public Stream<E> parallelStream() {
        try {
            return (Stream<E>) synchronize(queue::parallelStream);
        } catch (InterruptedException ex) {
            return null;
        }
    }
}
