package org.lucentrix.demo.async.taskmanager;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class NonInterruptibleTask implements NamedTask {
    private static final Logger logger = LoggerFactory.getLogger(NonInterruptibleTask.class);

    private final String name;
    private final Runnable work;

    public NonInterruptibleTask(String name, Runnable work) {
        this.name = name;
        this.work = work;
    }

    @Override
    public void run() {
        logger.info("Non-interruptible task {} started", name);

        while (!Thread.currentThread().isInterrupted()) {
            // Execute the external work
            work.run();

            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                logger.info("Non-interruptible task {} interrupted", name);
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.info("Non-interruptible task {} completed", name);
    }
}