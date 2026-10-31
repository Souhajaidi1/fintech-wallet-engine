package com.souhajaidi.wallet.audit.repository;

import com.souhajaidi.wallet.audit.domain.AuditEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    Page<AuditEvent> findByActorIdOrderByOccurredAtDesc(UUID actorId, Pageable pageable);
}
