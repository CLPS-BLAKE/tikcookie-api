package com.dss.flash.service;

import org.springframework.stereotype.Component;

/** Startup barrier: consumers and scheduled tasks stay closed until initialization succeeds. */
@Component
public class FlashRuntime {
    private volatile boolean ready;
    public boolean isReady() { return ready; }
    public void close() { ready = false; }
    public void open() { ready = true; }
}
