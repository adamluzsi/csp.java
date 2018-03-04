package com.github.adamluzsi.csp;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class ChannelIsClosedTest {

    @Test
    public void ChannelIsClosed_ThrownAsException_CanBeCatchedAsInterruptedException() {
        try {
            throw new ChannelIsClosed();
        } catch (InterruptedException e) {
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }
    }

}