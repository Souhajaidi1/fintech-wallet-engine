package com.souhajaidi.wallet.ledger.service;

import com.souhajaidi.wallet.common.error.LedgerImbalanceException;
import com.souhajaidi.wallet.common.money.Money;
import com.souhajaidi.wallet.ledger.domain.AccountType;
import com.souhajaidi.wallet.ledger.domain.EntryType;
import com.souhajaidi.wallet.ledger.domain.JournalEntry;
import com.souhajaidi.wallet.ledger.domain.LedgerAccount;
import com.souhajaidi.wallet.ledger.domain.LedgerPosting;
import com.souhajaidi.wallet.ledger.domain.PostingDirection;
import com.souhajaidi.wallet.ledger.repository.JournalEntryRepository;
import com.souhajaidi.wallet.ledger.repository.LedgerAccountRepository;
import com.souhajaidi.wallet.ledger.repository.LedgerPostingRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The double-entry engine.
 *
 * <p>Nothing outside this class writes a posting. Every movement of money goes through
 * {@link #post}, which refuses to persist an entry whose postings do not sum to zero.
 * That single check is what makes "money cannot be created or destroyed" a property of
 * the system rather than a property of each individual feature that happens to be
 * written carefully.
 *
 * <p>Deposits and withdrawals balance against a system clearing account rather than
 * being one-sided, so a deposit is not a hole in the books: it is money moving from
 * the outside world onto the platform's balance sheet.
 */
@Service
public class LedgerService {

    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository entries;
    private final LedgerPostingRepository postings;

    public LedgerService(
            LedgerAccountRepository accounts, JournalEntryRepository entries, LedgerPostingRepository postings) {
        this.accounts = accounts;
        this.entries = entries;
        this.postings = postings;
    }

    /** Creates the liability account that a new wallet's postings will land in. */
    @Transactional
    public LedgerAccount openWalletAccount(UUID walletId, String currency) {
        return accounts.save(LedgerAccount.forWallet(walletId, currency));
    }

    @Transactional(readOnly = true)
    public LedgerAccount requireWalletAccount(UUID walletId) {
        return accounts.findByWalletId(walletId)
                .orElseThrow(() -> new IllegalStateException("wallet " + walletId + " has no ledger account"));
    }

    /**
     * Returns the clearing account for a currency, creating it on first use.
     *
     * <p>Runs in its own transaction so that the lazily created account survives even
     * if the movement that triggered its creation is later rolled back; the alternative
     * is re-creating it on every retry and colliding with the unique index.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LedgerAccount requireSystemAccount(AccountType accountType, String currency) {
        return accounts.findByAccountTypeAndCurrency(accountType, currency)
                .orElseGet(() -> accounts.save(LedgerAccount.system(accountType, currency)));
    }

    /**
     * Writes a balanced journal entry.
     *
     * @param lines the movement's sides; at least two, summing to zero
     * @throws LedgerImbalanceException if the lines do not sum to zero
     */
    @Transactional
    public JournalEntry post(EntryType type, Money amount, UUID initiatedBy, String reference, List<Line> lines) {
        if (lines.size() < 2) {
            throw new IllegalArgumentException("a journal entry needs at least two postings");
        }

        long imbalance = 0L;
        for (Line line : lines) {
            if (!line.amount().currency().equals(amount.currency())) {
                throw new IllegalArgumentException("all postings in an entry must share the entry's currency");
            }
            imbalance += line.direction().applySign(line.amount().minorUnits());
        }
        if (imbalance != 0L) {
            throw new LedgerImbalanceException(imbalance);
        }

        JournalEntry entry = entries.save(JournalEntry.posted(type, amount, initiatedBy, reference));

        List<LedgerPosting> rows = new ArrayList<>(lines.size());
        for (Line line : lines) {
            rows.add(new LedgerPosting(entry.getId(), line.accountId(), line.direction(), line.amount()));
        }
        postings.saveAll(rows);

        return entry;
    }

    /**
     * Records a rejected movement.
     *
     * <p>Written in a fresh transaction because the caller's transaction is about to
     * roll back; without {@code REQUIRES_NEW} the record of the failure would roll
     * back with it and a refused transfer would leave no trace at all.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JournalEntry recordFailure(EntryType type, Money amount, UUID initiatedBy, String reference, String reason) {
        return entries.save(JournalEntry.failed(type, amount, initiatedBy, reference, reason));
    }

    /** The balance of a ledger account computed from its postings, ignoring any cache. */
    @Transactional(readOnly = true)
    public Money balanceFromPostings(UUID walletId) {
        LedgerAccount account = requireWalletAccount(walletId);
        return Money.ofMinor(postings.sumSignedMinorByAccountId(account.getId()), account.getCurrency());
    }

    /** One side of a journal entry, before it is persisted. */
    public record Line(UUID accountId, PostingDirection direction, Money amount) {

        public static Line debit(UUID accountId, Money amount) {
            return new Line(accountId, PostingDirection.DEBIT, amount);
        }

        public static Line credit(UUID accountId, Money amount) {
            return new Line(accountId, PostingDirection.CREDIT, amount);
        }
    }
}
