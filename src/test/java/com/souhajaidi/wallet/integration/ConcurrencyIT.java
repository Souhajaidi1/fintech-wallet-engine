package com.souhajaidi.wallet.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.souhajaidi.wallet.transaction.api.dto.TransferRequest;
import com.souhajaidi.wallet.transaction.service.MoneyMovementService;
import com.souhajaidi.wallet.wallet.repository.WalletRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The tests that justify the locking strategy.
 *
 * <p>They call the service directly rather than going through MockMvc, because the race
 * being tested lives between the balance read and the balance write inside one
 * transaction. Firing many threads at it at once is the only way to demonstrate that the
 * row lock, and not luck, is what keeps the balance correct.
 */
class ConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private MoneyMovementService movements;

    @Autowired
    private WalletRepository wallets;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    @Test
    @DisplayName("twenty concurrent transfers of 10 from a wallet holding 100 let exactly ten through")
    void concurrentTransfersCannotOverdraw() throws Exception {
        String sender = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String recipient = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID from = api.createWallet(sender, "USD");
        UUID to = api.createWallet(recipient, "USD");
        api.deposit(sender, from, "100.00", UUID.randomUUID().toString());

        UUID senderId = wallets.findById(from).orElseThrow().getOwnerId();

        int attempts = 20;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(attempts)) {
            List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Void>>mapToObj(i -> () -> {
                        startGun.await();
                        try {
                            movements.transfer(
                                    senderId, new TransferRequest(from, to, new BigDecimal("10.00"), "race-" + i));
                            succeeded.incrementAndGet();
                        } catch (RuntimeException expected) {
                            refused.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            startGun.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        // The wallet held 100.00 and each transfer moves 10.00, so at most ten can win.
        // Without SELECT ... FOR UPDATE, more than ten would succeed and the balance
        // would end up negative.
        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(attempts - 10);

        assertThat(wallets.findById(from).orElseThrow().getBalanceMinor()).isZero();
        assertThat(wallets.findById(to).orElseThrow().getBalanceMinor()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("transfers in opposite directions between the same pair do not deadlock")
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        String alice = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");
        String bob = api.registerAndLogin(uniqueEmail(), "correct-horse-battery");

        UUID aliceWallet = api.createWallet(alice, "USD");
        UUID bobWallet = api.createWallet(bob, "USD");
        api.deposit(alice, aliceWallet, "500.00", UUID.randomUUID().toString());
        api.deposit(bob, bobWallet, "500.00", UUID.randomUUID().toString());

        UUID aliceId = wallets.findById(aliceWallet).orElseThrow().getOwnerId();
        UUID bobId = wallets.findById(bobWallet).orElseThrow().getOwnerId();

        int rounds = 25;
        CountDownLatch startGun = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Void>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < rounds; i++) {
                int round = i;
                tasks.add(() -> {
                    startGun.await();
                    movements.transfer(
                            aliceId, new TransferRequest(aliceWallet, bobWallet, new BigDecimal("1.00"), "a" + round));
                    return null;
                });
                tasks.add(() -> {
                    startGun.await();
                    movements.transfer(
                            bobId, new TransferRequest(bobWallet, aliceWallet, new BigDecimal("1.00"), "b" + round));
                    return null;
                });
            }

            List<Future<Void>> futures = tasks.stream().map(pool::submit).toList();
            startGun.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

            // A deadlock would surface here as an ExecutionException on some future.
            for (Future<Void> future : futures) {
                future.get();
            }
        }

        // Every peso sent one way was matched by one sent back, so both end where they started.
        assertThat(wallets.findById(aliceWallet).orElseThrow().getBalanceMinor()).isEqualTo(50_000L);
        assertThat(wallets.findById(bobWallet).orElseThrow().getBalanceMinor()).isEqualTo(50_000L);
    }
}
