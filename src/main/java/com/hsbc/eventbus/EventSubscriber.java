package com.hsbc.eventbus;

/**
 * Interface for event subscribers
 */
public interface EventSubscriber {
    
    /**
     * Handles an incoming event
     * @param event the event to handle
     */
    void handleEvent(Object event);
}