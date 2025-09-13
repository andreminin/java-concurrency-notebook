package org.lucentrix.demo.async.basic;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;


public class RetryExecutor {
    private final int maxAttempts;
    private final long delayMs;
    private final Class<? extends Exception>[] retryableExceptions;

    @SafeVarargs
    public RetryExecutor(int maxAttempts, long delayMs, Class<? extends Exception>... retryableExceptions) {
        this.maxAttempts = maxAttempts;
        this.delayMs = delayMs;
        this.retryableExceptions = retryableExceptions;
    }

    public <T> T execute(Callable<T> task) throws Exception {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                System.out.println("Попытка " + attempt + " из " + maxAttempts);
                return task.call();

            } catch (Exception e) {
                lastException = e;

                if (!isRetryable(e) || attempt == maxAttempts) {
                    break;
                }

                System.out.println("Ошибка: " + e.getMessage() + ". Повтор через " + delayMs + "мс");
                TimeUnit.MILLISECONDS.sleep(delayMs);
            }
        }

        throw lastException;
    }

    private boolean isRetryable(Exception e) {
        for (Class<? extends Exception> exClass : retryableExceptions) {
            if (exClass.isInstance(e)) {
                return true;
            }
        }
        return false;
    }
}
