# Problems and Solutions of Using Legacy Async in Java and Modern Approaches

### 1. Legacy Async (wait(), notify())

Problems:

1. Low-level nature: requires manual monitor management and synchronization.

2. Debugging complexity: difficult to track thread states and wait conditions.

3. Risk of deadlock: easy to create deadlocks.

4. Spurious wakeups: spurious wakeups without notify() call.

5. Limitations: doesn't support call chains or combinations.



Problematic code example:

```java
public class LegacyExample {
    private boolean condition = false;
    
    public synchronized void waitForCondition() throws InterruptedException {
        while (!condition) {
            wait(); // May cause spurious wakeup
        }
    }
    
    public synchronized void setCondition() {
        condition = true;
        notify(); // Wakes up only one thread
    }
}
```



### 2. Future Interface

#### Problems:

- **Blocking operations**: get() blocks the current thread
- **No callback mechanism**: No mechanism to react to completion
- **Limited composition**: Difficult to combine multiple Futures
- **Manual management**: Need to manually create ExecutorService

Example:

```java
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<String> future = executor.submit(() -> {
    Thread.sleep(1000);
    return "Result";
});

// Blocking call
String result = future.get(); // Thread blocks here
```



## 3. CompletableFuture

### Solutions and Advantages:

- **Non-blocking operations**: Callback support via thenApply(), thenAccept()
- **Composition**: Easy to combine multiple async operations
- **Exception handling**: Built-in exception handling
- **Manual and automatic management**: Flexible thread pool selection

Example:

```java
 CompletableFuture.supplyAsync(() -> "Hello")
    .thenApplyAsync(s -> s + " World")
    .thenAcceptAsync(System.out::println)
    .exceptionally(ex -> {
        System.out.println("Error: " + ex.getMessage());
        return null;
    });
```



### Problems of CompletableFuture:

- **Debugging complexity**: Chains can be difficult to understand

- **Memory overhead**: Additional objects for each stage

- **Risk of pool blocking**: When used incorrectly

  

## 4. Virtual Threads (Project Loom)

### Solutions and Advantages:

- **Lightweight**: Minimal memory footprint (∼1KB vs 1MB for platform threads)

- **Simplified model**: Write as synchronous code, works as asynchronous

- **Scalability**: Supports millions of concurrent connections

- **Compatibility**: Works with existing code and libraries

  Example:

```java
  try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        executor.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return i;
        });
    }
} // Automatic waiting for completion
```



### Problems of Virtual Threads:

- **Novelty**: Requires Java 19+
- **Monitor locks**: synchronized blocks still block carrier thread
- **Migration**: Not all libraries are optimized for virtual threads

## Comparison table

| Approach          | Year     | Pros                    | Cons                 | Use Cases                 |
| ----------------- | -------- | ----------------------- | -------------------- | ------------------------- |
| wait()/notify()   | Java 1.0 | Low-level control       | Complexity, deadlock | Low-level synchronization |
| Future            | Java 5   | Simple abstraction      | Blocking, limited    | Simple async tasks        |
| CompletableFuture | Java 8   | Powerful composition    | Debugging complexity | Complex async chains      |
| Virtual Threads   | Java 19+ | Scalability, simplicity | Requires Java 19+    | High-load IO applications |



## Recommendations for usage

1. **New projects**: Use [Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html) for IO-bound tasks
2. **Existing code**: Gradually migrate from CompletableFuture to Virtual Threads
3. **Computations**: For CPU-bound tasks use platform threads with limited pool
4. **Synchronization**: Replace synchronized with [ReentrantLock](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html) for virtual threads

```java
// Modern approach with Virtual Threads and CompletableFuture
public class ModernAsyncExample {
    public CompletableFuture<String> processAsync() {
        return CompletableFuture.supplyAsync(() -> {
            // IO-bound operation in virtual thread
            return fetchDataFromNetwork();
        }, Executors.newVirtualThreadPerTaskExecutor())
        .thenApplyAsync(data -> {
            return processData(data);
        })
        .exceptionally(ex -> "Fallback result");
    }
    
    private String fetchDataFromNetwork() {
        // Simulated network request
        try { Thread.sleep(1000); } 
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "Network Data";
    }
    
    private String processData(String data) {
        return data.toUpperCase();
    }
}
```



