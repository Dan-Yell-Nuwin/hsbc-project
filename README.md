# EventBus & Throttler Implementation - HSBC Back End Exercise

## Overview

Production-ready EventBus system and Throttler implementation in Java, featuring both single-threaded and multi-threaded EventBus implementations plus a rolling window-based throttler for rate limiting.

## Key Features

### EventBus (Exercise 1)
- **Single-threaded EventBus**: publishEvent thread = callback thread
- **Multi-threaded EventBus**: publishEvent thread ≠ callback thread
- **Event filtering**: Subscribe to all events or specific types
- **Inheritance support**: Parent class subscribers receive child events
- **Event coalescing**: Optional feature for high-frequency updates
- **Thread safety**: Concurrent collections and proper synchronization
- **Error isolation**: Subscriber exceptions don't affect others

### Throttler (Exercise 2)
- **Rolling window throttling**: Accurate rate limiting over time windows
- **Poll-based checking**: `shouldProceed()` for immediate decisions
- **Push-based notifications**: Callbacks when throttling allows proceeding
- **Thread safety**: Concurrent access from multiple threads
- **Configurable limits**: Customizable operation counts and time windows

## Quick Start

### Basic Usage

```java
// Single-threaded version
EventBus bus = new SingleThreadedEventBus();

// Multi-threaded version  
EventBus bus = new MultiThreadedEventBus();

// Add subscribers
bus.addSubscriber(MySubscriber.class, subscriber);
bus.addSubscriberForFilteredEvents(MarketDataEvent.class, subscriber);

// Publish events
bus.publishEvent(new MarketDataEvent("AAPL", 150.0, 1000));
```

### Throttler Usage

```java
// Create throttler: max 10 operations per second
Throttler throttler = new RollingWindowThrottler(10, 1000);

// Poll-based approach
if (throttler.shouldProceed() == ThrottleResult.PROCEED) {
    // Send quote to exchange
    sendQuote(quote);
}

// Push-based approach
throttler.notifyWhenCanProceed(() -> {
    // Called when throttling allows proceeding
    sendQuote(quote);
});
```

### Integrated EventBus with Throttling

```java
// Combine EventBus with Throttler
EventBus eventBus = new SingleThreadedEventBus();
Throttler throttler = new RollingWindowThrottler(5, 1000); // 5 events/sec
ThrottledEventBus throttledBus = new ThrottledEventBus(eventBus, throttler);

// Events are automatically throttled
throttledBus.publishEvent(new MarketDataEvent("AAPL", 150.0, 1000));
```

### Event Coalescing

```java
// Enable coalescing for rapid updates
MultiThreadedEventBus bus = new MultiThreadedEventBus(
    Executors.newCachedThreadPool(),
    true // Enable coalescing
);
```

## Project Structure

```
src/
├── main/java/com/hsbc/
│   ├── eventbus/
│   │   ├── EventBus.java                    # Core EventBus interface
│   │   ├── EventSubscriber.java             # Subscriber interface
│   │   ├── SingleThreadedEventBus.java      # Single-threaded implementation
│   │   ├── MultiThreadedEventBus.java       # Multi-threaded implementation
│   │   └── events/
│   │       ├── MarketDataEvent.java         # Sample market data event
│   │       └── TradeEvent.java              # Sample trade event
│   ├── throttler/
│   │   ├── Throttler.java                   # Core Throttler interface
│   │   ├── ThrottleCallback.java            # Callback interface
│   │   └── RollingWindowThrottler.java      # Rolling window implementation
│   └── integration/
│       └── ThrottledEventBus.java           # EventBus + Throttler integration
└── test/java/com/hsbc/
    ├── eventbus/
    │   ├── EventBusTest.java                # EventBus functional tests
    │   └── PerformanceBenchmark.java        # Performance benchmarks
    ├── throttler/
    │   └── ThrottlerTest.java               # Throttler functional tests
    └── integration/
        └── ThrottledEventBusTest.java       # Integration tests
```

## Running Tests

### Build and Test (Unix/Mac)
```bash
chmod +x build.sh
./build.sh
```

### Build and Test (Windows)
```cmd
build.bat
```

### Manual Compilation
```bash
# Compile
find src -name "*.java" | xargs javac -d build/classes

# Run tests
java -cp build/classes com.hsbc.eventbus.EventBusTest
java -cp build/classes com.hsbc.throttler.ThrottlerTest
java -cp build/classes com.hsbc.integration.ThrottledEventBusTest
java -cp build/classes com.hsbc.eventbus.PerformanceBenchmark
```

## Implementation Highlights

### Thread Safety Strategy
- **CopyOnWriteArrayList**: Concurrent reads with occasional writes
- **ConcurrentHashMap**: Thread-safe filtered subscriber mappings
- **ReadWriteLock**: Subscriber management synchronization
- **BlockingQueue**: Event queuing in multi-threaded version

### Performance Optimizations
- Minimal object creation during event processing
- Efficient data structures for subscriber lookup
- Optional event coalescing for high-frequency scenarios
- Configurable thread pools

### Error Handling
- Subscriber exceptions caught and logged
- Graceful shutdown in multi-threaded version
- Null event protection

## Design Decisions

**Why CopyOnWriteArrayList?** Optimized for read-heavy workloads (event publishing) with thread safety.

**Why separate processing thread?** Decouples producers from consumers, prevents slow subscribers from blocking publishers.

**Why event coalescing?** Critical for high-frequency market data, prevents queue overflow, ensures latest information.

**Why inheritance matching?** Provides flexibility for event hierarchies, common enterprise pattern.

## Production Considerations

### Monitoring
```java
bus.getAllSubscribersCount()                    // General subscribers
bus.getFilteredSubscribersCount(EventType.class) // Filtered subscribers  
bus.getQueueSize()                              // Current queue size
```

### Configuration
```java
ExecutorService customPool = Executors.newFixedThreadPool(8);
MultiThreadedEventBus bus = new MultiThreadedEventBus(customPool, false);
```

### Shutdown
```java
bus.shutdown(); // Always cleanup multi-threaded resources
```

## Test Results

The implementation includes:
- ✅ Functional tests with proper assertions
- ✅ Multi-threaded producer/consumer scenarios  
- ✅ Event coalescing validation
- ✅ Performance benchmarking
- ✅ Error handling verification
- ✅ Inheritance-based event matching

**Time Investment**: ~4 hours total (analysis, implementation, testing, documentation)

This demonstrates production-ready Java code with proper design patterns, thread safety, comprehensive testing, and enterprise-grade features.