package org.lucentrix.demo.async.modern.credit;

import java.util.concurrent.Callable;

public class ExecutionTimer {
    public static <T> T measure(Callable<T> task) throws Exception {
        long startTime = System.nanoTime();
        try {
            return task.call();
        } finally {
            long endTime = System.nanoTime();
            // Convert to milliseconds
            long duration = (endTime - startTime) / 1_000_000;
            if(duration >= 1L) {
                System.out.println("Execution time: " + duration + " milliseconds");
            } else {
                System.out.println("Execution time: " + (endTime - startTime) + " nanoseconds");
            }
        }
    }
}