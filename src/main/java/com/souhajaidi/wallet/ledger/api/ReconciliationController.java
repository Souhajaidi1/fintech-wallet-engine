package com.souhajaidi.wallet.ledger.api;

import com.souhajaidi.wallet.iam.security.CurrentUser;
import com.souhajaidi.wallet.ledger.service.LedgerService;
import com.souhajaidi.wallet.transaction.api.dto.ReconciliationResponse;
import com.souhajaidi.wallet.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin view over the ledger.
 *
 * <p>A cached balance is only trustworthy if something checks it. This endpoint
 * recomputes a wallet's balance from its postings and reports any drift, which is what
 * turns "the projection is kept in sync" from a claim into something observable.
 */
@RestController
@RequestMapping("/api/v1/admin/ledger")
@Tag(name = "Ledger (admin)", description = "Reconciliation between cached balances and the ledger")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationController {

    private final LedgerService ledger;
    private final WalletService wallets;
    private final CurrentUser currentUser;

    public ReconciliationController(LedgerService ledger, WalletService wallets, CurrentUser currentUser) {
        this.ledger = ledger;
        this.wallets = wallets;
        this.currentUser = currentUser;
    }

    @GetMapping("/wallets/{id}/reconciliation")
    @Operation(summary = "Recompute a wallet's balance from its ledger postings and report any drift")
    public ReconciliationResponse reconcile(@PathVariable UUID id) {
        long cached = wallets.requireReadable(id, currentUser.requireId(), true).getBalanceMinor();
        long fromLedger = ledger.balanceFromPostings(id).minorUnits();
        return ReconciliationResponse.of(id, cached, fromLedger);
    }
}
