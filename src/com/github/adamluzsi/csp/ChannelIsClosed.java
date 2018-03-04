package com.github.adamluzsi.csp;

public class ChannelIsClosed extends InterruptedException {
    ChannelIsClosed() {
        super("cannot transfer new element in a closed channel");
    }
}