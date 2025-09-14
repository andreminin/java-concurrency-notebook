# Common Pitfalls to Avoid



## 1. Thread Pool Saturation

```java```

```java
// DON'T do this
@Async
public CompletableFuture<List<User>> findAll() {
    // This could overwhelm your thread pool with large datasets
    return CompletableFuture.supplyAsync(() -> repository.findAll());
}

// DO this instead
@Async
public CompletableFuture<List<User>> findAll() {
    return CompletableFuture.supplyAsync(() -> 
        repository.findAll(PageRequest.of(0, 100))
    );
}
```

**Problem**: If the `findAll()` method returns thousands of records, the async thread will be blocked  for an extended period, potentially exhausting all available threads in  the pool and causing performance degradation.

### Improved Solution with Pagination

The second example addresses this with pagination, but needs enhancement to return proper pagination metadata:

```java
// DO this instead - with enhanced pagination support
@Async
public CompletableFuture<PageWrapper<User>> findAfter(Optional<Integer> page) {
    int pageNumber = page.orElse(0);
    int pageSize = 100;
    
    return CompletableFuture.supplyAsync(() -> {
        Page<User> userPage = repository.findAll(PageRequest.of(pageNumber, pageSize));
        
        return new PageWrapper<>(
            userPage.getContent(),
            userPage.getNumber(),
            userPage.getTotalPages(),
            userPage.getTotalElements()
        );
    });
}

// Page wrapper class to hold pagination metadata
public class PageWrapper<T> {
    private List<T> content;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    
    // Constructor, getters, and setters
    public PageWrapper(List<T> content, int currentPage, int totalPages, long totalElements) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }
    
    // Getters omitted for brevity
}
```

Here's the complete implementation with a default page parameter:

```java
@Service
public class UserService {
    
    @Autowired
    private UserRepository repository;
    
    @Async
    public CompletableFuture<PageWrapper<User>> findAll() {
        return findAll(0); // Default to first page
    }
    
    @Async
    public CompletableFuture<PageWrapper<User>> findAll(int page) {
        int pageSize = 100;
        
        return CompletableFuture.supplyAsync(() -> {
            Page<User> userPage = repository.findAll(PageRequest.of(page, pageSize));
            
            return new PageWrapper<>(
                userPage.getContent(),
                userPage.getNumber(),
                userPage.getTotalPages(),
                userPage.getTotalElements()
            );
        });
    }
}

// Controller example
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @GetMapping
    public CompletableFuture<PageWrapper<User>> getUsers(
            @RequestParam(defaultValue = "0") int page) {
        return userService.findAll(page);
    }
}
```

### Key Benefits

1. **Thread Pool Protection**: Limits the amount of data processed by each async thread
2. **Pagination Metadata**: Provides clients with information about total pages and elements
3. **Flexible API**: Supports both default and explicit page requests
4. **Better Performance**: Reduces memory consumption and processing time per request



### Thread Pool Configuration Recommendation

Additionally, configure a dedicated thread pool for async operations:



```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(25);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

Then reference it in your async methods:

```java
@Async("taskExecutor")
public CompletableFuture<PageWrapper<User>> findAll(int page) {
    // implementation
}
```

### Implementation wit Virtual Threads ( Java 21)

Java 21's virtual threads offer a revolutionary approach to concurrency that can significantly simplify async programming. Let's explore how to use  them with Spring repositories. Virtual threads are lightweight threads managed by the JVM rather  than the OS. They allow you to write synchronous-looking code that runs  asynchronously without thread pool saturation concerns.

```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserService {
    
    private final UserRepository repository;
    // Use ReentrantLock instead of synchronized blocks with virtual threads
    private final ReentrantLock lock = new ReentrantLock();
    
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
    
    @Async
    public CompletableFuture<PageWrapper<User>> findAll() {
        return findAll(0); // Default to first page
    }
    
    @Async
    public CompletableFuture<PageWrapper<User>> findAll(int page) {
        // Virtual threads handle blocking operations efficiently
        return CompletableFuture.supplyAsync(() -> {
            Page<User> userPage = repository.findAll(PageRequest.of(page, 100));
            
            return new PageWrapper<>(
                userPage.getContent(),
                userPage.getNumber(),
                userPage.getTotalPages(),
                userPage.getTotalElements()
            );
        });
    }
    
    // Example method showing proper locking with virtual threads
    public void performThreadSafeOperation() {
        lock.lock(); // Use ReentrantLock instead of synchronized
        try {
            // Critical section - virtual threads won't block carrier threads here
            // ... perform operation ...
        } finally {
            lock.unlock();
        }
    }
}

