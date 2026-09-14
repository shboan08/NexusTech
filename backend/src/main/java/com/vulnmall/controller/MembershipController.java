package com.vulnmall.controller;

import com.vulnmall.dto.MembershipDto;
import com.vulnmall.entity.Product;
import com.vulnmall.entity.User;
import com.vulnmall.repository.CouponRepository;
import com.vulnmall.repository.ProductRepository;
import com.vulnmall.repository.UserRepository;
import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/membership")
public class MembershipController {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final ScoreboardService scoreboardService;

    // Concurrency tracking for VIP coupon issuance
    private final AtomicInteger couponIssueCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Boolean> issuedVipUsers = new ConcurrentHashMap<>();

    public MembershipController(UserRepository userRepository, ProductRepository productRepository,
                                CouponRepository couponRepository, ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.couponRepository = couponRepository;
        this.scoreboardService = scoreboardService;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    /**
     * 내 멤버십 구독 상태 조회
     */
    @GetMapping("/status")
    public ResponseEntity<?> getMembershipStatus() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        boolean isActive = Boolean.TRUE.equals(user.getMembershipActive());
        String tier = user.getMembershipTier() != null ? user.getMembershipTier() : "NONE";

        List<String> benefits = isActive ? List.of(
                "전 상품 무료 배송 (배송비 ₩3,000 전액 면제)",
                "월간 VIP 전용 ₩50,000 시크릿 바우처 발급",
                "NEXUS PRIME 전용 시크릿 특가관 (최대 60% 단독 할인)",
                "VIP 전용 24/7 우선 기술 지원 및 핫라인"
        ) : List.of(
                "일반 회원 상태 (배송비 건당 ₩3,000 부과)",
                "멤버십 가입 시 즉시 무료배송 + ₩50,000 쿠폰 제공"
        );

        MembershipDto.StatusResponse response = new MembershipDto.StatusResponse(
                user.getUsername(),
                tier,
                isActive,
                user.getMembershipWelcomeNote(),
                user.getMembershipExpiresAt(),
                benefits
        );

        return ResponseEntity.ok(response);
    }

