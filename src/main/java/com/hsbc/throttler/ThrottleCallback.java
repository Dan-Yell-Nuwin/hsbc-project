package com.hsbc.throttler;

/**
 * Callback interface for throttle notifications
 */
public interface ThrottleCallback {
    
    /**
     * Called when the throttler determines that processing can proceed
     */
    void onCanProceed();
}