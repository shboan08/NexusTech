package com.vulnmall.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 취약점 스코어보드 서비스
 * 각 취약점이 트리거될 때 플래그를 생성하고 발견 상태를 추적합니다.
 */
@Service
public class ScoreboardService {

    public record VulnEntry(int id, String wstgId, String category, String name, String flag, boolean found, String foundAt) {}

    private static final Map<String, String[]> VULN_CATALOG = new LinkedHashMap<>();

    static {
        VULN_CATALOG.put("SQLI_LIKE",        new String[]{"WSTG-INPV-05", "Injection", "SQL Injection - LIKE 문자열 결합"});
        VULN_CATALOG.put("SQLI_UNION",       new String[]{"WSTG-INPV-05", "Injection", "SQL Injection - UNION-Based"});
        VULN_CATALOG.put("SQLI_ORDERBY",     new String[]{"WSTG-INPV-05", "Injection", "SQL Injection - ORDER BY Blind"});
        VULN_CATALOG.put("SQLI_BOOL_BLIND",  new String[]{"WSTG-INPV-05", "Injection", "SQL Injection - Boolean-Based Blind"});
        VULN_CATALOG.put("SQLI_TIME_BLIND",  new String[]{"WSTG-INPV-05", "Injection", "SQL Injection - Time-Based Blind"});
        VULN_CATALOG.put("SQLI_SECOND",      new String[]{"WSTG-INPV-05", "Injection", "Second-Order SQL Injection"});
        VULN_CATALOG.put("XSS_REFLECTED",    new String[]{"WSTG-INPV-01", "XSS", "Reflected XSS (HTML 응답)"});
        VULN_CATALOG.put("XSS_STORED_REVIEW",new String[]{"WSTG-INPV-02", "XSS", "Stored XSS (리뷰 댓글)"});
        VULN_CATALOG.put("XSS_STORED_INQ",   new String[]{"WSTG-INPV-02", "XSS", "Stored XSS (문의글)"});
        VULN_CATALOG.put("PATH_TRAVERSAL",   new String[]{"WSTG-INPV-09", "Injection", "Path Traversal (임의 파일 읽기)"});
        VULN_CATALOG.put("XXE",              new String[]{"WSTG-INPV-07", "Injection", "XML External Entity (XXE)"});
        VULN_CATALOG.put("SSTI",             new String[]{"WSTG-INPV-18", "Injection", "Server-Side Template Injection (SpEL)"});
        VULN_CATALOG.put("CMD_INJECTION",    new String[]{"WSTG-INPV-12", "Injection", "Command Injection (OS 명령 실행)"});
        VULN_CATALOG.put("SSRF",             new String[]{"WSTG-INPV-19", "Injection", "SSRF (서버 사이드 요청 위조)"});
        VULN_CATALOG.put("ZIP_SLIP",         new String[]{"WSTG-INPV-12", "Injection", "Zip Slip (경로 탈출)"});
        VULN_CATALOG.put("CRLF_LOG",         new String[]{"WSTG-INPV-15", "Injection", "CRLF / Log Injection"});
        VULN_CATALOG.put("AES_ECB",          new String[]{"WSTG-CRYP-02", "Crypto", "AES-ECB 모드 블록 셔플링"});
        VULN_CATALOG.put("PREDICTABLE_TOKEN",new String[]{"WSTG-CRYP-03", "Crypto", "취약한 비밀번호 재설정 토큰"});
        VULN_CATALOG.put("JWT_CONFUSION",    new String[]{"WSTG-ATHN-08", "Auth", "JWT Algorithm Confusion"});
        VULN_CATALOG.put("USER_ENUM",        new String[]{"WSTG-ATHN-02", "Auth", "계정 열거 (Username Enumeration)"});
        VULN_CATALOG.put("MASS_ASSIGN_PROFILE", new String[]{"WSTG-ATHN-08", "Auth", "Mass Assignment (프로필 권한 상승)"});
        VULN_CATALOG.put("MASS_ASSIGN_REG",  new String[]{"WSTG-ATHN-08", "Auth", "Mass Assignment (회원가입 권한 지정)"});
        VULN_CATALOG.put("BOLA_READ",        new String[]{"WSTG-ATHZ-04", "Auth", "BOLA/IDOR (타인 주문 조회)"});
        VULN_CATALOG.put("BOLA_WRITE",       new String[]{"WSTG-ATHZ-04", "Auth", "BOLA/IDOR (타인 배송지 변경)"});
        VULN_CATALOG.put("HPP",              new String[]{"WSTG-ATHZ-04", "Auth", "HTTP Parameter Pollution"});
        VULN_CATALOG.put("CSRF",             new String[]{"WSTG-SESS-05", "Client", "CSRF (이메일 강제 변경)"});
        VULN_CATALOG.put("XSS_DOM",          new String[]{"WSTG-CLNT-01", "Client", "DOM-Based XSS"});
        VULN_CATALOG.put("OPEN_REDIRECT",    new String[]{"WSTG-CLNT-04", "Client", "Open Redirect"});
        VULN_CATALOG.put("PRICE_TAMPER",     new String[]{"WSTG-BUSL-09", "BizLogic", "Price Tampering (가격 조작)"});
        VULN_CATALOG.put("NEG_QUANTITY",     new String[]{"WSTG-BUSL-09", "BizLogic", "Negative Quantity (음수 수량)"});
        VULN_CATALOG.put("ROUNDING_ERROR",   new String[]{"WSTG-BUSL-03", "BizLogic", "부동소수점 오차 포인트 차익거래"});
        VULN_CATALOG.put("RACE_CONDITION",   new String[]{"WSTG-BUSL-04", "BizLogic", "동시성 Race Condition"});
        VULN_CATALOG.put("WORKFLOW_SKIP",    new String[]{"WSTG-BUSL-02", "BizLogic", "Workflow Step Skipping"});
        VULN_CATALOG.put("WAF_BYPASS",       new String[]{"WSTG-CONF-05", "Config", "엔터프라이즈 WAF 우회"});
        VULN_CATALOG.put("INFO_EXPOSURE",    new String[]{"WSTG-INFO-05", "Config", "민감정보 노출 (.env/.git/backup.sql)"});
        VULN_CATALOG.put("FILE_UPLOAD",      new String[]{"WSTG-INPV-12", "Injection", "무제한 파일 업로드"});
        VULN_CATALOG.put("BOLA_ADDRESS",     new String[]{"WSTG-ATHZ-04", "Auth", "BOLA/IDOR (타인 배송지 조회/수정/삭제)"});
        VULN_CATALOG.put("XSS_STORED_ADDRESS",new String[]{"WSTG-INPV-02", "XSS", "Stored XSS (배송지 및 배송 메모)"});
        VULN_CATALOG.put("SQLI_ADDRESS",     new String[]{"WSTG-INPV-05", "Injection", "SQL Injection (주소 및 우편번호 검색)"});
        VULN_CATALOG.put("WALLET_NEGATIVE_CHARGE", new String[]{"WSTG-BUSL-09", "BizLogic", "결제 금액 음수 충전 및 PG 변조"});
        VULN_CATALOG.put("WALLET_RACE_CONDITION", new String[]{"WSTG-BUSL-04", "BizLogic", "바우처 동시성 Race Condition"});
        VULN_CATALOG.put("CSRF_WALLET",      new String[]{"WSTG-SESS-05", "Client", "CSRF (지갑 잔액 무단 송금)"});
        VULN_CATALOG.put("XSS_STORED_CART",  new String[]{"WSTG-INPV-02", "XSS", "Stored XSS (장바구니 요청 메모)"});
        VULN_CATALOG.put("BOLA_CART",        new String[]{"WSTG-ATHZ-04", "Auth", "BOLA/IDOR (타인 장바구니 품목 변조/삭제)"});
        VULN_CATALOG.put("ADMIN_BYPASS",     new String[]{"WSTG-ATHZ-02", "Auth", "통합 관리자 포털 권한 우회"});
        VULN_CATALOG.put("MEMBERSHIP_PRICE_TAMPER", new String[]{"WSTG-BUSL-09", "BizLogic", "Membership Price Tampering (가입 금액 변조)"});
        VULN_CATALOG.put("MEMBERSHIP_BFLA_BYPASS",  new String[]{"WSTG-ATHZ-02", "Auth", "VIP Exclusive Deals BFLA (인가 우회)"});
        VULN_CATALOG.put("MEMBERSHIP_COUPON_RACE",  new String[]{"WSTG-BUSL-04", "BizLogic", "VIP Coupon Race Condition (쿠폰 무한 중복 발급)"});
        VULN_CATALOG.put("MEMBERSHIP_STORED_XSS",   new String[]{"WSTG-INPV-02", "XSS", "Membership Welcome Note Stored XSS"});
        VULN_CATALOG.put("REFUND_RACE_CONDITION",   new String[]{"WSTG-BUSL-04", "BizLogic", "Double Refund Concurrency Race Condition (이중 환불)"});
        VULN_CATALOG.put("REFUND_WORKFLOW_BYPASS",  new String[]{"WSTG-BUSL-02", "BizLogic", "Refund State & Workflow Step Skipping (반품 검수 우회)"});
        VULN_CATALOG.put("REFUND_STORED_XSS",       new String[]{"WSTG-INPV-02", "XSS", "Refund Reason & Memo Stored XSS"});
        VULN_CATALOG.put("WISHLIST_BOLA_IDOR",      new String[]{"WSTG-ATHZ-04", "Auth", "Wishlist & Custom Deck BOLA/IDOR (비공개 덱 무단 열람)"});
        VULN_CATALOG.put("WISHLIST_STORED_XSS",     new String[]{"WSTG-INPV-02", "XSS", "Custom Deck Name & Description Stored XSS"});
        VULN_CATALOG.put("ATTENDANCE_DATE_TAMPER",  new String[]{"WSTG-BUSL-04", "BizLogic", "Attendance Check-in Date Manipulation & Multi-Claim Race"});
        VULN_CATALOG.put("ROULETTE_CLIENT_TAMPER",  new String[]{"WSTG-CLNT-01", "Client", "Roulette Client-Side Prize Manipulation"});
        VULN_CATALOG.put("POINTS_NEGATIVE_EXPLOIT", new String[]{"WSTG-BUSL-09", "BizLogic", "Negative Points Usage & Compound Payment Tampering"});
        VULN_CATALOG.put("INQUIRY_BOLA_IDOR",       new String[]{"WSTG-ATHZ-04", "Auth", "BOLA/IDOR on Support Ticket & Secret Inquiries"});
        VULN_CATALOG.put("TICKET_FILE_UPLOAD",      new String[]{"WSTG-INPV-12", "Injection", "Unrestricted File Upload on Support Tickets"});
        VULN_CATALOG.put("STOCK_RACE_CONDITION",    new String[]{"WSTG-BUSL-04", "BizLogic", "Inventory Overselling Concurrency Race Condition"});
        VULN_CATALOG.put("RESTOCK_WEBHOOK_SSRF",    new String[]{"WSTG-INPV-19", "Injection", "Restock Notification Callback SSRF"});
    }

