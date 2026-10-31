package com.souhajaidi.wallet.wallet.repository;

import com.souhajaidi.wallet.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    List<Wallet> findByOwnerIdOrderByCreatedAtAsc(UUID ownerId);

    /**
     * Loads wallets for update, taking a row lock on each (SELECT ... FOR UPDATE).
     *
     * <p>Callers must pass ids in a deterministic order. PostgreSQL locks the rows in
     * the order the scan returns them, so two transfers touching the same pair of
     * wallets in opposite directions would otherwise be able to deadlock: one holds A
     * and waits for B while the other holds B and waits for A. Sorting the ids first
     * means every transaction takes the locks in the same sequence, which makes that
     * cycle impossible. See ADR-004.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids ORDER BY w.id")
    List<Wallet> lockAllById(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> lockById(@Param("id") UUID id);
}
