package com.github.adamluzsi.csp;

import org.testng.annotations.Test;

import java.util.concurrent.*;

import static org.testng.Assert.*;

public class WaitGroupTest {
    private ExecutorService es = Executors.newCachedThreadPool();

    @Test
    public void testSize_CounterAtInitial_ZeroReturned() throws Exception {
        WaitGroup wg = new WaitGroup();

        assertEquals(wg.size(), 0);
    }

    @Test
    public void testAdd_NegativeValueAddedWhileSizeBiggerThanTheGivenValue_CounterDecremented() throws Exception {
        WaitGroup wg = new WaitGroup();

        wg.add(2);
        wg.add(-1);

        assertEquals(wg.size(), 1);
    }

    @Test
    public void testAdd_NegativeValueAddedSizePlusValueWillBeSmallerInTotal_ExceptionRaied() throws Exception {
        WaitGroup wg = new WaitGroup();

        assertThrows(IllegalArgumentException.class, () -> wg.add(-1));
    }

    @Test
    public void testAdd_PositiveValueAdded_CounterIncremented() throws Exception {
        WaitGroup wg = new WaitGroup();

        wg.add(1);

        assertEquals(wg.size(), 1);
    }


    @Test
    public void testDone_WhileTheCounterIsPositiv_CounterDecremented() throws Exception {
        WaitGroup wg = new WaitGroup();

        wg.add(1);
        wg.done();

        assertEquals(wg.size(), 0);
    }

    @Test
    public void testDone_WhileTheCounterIsZero_ExceptionThrown() throws Exception {
        WaitGroup wg = new WaitGroup();

        assertThrows(IllegalArgumentException.class, wg::done);
    }


    class HoldRunner implements Runnable {
        private final WaitGroup wg;
        private InterruptedException ex;

        HoldRunner(WaitGroup wg) {
            this.wg = wg;
        }

        public void run() {
            try {
                wg.hold();
            } catch (InterruptedException e) {
                this.ex = e;
            }
        }
    }

    @Test
    public void testHold_WhenEverythingDone_NotBlocking() throws Exception {
        WaitGroup wg = new WaitGroup();
        HoldRunner hr = new HoldRunner(wg);
        final Future future = es.submit(hr);
        boolean raised = false;

        try {
            future.get(3, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testHold_WhenSomethingIsStillNotDoneYet_Blocking() throws Exception {
        WaitGroup wg = new WaitGroup();
        HoldRunner hr = new HoldRunner(wg);
        boolean raised = false;

        wg.add(1);
        final Future future = es.submit(hr);

        try {
            future.get(3, TimeUnit.MILLISECONDS);

        } catch (TimeoutException t) {
            raised = true;
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        assertTrue(raised);
    }


}