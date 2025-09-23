package org.lucentrix.demo.async.modern.locksupport;

import java.util.concurrent.locks.LockSupport;

public class LockSupportExample {

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            System.out.println("Worker thread: About to park...");
            LockSupport.park(Thread.currentThread()); // Park the worker thread
            System.out.println("Worker thread: Unparked!");
        });

        worker.start();

        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000); // Give the worker a chance to park
            System.out.print(". ");
        }
        System.out.println();

        System.out.println("Main thread: Unparking worker thread...");
        LockSupport.unpark(worker); // Unpark the worker thread
    }
}
