package com.souhajaidi.wallet.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.souhajaidi.wallet.common.error.LedgerImbalanceException;
import com.souhajaidi.wallet.common.money.Money;
import com.souhajaidi.wallet.ledger.domain.EntryType;
import com.souhajaidi.wallet.ledger.domain.JournalEntry;
import com.souhajaidi.wallet.ledger.repository.JournalEntryRepository;
import com.souhajaidi.wallet.ledger.repository.LedgerAccountRepository;
import com.souhajaidi.wallet.ledger.repository.LedgerPostingRepository;
import com.souhajaidi.wallet.ledger.service.LedgerService;
import com.souhajaidi.wallet.ledger.service.LedgerService.Line;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerBalanceTest {

    @Mock
    private LedgerAccountRepository accounts;

    @Mock
    private JournalEntryRepository entries;

    @Mock
    private LedgerPostingRepository postings;

    @InjectMocks
    private LedgerService ledger;

    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    @Test
    @DisplayName("a balanced entry is written")
    void writesBalancedEntry() {
        Money amount = Money.ofMinor(2500L, "USD");
        when(entries.save(any(JournalEntry.class))).thenAnswer(call -> call.getArgument(0));

        JournalEntry entry = ledger.post(
                EntryType.TRANSFER,
                amount,
                actor,
                "rent",
                List.of(Line.debit(accountA, amount), Line.credit(accountB, amount)));

        assertThat(entry.getAmountMinor()).isEqualTo(2500L);
        verify(postings).saveAll(anyList());
    }

    @Test
    @DisplayName("an unbalanced entry is refused before anything is written")
    void refusesUnbalancedEntry() {
        Money debit = Money.ofMinor(2500L, "USD");
        Money credit = Money.ofMinor(2400L, "USD");

        assertThatThrownBy(() -> ledger.post(
                        EntryType.TRANSFER,
                        debit,
                        actor,
                        "skimmed",
                        List.of(Line.debit(accountA, debit), Line.credit(accountB, credit))))
                .isInstanceOf(LedgerImbalanceException.class);

        verify(entries, never()).save(any());
        verify(postings, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("a one-sided entry is refused")
    void refusesSingleSidedEntry() {
        Money amount = Money.ofMinor(100L, "USD");

        assertThatThrownBy(() ->
                        ledger.post(EntryType.DEPOSIT, amount, actor, null, List.of(Line.credit(accountA, amount))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least two postings");
    }

    @Test
    @DisplayName("postings in a foreign currency are refused")
    void refusesMixedCurrencyEntry() {
        Money usd = Money.ofMinor(100L, "USD");
        Money eur = Money.ofMinor(100L, "EUR");

        assertThatThrownBy(() -> ledger.post(
                        EntryType.TRANSFER, usd, actor, null, List.of(Line.debit(accountA, usd), Line.credit(accountB, eur))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
