package org.lucentrix.demo.async.weather;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class WeatherService {
    private static final Logger logger = LoggerFactory.getLogger(WeatherService.class);

    private final List<WeatherSource> weatherSources;
    private final ExecutorService executor;

    public WeatherService(List<WeatherSource> weatherSources) {
        this.weatherSources = new ArrayList<>(weatherSources);
        this.executor = Executors.newCachedThreadPool();
    }

    public WeatherService(List<WeatherSource> weatherSources, ExecutorService executor) {
        this.weatherSources = new ArrayList<>(weatherSources);
        this.executor = executor;
    }

    // Fetch from all sources asynchronously
    public CompletableFuture<List<WeatherResult>> fetchWeatherFromAllSources(String location) {
        CompletableFuture<WeatherResult>[] futures = weatherSources.stream()
                .map(source -> source.fetchWeather(location)
                        .thenApply(WeatherResult::new)
                        .exceptionally(ex -> {
                            logger.warn("Failed to fetch from {}: {}", source.getName(), ex.getMessage());
                            return new WeatherResult(source.getName(), ex); // Pass source name
                        }))
                .toArray((IntFunction<CompletableFuture<WeatherResult>[]>) CompletableFuture[]::new);

        return CompletableFuture.allOf(futures)
                .thenApply(v -> Arrays.stream(futures)
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }


    // Fetch from first successful source
    public CompletableFuture<WeatherData> fetchWeatherFastest(String location) {
        List<CompletableFuture<WeatherData>> futures = weatherSources.stream()
                .map(source -> source.fetchWeather(location)
                        .exceptionally(ex -> null)) // Convert exceptions to null
                .collect(Collectors.toList());

        return CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(result -> (WeatherData) result)
                .thenApply(data -> {
                    if (data == null) {
                        throw new WeatherException("All weather sources failed");
                    }
                    return data;
                });
    }

    // Fetch with timeout
    public CompletableFuture<WeatherData> fetchWeatherWithTimeout(String location, int timeoutSeconds) {
        CompletableFuture<WeatherData> future = fetchWeatherFastest(location);
        return future.orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        throw new WeatherException("Weather request timed out after " + timeoutSeconds + " seconds");
                    }
                    throw new WeatherException("Failed to fetch weather data", ex);
                });
    }

    // Calculate average temperature from successful sources
    public CompletableFuture<Double> getAverageTemperature(String location) {
        return fetchWeatherFromAllSources(location)
                .thenApply(weatherResultList -> weatherResultList.stream()
                        .filter(WeatherResult::isSuccess)
                        .map(WeatherResult::getData)
                        .mapToDouble(WeatherData::getTemperature)
                        .average()
                        .orElseThrow(() -> new WeatherException("No weather data available")));
    }

    // Process weather data with retry logic
    public CompletableFuture<WeatherData> fetchWeatherWithRetry(String location, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            while (attempts < maxRetries) {
                try {
                    return fetchWeatherFastest(location).get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    attempts++;
                    if (attempts == maxRetries) {
                        throw new WeatherException("Failed after " + maxRetries + " attempts", e);
                    }
                    try {
                        Thread.sleep(1000 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new WeatherException("Operation interrupted", ie);
                    }
                }
            }
            throw new WeatherException("Unexpected error in retry logic");
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
    }
}