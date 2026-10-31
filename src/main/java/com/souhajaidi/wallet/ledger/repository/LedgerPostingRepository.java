package com.souhajaidi.wallet.ledger.repository;

import com.souhajaidi.wallet.ledger.domain.LedgerPosting;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerPostingRepository extends JpaRepository<LedgerPosting, UUID> {

    List<LedgerPosting> findByJournalEntryId(UUID journalEntryId);

    Page<LedgerPosting> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /**
     * The authoritative balance of a ledger account, computed from its postings.
     * Used by reconciliation to prove the cached wallet balance has not drifted.
     */
    @Query("SELECT COALESCE(SUM(p.signedMinor), 0) FROM LedgerPosting p WHERE p.accountId = :accountId")
    long sumSignedMinorByAccountId(@Param("accountId") UUID accountId);
}
