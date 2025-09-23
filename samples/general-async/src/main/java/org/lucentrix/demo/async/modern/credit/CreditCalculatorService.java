package org.lucentrix.demo.async.modern.credit;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.supplyAsync;

public class CreditCalculatorService {

    // Credit calculation models
    record Credit(double score) {
    }

    record Person(Long id, String name) {
    }

    record Asset(String type, double value) {
    }

    record Liability(String type, double amount) {
    }

    public Credit calculateCredit(Long personId) {
        var person = getPerson(personId);          // Database call - blocks thread
        var assets = getAssets(person);            // API call - blocks thread
        var liabilities = getLiabilities(person);  // Database call - blocks thread
        importantWork();
        // CPU-intensive work
        return calculateCredits(assets, liabilities);
    }

    private Person getPerson(Long personId) {
        System.out.println("getPerson started");
        simulateDelay(200);

        System.out.println("getPerson completed");
        return new Person(personId, "John Doe");
    }

    private List<Asset> getAssets(Person person) {
        System.out.println("getAssets started");

        simulateDelay(200);

        System.out.println("getAssets completed");
        return List.of(
                new Asset("House", 300000),
                new Asset("Car", 25000)
        );
    }

    private List<Liability> getLiabilities(Person person) {
        System.out.println("getLiabilities started");
        simulateDelay(200);

        System.out.println("calculateCredits completed");

        return List.of(
                new Liability("Mortgage", 200000),
                new Liability("Credit Card", 5000)
        );
    }

    private void importantWork() {
        System.out.println("Important work started");
        simulateDelay(200);
        System.out.println("Important work completed");
    }

    private void simulateDelay(long sleep) {
        try {
            Thread.sleep(sleep);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Credit calculateCredits(List<Asset> assets,
                                    List<Liability> liabilities) {
        System.out.println("calculateCredits started");
        simulateDelay(200);
        double totalAssets = assets.stream().mapToDouble(Asset::value).sum();
        double totalLiabilities = liabilities.stream()
                .mapToDouble(Liability::amount)
                .sum();
        double creditScore = (totalAssets - totalLiabilities) / 1000;

        System.out.println("calculateCredits completed");

        return new Credit(creditScore);
    }


    private void simulateDelay(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public Credit calculateCreditWithExecutor(Long personId) throws ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            var person = getPerson(personId);
            var assetsFuture = executor.submit(() -> getAssets(person));
            var liabilitiesFuture = executor.submit(() -> getLiabilities(person));
            executor.submit(this::importantWork);

            return calculateCredits(assetsFuture.get(), liabilitiesFuture.get());
        } finally {
            executor.shutdownNow();
        }
    }


    public Credit calculateCreditWithCompletableFuture2(Long personId) throws InterruptedException, ExecutionException {
        return CompletableFuture.runAsync(() -> importantWork())
                .thenCompose(aVoid -> supplyAsync(() -> getPerson(personId)))
                .thenCombineAsync(
                        supplyAsync(() -> getAssets(getPerson(personId))),
                        (person, assets) -> calculateCredits(assets, getLiabilities(person))
                )
                .get();
    }

    public Credit calculateCreditWithCompletableFuture(Long personId) throws InterruptedException, ExecutionException {
        // Execute all independent operations concurrently from the start
        CompletableFuture<Void> importantWorkFuture = CompletableFuture.runAsync(() -> importantWork());
        CompletableFuture<Person> personFuture = CompletableFuture.supplyAsync(() -> getPerson(personId));

        // Pre-compute assets and liabilities as soon as person is available
        CompletableFuture<List<Asset>> assetsFuture = personFuture.thenApplyAsync(person -> getAssets(person));
        CompletableFuture<List<Liability>> liabilitiesFuture = personFuture.thenApplyAsync(person -> getLiabilities(person));

        // Combine results efficiently
        CompletableFuture<Credit> creditFuture = assetsFuture.thenCombineAsync(liabilitiesFuture,
                (assets, liabilities) -> calculateCredits(assets, liabilities));

        // Return when all essential operations complete
        return importantWorkFuture
                .thenCompose(aVoid -> creditFuture)
                .get();
    }

    Credit calculateCreditWithUnboundedThreads(Long personId) throws InterruptedException {

        var person = getPerson(personId);

        var assetsRef = new AtomicReference<List<Asset>>();
        var t1 = new Thread(() -> {
            var assets = getAssets(person);
            assetsRef.set(assets);
        });

        var liabilitiesRef = new AtomicReference<List<Liability>>();
        Thread t2 = new Thread(() -> {
            var liabilities = getLiabilities(person);
            liabilitiesRef.set(liabilities);
        });

        var t3 = new Thread(this::importantWork);

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();

        var credit = calculateCredits(assetsRef.get(), liabilitiesRef.get());

        t3.join();

        return credit;
    }

    public Mono<Credit> calculateCreditReactive(Long personId) {
        Mono<Void> importantWorkMono = Mono.fromRunnable(() -> importantWork());
        Mono<Person> personMono = Mono.fromSupplier(() -> getPerson(personId));
        Mono<List<Asset>> assetsMono = personMono
                .map(person -> getAssets(person));
        Mono<List<Liability>> liabilitiesMono = personMono
                .map(person -> getLiabilities(person));

        return importantWorkMono.then(
                Mono.zip(assetsMono, liabilitiesMono)
                        .map(tuple -> {
                            List<Asset> assets = tuple.getT1();
                            List<Liability> liabilities = tuple.getT2();
                            return calculateCredits(assets, liabilities);
                        })
        );
    }

}
