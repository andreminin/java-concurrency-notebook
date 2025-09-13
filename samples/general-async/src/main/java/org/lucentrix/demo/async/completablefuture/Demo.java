package org.lucentrix.demo.async.completablefuture;

import java.util.concurrent.CompletableFuture;

public class Demo implements Runnable {

    private void doAsyncOperation() {
        System.out.println("Async operation started...");
        try {
            for(int i=1; i < 10; i++) {
                System.out.print(".");
                Thread.sleep(1000L);
            }
            System.out.println("");
        } catch (InterruptedException e) {
            System.out.println("Async operation interrupted: " + e);
            Thread.currentThread().interrupt();
        }
    }

    private void doSomethingElse() {
        System.out.println("Doing something else");
    }

    public void run() {
        CompletableFuture<Void> asyncOp = CompletableFuture.runAsync(
                this::doAsyncOperation
        );

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        doSomethingElse();

        System.out.println("Waiting for async operation to complete");
        asyncOp.join();

        System.out.println("Async operation completed");
    }

    public static void main(String[] args) {
        Demo demo = new Demo();

        demo.run();

    }
}