// Page wrapper with pagination metadata
public class PageWrapper<T> {
    private List<T> content;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    
    public PageWrapper(List<T> content, int currentPage, int totalPages, long totalElements) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }
    
    // Getters and setters
}
```

### Configuration for Virtual Threads

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.support.TaskExecutorAdapter;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public TaskExecutorAdapter taskExecutor() {
        // Use virtual thread per task executor (Java 21+)
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

### Controller Example

```java
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private final UserService userService;
    
    public UserController(UserService userService) {
        this.userService = userService;
    }
    
    @GetMapping
    public CompletableFuture<PageWrapper<User>> getUsers(
            @RequestParam(defaultValue = "0") int page) {
        return userService.findAll(page);
    }
}
```

### Key Considerations with Virtual Threads

1. **No Thread Pool Saturation**: Virtual threads are lightweight (not OS threads), so you can have millions of them without significant resource consumption.
2. **Blocking Operations**: Virtual threads handle blocking I/O efficiently by automatically unmounting from carrier threads during blocking operations.
3. **Synchronized Blocks**:
   - **Problem**: `synchronized` blocks can pin virtual threads to carrier threads, reducing scalability
   - **Solution**: Replace with `ReentrantLock` or other java.util.concurrent locks
4. **Thread-Local Variables**: Be cautious with thread-locals as virtual threads can be much more numerous than platform threads.

### Benefits of Virtual Threads Approach

1. **Simplified Code**: Write synchronous-looking code that runs asynchronously
2. **Eliminates Pool Tuning**: No need to configure thread pool sizes
3. **Improved Scalability**: Handle orders of magnitude more concurrent requests
4. **Better Resource Utilization**: More efficient use of system resources

### When to Use Virtual Threads

- I/O-bound applications (database calls, HTTP requests)
- Applications with many concurrent connections
- Services that need to handle blocking operations efficiently

Virtual threads represent a paradigm shift in Java concurrency, making it much  easier to write highly scalable applications without complex thread pool management.



## 2. Resource Leaks

```java
// DON'T do this
@Async
public CompletableFuture<Stream<User>> streamUsers() {
    // Stream will remain open
    return CompletableFuture.supplyAsync(() -> 
        repository.streamAll());
}
```

**Key Issues:**

1. **Unclosed Resources**: The returned Stream maintains an open database connection
2. **Async Context**: The stream is created in one thread but consumed in another
3. **Timing Issues**: The consumer might not process the stream before the async method completes
4. **Connection Exhaustion**: Database connections remain open, potentially exhausting the connection pool



## The Improved Solution

The second example properly handles resource cleanup:

```java
// DO this instead
@Async
public CompletableFuture<List<User>> streamUsers() {
    return CompletableFuture.supplyAsync(() -> {
        try (Stream<User> stream = repository.streamAll()) {
            return stream.collect(Collectors.toList());
        }
    });
}
```

**Why This Works:**

1. **Try-with-Resources**: Ensures the stream is properly closed
2. **In-Memory Collection**: Converts stream to list before returning
3. **No Open Handles**: No database connections remain open after method completion

## Better: Virtual Threads with Pagination

For Java 21+, here's an improved approach using virtual threads and proper resource handling:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Service
public class UserService {
    
    private final UserRepository repository;
    
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
    
    @Async
    public CompletableFuture<UserPageResponse> streamUsers(int page, int size) {
        return CompletableFuture.supplyAsync(() -> {
            try (Stream<User> stream = repository.streamAll(PageRequest.of(page, size))) {
                List<User> users = stream.collect(Collectors.toList());
                
                // Get total count for pagination metadata (if needed)
                long totalCount = repository.count();
                int totalPages = (int) Math.ceil((double) totalCount / size);
                
                return new UserPageResponse(users, page, totalPages, totalCount);
            }
        });
    }
    
    // Alternative: Process data without loading everything into memory
    @Async
    public CompletableFuture<Void> processUsersInBatches(Consumer<User> processor, int batchSize) {
        return CompletableFuture.runAsync(() -> {
            int page = 0;
            boolean hasMore = true;
            
            while (hasMore) {
                try (Stream<User> stream = repository.streamAll(PageRequest.of(page, batchSize))) {
                    List<User> batch = stream.limit(batchSize).collect(Collectors.toList());
                    
                    if (batch.isEmpty()) {
                        hasMore = false;
                    } else {
                        batch.forEach(processor);
                        page++;
                    }
                }
            }
        });
    }
}

// Response DTO with pagination info
public class UserPageResponse {
    private List<User> content;
    private int currentPage;
    private int totalPages;
    private long totalElements;
    
    // Constructor, getters, setters
    public UserPageResponse(List<User> content, int currentPage, int totalPages, long totalElements) {
        this.content = content;
        this.currentPage = currentPage;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
    }
}
```

