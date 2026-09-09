package com.endstep.ms.service.game;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resumo do que uma acao mudou. O {@link com.endstep.ms.service.GameEventPublisher}
 * expande isto em uma mensagem PATCH por destinatario (aplicando a privacidade da mao).
 *
 * @author Kauã Ferreira
 * @since 2026-09-08
 */
public final class EngineResult {
    private long sequence;
    private final Set<Long> changedCardIds = new LinkedHashSet<>();
    private final Set<Long> removedCardIds = new LinkedHashSet<>();
    private final Set<Long> changedPlayerUserIds = new LinkedHashSet<>();
    private boolean turnChanged;
    private String eventType;
    private String logLine;

    private Map<String, Object> actorNotice;

    public long sequence() {
        return sequence;
    }

    public EngineResult sequence(long s) {
        this.sequence = s;
        return this;
    }

    public Set<Long> changedCardIds() {
        return changedCardIds;
    }

    public Set<Long> changedPlayerUserIds() {
        return changedPlayerUserIds;
    }

    public EngineResult card(Long id) {
        if (id != null) {
            changedCardIds.add(id);
        }
        return this;
    }

    public Set<Long> removedCardIds() {
        return removedCardIds;
    }

    public EngineResult removed(Long id) {
        if (id != null) {
            removedCardIds.add(id);
            changedCardIds.remove(id);
        }
        return this;
    }

    public EngineResult player(Long userId) {
        if (userId != null) {
            changedPlayerUserIds.add(userId);
        }
        return this;
    }

    public boolean turnChanged() {
        return turnChanged;
    }

    public EngineResult turnChanged(boolean v) {
        this.turnChanged = v;
        return this;
    }

    public String eventType() {
        return eventType;
    }

    public EngineResult eventType(String v) {
        this.eventType = v;
        return this;
    }

    public String logLine() {
        return logLine;
    }

    public EngineResult logLine(String v) {
        this.logLine = v;
        return this;
    }

    public Map<String, Object> actorNotice() {
        return actorNotice;
    }

    public EngineResult actorNotice(Map<String, Object> v) {
        this.actorNotice = v;
        return this;
    }
}
