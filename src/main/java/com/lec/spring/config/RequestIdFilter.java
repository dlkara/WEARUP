package com.lec.spring.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // - 외부에서 보낸 ID 대신 서버에서 요청 ID 생성
        String previous = MDC.get("requestId");
        String requestId = UUID.randomUUID().toString();
        // - 같은 요청의 로그를 묶고 응답 헤더에서도 확인할 수 있게 연결
        MDC.put("requestId", requestId);
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            // - 비밀번호·토큰 등이 섞일 수 있어 요청값과 예외 메시지 전문은 기록하지 않음
            log.error("event=REQUEST_FAILED requestId={} exceptionType={}", requestId, ex.getClass().getSimpleName());
            throw ex;
        } finally {
            // - 예외는 컨테이너 처리 전 상태가 찍힐 수 있어 status와 failed를 함께 확인
            log.info("event=REQUEST_COMPLETED requestId={} status={} failed={} durationMs={}", requestId,
                    response.getStatus(), failed, (System.nanoTime() - started) / 1_000_000);
            // - 다음 요청의 로그와 섞이지 않도록 요청 ID 정리
            if (previous == null) MDC.remove("requestId");
            else MDC.put("requestId", previous);
        }
    }
}
