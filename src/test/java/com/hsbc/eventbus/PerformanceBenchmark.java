package com.hsbc.eventbus;

import com.hsbc.eventbus.events.MarketDataEvent;
import com.hsbc.eventbus.events.TradeEvent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concise performance benchmark for EventBus implementations
 */
public class PerformanceBenchmark {
    
    private static final int EVENTS = 10000;
    private static final int SUBSCRIBERS = 5;
    private static final int THREADS = 4;
    
    public static void main(String[] args) {
        System.out.println("=== EventBus Performance Benchmark ===");
        System.out.printf("Events: %d, Subscribers: %d, Threads: %d%n%n", EVENTS, SUBSCRIBERS, THREADS);
        
        benchmarkSingleThreaded();
        benchmarkMultiThreaded();
        benchmarkCoalescing();
        
        System.out.println("=== Benchmark Complete ===");
    }
    
    private static void benchmarkSingleThreaded() {
        System.out.println("--- Single-Threaded ---");
        
        SingleThreadedEventBus bus = new SingleThreadedEventBus();
        for (int i = 0; i < SUBSCRIBERS; i++) {
            bus.addSubscriber(BenchSubscriber.class, new BenchSubscriber());
            bus.addSubscriberForFilteredEvents(MarketDataEvent.class, new BenchSubscriber());
        }
        
        long start = System.nanoTime();
        for (int i = 0; i < EVENTS; i++) {
            if (i % 2 == 0) {
                bus.publishEvent(new MarketDataEvent("SYM" + (i % 10), 100.0 + i, 1000));
            } else {
                bus.publishEvent(new TradeEvent("T" + i, "SYM" + (i % 10), 100.0 + i, 100, "BUY"));
            }
        }
        long duration = System.nanoTime() - start;
        
        System.out.printf("Time: %d ms, Rate: %,.0f events/sec%n%n", 
            duration / 1_000_000, EVENTS * 1_000_000_000.0 / duration);
    }
    
    private static void benchmarkMultiThreaded() {
        System.out.println("--- Multi-Threaded ---");
        
        MultiThreadedEventBus bus = new MultiThreadedEventBus();
        try {
            CountDownLatch latch = new CountDownLatch(EVENTS * SUBSCRIBERS);
            
            for (int i = 0; i < SUBSCRIBERS; i++) {
                bus.addSubscriber(CountingSubscriber.class, new CountingSubscriber(latch));
                bus.addSubscriberForFilteredEvents(MarketDataEvent.class, new CountingSubscriber(latch));
            }
            
            long start = System.nanoTime();
            
            Thread[] producers = new Thread[THREADS];
            int eventsPerThread = EVENTS / THREADS;
            
            for (int t = 0; t < THREADS; t++) {
                final int threadId = t;
                producers[t] = new Thread(() -> {
                    int startEvent = threadId * eventsPerThread;
                    int endEvent = (threadId == THREADS - 1) ? EVENTS : startEvent + eventsPerThread;
                    
                    for (int i = startEvent; i < endEvent; i++) {
                        if (i % 2 == 0) {
                            bus.publishEvent(new MarketDataEvent("SYM" + (i % 10), 100.0 + i, 1000));
                        } else {
                            bus.publishEvent(new TradeEvent("T" + i, "SYM" + (i % 10), 100.0 + i, 100, "BUY"));
                        }
                    }
                });
                producers[t].start();
            }
            
            for (Thread producer : producers) {
                producer.join();
            }
            long publishTime = System.nanoTime() - start;
            
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            long totalTime = System.nanoTime() - start;
            
            if (completed) {
                System.out.printf("Publish: %d ms, Total: %d ms%n", 
                    publishTime / 1_000_000, totalTime / 1_000_000);
                System.out.printf("Publish Rate: %,.0f events/sec, Total Rate: %,.0f events/sec%n%n",
                    EVENTS * 1_000_000_000.0 / publishTime, EVENTS * 1_000_000_000.0 / totalTime);
            } else {
                System.out.println("❌ Benchmark timed out%n");
            }
            
        } catch (InterruptedException e) {
            System.out.println("❌ Benchmark interrupted: " + e.getMessage());
        } finally {
            bus.shutdown();
        }
    }
    
    private static void benchmarkCoalescing() {
        System.out.println("--- Coalescing ---");
        
        MultiThreadedEventBus bus = new MultiThreadedEventBus(
            java.util.concurrent.Executors.newCachedThreadPool(), true);
        
        try {
            AtomicInteger processed = new AtomicInteger(0);
            
            for (int i = 0; i < SUBSCRIBERS; i++) {
                bus.addSubscriberForFilteredEvents(MarketDataEvent.class, 
                    event -> processed.incrementAndGet());
            }
            
            long start = System.nanoTime();
            
            // Rapid fire same symbols (should coalesce heavily)
            for (int i = 0; i < EVENTS; i++) {
                bus.publishEvent(new MarketDataEvent("AAPL", 100.0 + (i % 5), 1000 + i));
                bus.publishEvent(new MarketDataEvent("GOOGL", 2000.0 + (i % 5), 500 + i));
            }
            
            Thread.sleep(3000); // Wait for processing
            long duration = System.nanoTime() - start;
            
            int totalProcessed = processed.get();
            int published = EVENTS * 2;
            
            System.out.printf("Published: %d, Processed: %d (%.1f%% reduction)%n",
                published, totalProcessed, 100.0 * (1.0 - (double)totalProcessed / (published * SUBSCRIBERS)));
            System.out.printf("Time: %d ms%n%n", duration / 1_000_000);
            
        } catch (InterruptedException e) {
            System.out.println("❌ Coalescing benchmark interrupted: " + e.getMessage());
        } finally {
            bus.shutdown();
        }
    }
    
    // Minimal benchmark subscribers
    private static class BenchSubscriber implements EventSubscriber {
        @Override public void handleEvent(Object event) { /* minimal work */ }
    }
    
    private static class CountingSubscriber implements EventSubscriber {
        private final CountDownLatch latch;
        
        CountingSubscriber(CountDownLatch latch) { this.latch = latch; }
        
        @Override 
        public void handleEvent(Object event) { 
            latch.countDown(); 
        }
    }
}