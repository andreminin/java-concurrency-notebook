package org.lucentrix.demo.async.basic;

public class BasicThreadCoordination {
    private volatile int turn = 1;



    public synchronized void process(int waitForValue, int nextValue, String nextName) {
        String name = Thread.currentThread().getName();
        try {
            while (turn != waitForValue) {
                wait();
            }
            System.out.println(name + " worker processing task");
            Thread.sleep(1000);
            turn = nextValue;
            System.out.println(name + " worker completed task, delegated to "+nextName+" worker");
            notifyAll();
        } catch (InterruptedException e) {
            // 1. Log the interruption if needed (good practice for debugging)
            System.out.println(name + " was interrupted. Exiting gracefully.");

            // 2. CRITICAL: Restore the interrupt status.
            // This lets code higher up the call stack (e.g., a thread pool) know about the interrupt.
            Thread.currentThread().interrupt();

            // 3. Optional: Perform any necessary cleanup here.
            // For example, ensure 'turn' is in a valid state if the interrupt happened at a bad time.

            // 4. The method will exit, and the thread will likely terminate because
            // its interrupt flag is now set.
        }
    }


    public static void main(String[] args) {
        BasicThreadCoordination coordinator = new BasicThreadCoordination();

        String[] workerNames = new String[] { "first", "second", "third"};
        int count = workerNames.length;

        for(int i = 0; i < count; i++) {
            int index = (i + 1) % count;

            Thread thread = new Thread(() -> {
                String name = Thread.currentThread().getName();
                while (!Thread.currentThread().isInterrupted()) {
                    System.out.println(name + " worker is waiting to start task");
                    coordinator.process(index, (index + 1) % count, workerNames[index]);
                }
            }, "Worker-"+index);

            System.out.println("Created worker: "+thread.getName());

            thread.start();
        }



    }
}
