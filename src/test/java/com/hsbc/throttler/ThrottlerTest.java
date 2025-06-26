package com.hsbc.throttler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive test suite for Throttler implementation
 */
public class ThrottlerTest {
    
    private static int testsRun = 0;
    private static int testsPassed = 0;
    
    public static void main(String[] args) {
        System.out.println("=== Throttler Test Suite ===\n");
        
        runTest("Basic Throttling", ThrottlerTest::testBasicThrottling);
        runTest("Rolling Window", ThrottlerTest::testRollingWindow);
        runTest("Callback Notifications", ThrottlerTest::testCallbackNotifications);
        runTest("Multiple Callbacks", ThrottlerTest::testMultipleCallbacks);
        runTest("Concurrent Access", ThrottlerTest::testConcurrentAccess);
        runTest("Edge Cases", ThrottlerTest::testEdgeCases);
        
        System.out.println("\n=== Results ===");
        System.out.printf("Tests: %d, Passed: %d, Failed: %d%n", testsRun, testsPassed, testsRun - testsPassed);
        
        if (testsPassed == testsRun) {
            System.out.println("✅ All throttler tests PASSED!");
        } else {
            System.out.println("❌ Some throttler tests FAILED!");
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
    
    private static void testBasicThrottling() {
        RollingWindowThrottler throttler = new RollingWindowThrottler(3, 1000); // 3 ops per second
        
        try {
            // First 3 operations should proceed
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "First operation");
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "Second operation");
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "Third operation");
            
            // Fourth operation should be throttled
            assertEqual(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed(), "Fourth operation");
            
            // Check current count
            assertEqual(3, throttler.getCurrentOperationCount(), "Current operation count");
            
        } finally {
            throttler.shutdown();
        }
    }
    
    private static void testRollingWindow() throws Exception {
        RollingWindowThrottler throttler = new RollingWindowThrottler(2, 500); // 2 ops per 500ms
        
        try {
            // Use up the quota
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "First operation");
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "Second operation");
            assertEqual(Throttler.ThrottleResult.DO_NOT_PROCEED, throttler.shouldProceed(), "Third operation blocked");
            
            // Wait for window to roll
            Thread.sleep(600);
            
            // Should be able to proceed again
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "After window roll");
            assertEqual(1, throttler.getCurrentOperationCount(), "Count after cleanup");
            
        } finally {
            throttler.shutdown();
        }
    }
    
    private static void testCallbackNotifications() throws Exception {
        RollingWindowThrottler throttler = new RollingWindowThrottler(1, 300); // 1 op per 300ms
        
        try {
            AtomicInteger callbackCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            
            // Use up quota
            assertEqual(Throttler.ThrottleResult.PROCEED, throttler.shouldProceed(), "Initial operation");
            
            // Register callback
            throttler.notifyWhenCanProceed(() -> {
                callbackCount.incrementAndGet();
                latch.countDown();
            });
            
            // Should be notified when window opens
            assertTrue(latch.await(500, TimeUnit.MILLISECONDS), "Callback should be triggered");
            assertEqual(1, callbackCount.get(), "Callback should be called once");
            
        } finally {
            throttler.shutdown();
        }
    }
    
    private static void testMultipleCallbacks() throws Exception {
        RollingWindowThrottler throttler = new RollingWindowThrottler(1, 200);
        
        try {
            AtomicInteger callback1Count = new AtomicInteger(0);
            AtomicInteger callback2Count = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(2);
            
            // Use up quota
            throttler.shouldProceed();
            
            // Register multiple callbacks
            throttler.notifyWhenCanProceed(() -> {
                callback1Count.incrementAndGet();
                latch.countDown();
            });
            
            throttler.notifyWhenCanProceed(() -> {
                callback2Count.incrementAndGet();
                latch.countDown();
            });
            
            // Both should be notified
            assertTrue(latch.await(400, TimeUnit.MILLISECONDS), "Both callbacks should be triggered");
            assertEqual(1, callback1Count.get(), "First callback");
            assertEqual(1, callback2Count.get(), "Second callback");
            
        } finally {
            throttler.shutdown();
        }
    }
    
    private static void testConcurrentAccess() throws Exception {
        RollingWindowThrottler throttler = new RollingWindowThrottler(10, 1000);
        
        try {
            int numThreads = 5;
            int operationsPerThread = 4;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(numThreads);
            AtomicInteger proceedCount = new AtomicInteger(0);
            AtomicInteger blockedCount = new AtomicInteger(0);
            
            // Start multiple threads
            for (int i = 0; i < numThreads; i++) {
                new Thread(() -> {
                    try {
                        startLatch.await();
                        for (int j = 0; j < operationsPerThread; j++) {
                            if (throttler.shouldProceed() == Throttler.ThrottleResult.PROCEED) {
                                proceedCount.incrementAndGet();
                            } else {
                                blockedCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }
            
            startLatch.countDown(); // Start all threads
            assertTrue(doneLatch.await(2, TimeUnit.SECONDS), "All threads should complete");
            
            // Should have exactly 10 proceed and 10 blocked
            assertEqual(10, proceedCount.get(), "Proceed count");
            assertEqual(10, blockedCount.get(), "Blocked count");
            
        } finally {
            throttler.shutdown();
        }
    }
    
    private static void testEdgeCases() {
        // Test invalid parameters
        try {
            new RollingWindowThrottler(0, 1000);
            throw new AssertionError("Should throw exception for zero maxOperations");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        
        try {
            new RollingWindowThrottler(1, 0);
            throw new AssertionError("Should throw exception for zero windowSize");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        
        // Test null callback
        RollingWindowThrottler throttler = new RollingWindowThrottler(1, 1000);
        try {
            throttler.notifyWhenCanProceed(null); // Should not crash
            
            // Test immediate callback when can proceed
            AtomicInteger callbackCount = new AtomicInteger(0);
            throttler.notifyWhenCanProceed(() -> callbackCount.incrementAndGet());
            
            // Should be called immediately since we can proceed
            Thread.sleep(50); // Give callback time to execute
            assertEqual(1, callbackCount.get(), "Immediate callback");
            
        } catch (Exception e) {
            throw new AssertionError("Edge case handling failed: " + e.getMessage());
        } finally {
            throttler.shutdown();
        }
    }
    
    // Assertion helpers
    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " - Expected: " + expected + ", Got: " + actual);
        }
    }
    
    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}