    /**
     * [WSTG-BUSL-09: Membership Price Tampering]
     * [WSTG-INPV-02: Stored XSS in Welcome Note]
     * 멤버십 가입/구독 신청 API
     * 1) Price Tampering: 정상 월 구독료(₩9,900) 대조 없이 클라이언트 전송 금액 그대로 신뢰
     *    (0원 무료 가입 또는 음수 금액 전송 시 사용자 잔액 부당 증액)
     * 2) Stored XSS: 가입 환영 메시지(welcomeNote) 필터링 부재
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribeMembership(@RequestBody MembershipDto.SubscribeRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        BigDecimal expectedFee = BigDecimal.valueOf(9900);
        BigDecimal chargedFee = (request.getPrice() != null) ? request.getPrice() : expectedFee;

        // 1. 가격 변조 취약점 감지
        if (request.getPrice() != null && (request.getPrice().compareTo(expectedFee) != 0 || request.getPrice().compareTo(BigDecimal.ZERO) <= 0)) {
            scoreboardService.markFound("MEMBERSHIP_PRICE_TAMPER");
        }

        // 2. 환영 메시지 Stored XSS 취약점 감지
        String welcomeNote = request.getWelcomeNote() != null ? request.getWelcomeNote() : "NEXUS PRIME VIP 회원 환영합니다!";
        String noteLower = welcomeNote.toLowerCase();
        if (noteLower.contains("<script") || noteLower.contains("<img") || noteLower.contains("onerror") || noteLower.contains("javascript:")) {
            scoreboardService.markFound("MEMBERSHIP_STORED_XSS");
        }

        // 잔액 확인 및 차감 (클라이언트가 제시한 chargedFee 그대로 신뢰)
        if (user.getBalance().compareTo(chargedFee) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "잔액이 부족합니다. 현재 잔액: ₩" + user.getBalance()));
        }

        user.setBalance(user.getBalance().subtract(chargedFee));
        userRepository.updateUser(user);

        // 30일 후 만료일 설정
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, 30);
        Timestamp expiresAt = new Timestamp(cal.getTimeInMillis());

        String targetTier = (request.getTier() != null && !request.getTier().trim().isEmpty()) ? request.getTier().toUpperCase() : "PRIME";
        userRepository.updateMembership(user.getId(), targetTier, true, welcomeNote, expiresAt);

        return ResponseEntity.ok(Map.of(
                "message", "NEXUS PRIME 멤버십 가입이 완료되었습니다!",
                "tier", targetTier,
                "active", true,
                "chargedFee", chargedFee,
                "remainingBalance", user.getBalance(),
                "expiresAt", expiresAt
        ));
    }

    /**
     * 멤버십 구독 해지
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMembership() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        userRepository.updateMembership(user.getId(), "NONE", false, null, null);
        return ResponseEntity.ok(Map.of("message", "NEXUS PRIME 멤버십 구독이 해지되었습니다."));
    }

    /**
     * [WSTG-ATHZ-02: Broken Function Level Authorization (BFLA)]
     * NEXUS PRIME 전용 시크릿 특가관 상품 목록
     * - 정상: 멤버십 active = true 회원만 접근 가능
     * - 취약점: 비회원이 헤더 'X-Membership-Override: VIP' 전달 또는 파라미터 bypass=true 전달 시,
     *          혹은 인증 없이 직접 호출 시 권한 검증 누락으로 VIP 전용 품목 노출
     */
    @GetMapping("/exclusive-products")
    public ResponseEntity<?> getExclusiveProducts(
            @RequestHeader(value = "X-Membership-Override", required = false) String overrideHeader,
            @RequestParam(value = "bypass", required = false) String bypassParam,
            @RequestParam(value = "tier", required = false) String tierParam) {

        User user = getCurrentUser();
        boolean isVip = user != null && Boolean.TRUE.equals(user.getMembershipActive());

        // BFLA 인가 우회 시도 감지
        boolean bypassAttempt = (overrideHeader != null && overrideHeader.equalsIgnoreCase("VIP"))
                || "true".equalsIgnoreCase(bypassParam)
                || "VIP".equalsIgnoreCase(tierParam)
                || (!isVip);

        if (bypassAttempt) {
            scoreboardService.markFound("MEMBERSHIP_BFLA_BYPASS");
        }

        // 취약한 구현: 우회 헤더나 파라미터가 있거나 일반 요청이어도 검증 우회 후 VIP 목록 반환
        List<Product> products = productRepository.findExclusiveProducts();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "vipAccessGranted", true,
                "totalCount", products.size(),
                "products", products
        ));
    }

    /**
     * [WSTG-BUSL-04: Concurrency Race Condition / TOCTOU]
     * 월간 VIP 전용 ₩50,000 바우처 쿠폰 발급 API
     * 1인 1회 발급 제한이 있으나, 동시 다중 요청 시 동시성 제어(트랜잭션 락) 누락으로
     * 동일 회원에게 복수 쿠폰이 대량 발급되는 결함
     */
    @PostMapping("/coupons/issue")
    public ResponseEntity<?> issueVipCoupon(@RequestBody(required = false) MembershipDto.IssueCouponRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        if (!Boolean.TRUE.equals(user.getMembershipActive())) {
            return ResponseEntity.status(403).body(Map.of("message", "NEXUS PRIME 멤버십 회원만 VIP 쿠폰을 발급받을 수 있습니다."));
        }

        String username = user.getUsername();
        int activeCount = couponIssueCounter.incrementAndGet();

        // 1. 검증 (Check) - 이미 발급받았는지 검사
        if (issuedVipUsers.containsKey(username)) {
            couponIssueCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "이미 이번 달 VIP 바우처를 발급받으셨습니다. (1인 1회 제한)"));
        }

        // 인위적인 레이스 윈도우 (TOCTOU 유발 지연)
        try {
            Thread.sleep(70);
        } catch (InterruptedException ignored) {}

        // 동시 요청 또는 연속 발급 시 Race Condition 감지
        if (activeCount > 1 || issuedVipUsers.containsKey(username)) {
            scoreboardService.markFound("MEMBERSHIP_COUPON_RACE");
        }

        // 2. 실행 (Act) - 새 고유 VIP 쿠폰 생성 및 지급
        String code = "PRIME-VIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        couponRepository.createCoupon(code, BigDecimal.valueOf(50000));
        issuedVipUsers.put(username, true);
        couponIssueCounter.decrementAndGet();

        return ResponseEntity.ok(Map.of(
                "message", "NEXUS PRIME VIP ₩50,000 할인 바우처가 발급되었습니다!",
                "couponCode", code,
                "discountAmount", 50000,
                "note", "장바구니 결제창에서 쿠폰 코드를 입력하여 즉시 사용하세요."
        ));
    }
}
