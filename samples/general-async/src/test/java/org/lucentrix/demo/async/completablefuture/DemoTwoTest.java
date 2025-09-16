package org.lucentrix.demo.async.completablefuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

public class DemoTwoTest {

    private final DemoTwo demo = new DemoTwo();

    @Test
    void testAsyncTaskSuccess() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = demo.asyncTask(100, "Test");
        assertEquals("Test", future.get());
    }

    @Test
    void testChaining() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = demo.asyncTask(100, "Hello")
                .thenCompose(demo::chainTask);
        assertEquals("Hello -> Processed", future.get());
    }

    @Test
    void testErrorHandling() throws ExecutionException, InterruptedException {
        CompletableFuture<String> future = demo.errorTask()
                .exceptionally(ex -> "Handled: " + ex.getMessage());
        assertTrue(future.get().startsWith("Handled:"));
    }

    @Test
    @Timeout(2)
    void testCancellation() {
        CompletableFuture<String> future = demo.asyncTask(2000, "Delayed");
        future.cancel(true);
        assertThrows(CancellationException.class, future::get);
    }
}
