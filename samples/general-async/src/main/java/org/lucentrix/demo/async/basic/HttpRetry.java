package org.lucentrix.demo.async.basic;

public class HttpRetry {
    private final int maxRetries;
    private final long retryDelayMs;

    public HttpRetry(int maxRetries, long retryDelayMs) {
        this.maxRetries = maxRetries;
        this.retryDelayMs = retryDelayMs;
    }

    public String executeHttpRequest(String url) throws Exception {
        SimpleRetry retry = new SimpleRetry(maxRetries, retryDelayMs);

        return retry.execute(() -> {
            // Имитация HTTP запроса
            System.out.println("Выполняем запрос к: " + url);

            double random = Math.random();
            if (random > 0.6) {
                return "HTTP 200 OK - Данные получены";
            } else if (random > 0.3) {
                throw new RuntimeException("HTTP 500 - Internal Server Error");
            } else {
                throw new RuntimeException("HTTP 503 - Service Unavailable");
            }
        });
    }
}