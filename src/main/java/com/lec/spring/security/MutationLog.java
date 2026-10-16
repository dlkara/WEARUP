package com.lec.spring.security;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
public final class MutationLog {
    private MutationLog() { }

    public static void success(String action, String resource, Long id, Long actorId) {
        // - 커밋 후에도 원래 요청을 찾을 수 있도록 요청 ID 보관
        String requestId = MDC.get("requestId");
        Runnable record = () -> log.info(
                "event=MUTATION_SUCCEEDED requestId={} action={} resource={} resourceId={} actorId={}",
                requestId, action, resource, id, actorId);
        // - DB 트랜잭션이 있으면 커밋이 끝난 뒤 성공 기록
        // - 파일 작업까지 함께 롤백되는 것은 아님
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { record.run(); }
            });
        } else {
            // - 트랜잭션이 없으면 호출 시점에 기록
            record.run();
        }
    }
}
