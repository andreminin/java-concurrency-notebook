package org.lucentrix.demo.async.taskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class AsyncCancelDemo {
    private static final Logger logger = LoggerFactory.getLogger(AsyncCancelDemo.class);

    public static void main(String[] args) throws InterruptedException {
        // Configurable TaskManager with 10s shutdown timeout, max 3 retries, 2s retry delay
        TaskManager taskManager = new TaskManager(4, 10000, 3, 2000);
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.warn("Ctrl-C event received");
            logger.info("\n=== Shutting down executor by Ctrl-C ===");
            taskManager.shutdown();
            shutdownLatch.countDown();
        }));

        logger.info("=== Starting tasks ===");

        // Submit interruptible tasks with retry enabled
        taskManager.submit(new InterruptibleTask("Task-1", () -> Thread.sleep(1000), true), true);
        taskManager.submit(new InterruptibleTask("Task-2", () -> Thread.sleep(1000), true), true);
        taskManager.submit(new InterruptibleTask("Task-3", () -> Thread.sleep(1000), false), false);

        // Submit non-interruptible task with retry
        // Submit non-interruptible task with retry
        taskManager.submit(new NonInterruptibleTask("NonInt-Task-1", () -> {
            // Simulate CPU-intensive work
            for (int i = 0; i < 1000000; i++) {
                Math.sqrt(i);
            }
        }), true);

        // Monitor task status periodically
        startStatusMonitor(taskManager);

        // Schedule automatic shutdown after 20 seconds
        scheduleShutdown(taskManager, shutdownLatch, 20000);

        // Wait for shutdown signal
        shutdownLatch.await();

        logger.info("Demo completed");
    }

    private static void startStatusMonitor(TaskManager taskManager) {
        new Thread(() -> {
            while (!taskManager.isShutdown()) {
                try {
                    Thread.sleep(3000);
                    logger.info("=== Current Task Status ===");
                    taskManager.getAllTaskInfos().forEach((name, info) -> {
                        logger.info("Task {}: Status={}, Retries={}, Duration={}ms",
                                name, info.getStatus(), info.getRetryCount(), info.getDuration());
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    private static void scheduleShutdown(TaskManager taskManager, CountDownLatch shutdownLatch, long delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                if (!taskManager.isShutdown()) {
                    logger.info("\n=== Shutting down executor by {} sec timeout ===", delayMs/1000);
                    taskManager.shutdownGracefully(10000); // 10 second timeout
                    shutdownLatch.countDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}