package com.hsbc.eventbus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Multithreaded implementation of EventBus that supports multiple producers and consumers
 * Thread calling publishEvent is different from the thread making the callback
 */
public class MultiThreadedEventBus implements EventBus {
    
    // Thread pool for executing subscriber callbacks
    private final ExecutorService executorService;
    
    // Thread-safe collections for subscribers
    private final List<EventSubscriber> allEventSubscribers = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<EventSubscriber>> filteredEventSubscribers = new ConcurrentHashMap<>();
    
    // Read-write lock for subscriber management
    private final ReadWriteLock subscriberLock = new ReentrantReadWriteLock();
    
    // Queue for event processing with coalescing support
    private final BlockingQueue<EventWrapper> eventQueue = new LinkedBlockingQueue<>();
    private final Map<Class<?>, EventWrapper> latestEvents = new ConcurrentHashMap<>();
    private final Set<Class<?>> queuedEventTypes = ConcurrentHashMap.newKeySet();
    
    // Flag to support event coalescing
    private final boolean enableCoalescing;
    
    // Event processing thread
    private final Thread eventProcessorThread;
    private volatile boolean running = true;
    
    /**
     * Constructor with default thread pool and no coalescing
     */
    public MultiThreadedEventBus() {
        this(Executors.newCachedThreadPool(), false);
    }
    
    /**
     * Constructor with custom thread pool and coalescing option
     */
    public MultiThreadedEventBus(ExecutorService executorService, boolean enableCoalescing) {
        this.executorService = executorService;
        this.enableCoalescing = enableCoalescing;
        
        // Start event processor thread
        this.eventProcessorThread = new Thread(this::processEvents, "EventBus-Processor");
        this.eventProcessorThread.setDaemon(true);
        this.eventProcessorThread.start();
    }
    
    @Override
    public void publishEvent(Object event) {
        if (event == null || !running) {
            return;
        }
        
        EventWrapper wrapper = new EventWrapper(event, System.currentTimeMillis());
        
        if (enableCoalescing) {
            // For coalescing, replace any existing event of the same type
            Class<?> eventType = event.getClass();
            latestEvents.put(eventType, wrapper);
            
            // Only add to queue if this event type is not already queued
            if (queuedEventTypes.add(eventType)) {
                eventQueue.offer(wrapper);
            }
            // If the event type was already queued, the new event just replaces
            // the old one in latestEvents and will be processed when the queue item is handled
        } else {
            // Normal mode - add all events to queue
            eventQueue.offer(wrapper);
        }
    }
    
    @Override
    public void addSubscriber(Class<?> subscriberClass, EventSubscriber subscriber) {
        if (subscriber != null) {
            subscriberLock.writeLock().lock();
            try {
                allEventSubscribers.add(subscriber);
            } finally {
                subscriberLock.writeLock().unlock();
            }
        }
    }
    
    @Override
    public void addSubscriberForFilteredEvents(Class<?> eventType, EventSubscriber subscriber) {
        if (eventType != null && subscriber != null) {
            subscriberLock.writeLock().lock();
            try {
                filteredEventSubscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscriber);
            } finally {
                subscriberLock.writeLock().unlock();
            }
        }
    }
    
    /**
     * Event processing loop - runs in dedicated thread
     */
    private void processEvents() {
        while (running) {
            try {
                EventWrapper wrapper = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (wrapper != null) {
                    if (enableCoalescing) {
                        // Get the latest event for this type
                        Class<?> eventType = wrapper.event.getClass();
                        EventWrapper latestEvent = latestEvents.get(eventType);
                        if (latestEvent != null && latestEvent.timestamp >= wrapper.timestamp) {
                            // Use the latest event instead
                            wrapper = latestEvent;
                        }
                        // Remove from latest events map and queued types set after processing
                        latestEvents.remove(eventType);
                        queuedEventTypes.remove(eventType);
                    }
                    
                    processEvent(wrapper.event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in event processor: " + e.getMessage());
            }
        }
    }
    
    /**
     * Process a single event by notifying all relevant subscribers
     */
    private void processEvent(Object event) {
        subscriberLock.readLock().lock();
        try {
            // Notify all general subscribers
            for (EventSubscriber subscriber : allEventSubscribers) {
                executorService.submit(() -> {
                    try {
                        subscriber.handleEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error processing event in subscriber: " + e.getMessage());
                    }
                });
            }
            
            // Notify filtered subscribers for this event type
            Class<?> eventType = event.getClass();
            notifyFilteredSubscribers(event, eventType);
            
            // Also check for superclass and interface matches
            notifyInheritanceBasedSubscribers(event, eventType);
            
        } finally {
            subscriberLock.readLock().unlock();
        }
    }
    
    /**
     * Notify subscribers registered for specific event type
     */
    private void notifyFilteredSubscribers(Object event, Class<?> eventType) {
        List<EventSubscriber> typeSubscribers = filteredEventSubscribers.get(eventType);
        if (typeSubscribers != null) {
            for (EventSubscriber subscriber : typeSubscribers) {
                executorService.submit(() -> {
                    try {
                        subscriber.handleEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error processing event in filtered subscriber: " + e.getMessage());
                    }
                });
            }
        }
    }
    
    /**
     * Notify subscribers registered for parent classes and interfaces
     */
    private void notifyInheritanceBasedSubscribers(Object event, Class<?> eventType) {
        // Check superclasses
        Class<?> superClass = eventType.getSuperclass();
        while (superClass != null && superClass != Object.class) {
            notifyFilteredSubscribers(event, superClass);
            superClass = superClass.getSuperclass();
        }
        
        // Check interfaces
        for (Class<?> interfaceType : eventType.getInterfaces()) {
            notifyFilteredSubscribers(event, interfaceType);
        }
    }
    
    /**
     * Shutdown the event bus and cleanup resources
     */
    public void shutdown() {
        running = false;
        eventProcessorThread.interrupt();
        
        try {
            eventProcessorThread.join(5000); // Wait up to 5 seconds
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Returns the number of all-event subscribers
     */
    public int getAllSubscribersCount() {
        subscriberLock.readLock().lock();
        try {
            return allEventSubscribers.size();
        } finally {
            subscriberLock.readLock().unlock();
        }
    }
    
    /**
     * Returns the number of filtered subscribers for a given event type
     */
    public int getFilteredSubscribersCount(Class<?> eventType) {
        subscriberLock.readLock().lock();
        try {
            List<EventSubscriber> subscribers = filteredEventSubscribers.get(eventType);
            return subscribers != null ? subscribers.size() : 0;
        } finally {
            subscriberLock.readLock().unlock();
        }
    }
    
    /**
     * Returns the current queue size (for monitoring)
     */
    public int getQueueSize() {
        return eventQueue.size();
    }
    
    /**
     * Wrapper class for events with timestamp for coalescing
     */
    private static class EventWrapper {
        final Object event;
        final long timestamp;
        
        EventWrapper(Object event, long timestamp) {
            this.event = event;
            this.timestamp = timestamp;
        }
    }
}