Virtual Threads represent the most promising direction, combining the  simplicity of synchronous programming with the scalability of the  asynchronous approach.

# Solving carrier thread blocking when calling "synchronized" section code in virtual threads

## Problem

When virtual thread calls legacy code with `synchronized`, it blocks the carrier thread (real platform thread). If all carrier  threads get blocked, the application cannot process new tasks.

## Possible solutions

### 1. Replacing synchronized with ReentrantLock

**Most recommended solution** - modifying legacy code:

```java
// Before (problematic code)
public class LegacyService {
    private final Object lock = new Object();
    
    public String legacyMethod() {
        synchronized(lock) {
            // Blocking operation
            return doWork();
        }
    }
}

// After (solution)
public class FixedLegacyService {
    private final ReentrantLock lock = new ReentrantLock();
    
    public String fixedLegacyMethod() {
        lock.lock();
        try {
            // Virtual thread can be unmounted here
            return doWork();
        } finally {
            lock.unlock();
        }
    }
}
```



### 2. Using executors with more carrier threads

**Temporary solution** if legacy code cannot be modified:

```java
public class ThreadPoolConfig {
    // Increase the number of carrier threads
    private static final ExecutorService virtualThreadExecutor = 
        Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual()
                .scheduler(Executors.newFixedThreadPool(100)) // More carrier threads
                .factory()
        );
    
    public static CompletableFuture<String> executeSafely(Runnable task) {
        return CompletableFuture.supplyAsync(() -> {
            task.run();
            return "Done";
        }, virtualThreadExecutor);
    }
}
```



### 3. Isolating blocking operations in separate thread pool

**Isolating blocking code**:

```java
public class BlockingOperationsIsolator {
    private static final ExecutorService blockingPool = 
        Executors.newFixedThreadPool(50); // Dedicated pool for blocking operations
    
    private static final ExecutorService virtualPool = 
        Executors.newVirtualThreadPerTaskExecutor();
    
    public static CompletableFuture<String> executeWithIsolation(Callable<String> task) {
        return CompletableFuture.supplyAsync(() -> {
            // Run blocking operation in a separate pool
            Future<String> future = blockingPool.submit(task);
            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, virtualPool);
    }
}
```



### 4. Using async wrappers

**Creating non-blocking wrappers** around legacy code:

```java
public class AsyncLegacyWrapper {
    private final LegacyService legacyService;
    private final ExecutorService executor;
    
    public AsyncLegacyWrapper(LegacyService legacyService) {
        this.legacyService = legacyService;
        this.executor = Executors.newFixedThreadPool(20);
    }
    
    public CompletableFuture<String> legacyMethodAsync() {
        return CompletableFuture.supplyAsync(legacyService::legacyMethod, executor);
    }
    
    // Decorator pattern for gradual migration
    public String legacyMethodWithVirtualThread() {
        if (Thread.currentThread().isVirtual()) {
            return legacyMethodAsync().join(); // Careful: may block
        } else {
            return legacyService.legacyMethod();
        }
    }
}
```



### 5. Monitoring and limiting

**Detecting and limiting** problematic calls:

```java
public class BlockingCallMonitor {
    private static final Counter blockingCalls = Metrics.counter("blocking.calls");
    private static final ExecutorService limitedExecutor = 
        Executors.newFixedThreadPool(10); // Limit concurrency
    
    public static <T> T executeWithMonitoring(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            CompletableFuture<T> future = CompletableFuture.supplyAsync(supplier, limitedExecutor);
            return future.get(5, TimeUnit.SECONDS); // Timeout
        } catch (TimeoutException e) {
            blockingCalls.increment();
            throw new RuntimeException("Blocking call timeout", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            long duration = System.nanoTime() - start;
            if (duration > TimeUnit.MILLISECONDS.toNanos(100)) {
                System.out.println("Warning: potential blocking call took " + 
                    TimeUnit.NANOSECONDS.toMillis(duration) + "ms");
            }
        }
    }
}
```



### 6. Gradual migration

**Phased approach** to fixing:

