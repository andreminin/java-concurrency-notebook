package org.lucentrix.demo.async.weather;

import java.util.concurrent.CompletableFuture;

public interface WeatherSource {
    String getName();
    CompletableFuture<WeatherData> fetchWeather(String location);
    CompletableFuture<WeatherData> fetchWeather(String location, int timeoutSeconds);
}