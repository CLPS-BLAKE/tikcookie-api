package com.dss.flash.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** Bounded single-JVM striped locks. Call TransactionTemplate INSIDE this scope. */
@Component
public class FlashRequestCoordinator {
    private final ReentrantLock[] locks = new ReentrantLock[256];
    public FlashRequestCoordinator() {
        for (int i = 0; i < locks.length; i++) locks[i] = new ReentrantLock();
    }
    public <T> T locked(long productId, Supplier<T> action) {
        ReentrantLock lock = locks[Math.floorMod(Long.hashCode(productId), locks.length)];
        lock.lock();
        try { return action.get(); } finally { lock.unlock(); }
    }
}