## Configuration for Virtual Threads

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.support.TaskExecutorAdapter;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean
    public TaskExecutorAdapter taskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}
```



# Enhanced Enterprise Repository with Monitoring and Resilience

```java
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class EnterpriseUserRepository {
    private final EntityManager entityManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final MonitoredThreadPoolTaskExecutor taskExecutor;
    
    // Track active async operations for monitoring
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final ConcurrentMap<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    
    @Async("taskExecutor")
    public CompletableFuture<List<User>> findUsersByCustomCriteria(UserSearchCriteria criteria) {
        String operationId = UUID.randomUUID().toString();
        operationStartTimes.put(operationId, System.currentTimeMillis());
        activeOperations.incrementAndGet();
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userRepository");
                
                return circuitBreaker.executeSupplier(() -> {
                    List<User> users = executeSearch(criteria);
                    sample.stop(meterRegistry.timer("user.search.time", "criteria", criteria.toString()));
                    return users;
                });
            } catch (Exception e) {
                meterRegistry.counter("user.search.errors").increment();
                log.error("Search failed for criteria: {}", criteria, e);
                throw new AsyncQueryExecutionException("Failed to execute search", e);
            } finally {
                operationStartTimes.remove(operationId);
                activeOperations.decrementAndGet();
            }
        }, taskExecutor);
    }
    
    // Combine multiple async operations with resilience
    public CompletableFuture<UserProfile> getUserProfileAsync(Long userId) {
        CompletableFuture<User> userFuture = findById(userId);
        CompletableFuture<List<Order>> ordersFuture = findUserOrders(userId);
        
        return userFuture.thenCombineAsync(ordersFuture, (user, orders) -> 
            new UserProfile(user, orders)
        ).exceptionally(ex -> {
            log.error("Error creating user profile for user ID: {}", userId, ex);
            meterRegistry.counter("user.profile.errors").increment();
            return UserProfile.empty(userId);
        }, taskExecutor);
    }
    
    // Paginated search with monitoring
    @Async("taskExecutor")
    public CompletableFuture<Page<User>> findUsersByCriteriaPaged(UserSearchCriteria criteria, int page, int size) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userRepository");
                
                return circuitBreaker.executeSupplier(() -> {
                    Page<User> result = executePagedSearch(criteria, page, size);
                    sample.stop(meterRegistry.timer("user.paged.search.time", 
                            "criteria", criteria.toString(), "page", String.valueOf(page)));
                    return result;
                });
            } catch (Exception e) {
                meterRegistry.counter("user.paged.search.errors").increment();
                log.error("Paged search failed for criteria: {}", criteria, e);
                throw new AsyncQueryExecutionException("Failed to execute paged search", e);
            }
        }, taskExecutor);
    }
    
    // Scheduled method to monitor async operations
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorAsyncOperations() {
        int active = activeOperations.get();
        long now = System.currentTimeMillis();
        
        // Log long-running operations
        operationStartTimes.forEach((id, startTime) -> {
            long duration = now - startTime;
            if (duration > 30000) { // 30 seconds threshold
                log.warn("Long-running async operation detected: ID={}, duration={}ms", id, duration);
            }
        });
        
        // Record metrics
        meterRegistry.gauge("async.operations.active", active);
        log.info("Current async operations: {}", active);
    }
    
    // Helper method to execute the search
    private List<User> executeSearch(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getName() != null) {
            predicates.add(cb.like(root.get("name"), "%" + criteria.getName() + "%"));
        }
        
        if (criteria.getEmail() != null) {
            predicates.add(cb.equal(root.get("email"), criteria.getEmail()));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.ge(root.get("age"), criteria.getMinAge()));
        }
        
        if (criteria.getMaxAge() != null) {
            predicates.add(cb.le(root.get("age"), criteria.getMaxAge()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Helper method for paged search
    private Page<User> executePagedSearch(UserSearchCriteria criteria, int page, int size) {
        // Implementation would use Spring Data's Pagination
        // This is a simplified example
        List<User> allResults = executeSearch(criteria);
        int total = allResults.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<User> pageContent = allResults.subList(start, end);
        
        return new PageImpl<>(pageContent, PageRequest.of(page, size), total);
    }
    
    // Simulated methods
    private CompletableFuture<User> findById(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            // Implementation would fetch user from database
            return new User(userId, "User " + userId, "user" + userId + "@example.com", 30);
        }, taskExecutor);
    }
    
    private CompletableFuture<List<Order>> findUserOrders(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            // Implementation would fetch orders from database
            return Arrays.asList(
                new Order(1000L + userId, "ORDER-" + userId + "-1"),
                new Order(2000L + userId, "ORDER-" + userId + "-2")
            );
        }, taskExecutor);
    }
}

