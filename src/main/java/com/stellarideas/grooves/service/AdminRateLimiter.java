package com.stellarideas.grooves.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Separate rate limit bucket for admin-only operations. Keeps admin traffic
 * (user listings, catalog edits, bulk deletes) from sharing the per-user bucket
 * used for export/backup/bulk-delete, and lets operators tune admin throughput
 * independently.
 */
@Service
public class AdminRateLimiter {

    @Value("${stellar.grooves.adminRateLimit.maxRequests:20}")
    private int maxRequests;

    @Value("${stellar.grooves.adminRateLimit.windowSeconds:60}")
    private long windowSeconds;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String adminId, String operation) {
        String key = adminId + ":" + operation;
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        return counter.tryIncrement(maxRequests, windowSeconds * 1000);
    }

    public long secondsUntilAllowed(String adminId, String operation) {
        String key = adminId + ":" + operation;
        WindowCounter counter = counters.get(key);
        if (counter == null) return 0;
        long elapsed = System.currentTimeMillis() - counter.windowStart;
        long remaining = windowSeconds - (elapsed / 1000);
        return Math.max(0, remaining);
    }

    @Scheduled(fixedRate = 300_000)
    void evictStaleEntries() {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000 * 2);
        counters.entrySet().removeIf(entry -> entry.getValue().windowStart < cutoff);
    }

    private static class WindowCounter {
        volatile long windowStart = System.currentTimeMillis();
        final AtomicInteger count = new AtomicInteger(0);

        boolean tryIncrement(int maxRequests, long windowMs) {
            long now = System.currentTimeMillis();
            if (now - windowStart > windowMs) {
                windowStart = now;
                count.set(1);
                return true;
            }
            return count.incrementAndGet() <= maxRequests;
        }
    }
}
