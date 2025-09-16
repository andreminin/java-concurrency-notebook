package org.lucentrix.demo.async.completablefuture;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.*;
import java.util.*;

public class CombinedDemo {

    static class SharedResource {
        private final int state = 0;
        private static final VarHandle STATE_HANDLE;

        static {
            try {
                STATE_HANDLE = MethodHandles.lookup()
                        .findVarHandle(SharedResource.class, "state", int.class);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        public CompletableFuture<Integer> atomicUpdate(int newValue) {
            return CompletableFuture.supplyAsync(() -> {
                int current;
                do {
                    current = (int) STATE_HANDLE.getVolatile(this);
                } while (!STATE_HANDLE.compareAndSet(this, current, newValue));

                return current; // Return previous value
            });
        }

        public int getState() {
            return (int) STATE_HANDLE.getVolatile(this);
        }
    }

    public static void main(String[] args) throws Exception {
        SharedResource resource = new SharedResource();
        ExecutorService executor = Executors.newFixedThreadPool(5);

        // Multiple threads trying to update the state atomically
        List<CompletableFuture<Integer>> updates = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            final int newValue = i * 10;
            updates.add(resource.atomicUpdate(newValue));
        }

        // Wait for all updates and collect results
        CompletableFuture<Void> allUpdates = CompletableFuture.allOf(
                updates.toArray(new CompletableFuture[0])
        );

        allUpdates.thenRun(() -> {
            System.out.println("All updates completed");
            System.out.println("Final state: " + resource.getState());

            // Print all previous values
            updates.forEach(future -> {
                try {
                    System.out.println("Previous value: " + future.get());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }).get();

        executor.shutdown();
    }
}