// Custom exception for async query failures
class AsyncQueryExecutionException extends RuntimeException {
    public AsyncQueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Supporting classes
class UserSearchCriteria {
    private String name;
    private String email;
    private Integer minAge;
    private Integer maxAge;
    
    // Getters, setters, and toString
}

class UserProfile {
    private User user;
    private List<Order> orders;
    
    public UserProfile(User user, List<Order> orders) {
        this.user = user;
        this.orders = orders;
    }
    
    public static UserProfile empty(Long userId) {
        return new UserProfile(new User(userId, "Unknown", "unknown", 0), Collections.emptyList());
    }
}

class User {
    private Long id;
    private String name;
    private String email;
    private int age;
    
    public User(Long id, String name, String email, int age) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
    }
}

class Order {
    private Long id;
    private String orderNumber;
    
    public Order(Long id, String orderNumber) {
        this.id = id;
        this.orderNumber = orderNumber;
    }
}
```

## Enhanced Monitored Thread Pool Executor

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class MonitoredThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> taskExecutionTimes;
    
    public MonitoredThreadPoolTaskExecutor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.taskExecutionTimes = new ConcurrentHashMap<>();
        
        // Initialize with reasonable defaults
        setCorePoolSize(10);
        setMaxPoolSize(25);
        setQueueCapacity(100);
        setThreadNamePrefix("monitored-async-");
        initialize();
    }
    
    @Override
    public void execute(Runnable task) {
        String taskId = UUID.randomUUID().toString();
        taskExecutionTimes.put(taskId, System.nanoTime());
        
        super.execute(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                task.run();
            } finally {
                sample.stop(meterRegistry.timer("async.task.execution.time"));
                recordMetrics(taskId);
            }
        });
    }
    
    private void recordMetrics(String taskId) {
        Long startTime = taskExecutionTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("async.task.duration").record(duration, TimeUnit.NANOSECONDS);
            
            // Record thread pool metrics
            meterRegistry.gauge("async.pool.active.threads", getActiveCount());
            meterRegistry.gauge("async.pool.queue.size", getThreadPoolExecutor().getQueue().size());
            meterRegistry.gauge("async.pool.pool.size", getPoolSize());
        }
    }
    
    // Scheduled method to monitor thread pool health
    @Scheduled(fixedRate = 15000) // Every 15 seconds
    public void monitorThreadPool() {
        log.info("Thread Pool Stats - Active: {}, Queue: {}, Pool: {}", 
                getActiveCount(), 
                getThreadPoolExecutor().getQueue().size(), 
                getPoolSize());
        
        // Alert if queue is getting too large
        if (getThreadPoolExecutor().getQueue().size() > 80) {
            log.warn("Thread pool queue is filling up: {} tasks queued", 
                    getThreadPoolExecutor().getQueue().size());
        }
    }
}
```

## Key Features of This Implementation

