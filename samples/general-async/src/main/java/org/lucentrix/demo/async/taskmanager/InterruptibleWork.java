package org.lucentrix.demo.async.taskmanager;

@FunctionalInterface
public interface InterruptibleWork {
    void perform() throws InterruptedException;
}