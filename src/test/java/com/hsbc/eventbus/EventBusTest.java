package com.hsbc.eventbus;

import com.hsbc.eventbus.events.MarketDataEvent;
import com.hsbc.eventbus.events.TradeEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concise test suite for EventBus implementations with proper assertions
 */
public class EventBusTest {
    
    private static int testsRun = 0;
    private static int testsPassed = 0;
    
    public static void main(String[] args) {
        System.out.println("=== EventBus Test Suite ===\n");
        
        runTest("Single-Threaded EventBus", EventBusTest::testSingleThreaded);
        runTest("Multi-Threaded EventBus", EventBusTest::testMultiThreaded);
        runTest("Event Coalescing", EventBusTest::testCoalescing);
        runTest("Error Handling", EventBusTest::testErrorHandling);
        runTest("Inheritance Matching", EventBusTest::testInheritance);
        
        System.out.println("\n=== Results ===");
        System.out.printf("Tests: %d, Passed: %d, Failed: %d%n", testsRun, testsPassed, testsRun - testsPassed);
        
        if (testsPassed == testsRun) {
            System.out.println("✅ All tests PASSED!");
        } else {
            System.out.println("❌ Some tests FAILED!");
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
    
    private static void testSingleThreaded() {
        SingleThreadedEventBus bus = new SingleThreadedEventBus();
        TestSubscriber all = new TestSubscriber();
        TestSubscriber market = new TestSubscriber();
        TestSubscriber trade = new TestSubscriber();
        
        bus.addSubscriber(TestSubscriber.class, all);
        bus.addSubscriberForFilteredEvents(MarketDataEvent.class, market);
        bus.addSubscriberForFilteredEvents(TradeEvent.class, trade);
        
        bus.publishEvent(new MarketDataEvent("AAPL", 150.0, 1000));
        bus.publishEvent(new TradeEvent("T001", "AAPL", 150.0, 100, "BUY"));
        bus.publishEvent("String Event");
        
        assertEqual(3, all.count, "All events subscriber");
        assertEqual(1, market.count, "Market data subscriber");
        assertEqual(1, trade.count, "Trade subscriber");
    }
    
    private static void testMultiThreaded() throws Exception {
        MultiThreadedEventBus bus = new MultiThreadedEventBus();
        try {
            AtomicTestSubscriber all = new AtomicTestSubscriber();
            AtomicTestSubscriber market = new AtomicTestSubscriber();
            
            bus.addSubscriber(AtomicTestSubscriber.class, all);
            bus.addSubscriberForFilteredEvents(MarketDataEvent.class, market);
            
            CountDownLatch allLatch = new CountDownLatch(4);
            CountDownLatch marketLatch = new CountDownLatch(2);
            all.latch = allLatch;
            market.latch = marketLatch;
            
            // Publish from multiple threads
            Thread t1 = new Thread(() -> {
                bus.publishEvent(new MarketDataEvent("AAPL", 150.0, 1000));
                bus.publishEvent(new MarketDataEvent("GOOGL", 2800.0, 500));
            });
            Thread t2 = new Thread(() -> {
                bus.publishEvent("Event 1");
                bus.publishEvent("Event 2");
            });
            
            t1.start(); t2.start();
            t1.join(); t2.join();
            
            assertTrue(allLatch.await(5, TimeUnit.SECONDS), "All events processed within timeout");
            assertTrue(marketLatch.await(5, TimeUnit.SECONDS), "Market events processed within timeout");
            assertEqual(4, all.count.get(), "All events received");
            assertEqual(2, market.count.get(), "Market events received");
        } finally {
            bus.shutdown();
        }
    }
    
    private static void testCoalescing() throws Exception {
        MultiThreadedEventBus bus = new MultiThreadedEventBus(
            java.util.concurrent.Executors.newCachedThreadPool(), true);
        try {
            AtomicTestSubscriber market = new AtomicTestSubscriber();
            bus.addSubscriberForFilteredEvents(MarketDataEvent.class, market);
            
            // Rapid fire events with no delay to trigger coalescing
            for (int i = 0; i < 20; i++) {
                bus.publishEvent(new MarketDataEvent("AAPL", 150.0 + i, 1000));
            }
            Thread.sleep(2000); // Wait longer for processing
            
            int received = market.count.get();
            System.out.println("Received events: " + received);
            // With coalescing enabled, we should receive fewer events than published
            // The exact number depends on timing, but should be significantly less than 20
            assertTrue(received > 0, "Should receive at least 1 event");
            assertTrue(received < 20, "Coalescing should reduce events (got " + received + ")");
            // Note: Coalescing effectiveness depends on timing and may vary
        } finally {
            bus.shutdown();
        }
    }
    
    private static void testErrorHandling() {
        SingleThreadedEventBus bus = new SingleThreadedEventBus();
        TestSubscriber normal = new TestSubscriber();
        
        bus.addSubscriber(EventSubscriber.class, event -> { throw new RuntimeException("Test error"); });
        bus.addSubscriber(TestSubscriber.class, normal);
        
        bus.publishEvent("Test Event");
        assertEqual(1, normal.count, "Normal subscriber unaffected by error");
    }
    
    private static void testInheritance() {
        SingleThreadedEventBus bus = new SingleThreadedEventBus();
        TestSubscriber object = new TestSubscriber();
        TestSubscriber string = new TestSubscriber();
        
        bus.addSubscriberForFilteredEvents(Object.class, object);
        bus.addSubscriberForFilteredEvents(String.class, string);
        
        bus.publishEvent("String");
        bus.publishEvent(42);
        bus.publishEvent(new MarketDataEvent("AAPL", 150.0, 1000));
        
        assertEqual(3, object.count, "Object subscriber gets all events");
        assertEqual(1, string.count, "String subscriber gets only strings");
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
    
    // Test subscribers
    private static class TestSubscriber implements EventSubscriber {
        int count = 0;
        @Override public void handleEvent(Object event) { count++; }
    }
    
    private static class AtomicTestSubscriber implements EventSubscriber {
        AtomicInteger count = new AtomicInteger(0);
        CountDownLatch latch;
        
        @Override 
        public void handleEvent(Object event) { 
            count.incrementAndGet();
            if (latch != null) latch.countDown();
        }
    }
}