1. **Comprehensive Monitoring**:
   - Tracks execution times for all async operations
   - Monitors active operations count
   - Detects long-running operations
   - Tracks thread pool metrics
2. **Resilience Patterns**:
   - Circuit breaker protection for database operations
   - Proper error handling with fallbacks
   - Combined async operations with graceful error handling
3. **Resource Management**:
   - Proper use of thread pool executor
   - Monitoring of queue sizes to prevent saturation
   - Tracking of active operations to identify bottlenecks
4. **Scheduled Monitoring**:
   - Regular health checks of async operations
   - Thread pool monitoring with alerts for potential issues
   - Metrics collection for performance analysis
5. **Enterprise Readiness**:
   - Pagination support for large datasets
   - Structured error handling
   - Comprehensive logging
   - Integration with Micrometer for observability

This implementation provides a robust foundation for enterprise-grade async  repository operations with proper monitoring, resilience, and resource  management



## Best Practices for Async Stream Handling

1. **Always Close Resources**: Use try-with-resources for streams, database connections, etc.
2. **Process In-Batch**: For large datasets, process in batches rather than loading everything into memory
3. **Use Pagination**: When possible, use paginated queries instead of streaming
4. **Consider Virtual Threads**: With Java 21+, virtual threads make async programming simpler and safer
5. **Monitor Resource Usage**: Implement monitoring to detect potential leaks early

## Common Pitfalls to Avoid

1. **Returning Open Streams**: Never return streams from async methods
2. **Ignoring Context**: Be aware of transaction boundaries when working with streams
3. **Memory Overload**: Avoid loading extremely large datasets into memory
4. **Connection Leaks**: Ensure all database connections are properly closed

By following these practices, you can avoid resource leaks while  efficiently handling large datasets in async Spring applications.



# Enterprise Repository with Virtual Threads and Enhanced Monitoring

