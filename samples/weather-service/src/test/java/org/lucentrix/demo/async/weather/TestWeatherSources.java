package org.lucentrix.demo.async.weather;

import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;

// Mock weather sources for testing
public class TestWeatherSources {

    public static class FastWeatherSource implements WeatherSource {
        private final String name;
        private final double temperature;

        public FastWeatherSource(String name, double temperature) {
            this.name = name;
            this.temperature = temperature;
        }

        @Override
        public String getName() { return name; }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location) {
            return CompletableFuture.completedFuture(
                    new WeatherData(name, temperature, "Sunny", LocalDateTime.now())
            );
        }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds) {
            return fetchWeather(location);
        }
    }

    public static class SlowWeatherSource implements WeatherSource {
        private final String name;
        private final long delayMs;

        public SlowWeatherSource(String name, long delayMs) {
            this.name = name;
            this.delayMs = delayMs;
        }

        @Override
        public String getName() { return name; }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                    return new WeatherData(name, 20.0, "Cloudy", LocalDateTime.now());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new WeatherException("Interrupted while fetching weather", e);
                }
            });
        }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds) {
            return fetchWeather(location);
        }
    }

    public static class FailingWeatherSource implements WeatherSource {
        private final String name;

        public FailingWeatherSource(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location) {
            return CompletableFuture.failedFuture(
                    new WeatherException("Network error from " + name)
            );
        }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds) {
            return fetchWeather(location);
        }
    }

    public static class RandomWeatherSource implements WeatherSource {
        private final String name;

        public RandomWeatherSource(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location) {
            return CompletableFuture.supplyAsync(() -> {
                if (Math.random() > 0.3) {
                    return new WeatherData(name, 15.0 + Math.random() * 15, "Variable", LocalDateTime.now());
                } else {
                    throw new WeatherException("Random failure from " + name);
                }
            });
        }

        @Override
        public CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds) {
            return fetchWeather(location);
        }
    }
}