    // key -> found timestamp (null = not found)
    private final ConcurrentHashMap<String, String> foundMap = new ConcurrentHashMap<>();
    private final com.vulnmall.websocket.ScoreboardWebSocketHandler webSocketHandler;

    public ScoreboardService(@org.springframework.context.annotation.Lazy com.vulnmall.websocket.ScoreboardWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * 플래그 생성: FLAG{KEY_랜덤해시}
     */
    private String generateFlag(String key) {
        String hash = Integer.toHexString((key + "NEXUSTECH_2026").hashCode());
        return "FLAG{" + key + "_" + hash + "}";
    }

    /**
     * 취약점을 발견 상태로 마킹하고 플래그를 반환합니다.
     * 웹소켓 클라이언트(스코어보드)에만 실시간으로 플래그 이벤트를 푸시합니다.
     */
    public String markFound(String vulnKey) {
        if (!VULN_CATALOG.containsKey(vulnKey)) return null;
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        foundMap.putIfAbsent(vulnKey, now);
        String flag = generateFlag(vulnKey);

        if (webSocketHandler != null) {
            String[] meta = VULN_CATALOG.get(vulnKey);
            int foundCount = foundMap.size();
            int total = VULN_CATALOG.size();
            long percent = Math.round((double) foundCount / total * 100);
            webSocketHandler.broadcastVulnFound(vulnKey, meta[0], meta[2], flag, foundCount, total, percent, foundMap.get(vulnKey));
        }

        return flag;
    }

    /**
     * 전체 스코어보드 조회
     */
    public Map<String, Object> getScoreboard() {
        List<VulnEntry> entries = new ArrayList<>();
        int idx = 1;
        int foundCount = 0;
        for (Map.Entry<String, String[]> e : VULN_CATALOG.entrySet()) {
            String key = e.getKey();
            String[] meta = e.getValue();
            boolean isFound = foundMap.containsKey(key);
            if (isFound) foundCount++;
            entries.add(new VulnEntry(
                idx++,
                meta[0],
                meta[1],
                meta[2],
                isFound ? generateFlag(key) : "???",
                isFound,
                isFound ? foundMap.get(key) : null
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalVulnerabilities", VULN_CATALOG.size());
        result.put("foundCount", foundCount);
        result.put("progressPercent", Math.round((double) foundCount / VULN_CATALOG.size() * 100));
        result.put("vulnerabilities", entries);
        return result;
    }

    /**
     * 전체 초기화
     */
    public void reset() {
        foundMap.clear();
        if (webSocketHandler != null) {
            webSocketHandler.broadcastReset();
        }
    }
}
