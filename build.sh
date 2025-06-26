#!/bin/bash

# HSBC EventBus Build and Test Script

echo "=== HSBC EventBus Build and Test ==="
echo

# Create output directories
mkdir -p build/classes
mkdir -p build/test-classes

# Compile main source files
echo "Compiling main source files..."
find src/main/java -name "*.java" -print0 | xargs -0 javac -d build/classes -cp build/classes
if [ $? -ne 0 ]; then
    echo "❌ Main compilation failed"
    exit 1
fi
echo "✅ Main compilation successful"

# Compile test files
echo "Compiling test files..."
find src/test/java -name "*.java" -print0 | xargs -0 javac -d build/test-classes -cp build/classes:build/test-classes
if [ $? -ne 0 ]; then
    echo "❌ Test compilation failed"
    exit 1
fi
echo "✅ Test compilation successful"

echo

# Run functional tests
echo "Running EventBus tests..."
java -cp build/classes:build/test-classes com.hsbc.eventbus.EventBusTest
if [ $? -ne 0 ]; then
    echo "❌ EventBus tests failed"
    exit 1
fi

echo

# Run throttler tests
echo "Running Throttler tests..."
java -cp build/classes:build/test-classes com.hsbc.throttler.ThrottlerTest
if [ $? -ne 0 ]; then
    echo "❌ Throttler tests failed"
    exit 1
fi

echo

# Run integration tests
echo "Running Integration tests..."
java -cp build/classes:build/test-classes com.hsbc.integration.ThrottledEventBusTest
if [ $? -ne 0 ]; then
    echo "❌ Integration tests failed"
    exit 1
fi

echo

# Run performance benchmarks
echo "Running performance benchmarks..."
java -cp build/classes:build/test-classes com.hsbc.eventbus.PerformanceBenchmark
if [ $? -ne 0 ]; then
    echo "❌ Performance benchmarks failed"
    exit 1
fi

echo
echo "🎉 All tests completed successfully!"
echo
echo "To run individual components:"
echo "  Functional Tests: java -cp build/classes:build/test-classes com.hsbc.eventbus.EventBusTest"
echo "  Performance Tests: java -cp build/classes:build/test-classes com.hsbc.eventbus.PerformanceBenchmark"