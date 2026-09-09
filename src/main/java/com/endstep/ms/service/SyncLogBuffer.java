package com.endstep.ms.service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Buffer em memoria com as ultimas linhas de log da sincronizacao de cartas,
 * capturadas direto do Logback dos loggers do sync. Serve para a tela de
 * sincronizacao mostrar exatamente o que sai no console do backend.
 *
 * @author Kauã Ferreira
 * @since 2026-09-09
 */
@Component
public class SyncLogBuffer {

    private static final int MAX_LINES = 600;
    private static final List<String> LOGGERS = List.of(
            "com.endstep.ms.service.CardSyncService",
            "com.endstep.ms.service.CardSyncBootstrap");

    public record Line(long seq, Instant at, String level, String message) {
    }

    private final Deque<Line> lines = new ArrayDeque<>(MAX_LINES);
    private final AtomicLong seq = new AtomicLong();
    private final Object lock = new Object();

    @PostConstruct
    void attach() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                add(event.getLevel().toString(), event.getFormattedMessage());
                IThrowableProxy tp = event.getThrowableProxy();
                if (tp != null) {
                    add("ERROR", tp.getClassName()
                            + (tp.getMessage() == null ? "" : ": " + tp.getMessage()));
                }
            }
        };
        appender.setContext(ctx);
        appender.start();
        for (String name : LOGGERS) {
            ctx.getLogger(name).addAppender(appender);
        }
    }

    public void add(String level, String message) {
        synchronized (lock) {
            lines.addLast(new Line(seq.incrementAndGet(), Instant.now(), level, message));
            while (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }
    }

    public List<Line> since(long afterSeq) {
        synchronized (lock) {
            return lines.stream().filter(l -> l.seq() > afterSeq).toList();
        }
    }

    public long lastSeq() {
        return seq.get();
    }
}
