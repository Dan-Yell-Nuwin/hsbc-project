package com.hsbc.throttler;

/**
 * A Throttler allows a client to restrict how many times something can happen in a given time period.
 * For example, we may not want to send more than a number of quotes to an exchange in a specific time period.
 * 
 * It should be possible to ask it if we can proceed with the throttled operation, as well as be notified by it.
 * 
 * This implementation uses a rolling window-based approach for accurate throttling.
 */
public interface Throttler {
    
    /**
     * Check if we can proceed (poll)
     * @return ThrottleResult indicating whether to proceed or not
     */
    ThrottleResult shouldProceed();
    
    /**
     * Subscribe to be told when we can proceed (Push)
     * @param callback the callback to notify when throttling allows proceeding
     */
    void notifyWhenCanProceed(ThrottleCallback callback);
    
    /**
     * Result enum for throttle decisions
     */
    enum ThrottleResult {
        PROCEED,        // publish, aggregate, etc
        DO_NOT_PROCEED  // throttled
    }
}