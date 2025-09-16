package org.lucentrix.demo.async.completablefuture;

import java.util.concurrent.*;

public class DemoOne {

    // Simulate a long-running task
    private static String fetchData(String source) {
        try {
            Thread.sleep(1000); // Simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Data from " + source;
    }

    // Simulate data processing
    private static String processData(String data) {
        return data.toUpperCase() + " [PROCESSED]";
    }

    // Simulate an operation that might fail
    private static String riskyOperation(String input) {
        if (Math.random() > 0.5) {
            throw new RuntimeException("Operation failed randomly!");
        }
        return input + " [RISKY SUCCESS]";
    }

    public static void main(String[] args) throws Exception {
        // 1. Basic usage
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> fetchData("API"));
        future.thenAccept(result -> System.out.println("Basic result: " + result));

        // 2. Chaining operations
        CompletableFuture<String> processedFuture = CompletableFuture
                .supplyAsync(() -> fetchData("Database"))
                .thenApply(DemoOne::processData)
                .thenApply(String::toLowerCase);

        processedFuture.thenAccept(result -> System.out.println("Chained result: " + result));

        // 3. Exception handling
        CompletableFuture<String> safeFuture = CompletableFuture
                .supplyAsync(() -> riskyOperation("test"))
                .exceptionally(ex -> "Fallback value due to: " + ex.getMessage());

        safeFuture.thenAccept(result -> System.out.println("Exception handling: " + result));

        // 4. Combining multiple futures
        CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> fetchData("Source1"));
        CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> fetchData("Source2"));

        future1.thenCombine(future2, (result1, result2) -> result1 + " + " + result2)
                .thenAccept(combined -> System.out.println("Combined: " + combined));

        // 5. Running after both complete (without result) - CORRECTED
        future1.thenAcceptBoth(future2, (result1, result2) ->
                System.out.println("Both operations completed with: " + result1 + " and " + result2));

        // Alternative: runAfterBoth (no results)
        future1.runAfterBoth(future2, () ->
                System.out.println("Both operations completed!"));

        // 6. AnyOf - first completed future
        CompletableFuture<Object> anyFuture = CompletableFuture.anyOf(
                CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(2000); return "Slow Service"; }
                    catch (InterruptedException e) { return "Slow Failed"; }
                }),
                CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(500); return "Fast Service"; }
                    catch (InterruptedException e) { return "Fast Failed"; }
                })
        );

        anyFuture.thenAccept(result -> System.out.println("First completed: " + result));

        // 7. Complex composition with handle()
        CompletableFuture.supplyAsync(() -> riskyOperation("important"))
                .handle((result, exception) -> {
                    if (exception != null) {
                        return "Recovered from: " + exception.getMessage();
                    }
                    return result + " [HANDLED]";
                })
                .thenAccept(result -> System.out.println("Handled result: " + result));

        // 8. Using custom executor
        ExecutorService customExecutor = Executors.newFixedThreadPool(3);
        CompletableFuture<String> customFuture = CompletableFuture.supplyAsync(
                () -> fetchData("Custom Executor"), customExecutor
        );

        customFuture.thenAcceptAsync(result ->
                System.out.println("Custom executor result: " + result), customExecutor);

        // 9. Completing manually
        CompletableFuture<String> manualFuture = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Thread.sleep(800);
                manualFuture.complete("Manually completed!");
            } catch (InterruptedException e) {
                manualFuture.completeExceptionally(e);
            }
        }).start();

        manualFuture.thenAccept(result -> System.out.println("Manual completion: " + result));

        // 10. Timeout handling (Java 9+)
        CompletableFuture<String> timeoutFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(3000); // This will timeout
                        return "Success";
                    } catch (InterruptedException e) {
                        return "Interrupted";
                    }
                }).orTimeout(2, TimeUnit.SECONDS) // Timeout after 2 seconds
                .exceptionally(ex -> "Timeout occurred: " + ex.getMessage());

        timeoutFuture.thenAccept(result -> System.out.println("Timeout test: " + result));

        // Wait for all operations to complete
        Thread.sleep(4000);
        customExecutor.shutdown();
    }
}