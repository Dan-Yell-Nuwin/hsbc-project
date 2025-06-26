package com.hsbc.throttler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rolling window-based throttler implementation that restricts operations within a time window.
 * Thread-safe and uses a rolling window approach for accurate throttling.
 */
public class RollingWindowThrottler implements Throttler {
    
    private final int maxOperations;
    private final long windowSizeMs;
    private final ConcurrentLinkedQueue<Long> operationTimestamps;
    private final CopyOnWriteArrayList<ThrottleCallback> callbacks;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean notificationScheduled;
    
    /**
     * Creates a throttler with specified limits
     * @param maxOperations maximum number of operations allowed in the time window
     * @param windowSizeMs size of the rolling window in milliseconds
     */
    public RollingWindowThrottler(int maxOperations, long windowSizeMs) {
        if (maxOperations <= 0) {
            throw new IllegalArgumentException("maxOperations must be positive");
        }
        if (windowSizeMs <= 0) {
            throw new IllegalArgumentException("windowSizeMs must be positive");
        }
        
        this.maxOperations = maxOperations;
        this.windowSizeMs = windowSizeMs;
        this.operationTimestamps = new ConcurrentLinkedQueue<>();
        this.callbacks = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ThrottlerNotifier");
            t.setDaemon(true);
            return t;
        });
        this.notificationScheduled = new AtomicBoolean(false);
    }
    
    @Override
    public ThrottleResult shouldProceed() {
        long currentTime = System.currentTimeMillis();
        
        // Clean up old timestamps outside the window
        cleanupOldTimestamps(currentTime);
        
        // Check if we can proceed
        if (operationTimestamps.size() < maxOperations) {
            // Record this operation
            operationTimestamps.offer(currentTime);
            return ThrottleResult.PROCEED;
        } else {
            // Schedule notification for when we can proceed again
            scheduleNotification();
            return ThrottleResult.DO_NOT_PROCEED;
        }
    }
    
    @Override
    public void notifyWhenCanProceed(ThrottleCallback callback) {
        if (callback != null) {
            callbacks.add(callback);
            
            // If we can proceed now, notify immediately
            if (canProceedNow()) {
                notifyCallback(callback);
            } else {
                // Schedule notification for later
                scheduleNotification();
            }
        }
    }
    
    /**
     * Remove callbacks (useful for cleanup)
     */
    public void removeCallback(ThrottleCallback callback) {
        callbacks.remove(callback);
    }
    
    /**
     * Get current operation count in the window
     */
    public int getCurrentOperationCount() {
        cleanupOldTimestamps(System.currentTimeMillis());
        return operationTimestamps.size();
    }
    
    /**
     * Get time until next operation can proceed (in milliseconds)
     */
    public long getTimeUntilNextOperation() {
        if (canProceedNow()) {
            return 0;
        }
        
        Long oldestTimestamp = operationTimestamps.peek();
        if (oldestTimestamp == null) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - windowSizeMs;
        
        return Math.max(0, oldestTimestamp - windowStart);
    }
    
    /**
     * Shutdown the throttler and cleanup resources
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void cleanupOldTimestamps(long currentTime) {
        long windowStart = currentTime - windowSizeMs;
        
        // Remove timestamps that are outside the rolling window
        while (!operationTimestamps.isEmpty()) {
            Long timestamp = operationTimestamps.peek();
            if (timestamp != null && timestamp < windowStart) {
                operationTimestamps.poll();
            } else {
                break;
            }
        }
    }
    
    private boolean canProceedNow() {
        long currentTime = System.currentTimeMillis();
        cleanupOldTimestamps(currentTime);
        return operationTimestamps.size() < maxOperations;
    }
    
    private void scheduleNotification() {
        if (callbacks.isEmpty() || !notificationScheduled.compareAndSet(false, true)) {
            return;
        }
        
        long delay = getTimeUntilNextOperation();
        if (delay <= 0) {
            // Can proceed now
            notificationScheduled.set(false);
            notifyAllCallbacks();
        } else {
            // Schedule notification for when window opens up
            scheduler.schedule(() -> {
                notificationScheduled.set(false);
                if (canProceedNow()) {
                    notifyAllCallbacks();
                } else {
                    // Reschedule if still can't proceed
                    scheduleNotification();
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
    }
    
    private void notifyAllCallbacks() {
        for (ThrottleCallback callback : callbacks) {
            notifyCallback(callback);
        }
    }
    
    private void notifyCallback(ThrottleCallback callback) {
        try {
            callback.onCanProceed();
        } catch (Exception e) {
            // Log error but don't let it affect other callbacks
            System.err.println("Error in throttle callback: " + e.getMessage());
        }
    }
    
    @Override
    public String toString() {
        return String.format("RollingWindowThrottler{maxOps=%d, windowMs=%d, currentOps=%d}", 
                           maxOperations, windowSizeMs, getCurrentOperationCount());
    }
}