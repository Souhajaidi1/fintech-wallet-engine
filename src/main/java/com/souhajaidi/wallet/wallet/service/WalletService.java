package com.souhajaidi.wallet.wallet.service;

import com.souhajaidi.wallet.audit.service.AuditService;
import com.souhajaidi.wallet.common.error.ForbiddenWalletAccessException;
import com.souhajaidi.wallet.common.error.WalletNotFoundException;
import com.souhajaidi.wallet.ledger.service.LedgerService;
import com.souhajaidi.wallet.wallet.api.dto.CreateWalletRequest;
import com.souhajaidi.wallet.wallet.domain.Wallet;
import com.souhajaidi.wallet.wallet.repository.WalletRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {

    private final WalletRepository wallets;
    private final LedgerService ledger;
    private final AuditService audit;

    public WalletService(WalletRepository wallets, LedgerService ledger, AuditService audit) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.audit = audit;
    }

    /** Creates a wallet together with the ledger account its postings will land in. */
    @Transactional
    public Wallet create(UUID ownerId, CreateWalletRequest request) {
        Wallet wallet = new Wallet(ownerId, request.label().trim(), request.currency());
        wallets.save(wallet);
        ledger.openWalletAccount(wallet.getId(), wallet.getCurrency());
        audit.recordSuccess("WALLET_CREATED", ownerId, wallet.getId(), "currency=" + wallet.getCurrency());
        return wallet;
    }

    @Transactional(readOnly = true)
    public List<Wallet> listOwnedBy(UUID ownerId) {
        return wallets.findByOwnerIdOrderByCreatedAtAsc(ownerId);
    }

    /**
     * Loads a wallet the caller is entitled to see.
     *
     * <p>Ownership is checked here rather than in the controller so that no endpoint can
     * reach a wallet without passing the check. A wallet owned by someone else is
     * reported as missing, not as forbidden, so responses cannot be used to discover
     * which wallet ids exist.
     */
    @Transactional(readOnly = true)
    public Wallet requireReadable(UUID walletId, UUID requesterId, boolean requesterIsAdmin) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
        if (!requesterIsAdmin && !wallet.getOwnerId().equals(requesterId)) {
            throw new ForbiddenWalletAccessException();
        }
        return wallet;
    }

    /** Loads a wallet the caller is entitled to move money out of. Ownership only: admins do not spend other people's money. */
    @Transactional(readOnly = true)
    public Wallet requireOwned(UUID walletId, UUID ownerId) {
        Wallet wallet = wallets.findById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
        if (!wallet.getOwnerId().equals(ownerId)) {
            throw new ForbiddenWalletAccessException();
        }
        return wallet;
    }
}
