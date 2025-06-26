package com.hsbc.eventbus.events;

/**
 * Sample event representing trade execution
 */
public class TradeEvent {
    private final String tradeId;
    private final String symbol;
    private final double price;
    private final int quantity;
    private final String side; // BUY or SELL
    private final long timestamp;
    
    public TradeEvent(String tradeId, String symbol, double price, int quantity, String side) {
        this.tradeId = tradeId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.side = side;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getTradeId() {
        return tradeId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public double getPrice() {
        return price;
    }
    
    public int getQuantity() {
        return quantity;
    }
    
    public String getSide() {
        return side;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public double getNotionalValue() {
        return price * quantity;
    }
    
    @Override
    public String toString() {
        return String.format("TradeEvent{tradeId='%s', symbol='%s', price=%.2f, quantity=%d, side='%s', timestamp=%d}", 
                           tradeId, symbol, price, quantity, side, timestamp);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        TradeEvent that = (TradeEvent) obj;
        return Double.compare(that.price, price) == 0 &&
               quantity == that.quantity &&
               timestamp == that.timestamp &&
               tradeId.equals(that.tradeId) &&
               symbol.equals(that.symbol) &&
               side.equals(that.side);
    }
    
    @Override
    public int hashCode() {
        return tradeId.hashCode() + symbol.hashCode() + side.hashCode();
    }
}