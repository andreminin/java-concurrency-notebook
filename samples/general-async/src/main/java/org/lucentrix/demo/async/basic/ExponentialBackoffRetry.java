package org.lucentrix.demo.async.basic;

import java.util.concurrent.Callable;

public class ExponentialBackoffRetry {
    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;

    public ExponentialBackoffRetry(int maxAttempts, long initialDelayMs, long maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public <T> T execute(Callable<T> task) throws Exception {
        long currentDelay = initialDelayMs;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                System.out.printf("Попытка %d (задержка: %dмс)%n", attempt, currentDelay);
                return task.call();
            } catch (Exception e) {
                lastException = e;

                if (attempt == maxAttempts) {
                    break;
                }

                System.out.println("Ошибка: " + e.getMessage());
                Thread.sleep(currentDelay);

                currentDelay = Math.min(currentDelay * 2, maxDelayMs);
            }
        }

        throw new RuntimeException("Не удалось выполнить операцию после " + maxAttempts + " попыток", lastException);
    }
}