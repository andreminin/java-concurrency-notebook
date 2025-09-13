package org.lucentrix.demo.async.weather;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class WeatherData {
    private final String source;
    private final double temperature;
    private final String condition;
    private final LocalDateTime timestamp;

}
