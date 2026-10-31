package com.souhajaidi.wallet.transaction.api;

import com.souhajaidi.wallet.iam.security.CurrentUser;
import com.souhajaidi.wallet.idempotency.service.IdempotencyService;
import com.souhajaidi.wallet.transaction.api.dto.TransactionResponse;
import com.souhajaidi.wallet.transaction.api.dto.TransferRequest;
import com.souhajaidi.wallet.transaction.service.MoneyMovementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Moving money between wallets")
public class TransferController {

    private final MoneyMovementService movements;
    private final IdempotencyService idempotency;
    private final CurrentUser currentUser;

    public TransferController(
            MoneyMovementService movements, IdempotencyService idempotency, CurrentUser currentUser) {
        this.movements = movements;
        this.idempotency = idempotency;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(
            summary = "Transfer money between two wallets",
            description =
                    """
                    The caller must own the source wallet. Both wallets must be ACTIVE and share a currency.

                    Send an `Idempotency-Key` header. If the response is lost and the request is
                    retried with the same key and body, the stored response is replayed and no
                    second transfer happens; the reply then carries `Idempotent-Replay: true`.
                    """)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer posted"),
        @ApiResponse(responseCode = "200", description = "Replay of an earlier identical request"),
        @ApiResponse(responseCode = "409", description = "The original request is still in flight"),
        @ApiResponse(responseCode = "422", description = "Insufficient funds, or the key was reused with a different body")
    })
    public ResponseEntity<TransactionResponse> transfer(
            @Parameter(description = "Client-generated key that makes this request safe to retry")
                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        UUID userId = currentUser.requireId();
        var outcome = idempotency.execute(
                idempotencyKey,
                userId,
                "POST /api/v1/transfers",
                request,
                TransactionResponse.class,
                () -> TransactionResponse.from(movements.transfer(userId, request)));

        return ResponseEntity.status(outcome.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replay", Boolean.toString(outcome.replayed()))
                .body(outcome.result());
    }
}