```java
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class EnterpriseUserRepository {
    private final EntityManager entityManager;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    private final VirtualThreadTaskExecutor taskExecutor;
    
    // Use ReentrantLock instead of synchronized blocks for virtual threads
    private final ReentrantLock dataLock = new ReentrantLock();
    
    // Track active async operations for monitoring
    private final AtomicInteger activeOperations = new AtomicInteger(0);
    private final ConcurrentMap<String, Long> operationStartTimes = new ConcurrentHashMap<>();
    
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<List<User>> findUsersByCustomCriteria(UserSearchCriteria criteria) {
        String operationId = UUID.randomUUID().toString();
        operationStartTimes.put(operationId, System.currentTimeMillis());
        activeOperations.incrementAndGet();
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userRepository");
                
                return circuitBreaker.executeSupplier(() -> {
                    List<User> users = executeSearch(criteria);
                    sample.stop(meterRegistry.timer("user.search.time", "criteria", criteria.toString()));
                    return users;
                });
            } catch (Exception e) {
                meterRegistry.counter("user.search.errors").increment();
                log.error("Search failed for criteria: {}", criteria, e);
                throw new AsyncQueryExecutionException("Failed to execute search", e);
            } finally {
                operationStartTimes.remove(operationId);
                activeOperations.decrementAndGet();
            }
        }, taskExecutor);
    }
    
    // Combine multiple async operations with resilience
    public CompletableFuture<UserProfile> getUserProfileAsync(Long userId) {
        CompletableFuture<User> userFuture = findById(userId);
        CompletableFuture<List<Order>> ordersFuture = findUserOrders(userId);
        
        return userFuture.thenCombineAsync(ordersFuture, (user, orders) -> 
            new UserProfile(user, orders)
        ).exceptionally(ex -> {
            log.error("Error creating user profile for user ID: {}", userId, ex);
            meterRegistry.counter("user.profile.errors").increment();
            return UserProfile.empty(userId);
        }, taskExecutor);
    }
    
    // Thread-safe operation using ReentrantLock for virtual threads
    public void performThreadSafeOperation() {
        dataLock.lock(); // Use ReentrantLock instead of synchronized
        try {
            // Critical section - virtual threads won't block carrier threads here
            Timer.Sample sample = Timer.start(meterRegistry);
            // ... perform operation ...
            sample.stop(meterRegistry.timer("thread.safe.operation.time"));
        } finally {
            dataLock.unlock();
        }
    }
    
    // Paginated search with monitoring using virtual threads
    @Async("virtualThreadTaskExecutor")
    public CompletableFuture<Page<User>> findUsersByCriteriaPaged(UserSearchCriteria criteria, int page, int size) {
        String operationId = UUID.randomUUID().toString();
        operationStartTimes.put(operationId, System.currentTimeMillis());
        activeOperations.incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userRepository");
                
                return circuitBreaker.executeSupplier(() -> {
                    Timer.Sample sample = Timer.start(meterRegistry);
                    Page<User> result = executePagedSearch(criteria, page, size);
                    sample.stop(meterRegistry.timer("user.paged.search.time", 
                            "criteria", criteria.toString(), "page", String.valueOf(page)));
                    return result;
                });
            } catch (Exception e) {
                meterRegistry.counter("user.paged.search.errors").increment();
                log.error("Paged search failed for criteria: {}", criteria, e);
                throw new AsyncQueryExecutionException("Failed to execute paged search", e);
            } finally {
                operationStartTimes.remove(operationId);
                activeOperations.decrementAndGet();
            }
        }, taskExecutor);
    }
    
    // Scheduled method to monitor async operations
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void monitorAsyncOperations() {
        int active = activeOperations.get();
        long now = System.currentTimeMillis();
        
        // Log long-running operations
        operationStartTimes.forEach((id, startTime) -> {
            long duration = now - startTime;
            if (duration > 30000) { // 30 seconds threshold
                log.warn("Long-running async operation detected: ID={}, duration={}ms", id, duration);
            }
        });
        
        // Record metrics
        meterRegistry.gauge("async.operations.active", active);
        log.info("Current async operations: {}", active);
    }
    
    // Helper method to execute the search
    private List<User> executeSearch(UserSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getName() != null) {
            predicates.add(cb.like(root.get("name"), "%" + criteria.getName() + "%"));
        }
        
        if (criteria.getEmail() != null) {
            predicates.add(cb.equal(root.get("email"), criteria.getEmail()));
        }
        
        if (criteria.getMinAge() != null) {
            predicates.add(cb.ge(root.get("age"), criteria.getMinAge()));
        }
        
        if (criteria.getMaxAge() != null) {
            predicates.add(cb.le(root.get("age"), criteria.getMaxAge()));
        }
        
        query.where(predicates.toArray(new Predicate[0]));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    // Helper method for paged search
    private Page<User> executePagedSearch(UserSearchCriteria criteria, int page, int size) {
        // Implementation would use Spring Data's Pagination
        // This is a simplified example
        List<User> allResults = executeSearch(criteria);
        int total = allResults.size();
        int start = page * size;
        int end = Math.min(start + size, total);
        
        List<User> pageContent = allResults.subList(start, end);
        
        return new PageImpl<>(pageContent, PageRequest.of(page, size), total);
    }
    
    // Simulated methods
    private CompletableFuture<User> findById(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            // Implementation would fetch user from database
            return new User(userId, "User " + userId, "user" + userId + "@example.com", 30);
        }, taskExecutor);
    }
    
    private CompletableFuture<List<Order>> findUserOrders(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            // Implementation would fetch orders from database
            return Arrays.asList(
                new Order(1000L + userId, "ORDER-" + userId + "-1"),
                new Order(2000L + userId, "ORDER-" + userId + "-2")
            );
        }, taskExecutor);
    }
}

// Virtual Thread Task Executor with Monitoring
@Component
@Slf4j
class VirtualThreadTaskExecutor implements TaskExecutor {
    private final MeterRegistry meterRegistry;
    private final ExecutorService virtualThreadExecutor;
    private final ConcurrentHashMap<String, Long> taskExecutionTimes = new ConcurrentHashMap<>();
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    
    public VirtualThreadTaskExecutor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Register metrics
        meterRegistry.gauge("virtual.threads.active.tasks", activeTasks);
    }
    
    @Override
    public void execute(Runnable task) {
        String taskId = UUID.randomUUID().toString();
        taskExecutionTimes.put(taskId, System.nanoTime());
        activeTasks.incrementAndGet();
        
        virtualThreadExecutor.execute(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                task.run();
            } finally {
                sample.stop(meterRegistry.timer("virtual.thread.task.time"));
                recordMetrics(taskId);
                activeTasks.decrementAndGet();
            }
        });
    }
    
    private void recordMetrics(String taskId) {
        Long startTime = taskExecutionTimes.remove(taskId);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            meterRegistry.timer("virtual.thread.task.duration").record(duration, TimeUnit.NANOSECONDS);
        }
    }
    
    // Scheduled method to monitor virtual thread executor
    @Scheduled(fixedRate = 15000) // Every 15 seconds
    public void monitorVirtualThreads() {
        log.info("Virtual Thread Stats - Active Tasks: {}", activeTasks.get());
        
        // Record additional metrics
        meterRegistry.gauge("virtual.threads.executor.queue.size", 
            ThreadPoolExecutor.class.cast(virtualThreadExecutor).getQueue().size());
    }
    
    @PreDestroy
    public void shutdown() {
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

// Configuration for Virtual Threads
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
    
    @Bean(name = "virtualThreadTaskExecutor")
    public VirtualThreadTaskExecutor virtualThreadTaskExecutor(MeterRegistry meterRegistry) {
        return new VirtualThreadTaskExecutor(meterRegistry);
    }
}

// Custom exception for async query failures
class AsyncQueryExecutionException extends RuntimeException {
    public AsyncQueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Supporting classes
class UserSearchCriteria {
    private String name;
    private String email;
    private Integer minAge;
    private Integer maxAge;
    
    // Getters, setters, and toString
    @Override
    public String toString() {
        return "UserSearchCriteria{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", minAge=" + minAge +
                ", maxAge=" + maxAge +
                '}';
    }
}

class UserProfile {
    private User user;
    private List<Order> orders;
    
    public UserProfile(User user, List<Order> orders) {
        this.user = user;
        this.orders = orders;
    }
    
    public static UserProfile empty(Long userId) {
        return new UserProfile(new User(userId, "Unknown", "unknown", 0), Collections.emptyList());
    }
}

class User {
    private Long id;
    private String name;
    private String email;
    private int age;
    
    public User(Long id, String name, String email, int age) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
    }
}

class Order {
    private Long id;
    private String orderNumber;
    
    public Order(Long id, String orderNumber) {
        this.id = id;
        this.orderNumber = orderNumber;
    }
}
```

