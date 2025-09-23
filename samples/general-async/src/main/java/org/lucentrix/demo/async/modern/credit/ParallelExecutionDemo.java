package org.lucentrix.demo.async.modern.credit;

import reactor.core.publisher.Mono;

public class ParallelExecutionDemo {

    public static void main(String[] args) throws Exception {
        CreditCalculatorService service = new CreditCalculatorService();

        System.out.println("=== Sequential Execution ===");
        ExecutionTimer.measure(() -> service.calculateCredit(1L));

        System.out.println("\n=== Parallel Execution ===");
        ExecutionTimer.measure(() -> {
            try {
                return service.calculateCreditWithUnboundedThreads(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        System.out.println("\n=== Parallel Execution With Executors ===");
        ExecutionTimer.measure(() -> {
            try {
                return service.calculateCreditWithExecutor(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        System.out.println("\n=== Parallel Execution With Completable Future ===");
        ExecutionTimer.measure(() -> {
            try {
                return service.calculateCreditWithCompletableFuture(1L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        System.out.println("\n=== Parallel Execution With Reactive ===");
        ExecutionTimer.measure(() -> {
            Mono<CreditCalculatorService.Credit> mono = service.calculateCreditReactive(1L);
            return mono.block();
        });
    }
}