```java
public class MigrationStrategy {
    // Phase 1: Detect problematic areas
    public static void detectBlockingCalls() {
        System.setProperty("jdk.tracePinnedThreads", "full");
        // JVM will log pinned threads
    }
    
    // Phase 2: Temporary solution - more carrier threads
    public static ExecutorService createSafeExecutor() {
        return Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual()
                .scheduler(Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors() * 10
                ))
                .factory()
        );
    }
    
    // Phase 3: Gradual synchronized replacement
    public static class MigratedService {
        private final Lock lock = new ReentrantLock();
        
        public void migratedMethod() {
            lock.lock();
            try {
                // Old logic
            } finally {
                lock.unlock();
            }
        }
    }
}
```



### 7. Using jstack for diagnostics

**Diagnosing** blocked carrier threads:

```bash
# Run application with pinned threads monitoring
java -Djdk.tracePinnedThreads=full -jar your-application.jar

# Search for blocked threads
jstack <pid> | grep -A 10 -B 5 "carrier"
```



## Recommendations for choosing a solution

1. **Priority 1**: Replace `synchronized` with `ReentrantLock` in legacy code
2. **Priority 2**: Isolate blocking operations in separate pool
3. **Priority 3**: Increase number of carrier threads (temporary solution)
4. **Monitoring**: Always enable `-Djdk.tracePinnedThreads=full` in production



## Example of a complete solution

```java
public class SafeVirtualThreadExecutor {
    private final ExecutorService virtualThreadExecutor;
    private final ExecutorService blockingOperationsPool;
    
    public SafeVirtualThreadExecutor() {
        this.blockingOperationsPool = Executors.newFixedThreadPool(20);
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    public <T> CompletableFuture<T> executeSafely(Supplier<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            if (isPotentiallyBlocking(task)) {
                // Move to separate pool
                return executeInBlockingPool(task);
            } else {
                // Execute in virtual thread
                return task.get();
            }
        }, virtualThreadExecutor);
    }
    
    private <T> T executeInBlockingPool(Supplier<T> task) {
        try {
            Future<T> future = blockingOperationsPool.submit(task::get);
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("Blocking operation timeout", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private boolean isPotentiallyBlocking(Supplier<?> task) {
        // Heuristic for detecting blocking operations
        // Can use annotations or configuration
        return task.getClass().getName().contains("Legacy");
    }
}
```



This approach allows safe use of virtual threads even with legacy code containing `synchronized` blocks.



# Heuristics for detecting blocking code in Virtual Threads

## 1. Annotation Approach

