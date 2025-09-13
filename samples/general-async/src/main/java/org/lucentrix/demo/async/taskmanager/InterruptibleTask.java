package org.lucentrix.demo.async.taskmanager;



import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class InterruptibleTask implements CancellableTask {
    private static final Logger logger = LoggerFactory.getLogger(InterruptibleTask.class);

    private final String name;
    private final InterruptibleWork work;
    private final boolean shouldFail; // For testing retry mechanism
    private volatile boolean cancellationRequested = false;

    public InterruptibleTask(String name, InterruptibleWork work) {
        this(name, work, false);
    }

    public InterruptibleTask(String name, InterruptibleWork work, boolean shouldFail) {
        this.name = name;
        this.work = work;
        this.shouldFail = shouldFail;
    }

    @Override
    public void run() {
        logger.info("Task {} started on thread: {}", name, Thread.currentThread().getName());

        // Simulate failure for testing retry mechanism
        if (shouldFail && Math.random() < 0.3) {
            throw new RuntimeException("Simulated task failure for testing retry");
        }

        try {
            while (!cancellationRequested && !Thread.currentThread().isInterrupted()) {
                work.perform(); // Use the external work function
                logger.info("Task {} working...", name);
            }
            logger.info("Task {} completed successfully", name);
        } catch (InterruptedException e) {
            logger.info("Task {} interrupted via InterruptedException", name);
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void cancelRequested() {
        cancellationRequested = true;
    }

    @Override
    public boolean isCancellationRequested() {
        return cancellationRequested;
    }
}