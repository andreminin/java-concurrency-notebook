package org.lucentrix.demo.async.basic;

import java.util.concurrent.Callable;

public class SimpleRetry {
    private final int maxRetries;
    private final long retryDelayMs;

    public SimpleRetry(int maxRetries, long retryDelayMs) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public void execute(Runnable task) throws InterruptedException {
        execute(() -> {
            task.run();
            return null;
        });
    }

    public <T> T execute(Callable<T> task) throws InterruptedException {
        int attempt = 0;
        while (true) {
            try {
                attempt++;
                System.out.println("Попытка #" + attempt);
                return task.call();
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    System.out.println("Все попытки исчерпаны");
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException("Ошибка после " + attempt + " попыток", e);
                }

                System.out.println("Ошибка, повтор через " + retryDelayMs + "мс: " + e.getMessage());
                Thread.sleep(retryDelayMs);
            }
        }
    }
}