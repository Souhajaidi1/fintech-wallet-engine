package com.souhajaidi.wallet.transaction.api;

import com.souhajaidi.wallet.iam.security.CurrentUser;
import com.souhajaidi.wallet.ledger.repository.JournalEntryRepository;
import com.souhajaidi.wallet.transaction.api.dto.PageResponse;
import com.souhajaidi.wallet.transaction.api.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Transaction history")
@Validated
public class TransactionController {

    private final JournalEntryRepository entries;
    private final CurrentUser currentUser;

    public TransactionController(JournalEntryRepository entries, CurrentUser currentUser) {
        this.entries = entries;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(
            summary = "List your transactions, newest first",
            description = "Page size is capped at 100 so a single request cannot ask the database for the whole history.")
    public PageResponse<TransactionResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return PageResponse.of(
                entries.findByInitiatedByOrderByCreatedAtDesc(currentUser.requireId(), PageRequest.of(page, size)),
                TransactionResponse::from);
    }
}
