package com.hsbc.integration;

import com.hsbc.eventbus.EventBus;
import com.hsbc.eventbus.EventSubscriber;
import com.hsbc.throttler.Throttler;
import com.hsbc.throttler.ThrottleCallback;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Integration example: EventBus with throttling capabilities
 * Demonstrates how to combine EventBus and Throttler for rate-limited event publishing
 */
public class ThrottledEventBus implements EventBus {
    
    private final EventBus delegate;
    private final Throttler throttler;
    private final BlockingQueue<Object> pendingEvents;
    private final Thread processingThread;
    private volatile boolean running = true;
    
    public ThrottledEventBus(EventBus delegate, Throttler throttler) {
        this.delegate = delegate;
        this.throttler = throttler;
        this.pendingEvents = new LinkedBlockingQueue<>();
        
        // Start background processing thread
        this.processingThread = new Thread(this::processEvents, "ThrottledEventBus-Processor");
        this.processingThread.setDaemon(true);
        this.processingThread.start();
        
        // Register for throttle notifications
        this.throttler.notifyWhenCanProceed(this::processPendingEvents);
    }
    
    @Override
    public void publishEvent(Object event) {
        if (event == null || !running) {
            return;
        }
        
        // Try to publish immediately if throttler allows
        if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
            delegate.publishEvent(event);
        } else {
            // Queue for later processing
            pendingEvents.offer(event);
        }
    }
    
    @Override
    public void addSubscriber(Class<?> subscriberClass, EventSubscriber subscriber) {
        delegate.addSubscriber(subscriberClass, subscriber);
    }
    
    @Override
    public void addSubscriberForFilteredEvents(Class<?> eventType, EventSubscriber subscriber) {
        delegate.addSubscriberForFilteredEvents(eventType, subscriber);
    }
    
    /**
     * Get the number of events waiting to be processed
     */
    public int getPendingEventCount() {
        return pendingEvents.size();
    }
    
    /**
     * Shutdown the throttled event bus
     */
    public void shutdown() {
        running = false;
        processingThread.interrupt();
        
        try {
            processingThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Process any remaining events
        while (!pendingEvents.isEmpty()) {
            Object event = pendingEvents.poll();
            if (event != null) {
                delegate.publishEvent(event);
            }
        }
    }
    
    private void processEvents() {
        while (running) {
            try {
                // Wait for events or interruption
                Object event = pendingEvents.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (event != null) {
                    // Try to process the event
                    if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
                        delegate.publishEvent(event);
                    } else {
                        // Put it back at the front of the queue
                        pendingEvents.offer(event);
                        // Wait a bit before trying again
                        Thread.sleep(10);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private void processPendingEvents() {
        // Process as many pending events as the throttler allows
        while (!pendingEvents.isEmpty() && running) {
            if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
                Object event = pendingEvents.poll();
                if (event != null) {
                    delegate.publishEvent(event);
                }
            } else {
                break; // Throttler says no more for now
            }
        }
    }
}