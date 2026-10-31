package com.souhajaidi.wallet.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An append-only record of something that happened.
 *
 * <p>Deliberately narrow: who acted, on what, whether it worked, and a short detail
 * string. It never carries amounts, credentials or request bodies — an audit table
 * that duplicates the ledger is a second source of truth to keep in sync, and one
 * that stores request bodies becomes a place where secrets accumulate.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "subject_id")
    private UUID subjectId;

    @Column(nullable = false)
    private String outcome;

    @Column
    private String detail;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(String eventType, UUID actorId, UUID subjectId, String outcome, String detail) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.actorId = actorId;
        this.subjectId = subjectId;
        this.outcome = outcome;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
