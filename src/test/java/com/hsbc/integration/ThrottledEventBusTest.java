package com.hsbc.integration;

import com.hsbc.eventbus.SingleThreadedEventBus;
import com.hsbc.eventbus.EventSubscriber;
import com.hsbc.eventbus.events.MarketDataEvent;
import com.hsbc.throttler.RollingWindowThrottler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration test for ThrottledEventBus
 */
public class ThrottledEventBusTest {
    
    private static int testsRun = 0;
    private static int testsPassed = 0;
    
    public static void main(String[] args) {
        System.out.println("=== ThrottledEventBus Integration Test ===\n");
        
        runTest("Basic Throttled Publishing", ThrottledEventBusTest::testBasicThrottling);
        runTest("Queue Management", ThrottledEventBusTest::testQueueManagement);
        runTest("High Volume Throttling", ThrottledEventBusTest::testHighVolumeThrottling);
        
        System.out.println("\n=== Results ===");
        System.out.printf("Tests: %d, Passed: %d, Failed: %d%n", testsRun, testsPassed, testsRun - testsPassed);
        
        if (testsPassed == testsRun) {
            System.out.println("✅ All integration tests PASSED!");
        } else {
            System.out.println("❌ Some integration tests FAILED!");
            System.exit(1);
        }
    }
    
    private static void runTest(String name, TestMethod test) {
        testsRun++;
        try {
            test.run();
            testsPassed++;
            System.out.println("✅ " + name + " - PASSED");
        } catch (Exception e) {
            System.out.println("❌ " + name + " - FAILED: " + e.getMessage());
        }
    }
    
    @FunctionalInterface
    private interface TestMethod {
        void run() throws Exception;
    }
    
    private static void testBasicThrottling() throws Exception {
        SingleThreadedEventBus eventBus = new SingleThreadedEventBus();
        RollingWindowThrottler throttler = new RollingWindowThrottler(2, 500); // 2 events per 500ms
        ThrottledEventBus throttledBus = new ThrottledEventBus(eventBus, throttler);
        
        try {
            AtomicInteger receivedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(4);
            
            throttledBus.addSubscriberForFilteredEvents(MarketDataEvent.class, new EventSubscriber() {
                @Override
                public void handleEvent(Object event) {
                    receivedCount.incrementAndGet();
                    latch.countDown();
                }
            });
            
            // Publish 4 events rapidly
            for (int i = 0; i < 4; i++) {
                throttledBus.publishEvent(new MarketDataEvent("AAPL", 150.0 + i, 1000));
            }
            
            // Wait for all events to be processed
            assertTrue(latch.await(2, TimeUnit.SECONDS), "All events should be processed");
            assertEqual(4, receivedCount.get(), "All events should be received");
            
        } finally {
            throttledBus.shutdown();
            throttler.shutdown();
        }
    }
    
    private static void testQueueManagement() throws Exception {
        SingleThreadedEventBus eventBus = new SingleThreadedEventBus();
        RollingWindowThrottler throttler = new RollingWindowThrottler(1, 300); // 1 event per 300ms
        ThrottledEventBus throttledBus = new ThrottledEventBus(eventBus, throttler);
        
        try {
            // Publish multiple events rapidly
            for (int i = 0; i < 5; i++) {
                throttledBus.publishEvent(new MarketDataEvent("SYMBOL" + i, 100.0 + i, 1000));
            }
            
            // Should have pending events
            assertTrue(throttledBus.getPendingEventCount() > 0, "Should have pending events");
            
            // Wait for queue to drain
            Thread.sleep(2000);
            
            // Queue should be empty or nearly empty
            assertTrue(throttledBus.getPendingEventCount() <= 1, "Queue should be mostly drained");
            
        } finally {
            throttledBus.shutdown();
            throttler.shutdown();
        }
    }
    
    private static void testHighVolumeThrottling() throws Exception {
        SingleThreadedEventBus eventBus = new SingleThreadedEventBus();
        RollingWindowThrottler throttler = new RollingWindowThrottler(10, 1000); // 10 events per second
        ThrottledEventBus throttledBus = new ThrottledEventBus(eventBus, throttler);
        
        try {
            AtomicInteger receivedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(50);
            
            throttledBus.addSubscriber(EventSubscriber.class, new EventSubscriber() {
                @Override
                public void handleEvent(Object event) {
                    receivedCount.incrementAndGet();
                    latch.countDown();
                }
            });
            
            long startTime = System.currentTimeMillis();
            
            // Publish 50 events rapidly
            for (int i = 0; i < 50; i++) {
                throttledBus.publishEvent(new MarketDataEvent("BULK" + i, 200.0 + i, 1000));
            }
            
            // Wait for all events to be processed
            assertTrue(latch.await(10, TimeUnit.SECONDS), "All events should eventually be processed");
            
            long duration = System.currentTimeMillis() - startTime;
            
            assertEqual(50, receivedCount.get(), "All events should be received");
            
            // Should take at least 4 seconds due to throttling (50 events / 10 per second = 5 seconds minimum)
            assertTrue(duration >= 4000, "Should be throttled (took " + duration + "ms)");
            
        } finally {
            throttledBus.shutdown();
            throttler.shutdown();
        }
    }
    
    // Assertion helpers
    private static void assertEqual(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " - Expected: " + expected + ", Got: " + actual);
        }
    }
    
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}