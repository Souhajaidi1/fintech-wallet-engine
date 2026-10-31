package com.souhajaidi.wallet.idempotency.repository;

import com.souhajaidi.wallet.idempotency.domain.IdempotencyKey;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByKeyValueAndUserIdAndEndpoint(String keyValue, UUID userId, String endpoint);
}
