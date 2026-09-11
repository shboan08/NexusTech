package com.vulnmall.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * [고난도 실전형 WAF / 인가 인터셉터 결함]
 * 겉보기엔 엄격한 경로 기반 접근 제어와 IP 화이트리스트를 수행하는 것처럼 보이지만:
 * 1. URL 정규화 불일치: getRequestURI()를 정규화 없이 단순 검사하여
 *    '/api/admin/..;/users' 나 세미콜론 매트릭스 변수('/api/admin;internal/...')로 우회 가능
 * 2. 헤더 신뢰 결함: X-Forwarded-For 또는 X-Custom-IP-Authorization 헤더가 있으면 내부망 접근으로 신뢰
 */
@Component
public class EnterpriseWafFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // 관리자 API 경로 방어 로직
        if (uri.startsWith("/api/admin/")) {
            // 결함 1: 신뢰할 수 없는 클라이언트 프록시 헤더 확인
            String xff = request.getHeader("X-Forwarded-For");
            String customAuthIp = request.getHeader("X-Custom-IP-Authorization");
            boolean isInternalIp = "127.0.0.1".equals(xff) || "localhost".equals(xff) || "127.0.0.1".equals(customAuthIp);

            if (isInternalIp) {
                // 내부망 헤더 존재 시 WAF 통과
                filterChain.doFilter(request, response);
                return;
            }

            // 결함 2: URI 정규화 미흡
            // uri.contains("..") 같은 단순 검사는 세미콜론 매트릭스(;)나 URL 인코딩 트릭으로 우회됨
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isAdmin) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"WAF Block: Admin privilege or internal IP required.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
