package org.lucentrix.demo.async.taskmanager;

import java.util.concurrent.Future;

public class TaskHandle {
    private final Future<?> future;
    private final TaskManager.TaskInfo taskInfo;

    public TaskHandle(Future<?> future, TaskManager.TaskInfo taskInfo) {
        this.future = future;
        this.taskInfo = taskInfo;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    public TaskManager.TaskStatus getStatus() {
        return taskInfo.getStatus();
    }

    public int getRetryCount() {
        return taskInfo.getRetryCount();
    }


}