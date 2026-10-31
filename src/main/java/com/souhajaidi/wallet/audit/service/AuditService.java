package com.souhajaidi.wallet.audit.service;

import com.souhajaidi.wallet.audit.domain.AuditEvent;
import com.souhajaidi.wallet.audit.repository.AuditEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    /**
     * Records a successful action inside the caller's transaction, so the audit row and
     * the change it describes commit or roll back together. An audit trail that can
     * claim something happened when it did not is worse than none.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSuccess(String eventType, UUID actorId, UUID subjectId, String detail) {
        events.save(new AuditEvent(eventType, actorId, subjectId, "SUCCESS", detail));
    }

    /**
     * Records a rejected action in its own transaction. The caller's transaction is
     * usually about to roll back, and the whole point of recording a failure is that it
     * must survive that rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String eventType, UUID actorId, UUID subjectId, String detail) {
        events.save(new AuditEvent(eventType, actorId, subjectId, "FAILURE", detail));
    }
}
