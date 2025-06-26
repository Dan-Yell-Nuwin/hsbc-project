@echo off
REM HSBC EventBus Build and Test Script for Windows

echo === HSBC EventBus Build and Test ===
echo.

REM Create output directories
if not exist build\classes mkdir build\classes
if not exist build\test-classes mkdir build\test-classes

REM Compile main source files
echo Compiling main source files...
for /r src\main\java %%f in (*.java) do (
    javac -d build\classes -cp build\classes "%%f"
    if errorlevel 1 (
        echo ❌ Main compilation failed
        exit /b 1
    )
)
echo ✅ Main compilation successful

REM Compile test files
echo Compiling test files...
for /r src\test\java %%f in (*.java) do (
    javac -d build\test-classes -cp build\classes;build\test-classes "%%f"
    if errorlevel 1 (
        echo ❌ Test compilation failed
        exit /b 1
    )
)
echo ✅ Test compilation successful

echo.

REM Run EventBus tests
echo Running EventBus tests...
java -cp build\classes;build\test-classes com.hsbc.eventbus.EventBusTest
if errorlevel 1 (
    echo ❌ EventBus tests failed
    exit /b 1
)

echo.

REM Run Throttler tests
echo Running Throttler tests...
java -cp build\classes;build\test-classes com.hsbc.throttler.ThrottlerTest
if errorlevel 1 (
    echo ❌ Throttler tests failed
    exit /b 1
)

echo.

REM Run Integration tests
echo Running Integration tests...
java -cp build\classes;build\test-classes com.hsbc.integration.ThrottledEventBusTest
if errorlevel 1 (
    echo ❌ Integration tests failed
    exit /b 1
)

echo.

REM Run performance benchmarks
echo Running performance benchmarks...
java -cp build\classes;build\test-classes com.hsbc.eventbus.PerformanceBenchmark
if errorlevel 1 (
    echo ❌ Performance benchmarks failed
    exit /b 1
)

echo.
echo 🎉 All tests completed successfully!
echo.
echo To run individual components:
echo   Functional Tests: java -cp build\classes;build\test-classes com.hsbc.eventbus.EventBusTest
echo   Performance Tests: java -cp build\classes;build\test-classes com.hsbc.eventbus.PerformanceBenchmark

pause