## Key Enhancements for Virtual Threads

1. **Virtual Thread Executor**:
   - Uses `Executors.newVirtualThreadPerTaskExecutor()` for optimal scalability
   - Properly implements `TaskExecutor` interface for Spring integration
   - Includes cleanup with `@PreDestroy` for graceful shutdown
2. **Locking Strategy**:
   - Replaced `synchronized` blocks with `ReentrantLock` to prevent pinning virtual threads
   - Ensures virtual threads can yield during lock contention
3. **Enhanced Monitoring**:
   - Tracks active virtual thread tasks with gauges
   - Measures virtual thread task execution times
   - Monitors executor queue size (though virtual threads have different characteristics)
4. **Resource Management**:
   - Proper error handling and resource cleanup
   - Circuit breaker integration for resilience
   - Comprehensive metrics collection
5. **Scheduled Monitoring**:
   - Regular health checks of virtual thread usage
   - Logging of virtual thread statistics
   - Detection of potential issues

## Benefits of Virtual Threads Implementation

1. **Massive Scalability**: Virtual threads allow handling millions of concurrent operations with minimal resource overhead
2. **Simplified Code**: Eliminates complex thread pool tuning and configuration
3. **Better Resource Utilization**: More efficient use of system resources compared to platform threads
4. **Improved Resilience**: Virtual threads handle blocking operations more efficiently
5. **Enhanced Observability**: Comprehensive monitoring of virtual thread usage and performance

## Considerations for Virtual Threads

1. **Thread-Local Variables**: Be cautious with thread-locals as virtual threads can be much more numerous
2. **Synchronized Blocks**: Avoid synchronized blocks as they can pin virtual threads to carrier threads
3. **Native Code**: Operations that call native code may still block carrier threads
4. **Monitoring Overhead**: Consider the impact of monitoring on performance with high volumes of virtual threads

This implementation provides a robust foundation for enterprise applications leveraging Java 21 virtual threads with Spring, offering excellent  scalability while maintaining comprehensive monitoring and resilience  patterns.



