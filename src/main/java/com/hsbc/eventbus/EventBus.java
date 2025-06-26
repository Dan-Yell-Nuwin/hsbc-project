package com.hsbc.eventbus;

/**
 * EventBus interface for publishing events and managing subscribers
 */
public interface EventBus {
    
    /**
     * Publishes an event to all registered subscribers
     * @param event the event object to publish
     */
    void publishEvent(Object event);
    
    /**
     * Adds a subscriber that will receive all events
     * @param subscriberClass the class type of the subscriber
     * @param subscriber the subscriber instance
     */
    void addSubscriber(Class<?> subscriberClass, EventSubscriber subscriber);
    
    /**
     * Adds a subscriber that will receive filtered events based on event type
     * @param eventType the type of events this subscriber is interested in
     * @param subscriber the subscriber instance
     */
    void addSubscriberForFilteredEvents(Class<?> eventType, EventSubscriber subscriber);
}