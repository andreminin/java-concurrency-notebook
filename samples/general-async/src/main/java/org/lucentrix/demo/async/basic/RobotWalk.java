package org.lucentrix.demo.async.basic;

import java.util.Arrays;

public class RobotWalk implements Runnable {
    private final Step step;
    private static volatile Step nextStep = Step.left;
    private final static Object lock = new Object();

    enum Step {
        left,
        right,
        jump;

        public static Step next(Step step) {
            if(step == null) {
                return values()[0];
            }

            for(int i = 0; i < values().length; i++) {
                if(values()[i] == step) {
                    int nextIndex = (i + 1) % values().length;
                    return values()[nextIndex];
                }
            }

            throw new RuntimeException("Unrecognized step: " + step);
        }
    }

    public RobotWalk(String step) {
        this.step = Step.valueOf(step);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            step();
        }
    }

    private void step() {
        synchronized (lock) {
            while (this.step != nextStep && !Thread.currentThread().isInterrupted()) {
                try {
                    lock.wait(); // Wait on the lock object
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // Exit if interrupted
                }
            }

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            System.out.println("Step " + step);
            try {
                //Simulate real step, take some time, for demonstration
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            nextStep = Step.next(this.step);

            lock.notifyAll();
        }
    }

    public static void main(String[] args) {
        Thread[] threads = Arrays.stream(Step.values())
                .map(step-> {
                    Thread thread =new Thread(new RobotWalk(step.name()));
                    thread.start();
                    return thread;
                })
                .toArray(Thread[]::new);

        try {
            // Let threads run for a while
            Thread.sleep(15000L);

            // Interrupt threads gracefully
            for(Thread thread : threads) {
                try {
                    thread.interrupt();
                } catch (Exception ex) {
                    //NOP
                }
            }

            for(Thread thread : threads) {
                try {
                    thread.join(2000L);
                } catch (Exception ex) {
                    //NOP
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("Done.");
    }
}