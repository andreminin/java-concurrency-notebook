package org.lucentrix.demo.async.completablefuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DemoTwo {
    private static final Logger logger = LoggerFactory.getLogger(DemoTwo.class);

    private void sleep(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Task interrupted, thread: "+Thread.currentThread().getName(), e);
        }
    }

    public CompletableFuture<String> asyncTask(int delay, String result) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Executing asyncTask on thread: {}", Thread.currentThread().getName());
            sleep(delay);

            return result;
        });
    }

    public CompletableFuture<String> chainTask(String input) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Executing chainTask \"{}\" on thread: {}", input, Thread.currentThread().getName());
            sleep(2000L);
            return input + " -> Processed";
        });
    }

    public CompletableFuture<String> errorTask() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Simulating error on thread: {}", Thread.currentThread().getName());
            sleep(2000L);

            throw new RuntimeException("Simulated Error in thread "+Thread.currentThread().getName());
        });
    }

    public void demonstrate() throws ExecutionException, InterruptedException {
        // Chaining
        CompletableFuture<String> future = asyncTask(1000, "Hello")
                .thenCompose(this::chainTask);

        // Branching and combining
        CompletableFuture<String> future2 = asyncTask(500, "World");
        CompletableFuture<String> combined = future.thenCombine(future2, (r1, r2) -> r1 + " | " + r2);

        // Exception handling
        CompletableFuture<String> withException = errorTask()
                .exceptionally(ex -> "Handled: " + ex.getMessage());

        // Wait for results and log
        logger.info("Combined result: {}", combined.get());
        logger.info("Exception result: {}", withException.get());
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        new DemoTwo().demonstrate();
    }
}
