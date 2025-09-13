Key Features:

    Graceful Interruption: Tasks check Thread.interrupted() and respond appropriately

    Dual Cancellation Strategy: Supports both interruptible and non-interruptible tasks

    Task Tracking: Maintains references to all submitted tasks for cancellation

    Proper Shutdown: Uses shutdown() and awaitTermination() for clean executor shutdown

    Exception Handling: Properly handles InterruptedException and preserves interrupt status

Best Practices Demonstrated:

    Always check Thread.interrupted() in long-running tasks

    Use future.cancel(true) to interrupt running threads

    Implement cooperative cancellation for non-interruptible operations

    Clean up resources and maintain task references for management

    Proper executor shutdown sequence to avoid resource leaks

This implementation provides a robust foundation for managing and cancelling async tasks efficiently in Java.