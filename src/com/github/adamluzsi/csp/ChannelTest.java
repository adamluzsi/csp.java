package com.github.adamluzsi.csp;

import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

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
    public void testAdd() throws Exception {
        Channel<Integer> subject = new Channel<>();

        e.submit(subject::take);

        assertTrue(e.submit(() -> subject.add(42)).get(1, TimeUnit.SECONDS));
        subject.close();
        assertFalse(e.submit(() -> subject.add(42)).get(1, TimeUnit.SECONDS));
    }

}