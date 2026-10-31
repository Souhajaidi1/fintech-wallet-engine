package com.souhajaidi.wallet.transaction.service;

import com.souhajaidi.wallet.audit.service.AuditService;
import com.souhajaidi.wallet.common.error.ForbiddenWalletAccessException;
import com.souhajaidi.wallet.common.error.InvalidTransferException;
import com.souhajaidi.wallet.common.error.WalletNotFoundException;
import com.souhajaidi.wallet.common.money.Money;
import com.souhajaidi.wallet.ledger.domain.AccountType;
import com.souhajaidi.wallet.ledger.domain.EntryType;
import com.souhajaidi.wallet.ledger.domain.JournalEntry;
import com.souhajaidi.wallet.ledger.domain.LedgerAccount;
import com.souhajaidi.wallet.ledger.service.LedgerService;
import com.souhajaidi.wallet.ledger.service.LedgerService.Line;
import com.souhajaidi.wallet.transaction.api.dto.AmountRequest;
import com.souhajaidi.wallet.transaction.api.dto.TransferRequest;
import com.souhajaidi.wallet.wallet.domain.Wallet;
import com.souhajaidi.wallet.wallet.repository.WalletRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three ways money moves: in, out, and between wallets.
 *
 * <p>Every method here follows the same shape, and the order of the steps is the
 * important part:
 *
 * <ol>
 *   <li>take a row lock on every wallet involved, in a deterministic order</li>
 *   <li>check the invariants against the locked state, not against an earlier read</li>
 *   <li>write the balanced journal entry</li>
 *   <li>update the cached balances</li>
 * </ol>
 *
 * <p>Steps 3 and 4 share the caller's transaction, so a failure at any point leaves both
 * the ledger and the balances untouched. There is no window in which a posting exists
 * without its matching balance change, or the reverse.
 *
 * <h2>The race this prevents</h2>
 *
 * <p>Suppose a wallet holds 100 and two withdrawals of 80 arrive at the same instant.
 * Without a lock, both transactions read a balance of 100, both find 80 &le; 100, and
 * both write. The wallet ends at -60 and the platform is owed money that does not exist.
 * This is not a rare interleaving: it is the normal outcome of a user double-tapping a
 * button, and it is invisible in testing until it happens with real money.
 *
 * <p>With {@code SELECT ... FOR UPDATE}, the second transaction blocks at step 1 until
 * the first commits, then reads 20 and correctly refuses. See ADR-004 for why a
 * pessimistic lock is preferred here over an optimistic version check.
 */
@Service
public class MoneyMovementService {

    private final WalletRepository wallets;
    private final LedgerService ledger;
    private final AuditService audit;

    public MoneyMovementService(WalletRepository wallets, LedgerService ledger, AuditService audit) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.audit = audit;
    }

    /**
     * Money arriving from outside the platform.
     *
     * <p>Balanced against a clearing account rather than posted one-sided, so the books
     * still sum to zero: the wallet's liability grows and the clearing account records
     * the matching claim on the outside world.
     */
    @Transactional
    public JournalEntry deposit(UUID walletId, UUID ownerId, AmountRequest request) {
        Wallet wallet = lockOwned(walletId, ownerId);
        wallet.requireActive();

        Money amount = Money.ofMajor(request.amount(), wallet.getCurrency());
        LedgerAccount walletAccount = ledger.requireWalletAccount(wallet.getId());
        LedgerAccount clearing = ledger.requireSystemAccount(AccountType.CASH_IN_CLEARING, wallet.getCurrency());

        JournalEntry entry = ledger.post(
                EntryType.DEPOSIT,
                amount,
                ownerId,
                request.reference(),
                List.of(Line.credit(walletAccount.getId(), amount), Line.debit(clearing.getId(), amount)));

        wallet.applySignedAmount(amount);
        audit.recordSuccess("DEPOSIT", ownerId, wallet.getId(), "entry=" + entry.getId());
        return entry;
    }

    /** Money leaving for the outside world. Refused if the locked balance is short. */
    @Transactional
    public JournalEntry withdraw(UUID walletId, UUID ownerId, AmountRequest request) {
        Wallet wallet = lockOwned(walletId, ownerId);
        wallet.requireActive();

        Money amount = Money.ofMajor(request.amount(), wallet.getCurrency());
        LedgerAccount walletAccount = ledger.requireWalletAccount(wallet.getId());
        LedgerAccount clearing = ledger.requireSystemAccount(AccountType.CASH_OUT_CLEARING, wallet.getCurrency());

        JournalEntry entry = ledger.post(
                EntryType.WITHDRAWAL,
                amount,
                ownerId,
                request.reference(),
                List.of(Line.debit(walletAccount.getId(), amount), Line.credit(clearing.getId(), amount)));

        // Throws InsufficientFundsException, rolling back the postings written above.
        wallet.applySignedAmount(amount.negated());
        audit.recordSuccess("WITHDRAWAL", ownerId, wallet.getId(), "entry=" + entry.getId());
        return entry;
    }

    /**
     * Money moving between two wallets on the platform.
     *
     * <p>Both wallets are locked in a single query ordered by id, so two transfers in
     * opposite directions between the same pair cannot deadlock by grabbing the rows in
     * different orders.
     */
    @Transactional
    public JournalEntry transfer(UUID ownerId, TransferRequest request) {
        if (request.sourceWalletId().equals(request.destinationWalletId())) {
            throw new InvalidTransferException("A transfer needs two different wallets.");
        }

        List<UUID> ids = List.of(request.sourceWalletId(), request.destinationWalletId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Map<UUID, Wallet> locked = wallets.lockAllById(ids).stream()
                .collect(Collectors.toMap(Wallet::getId, Function.identity()));

        Wallet source = required(locked, request.sourceWalletId());
        Wallet destination = required(locked, request.destinationWalletId());

        // Only the sender's ownership is checked. Anyone may be sent money; only the
        // owner may send it.
        if (!source.getOwnerId().equals(ownerId)) {
            throw new ForbiddenWalletAccessException();
        }
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new InvalidTransferException(
                    "Cannot transfer between a " + source.getCurrency() + " and a " + destination.getCurrency()
                            + " wallet. Currency conversion is not supported.");
        }
        source.requireActive();
        destination.requireActive();

        Money amount = Money.ofMajor(request.amount(), source.getCurrency());
        LedgerAccount from = ledger.requireWalletAccount(source.getId());
        LedgerAccount to = ledger.requireWalletAccount(destination.getId());

        JournalEntry entry = ledger.post(
                EntryType.TRANSFER,
                amount,
                ownerId,
                request.reference(),
                List.of(Line.debit(from.getId(), amount), Line.credit(to.getId(), amount)));

        source.applySignedAmount(amount.negated());
        destination.applySignedAmount(amount);

        audit.recordSuccess("TRANSFER", ownerId, source.getId(), "to=" + destination.getId() + " entry=" + entry.getId());
        return entry;
    }

    private Wallet lockOwned(UUID walletId, UUID ownerId) {
        Wallet wallet = wallets.lockById(walletId).orElseThrow(() -> new WalletNotFoundException(walletId));
        if (!wallet.getOwnerId().equals(ownerId)) {
            throw new ForbiddenWalletAccessException();
        }
        return wallet;
    }

    private Wallet required(Map<UUID, Wallet> locked, UUID walletId) {
        Wallet wallet = locked.get(walletId);
        if (wallet == null) {
            throw new WalletNotFoundException(walletId);
        }
        return wallet;
    }
}
