package com.lec.spring.security;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public final class MutationLog {
    private MutationLog() { }

    public static void success(String action, String resource, Long id, Long actorId) {
        String requestId = MDC.get("requestId");
        Runnable record = () -> log.info(
                "event=MUTATION_SUCCEEDED requestId={} action={} resource={} resourceId={} actorId={}",
                requestId, action, resource, id, actorId);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { record.run(); }
            });
        } else {
            record.run();
        }
    }
}
