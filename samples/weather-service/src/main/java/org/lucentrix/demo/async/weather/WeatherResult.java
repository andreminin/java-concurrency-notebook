package org.lucentrix.demo.async.weather;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class WeatherResult {
    private final WeatherData data;
    private final Throwable error;
    private final boolean success;
    private final String sourceName; // Add this field

    public WeatherResult(WeatherData data) {
        this.data = data;
        this.error = null;
        this.success = true;
        this.sourceName = data != null ? data.getSource() : "unknown";
    }

    public WeatherResult(String sourceName, Throwable error) {
        this.data = null;
        this.error = error;
        this.success = false;
        this.sourceName = sourceName;
    }
}