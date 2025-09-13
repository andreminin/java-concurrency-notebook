package org.lucentrix.demo.async.taskmanager;

public interface CancellableTask extends NamedTask {
    void cancelRequested();
    boolean isCancellationRequested();
}