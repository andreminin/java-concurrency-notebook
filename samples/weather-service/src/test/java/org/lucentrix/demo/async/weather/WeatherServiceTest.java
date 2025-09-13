package org.lucentrix.demo.async.weather;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    private WeatherService weatherService;
    private ExecutorService testExecutor;

    @BeforeEach
    void setUp() {
        testExecutor = Executors.newFixedThreadPool(4);

        // Create test weather sources
        List<WeatherSource> weatherSources = Arrays.asList(
                new TestWeatherSources.FastWeatherSource("FastWeatherSource", 25.0),
                new TestWeatherSources.FastWeatherSource("FastWeatherSource2", 22.0),
                new TestWeatherSources.SlowWeatherSource("SlowWeatherSource", 1000),
                new TestWeatherSources.FailingWeatherSource("FailingWeatherSource")
        );

        weatherService = new WeatherService(weatherSources, testExecutor);
    }

    @AfterEach
    void tearDown() {
        weatherService.shutdown();
        testExecutor.shutdown();
    }

    @Test
    void testFetchWeatherFromAllSources_Success() throws Exception {
        CompletableFuture<List<WeatherResult>> future = weatherService.fetchWeatherFromAllSources("London");

        List<WeatherResult> results = future.get(5, TimeUnit.SECONDS);

        assertEquals(4, results.size());

        // Verify all expected sources are represented
        Set<String> resultSources = results.stream()
                .map(WeatherResult::getSourceName)
                .collect(Collectors.toSet());

        Set<String> expectedSources = Set.of("FastWeatherSource", "FastWeatherSource2", "SlowWeatherSource", "FailingWeatherSource");
        assertEquals(expectedSources, resultSources);

        // Check specific sources
        assertTrue(results.stream()
                .anyMatch(result -> result.getSourceName().equals("FastWeatherSource") && result.isSuccess()));

        assertTrue(results.stream()
                .anyMatch(result -> result.getSourceName().equals("FastWeatherSource2") && result.isSuccess()));

        assertTrue(results.stream()
                .anyMatch(result -> result.getSourceName().equals("SlowWeatherSource") && result.isSuccess()));

        assertTrue(results.stream()
                .anyMatch(result -> result.getSourceName().equals("FailingWeatherSource") && !result.isSuccess()));
    }

    @Test
    void testFetchWeatherFastest_ReturnsFirstSuccessful() throws Exception {
        CompletableFuture<WeatherData> future = weatherService.fetchWeatherFastest("London");

        WeatherData result = future.get(2, TimeUnit.SECONDS);

        assertNotNull(result);
        // Should be one of the fast sources (not the slow one)
        assertTrue(result.getSource().equals("FastWeatherSource") || result.getSource().equals("FastWeatherSource2"));
    }

    @Test
    void testFetchWeatherWithTimeout_Success() throws Exception {
        CompletableFuture<WeatherData> future = weatherService.fetchWeatherWithTimeout("London", 3);

        WeatherData result = future.get(4, TimeUnit.SECONDS);
        assertNotNull(result);
    }

    @Test
    void testFetchWeatherWithTimeout_ThrowsOnTimeout() {
        // Create a service with only slow sources
        List<WeatherSource> slowSources = List.of(
                new TestWeatherSources.SlowWeatherSource("VerySlow", 5000)
        );
        WeatherService slowService = new WeatherService(slowSources, testExecutor);

        CompletableFuture<WeatherData> future = slowService.fetchWeatherWithTimeout("London", 1);

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void testGetAverageTemperature_Success() throws Exception {
        CompletableFuture<Double> future = weatherService.getAverageTemperature("London");

        Double average = future.get(5, TimeUnit.SECONDS);

        assertTrue(average >= 20.0 && average <= 25.0);
    }

    @Test
    void testGetAverageTemperature_AllSourcesFail_ThrowsException() {
        List<WeatherSource> failingSources = List.of(
                new TestWeatherSources.FailingWeatherSource("Fail1"),
                new TestWeatherSources.FailingWeatherSource("Fail2")
        );
        WeatherService failingService = new WeatherService(failingSources, testExecutor);

        CompletableFuture<Double> future = failingService.getAverageTemperature("London");

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void testFetchWeatherWithRetry_SuccessAfterRetry() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        WeatherSource retrySource = new WeatherSource() {
            @Override
            public String getName() {
                return "RetrySource";
            }

            @Override
            public CompletableFuture<WeatherData> fetchWeather(String location) {
                return CompletableFuture.supplyAsync(() -> {
                    if (callCount.incrementAndGet() < 3) {
                        throw new WeatherException("Temporary failure");
                    }
                    return new WeatherData(getName(), 18.0, "Clear", java.time.LocalDateTime.now());
                });
            }

            @Override
            public CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds) {
                return fetchWeather(location);
            }
        };

        WeatherService retryService = new WeatherService(List.of(retrySource), testExecutor);
        CompletableFuture<WeatherData> future = retryService.fetchWeatherWithRetry("London", 3);

        WeatherData result = future.get(10, TimeUnit.SECONDS);

        assertEquals(18.0, result.getTemperature());
        assertEquals(3, callCount.get()); // Should have been called 3 times
    }

    @Test
    void testFetchWeatherWithRetry_MaxRetriesExceeded_ThrowsException() {
        WeatherSource alwaysFailing = new TestWeatherSources.FailingWeatherSource("AlwaysFails");
        WeatherService failingService = new WeatherService(List.of(alwaysFailing), testExecutor);

        CompletableFuture<WeatherData> future = failingService.fetchWeatherWithRetry("London", 2);

        assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testExceptionHandlingInPipeline() throws Exception {
        // Test that exceptions are properly handled in the completion pipeline
        CompletableFuture<List<WeatherResult>> future = weatherService.fetchWeatherFromAllSources("London");

        List<WeatherResult> results = future.exceptionally(ex -> {
            fail("Exception should not propagate to this level");
            return Collections.emptyList();
        }).get(5, TimeUnit.SECONDS);

        assertNotNull(results);
    }

}