Defining Annotations example:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BlockingOperation {
    String reason() default "Uses synchronized or blocking IO";
    int timeoutMs() default 5000;
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface NonBlocking {
    String version() default "1.0";
}
```



Using Annotations sample:

```java
public class LegacyService {
    
    @BlockingOperation(reason = "Uses synchronized block", timeoutMs = 3000)
    public String blockingMethod() {
        synchronized(this) {
            return doBlockingWork();
        }
    }
    
    @NonBlocking(version = "2.0")
    public String nonBlockingMethod() {
        return doNonBlockingWork();
    }
}
```



## 2. Code analysis heuristic

Method and class signature analysis

```java
public class BlockingCodeHeuristic {
    
    private static final Set<String> BLOCKING_KEYWORDS = Set.of(
        "synchronized", "wait", "notify", "notifyAll", 
        "lock", "Lock", "Semaphore", "CountDownLatch"
    );
    
    private static final Set<String> BLOCKING_CLASS_PATTERNS = Set.of(
        ".*Legacy.*", ".*Blocking.*", ".*Sync.*", ".*Old.*",
        ".*Traditional.*", ".*Classic.*"
    );
    
    private static final Set<String> BLOCKING_METHOD_PATTERNS = Set.of(
        ".*synchronized.*", ".*lock.*", ".*wait.*", ".*blocking.*",
        ".*sync.*", ".*critical.*", ".*mutex.*"
    );
    
    public static boolean isPotentiallyBlocking(Method method) {
        // Check annotations
        if (method.isAnnotationPresent(BlockingOperation.class)) {
            return true;
        }
        if (method.isAnnotationPresent(NonBlocking.class)) {
            return false;
        }
        
        // Check method and class names
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();
        
        return containsBlockingPattern(className, methodName);
    }
    
    private static boolean containsBlockingPattern(String className, String methodName) {
        // Check name patterns
        for (String pattern : BLOCKING_CLASS_PATTERNS) {
            if (className.matches(pattern)) {
                return true;
            }
        }
        
        for (String pattern : BLOCKING_METHOD_PATTERNS) {
            if (methodName.matches(pattern)) {
                return true;
            }
        }
        
        return false;
    }
}
```



## 3. Static Analysis Heuristic

Method bytecode analysis

```java
public class BytecodeAnalyzer {
    
    public static boolean hasSynchronizedBlocks(Method method) {
        try {
            byte[] bytecode = getBytecode(method);
            return analyzeBytecodeForSynchronization(bytecode);
        } catch (Exception e) {
            return false; // In case of error, assume the worst
        }
    }
    
    private static boolean analyzeBytecodeForSynchronization(byte[] bytecode) {
        // Simple search for bytecode monitor instructions
        // monitorenter (0xC2) and monitorexit (0xC3)
        for (byte b : bytecode) {
            if (b == (byte) 0xC2 || b == (byte) 0xC3) {
                return true;
            }
        }
        return false;
    }
}
```



## 4. Runtime Analysis Heuristic

Dynamic profiling

```java
public class RuntimeBlockingDetector {
    
    private static final Map<Method, Boolean> blockingCache = new ConcurrentHashMap<>();
    private static final Map<Method, Long> executionTimeStats = new ConcurrentHashMap<>();
    
    public static boolean isBlockingOperation(Supplier<?> operation) {
        Method method = getCallingMethod();
        return blockingCache.computeIfAbsent(method, m -> detectBlockingBehavior(m));
    }
    
    private static boolean detectBlockingBehavior(Method method) {
        // Run method in isolated environment for testing
        long startTime = System.nanoTime();
        
        try {
            // Attempt to execute method with timeout
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    Object instance = method.getDeclaringClass().getDeclaredConstructor().newInstance();
                    method.invoke(instance);
                    return false; // Didn't block
                } catch (Exception e) {
                    return true; // Possibly blocking
                }
            });
            
            return future.get(100, TimeUnit.MILLISECONDS); // Short timeout
        } catch (TimeoutException e) {
            return true; // Method did not finish on time - likely blocking
        } catch (Exception e) {
            return false; // Other errors - considered non-blocking
        } finally {
            long duration = System.nanoTime() - startTime;
            executionTimeStats.put(method, duration);
        }
    }
}
```



## 5. Configurable Heuristic

Configuration file blocking-operations.yaml

```yaml
blockingOperations:
  - className: "com.example.LegacyService"
    methodName: "synchronizedMethod"
    reason: "Uses synchronized block"
    timeoutMs: 5000
  
  - className: ".*DatabaseService"
    methodName: ".*query.*"
    reason: "Blocking database calls"
    timeoutMs: 10000
  
  - className: ".*FileService"
    methodName: ".*read.*|.*write.*"
    reason: "Blocking file IO"
    timeoutMs: 3000

nonBlockingOperations:
  - className: ".*AsyncService"
    methodName: ".*"
    reason: "All methods are non-blocking"
  
  - className: "com.example.ModernService"
    methodName: "processStream"
    reason: "Uses NIO"
```



Configuration loader

```java
public class ConfigurationBasedHeuristic {
    
    private final Map<String, BlockingConfig> blockingConfigs = new HashMap<>();
    private final Set<Pattern> nonBlockingPatterns = new HashSet<>();
    
    public void loadConfig(String configPath) {
        // Load YAML/JSON configuration
        // Parse and create patterns
    }
    
    public boolean isBlockingOperation(Class<?> clazz, String methodName) {
        String fullClassName = clazz.getName();
        
        // Check exact match
        String key = fullClassName + "#" + methodName;
        if (blockingConfigs.containsKey(key)) {
            return true;
        }
        
        // Check patterns
        for (Map.Entry<String, BlockingConfig> entry : blockingConfigs.entrySet()) {
            if (fullClassName.matches(entry.getKey()) && methodName.matches(entry.getValue().methodPattern)) {
                return true;
            }
        }
        
        // Check non-blocking patterns
        for (Pattern pattern : nonBlockingPatterns) {
            if (pattern.matcher(fullClassName + "#" + methodName).matches()) {
                return false;
            }
        }
        
        // Default heuristic
        return hasSuspiciousNaming(clazz, methodName);
    }
    
