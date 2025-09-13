package org.lucentrix.demo.async.taskmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class TaskManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TaskManager.class);

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, CANCELLED, FAILED, RETRYING
    }

    // Configuration
    private final long defaultShutdownTimeoutMs;
    private final int maxRetryAttempts;
    private final long retryDelayMs;

    // Executors
    private final ExecutorService taskExecutor;
    private final ScheduledExecutorService retryExecutor;

    // Task tracking
    private final Map<String, TaskInfo> taskInfos = new ConcurrentHashMap<>();
    private final Map<String, TaskHandle> taskHandles = new ConcurrentHashMap<>();

    // Metrics
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong failedTasks = new AtomicLong(0);
    private final AtomicLong cancelledTasks = new AtomicLong(0);
    private final AtomicLong retriedTasks = new AtomicLong(0);

    public class TaskHandle {
        private final Future<?> future;
        private final TaskInfo taskInfo;
        private final String taskName;

        public TaskHandle(Future<?> future, TaskInfo taskInfo, String taskName) {
            this.future = future;
            this.taskInfo = taskInfo;
            this.taskName = taskName;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = future.cancel(mayInterruptIfRunning);
            if (cancelled) {
                taskInfo.setStatus(taskInfo.getStatus(), TaskStatus.CANCELLED);
                taskInfo.setCompletionTime(System.currentTimeMillis());
                cancelledTasks.incrementAndGet();
                taskHandles.remove(taskName);

                // Notify task if it supports cancellation
                TaskInfo info = taskInfos.get(taskName);
                if (info != null && info.getTask() instanceof CancellableTask) {
                    ((CancellableTask) info.getTask()).cancelRequested();
                }
            }
            return cancelled;
        }

        public TaskStatus getStatus() {
            return taskInfo.getStatus();
        }

        public int getRetryCount() {
            return taskInfo.getRetryCount();
        }

        public long getDuration() {
            return taskInfo.getDuration();
        }

        public Throwable getFailureCause() {
            return taskInfo.getFailureCause();
        }
    }

    public static class TaskInfo {
        private final String name;
        private final NamedTask task;
        private final AtomicReference<TaskStatus> status;
        private final AtomicInteger retryCount;
        private final long submissionTime;
        private volatile long completionTime;
        private volatile Throwable failureCause;

        public TaskInfo(String name, NamedTask task) {
            this.name = name;
            this.task = task;
            this.status = new AtomicReference<>(TaskStatus.PENDING);
            this.retryCount = new AtomicInteger(0);
            this.submissionTime = System.currentTimeMillis();
        }

        // Getters and setters
        public String getName() { return name; }
        public NamedTask getTask() { return task; }
        public TaskStatus getStatus() { return status.get(); }
        public boolean setStatus(TaskStatus expected, TaskStatus newStatus) {
            return status.compareAndSet(expected, newStatus);
        }
        public int getRetryCount() { return retryCount.get(); }
        public int incrementRetryCount() { return retryCount.incrementAndGet(); }
        public long getSubmissionTime() { return submissionTime; }
        public long getCompletionTime() { return completionTime; }
        public void setCompletionTime(long time) { completionTime = time; }
        public Throwable getFailureCause() { return failureCause; }
        public void setFailureCause(Throwable cause) { failureCause = cause; }
        public long getDuration() {
            return completionTime > 0 ? completionTime - submissionTime :
                    System.currentTimeMillis() - submissionTime;
        }
    }

    public TaskManager(int poolSize) {
        this(poolSize, 10000, 3, 1000); // Default 10s timeout, 3 retries, 1s delay
    }

    public TaskManager(int poolSize, long defaultShutdownTimeoutMs, int maxRetryAttempts, long retryDelayMs) {
        AtomicInteger counter = new AtomicInteger(1);
        this.taskExecutor = Executors.newFixedThreadPool(poolSize,
                r -> new Thread(r, "task-worker-" + counter.getAndIncrement()));
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "retry-scheduler"));
        this.defaultShutdownTimeoutMs = defaultShutdownTimeoutMs;
        this.maxRetryAttempts = maxRetryAttempts;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public void close() {
        shutdownGracefully(defaultShutdownTimeoutMs);
    }

    public TaskHandle submit(NamedTask task) {
        return submit(task, false);
    }

    public TaskHandle submit(NamedTask task, boolean retryOnFailure) {
        String taskName = task.getName();

        if (taskExecutor.isShutdown()) {
            throw new RejectedExecutionException("Executor is shutdown");
        }

        synchronized (taskInfos) {
            if (taskInfos.containsKey(taskName)) {
                throw new IllegalArgumentException("Duplicate task name: " + taskName);
            }

            TaskInfo taskInfo = new TaskInfo(taskName, task);
            taskInfos.put(taskName, taskInfo);

            try {
                Future<?> future = taskExecutor.submit(() -> {
                    try {
                        taskInfo.setStatus(TaskStatus.PENDING, TaskStatus.RUNNING);
                        task.run();
                        taskInfo.setCompletionTime(System.currentTimeMillis());
                        taskInfo.setStatus(TaskStatus.RUNNING, TaskStatus.COMPLETED);
                        completedTasks.incrementAndGet();
                    } catch (Exception e) {
                        handleTaskFailure(task, taskInfo, e, retryOnFailure);
                    } finally {
                        taskHandles.remove(taskName);
                    }
                });

                TaskHandle handle = new TaskHandle(future, taskInfo, taskName);
                taskHandles.put(taskName, handle);
                return handle;

            } catch (RejectedExecutionException e) {
                taskInfos.remove(taskName);
                throw new IllegalStateException("Executor is shutdown", e);
            }
        }
    }

    private void handleTaskFailure(NamedTask task, TaskInfo taskInfo, Throwable cause, boolean retryOnFailure) {
        taskInfo.setFailureCause(cause);
        taskInfo.setCompletionTime(System.currentTimeMillis());
        failedTasks.incrementAndGet();

        if (retryOnFailure && taskInfo.getRetryCount() < maxRetryAttempts) {
            taskInfo.setStatus(TaskStatus.RUNNING, TaskStatus.RETRYING);
            retriedTasks.incrementAndGet();
            taskInfo.incrementRetryCount();

            logger.warn("Task {} failed, retrying {}/{} in {}ms",
                    taskInfo.getName(), taskInfo.getRetryCount(), maxRetryAttempts, retryDelayMs);

            scheduleRetry(task, taskInfo, retryOnFailure);
        } else {
            taskInfo.setStatus(TaskStatus.RUNNING, TaskStatus.FAILED);
            logger.error("Task {} failed permanently after {} attempts",
                    taskInfo.getName(), taskInfo.getRetryCount() + 1, cause);
        }
    }

    private void scheduleRetry(NamedTask task, TaskInfo taskInfo, boolean retryOnFailure) {
        retryExecutor.schedule(() -> {
            if (!taskExecutor.isShutdown()) {
                submit(task, retryOnFailure);
            }
        }, retryDelayMs, TimeUnit.MILLISECONDS);
    }

    public boolean cancelTask(String taskName, boolean mayInterruptIfRunning) {
        TaskHandle handle = taskHandles.get(taskName);
        if (handle != null) {
            return handle.cancel(mayInterruptIfRunning);
        }
        return false;
    }

    public void cancelAllTasks(boolean mayInterruptIfRunning) {
        int cancelledCount = 0;
        try {
            for (TaskHandle handle : taskHandles.values()) {
                if (handle.cancel(mayInterruptIfRunning)) {
                    cancelledCount++;
                }
            }
        } catch (Exception ex) {
            logger.warn("Tasks cancel error", ex);
        } finally {
            logger.info("Cancelled {} tasks", cancelledCount);
        }
    }

    public ShutdownReport shutdownGracefully(long timeoutMs) {
        ShutdownReport report = new ShutdownReport();
        try {
            logger.info("Initiating graceful shutdown...");

            // Step 1: Prevent new tasks
            taskExecutor.shutdown();
            retryExecutor.shutdown();

            // Step 2: Give tasks a chance to complete naturally first
            logger.info("Waiting for tasks to complete naturally (timeout: {} ms)", timeoutMs);

            if (!taskExecutor.awaitTermination(timeoutMs / 2, TimeUnit.MILLISECONDS)) {
                // Step 3: If timeout, then cancel aggressively
                logger.info("Natural completion timeout, cancelling tasks aggressively");
                cancelAllTasks(true);

                // Step 4: Wait for cancellation to take effect
                taskExecutor.awaitTermination(timeoutMs / 2, TimeUnit.MILLISECONDS);
            }

            // Wait for retry executor to terminate
            retryExecutor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (InterruptedException e) {
            logger.warn("Shutdown interrupted, forcing immediate shutdown");
            Thread.currentThread().interrupt();
            forceShutdown();
        } finally {
            report.unfinishedTasks = getAllTaskInfos().values().stream()
                    .filter(info -> info.getStatus() != TaskStatus.COMPLETED &&
                            info.getStatus() != TaskStatus.FAILED &&
                            info.getStatus() != TaskStatus.CANCELLED)
                    .toList();
            printMetrics();
            cleanup();
        }

        return report;
    }

    public void shutdown() {
        shutdownGracefully(defaultShutdownTimeoutMs);
    }

    private void forceShutdown() {
        try {
            logger.warn("Initiating forced shutdown");
            List<Runnable> runningTasks = taskExecutor.shutdownNow();
            List<Runnable> retryTasks = retryExecutor.shutdownNow();
            logger.warn("{} tasks were forcibly terminated", runningTasks.size() + retryTasks.size());

        } catch (Exception e) {
            logger.error("Error during forced shutdown", e);
        } finally {
            taskHandles.clear();
        }
    }

    private void printMetrics() {
        logger.info("=== Task Execution Metrics ===");
        logger.info("Completed tasks: {}", completedTasks.get());
        logger.info("Failed tasks: {}", failedTasks.get());
        logger.info("Cancelled tasks: {}", cancelledTasks.get());
        logger.info("Retried tasks: {}", retriedTasks.get());
        logger.info("Currently tracked tasks: {}", taskInfos.size());
    }

    public void cleanup() {
        try {
            if (!taskExecutor.isTerminated()) {
                taskExecutor.shutdownNow();
            }
            if (!retryExecutor.isTerminated()) {
                retryExecutor.shutdownNow();
            }
        } finally {
            taskHandles.clear();
            logger.info("Cleanup completed");
        }
    }

    // Getters for monitoring
    public TaskInfo getTaskInfo(String taskName) {
        return taskInfos.get(taskName);
    }

    public Map<String, TaskInfo> getAllTaskInfos() {
        return new ConcurrentHashMap<>(taskInfos);
    }

    public long getCompletedTaskCount() {
        return completedTasks.get();
    }

    public long getFailedTaskCount() {
        return failedTasks.get();
    }

    public long getCancelledTaskCount() {
        return cancelledTasks.get();
    }

    public boolean isShutdown() {
        return taskExecutor.isShutdown();
    }

    public boolean isTerminated() {
        return taskExecutor.isTerminated();
    }

    public static class ShutdownReport {
        private List<TaskInfo> unfinishedTasks;

        public List<TaskInfo> getUnfinishedTasks() {
            return unfinishedTasks;
        }

        public int getUnfinishedTaskCount() {
            return unfinishedTasks != null ? unfinishedTasks.size() : 0;
        }
    }
}