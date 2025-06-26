package com.hsbc.eventbus.events;

/**
 * Sample event representing market data updates
 */
public class MarketDataEvent {
    private final String symbol;
    private final double price;
    private final long timestamp;
    private final int volume;
    
    public MarketDataEvent(String symbol, double price, int volume) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public double getPrice() {
        return price;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getVolume() {
        return volume;
    }
    
    @Override
    public String toString() {
        return String.format("MarketDataEvent{symbol='%s', price=%.2f, volume=%d, timestamp=%d}", 
                           symbol, price, volume, timestamp);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        MarketDataEvent that = (MarketDataEvent) obj;
        return Double.compare(that.price, price) == 0 &&
               volume == that.volume &&
               timestamp == that.timestamp &&
               symbol.equals(that.symbol);
    }
    
    @Override
    public int hashCode() {
        return symbol.hashCode() + Double.hashCode(price) + Integer.hashCode(volume);
    }
}