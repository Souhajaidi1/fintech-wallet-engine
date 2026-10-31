package com.souhajaidi.wallet.wallet.api;

import com.souhajaidi.wallet.iam.security.CurrentUser;
import com.souhajaidi.wallet.idempotency.service.IdempotencyService;
import com.souhajaidi.wallet.transaction.api.dto.AmountRequest;
import com.souhajaidi.wallet.transaction.api.dto.TransactionResponse;
import com.souhajaidi.wallet.transaction.service.MoneyMovementService;
import com.souhajaidi.wallet.wallet.api.dto.BalanceResponse;
import com.souhajaidi.wallet.wallet.api.dto.CreateWalletRequest;
import com.souhajaidi.wallet.wallet.api.dto.WalletResponse;
import com.souhajaidi.wallet.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
@Tag(name = "Wallets", description = "Wallet lifecycle, balances, deposits and withdrawals")
public class WalletController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String REPLAY_HEADER = "Idempotent-Replay";

    private final WalletService walletService;
    private final MoneyMovementService movements;
    private final IdempotencyService idempotency;
    private final CurrentUser currentUser;

    public WalletController(
            WalletService walletService,
            MoneyMovementService movements,
            IdempotencyService idempotency,
            CurrentUser currentUser) {
        this.walletService = walletService;
        this.movements = movements;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Open a wallet in a given currency")
    public ResponseEntity<WalletResponse> create(@Valid @RequestBody CreateWalletRequest request) {
        WalletResponse body = WalletResponse.from(walletService.create(currentUser.requireId(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "List the wallets you own")
    public List<WalletResponse> list() {
        return walletService.listOwnedBy(currentUser.requireId()).stream()
                .map(WalletResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one wallet")
    public WalletResponse get(@PathVariable UUID id) {
        return WalletResponse.from(
                walletService.requireReadable(id, currentUser.requireId(), currentUser.isAdmin()));
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Read a wallet's balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return BalanceResponse.from(
                walletService.requireReadable(id, currentUser.requireId(), currentUser.isAdmin()));
    }

    @PostMapping("/{id}/deposit")
    @Operation(
            summary = "Credit a wallet from an external source",
            description = "Send an Idempotency-Key header so a retried request cannot deposit twice.")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id,
            @Parameter(description = "Client-generated key that makes this request safe to retry")
                    @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        UUID userId = currentUser.requireId();
        var outcome = idempotency.execute(
                idempotencyKey,
                userId,
                "POST /api/v1/wallets/{id}/deposit",
                new KeyedRequest(id, request),
                TransactionResponse.class,
                () -> TransactionResponse.from(movements.deposit(id, userId, request)));

        return respond(outcome);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(
            summary = "Debit a wallet to an external destination",
            description = "Send an Idempotency-Key header so a retried request cannot withdraw twice.")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody AmountRequest request) {

        UUID userId = currentUser.requireId();
        var outcome = idempotency.execute(
                idempotencyKey,
                userId,
                "POST /api/v1/wallets/{id}/withdraw",
                new KeyedRequest(id, request),
                TransactionResponse.class,
                () -> TransactionResponse.from(movements.withdraw(id, userId, request)));

        return respond(outcome);
    }

    private ResponseEntity<TransactionResponse> respond(IdempotencyService.Outcome<TransactionResponse> outcome) {
        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(REPLAY_HEADER, Boolean.toString(outcome.replayed()))
                .body(outcome.result());
    }

    /**
     * The path variable and body together, so the idempotency fingerprint covers the
     * whole request. Hashing the body alone would let the same key deposit into a
     * different wallet and be mistaken for a retry.
     */
    private record KeyedRequest(UUID walletId, AmountRequest body) {}
}
