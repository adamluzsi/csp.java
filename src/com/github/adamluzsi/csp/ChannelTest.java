package com.github.adamluzsi.csp;

import org.testng.annotations.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.testng.Assert.*;

public class ChannelTest {
    private ExecutorService e = Executors.newWorkStealingPool();

    @Test
    public void testClose_ChannelIsOpen_ChannelIsClosed() throws Exception {
        Channel<String> subject = new Channel<>();

        subject.close();

        assertThrows(IOException.class, subject::close);
    }

    @Test
    public void testStaticClose_ChannelIsOpen_ChannelIsClosed() {
        Channel<String> subject = new Channel<>();

        Channel.close(subject);

        assertThrows(IllegalStateException.class, () -> Channel.close(subject));
    }

    @Test
    public void testStaticCloseBlockingQueue_ChannelIsOpen_ChannelIsClosed() {
        BlockingQueue<String> subject = new Channel<>();

        Channel.close(subject);

        assertThrows(IllegalStateException.class, () -> Channel.close(subject));
    }


    @Test
    public void testPutAndTake_ValuePut_ValueTaken() throws Exception {
        Channel<String> subject = new Channel<>();
        final String expectedElement = "Hello, world!";

        e.submit(() -> {
            try {
                subject.put(expectedElement);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Future<String> f = e.submit(() -> {
            try {
                return subject.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        });

        assertEquals(f.get(1, TimeUnit.SECONDS), expectedElement);
    }


    @Test
    public void testPutAndTake_ChannelIsClosedAndValuePut_BotPutAndTakeFails() throws Exception {
        Channel<String> subject = new Channel<>();
        subject.close();

        final String expectedElement = "Hello, world!";

        Future<ChannelIsClosed> fput = e.submit(() -> {
            try {
                subject.put(expectedElement);
            } catch (ChannelIsClosed e) {
                return e;
            }
            return null;
        });

        Future<ChannelIsClosed> ftake = e.submit(() -> {
            try {
                subject.take();
            } catch (ChannelIsClosed e) {
                return e;
            }

            return null;
        });

        assertEquals(fput.get(1, TimeUnit.SECONDS).getClass(), ChannelIsClosed.class);
        assertEquals(ftake.get(1, TimeUnit.SECONDS).getClass(), ChannelIsClosed.class);
    }

    @Test
    public void testTake_TakeBeginAndThanChannelClosed_TakeNotBlockButReturnWithException() throws Exception {
        Channel<String> subject = new Channel<>();

        Future<ChannelIsClosed> ftake = e.submit(() -> {
            try {
                subject.take();
            } catch (ChannelIsClosed e) {
                return e;
            }

            return null;
        });

        subject.close();
        assertEquals(ftake.get(1, TimeUnit.SECONDS).getClass(), ChannelIsClosed.class);
    }


    @Test
    public synchronized void testPut_PutBeginAndThanChannelClosed_PutNotBlockAnymoreButReturnWithException() throws Exception {
        Channel<String> subject = new Channel<>();

        Future<ChannelIsClosed> future = e.submit(() -> {
            try {
                subject.put("Hello, World!");
            } catch (ChannelIsClosed e) {
                return e;
            }

            return null;
        });

        wait(42);
        subject.close();
        wait(TimeUnit.MILLISECONDS.toMillis(42));

        try {
            Class<? extends ChannelIsClosed> klass = future.get(2, TimeUnit.MILLISECONDS).getClass();

            assertEquals(klass, ChannelIsClosed.class);
        } catch (InterruptedException | ExecutionException ex) {
            fail(ex.getClass().getName() + ": " + ex.getMessage() + ex.getCause());
        } catch (TimeoutException te) {
            fail("dealine extended");
        }

    }


    @Test
    public void testForEach_OfferValuesInTheChannelThanCloseIt_ReceiveValuesAndBreakOnClosedChannel() throws Exception {
        Channel<Integer> subject = new Channel<>();

        Future<List<Integer>> fForEach = e.submit(() -> {
            List<Integer> result = new ArrayList<>();
            subject.forEach(result::add); // for explicit forEach use
            return result;
        });

        List<Integer> expected = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            subject.put(i);

            expected.add(i);
        }

        subject.close();

        List<Integer> actual = fForEach.get(1, TimeUnit.SECONDS);

        assertEquals(actual, expected);

    }


    @Test
    public void testToArray_OfferValuesInTheChannelThanCloseIt_ReceiveValuesAndBreakOnClosedChannel() throws Exception {
        Channel<Integer> subject = new Channel<>();

        Future<Integer[]> fres = e.submit(() -> subject.toArray(new Integer[0]));

        for (int i = 0; i < 10; i++) {
            subject.put(i);
        }

        subject.close();

        Object[] values = fres.get(1, TimeUnit.SECONDS);

        for (int i = 0; i < 10; i++) {
            assertEquals(values[i], i);
        }

    }


    @Test
    public void testToArrayWithType_OfferValuesInTheChannelThanCloseIt_ReceiveValuesAndBreakOnClosedChannel() throws Exception {
        Channel<Integer> subject = new Channel<>();

        Future<Integer[]> fres = e.submit(() -> subject.toArray(new Integer[0]));

        Integer[] expectedValues = new Integer[10];

        for (int i = 0; i < 10; i++) {
            subject.put(i);
            expectedValues[i] = i;
        }

        subject.close();

        assertEquals(fres.get(1, TimeUnit.SECONDS), expectedValues);

    }

    @Test
    public void testOffer() throws Exception {
        Channel<Integer> subject = new Channel<>();

        e.submit(subject::take);

        Callable<Boolean> tryOffer = () -> {
            for (int i = 0; i < 1000; i++) {
                if (subject.offer(42)) {
                    return Boolean.TRUE;
                }
            }

            return Boolean.FALSE;
        };

        // will be taken
        assertTrue(e.submit(tryOffer).get(1, TimeUnit.SECONDS));

        // the take already received one, no one take this
        assertFalse(e.submit(tryOffer).get(1, TimeUnit.SECONDS));

        // okey, but what if someone there to take
        e.submit(subject::take);

        // but than closed
        subject.close();

        // so on closed channel there will be again a false
        assertFalse(e.submit(tryOffer).get(1, TimeUnit.SECONDS));

    }

    @Test
    public void testOfferWithDeadline() throws Exception {
        Channel<Integer> subject = new Channel<>();

        e.submit(subject::take);

        Callable<Boolean> tryOffer = () -> {
            for (int i = 0; i < 500; i++) {
                if (subject.offer(42, 1, TimeUnit.MILLISECONDS)) {
                    return Boolean.TRUE;
                }
            }

            return Boolean.FALSE;
        };

        // will be taken
        assertTrue(e.submit(tryOffer).get(1, TimeUnit.SECONDS));

        // the take already received one, no one take this
        assertFalse(e.submit(tryOffer).get(1, TimeUnit.SECONDS));

        // okey, but what if someone there to take
        e.submit(subject::take);

        // but than closed
        subject.close();

        // so on closed channel there will be again a false
        assertThrows(ChannelIsClosed.class, () -> subject.offer(42, 1, TimeUnit.MILLISECONDS));

    }

    @Test
    public void testRemainingQueueMethodsAreProxied() throws Exception {
        Channel<Integer> subject = new Channel<>();
        ProxyQueue<Integer> bq = new ProxyQueue<>();

        long timeout = 42;
        int maxElements = 42;
        TimeUnit unit = TimeUnit.SECONDS;

        Field field = Channel.class.getDeclaredField("queue");
        field.setAccessible(true);
        field.set(subject, bq);

        subject.add(null);
        assertNotNull(bq.calledMethods.get("add"));

        subject.offer(null);
        assertNotNull(bq.calledMethods.get("offer"));

        subject.offer(null, timeout, unit);
        assertNotNull(bq.calledMethods.get("offer with timeout"));

        subject.iterator();
        assertNotNull(bq.calledMethods.get("iterator"));

        subject.poll();
        assertNotNull(bq.calledMethods.get("poll"));

        subject.poll(timeout, unit);
        assertNotNull(bq.calledMethods.get("poll with timeout"));

        subject.remainingCapacity();
        assertNotNull(bq.calledMethods.get("remainingCapacity"));

        subject.remove(null);
        assertNotNull(bq.calledMethods.get("remove"));

        subject.contains(null);
        assertNotNull(bq.calledMethods.get("contains"));

        subject.drainTo(null);
        assertNotNull(bq.calledMethods.get("drainTo"));

        subject.drainTo(null, maxElements);
        assertNotNull(bq.calledMethods.get("drainTo with maxElements"));

        subject.remove();
        assertNotNull(bq.calledMethods.get("remove"));

        subject.element();
        assertNotNull(bq.calledMethods.get("element"));

        subject.peek();
        assertNotNull(bq.calledMethods.get("peek"));

        subject.size();
        assertNotNull(bq.calledMethods.get("size"));

        subject.isEmpty();
        assertNotNull(bq.calledMethods.get("isEmpty"));

        subject.containsAll(null);
        assertNotNull(bq.calledMethods.get("containsAll"));

        subject.addAll(null);
        assertNotNull(bq.calledMethods.get("addAll"));

        subject.removeAll(null);
        assertNotNull(bq.calledMethods.get("removeAll"));

        subject.retainAll(null);
        assertNotNull(bq.calledMethods.get("retainAll"));

        subject.clear();
        assertNotNull(bq.calledMethods.get("clear"));

        subject.removeIf(null);
        assertNotNull(bq.calledMethods.get("removeIf"));

        subject.spliterator();
        assertNotNull(bq.calledMethods.get("spliterator"));

        subject.stream();
        assertNotNull(bq.calledMethods.get("stream"));

        subject.parallelStream();
        assertNotNull(bq.calledMethods.get("parallelStream"));

    }

    @Test
    public void testRemainingQueueMethodsAreProxied_OnClosedChannel_TheyGetInteruptedBeforeEvenMakeACall() throws Exception {
        Channel<Integer> subject = new Channel<>();
        ProxyQueue<Integer> bq = new ProxyQueue<>();

        long timeout = 42;
        int maxElements = 42;
        TimeUnit unit = TimeUnit.SECONDS;

        Field field = Channel.class.getDeclaredField("queue");
        field.setAccessible(true);
        field.set(subject, bq);

        subject.close();

        subject.add(null);
        assertNull(bq.calledMethods.get("add"));

        subject.offer(null);
        assertNull(bq.calledMethods.get("offer"));

        assertThrows(ChannelIsClosed.class, () -> subject.offer(null, timeout, unit));
        assertNull(bq.calledMethods.get("offer with timeout"));

        subject.iterator();
        assertNull(bq.calledMethods.get("iterator"));

        subject.poll();
        assertNull(bq.calledMethods.get("poll"));

        assertThrows(ChannelIsClosed.class, () -> subject.poll(timeout, unit));
        assertNull(bq.calledMethods.get("poll with timeout"));

        subject.remainingCapacity();
        assertNull(bq.calledMethods.get("remainingCapacity"));

        subject.remove(null);
        assertNull(bq.calledMethods.get("remove"));

        subject.contains(null);
        assertNull(bq.calledMethods.get("contains"));

        subject.drainTo(null);
        assertNull(bq.calledMethods.get("drainTo"));

        subject.drainTo(null, maxElements);
        assertNull(bq.calledMethods.get("drainTo with maxElements"));

        assertThrows(NoSuchElementException.class, subject::remove);
        assertNull(bq.calledMethods.get("remove"));

        assertThrows(NoSuchElementException.class, subject::element);
        assertNull(bq.calledMethods.get("element"));

        subject.peek();
        assertNull(bq.calledMethods.get("peek"));

        subject.size();
        assertNull(bq.calledMethods.get("size"));

        subject.isEmpty();
        assertNull(bq.calledMethods.get("isEmpty"));

        subject.containsAll(null);
        assertNull(bq.calledMethods.get("containsAll"));

        subject.addAll(null);
        assertNull(bq.calledMethods.get("addAll"));

        subject.removeAll(null);
        assertNull(bq.calledMethods.get("removeAll"));

        subject.retainAll(null);
        assertNull(bq.calledMethods.get("retainAll"));

        subject.clear();
        assertNull(bq.calledMethods.get("clear"));

        subject.removeIf(null);
        assertNull(bq.calledMethods.get("removeIf"));

        subject.spliterator();
        assertNull(bq.calledMethods.get("spliterator"));

        subject.stream();
        assertNull(bq.calledMethods.get("stream"));

        subject.parallelStream();
        assertNull(bq.calledMethods.get("parallelStream"));

    }

}


class ProxyQueue<E> implements BlockingQueue<E> {
    public Map<String, Object> calledMethods = new HashMap<>();

    @Override
    public boolean add(E integer) {
        calledMethods.put("add", new Object());
        return false;
    }

    @Override
    public boolean offer(E integer) {
        calledMethods.put("offer", new Object());
        return false;
    }

    @Override
    public void put(E integer) throws InterruptedException {
        calledMethods.put("put", new Object());
    }

    @Override
    public boolean offer(E integer, long timeout, TimeUnit unit) throws InterruptedException {
        calledMethods.put("offer with timeout", new Object());

        return false;
    }

    @Override
    public E take() throws InterruptedException {
        calledMethods.put("take", new Object());

        return null;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        calledMethods.put("poll with timeout", new Object());

        return null;
    }

    @Override
    public int remainingCapacity() {
        calledMethods.put("remainingCapacity", new Object());
        return 0;
    }

    @Override
    public boolean remove(Object o) {
        calledMethods.put("remove", new Object());
        return false;
    }

    @Override
    public boolean contains(Object o) {
        calledMethods.put("contains", new Object());
        return false;
    }


    @Override
    public int drainTo(Collection<? super E> c) {
        calledMethods.put("drainTo", new Object());
        return 0;
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        calledMethods.put("drainTo with maxElements", new Object());
        return 0;
    }

    @Override
    public E remove() {
        calledMethods.put("remove", new Object());
        return null;
    }

    @Override
    public E poll() {
        calledMethods.put("poll", new Object());
        return null;
    }

    @Override
    public E element() {
        calledMethods.put("element", new Object());
        return null;
    }

    @Override
    public E peek() {
        calledMethods.put("peek", new Object());
        return null;
    }

    @Override
    public int size() {
        calledMethods.put("size", new Object());
        return 0;
    }

    @Override
    public boolean isEmpty() {
        calledMethods.put("isEmpty", new Object());
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        calledMethods.put("iterator", new Object());
        return null;
    }

    @Override
    public Object[] toArray() {
        calledMethods.put("toArray", new Object());
        return new Object[0];
    }

    @Override
    public <T> T[] toArray(T[] a) {
        calledMethods.put("toArray with type", new Object());
        return null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        calledMethods.put("containsAll", new Object());
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        calledMethods.put("addAll", new Object());
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        calledMethods.put("removeAll", new Object());
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        calledMethods.put("retainAll", new Object());
        return false;
    }

    @Override
    public void clear() {
        calledMethods.put("clear", new Object());
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        calledMethods.put("removeIf", new Object());
        return false;
    }

    @Override
    public Spliterator<E> spliterator() {
        calledMethods.put("spliterator", new Object());
        return null;
    }

    @Override
    public Stream<E> stream() {
        calledMethods.put("stream", new Object());
        return null;
    }

    @Override
    public Stream<E> parallelStream() {
        calledMethods.put("parallelStream", new Object());
        return null;
    }

    @Override
    public void forEach(Consumer<? super E> action) {
        calledMethods.put("forEach", new Object());
    }

}