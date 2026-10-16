package com.lec.spring.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lec.spring.config.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RequestLoggingTest {
    @AfterEach void cleanup() {
        MDC.clear();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void requestIdIsServerGeneratedAndMdcIsRestoredAfterSuccessOrFailure() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-ID", "untrusted\r\nfake-event");
        request.addParameter("password", "do-not-log");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("requestId", "parent");
        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isNotEqualTo("parent");
            assertThatCode(() -> UUID.fromString(MDC.get("requestId"))).doesNotThrowAnyException();
            assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
        });
        assertThat(MDC.get("requestId")).isEqualTo("parent");
        MDC.clear();
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new IllegalStateException("do-not-log"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test void failureLogsContainCorrelationButNoInputOrExceptionMessage() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter("password", "secret-value");
            request.addHeader("Authorization", "Bearer secret-token");
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertThatThrownBy(() -> new RequestIdFilter().doFilter(request, response,
                    (req, res) -> { throw new IllegalStateException("secret-message\r\nfake-event"); }))
                    .isInstanceOf(IllegalStateException.class);
            String output = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + b);
            assertThat(output).contains(response.getHeader("X-Request-ID"), "REQUEST_FAILED")
                    .doesNotContain("secret-value", "secret-token", "secret-message", "fake-event");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test void mutationSuccessIsDeferredUntilCommit() {
        Logger logger = (Logger) LoggerFactory.getLogger(MutationLog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            MutationLog.success("DELETE", "POST", 10L, 1L);
            assertThat(appender.list).isEmpty();
            for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) {
                callback.afterCommit();
            }
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("MUTATION_SUCCEEDED", "resourceId=10");
        } finally {
            logger.detachAppender(appender);
        }
    }
}
