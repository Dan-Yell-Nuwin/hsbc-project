package com.hsbc.eventbus;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Single-threaded implementation of EventBus
 * Thread calling publishEvent is the same as the thread used for the callback on the subscriber
 */
public class SingleThreadedEventBus implements EventBus {
    
    // List of all subscribers (receive all events)
    private final List<EventSubscriber> allEventSubscribers = new ArrayList<>();
    
    // Map of event type to subscribers (receive filtered events)
    private final Map<Class<?>, List<EventSubscriber>> filteredEventSubscribers = new HashMap<>();
    
    @Override
    public void publishEvent(Object event) {
        if (event == null) {
            return;
        }
        
        // Notify all general subscribers
        for (EventSubscriber subscriber : allEventSubscribers) {
            try {
                subscriber.handleEvent(event);
            } catch (Exception e) {
                // Log error but continue processing other subscribers
                System.err.println("Error processing event in subscriber: " + e.getMessage());
            }
        }
        
        // Notify filtered subscribers for this event type
        Class<?> eventType = event.getClass();
        List<EventSubscriber> typeSubscribers = filteredEventSubscribers.get(eventType);
        if (typeSubscribers != null) {
            for (EventSubscriber subscriber : typeSubscribers) {
                try {
                    subscriber.handleEvent(event);
                } catch (Exception e) {
                    // Log error but continue processing other subscribers
                    System.err.println("Error processing event in filtered subscriber: " + e.getMessage());
                }
            }
        }
        
        // Also check for superclass and interface matches
        notifyInheritanceBasedSubscribers(event, eventType);
    }
    
    @Override
    public void addSubscriber(Class<?> subscriberClass, EventSubscriber subscriber) {
        if (subscriber != null) {
            allEventSubscribers.add(subscriber);
        }
    }
    
    @Override
    public void addSubscriberForFilteredEvents(Class<?> eventType, EventSubscriber subscriber) {
        if (eventType != null && subscriber != null) {
            filteredEventSubscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscriber);
        }
    }
    
    /**
     * Notifies subscribers registered for parent classes and interfaces
     */
    private void notifyInheritanceBasedSubscribers(Object event, Class<?> eventType) {
        // Check superclasses (including Object.class)
        Class<?> superClass = eventType.getSuperclass();
        while (superClass != null) {
            List<EventSubscriber> superSubscribers = filteredEventSubscribers.get(superClass);
            if (superSubscribers != null) {
                for (EventSubscriber subscriber : superSubscribers) {
                    try {
                        subscriber.handleEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error processing event in inheritance subscriber: " + e.getMessage());
                    }
                }
            }
            superClass = superClass.getSuperclass();
        }
        
        // Check interfaces
        for (Class<?> interfaceType : eventType.getInterfaces()) {
            List<EventSubscriber> interfaceSubscribers = filteredEventSubscribers.get(interfaceType);
            if (interfaceSubscribers != null) {
                for (EventSubscriber subscriber : interfaceSubscribers) {
                    try {
                        subscriber.handleEvent(event);
                    } catch (Exception e) {
                        System.err.println("Error processing event in interface subscriber: " + e.getMessage());
                    }
                }
            }
        }
    }
    
    /**
     * Returns the number of all-event subscribers
     */
    public int getAllSubscribersCount() {
        return allEventSubscribers.size();
    }
    
    /**
     * Returns the number of filtered subscribers for a given event type
     */
    public int getFilteredSubscribersCount(Class<?> eventType) {
        List<EventSubscriber> subscribers = filteredEventSubscribers.get(eventType);
        return subscribers != null ? subscribers.size() : 0;
    }
}