    private boolean hasSuspiciousNaming(Class<?> clazz, String methodName) {
        return clazz.getSimpleName().toLowerCase().contains("legacy") ||
               methodName.toLowerCase().contains("sync") ||
               methodName.toLowerCase().contains("lock") ||
               methodName.toLowerCase().contains("block");
    }
}
```



## 6. Comprehensive Heuristic

Combined detector

```java
public class ComprehensiveBlockingDetector {
    
    private final List<BlockingDetectionStrategy> strategies = Arrays.asList(
        new AnnotationBasedStrategy(),
        new ConfigurationBasedStrategy(),
        new BytecodeAnalysisStrategy(),
        new RuntimeProfilingStrategy(),
        new NamingPatternStrategy()
    );
    
    public BlockingDetectionResult detectBlockingBehavior(Method method) {
        int blockingScore = 0;
        List<String> reasons = new ArrayList<>();
        
        for (BlockingDetectionStrategy strategy : strategies) {
            DetectionResult result = strategy.analyze(method);
            blockingScore += result.getScore();
            if (result.isBlocking()) {
                reasons.add(result.getReason());
            }
        }
        
        return new BlockingDetectionResult(
            blockingScore >= 3, // Threshold value
            reasons,
            blockingScore
        );
    }
    
    public boolean shouldIsolate(Supplier<?> operation) {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            // Analyze call stack to find called method
            for (int i = 2; i < Math.min(stackTrace.length, 10); i++) {
                String className = stackTrace[i].getClassName();
                String methodName = stackTrace[i].getMethodName();
                
                Class<?> clazz = Class.forName(className);
                Method method = findMethod(clazz, methodName);
                
                if (method != null) {
                    BlockingDetectionResult result = detectBlockingBehavior(method);
                    if (result.isBlocking()) {
                        System.out.println("Blocking operation detected: " + 
                            className + "#" + methodName + " - " + result.getReasons());
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // In case of error, better to be safe
            return true;
        }
        
        return false;
    }
}
```



## 7. Practical Usage

Executor integration

```java
public class SmartVirtualThreadExecutor {
    
    private final ComprehensiveBlockingDetector detector = new ComprehensiveBlockingDetector();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService blockingThreadPool = Executors.newFixedThreadPool(20);
    
    public <T> CompletableFuture<T> executeSmart(Supplier<T> task) {
        if (detector.shouldIsolate(task)) {
            // Blocking operation - execute in separate pool
            return CompletableFuture.supplyAsync(task, blockingThreadPool);
        } else {
            // Non-blocking operation - execute in virtual thread
            return CompletableFuture.supplyAsync(task, virtualThreadExecutor);
        }
    }
    
    public void executeWithFallback(Runnable task) {
        if (detector.shouldIsolate(() -> { task.run(); return null; })) {
            blockingThreadPool.execute(() -> {
                System.out.println("Executing blocking operation in isolated pool");
                task.run();
            });
        } else {
            virtualThreadExecutor.execute(() -> {
                System.out.println("Executing non-blocking operation in virtual thread");
                task.run();
            });
        }
    }
}
```



### Virtual threads usage example

```java
public class Application {
    
    private static final SmartVirtualThreadExecutor executor = new SmartVirtualThreadExecutor();
    
    public static void main(String[] args) {
        LegacyService legacyService = new LegacyService();
        ModernService modernService = new ModernService();
        
        // Automatic detection of where to execute
        executor.executeSmart(legacyService::synchronizedMethod);
        executor.executeSmart(modernService::asyncMethod);
        
        // Manual specification with annotations
        executor.executeWithFallback(() -> {
            if (Thread.currentThread().isVirtual()) {
                System.out.println("Running in virtual thread");
            } else {
                System.out.println("Running in platform thread");
            }
        });
    }
}
```



These heuristics allow automatic detection of potentially blocking operations and make decisions about where to best execute them to avoid blocking  virtual thread carrier threads.
