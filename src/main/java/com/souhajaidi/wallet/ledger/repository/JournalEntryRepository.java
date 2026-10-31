package com.souhajaidi.wallet.ledger.repository;

import com.souhajaidi.wallet.ledger.domain.JournalEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    Page<JournalEntry> findByInitiatedByOrderByCreatedAtDesc(UUID initiatedBy, Pageable pageable);
}
