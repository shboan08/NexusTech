package com.vulnmall.controller;

import com.vulnmall.entity.User;
import com.vulnmall.repository.CouponRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;
    private final java.util.concurrent.atomic.AtomicInteger redeemCounter = new java.util.concurrent.atomic.AtomicInteger(0);

    public CouponController(CouponRepository couponRepository, UserRepository userRepository,
                            com.vulnmall.service.ScoreboardService scoreboardService) {
        this.couponRepository = couponRepository;
        this.userRepository = userRepository;
        this.scoreboardService = scoreboardService;
    }

    /**
     * [WSTG-INPV-05: Boolean-Based Blind SQL Injection]
     * 쿠폰 유효성 사전 조회 API
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyCoupon(@RequestParam("code") String code) {
        if (code != null && (code.contains("'") || code.toUpperCase().contains("1=1") || code.toUpperCase().contains("1=2") || code.toUpperCase().contains(" OR ") || code.toUpperCase().contains(" AND "))) {
            scoreboardService.markFound("SQLI_BOOL_BLIND");
        }

        boolean isValid = couponRepository.verifyCouponCode(code);
        return ResponseEntity.ok(Map.of(
                "valid", isValid,
                "code", code,
                "message", isValid ? "사용 가능한 유효한 할인 쿠폰입니다." : "존재하지 않거나 이미 사용된 쿠폰입니다."
        ));
    }

    /**
     * [WSTG-BUSL-04: Concurrency Race Condition]
     * 쿠폰 등록 및 포인트 충전:
     * DB 트랜잭션 락(Lock) 부재로 인해 동시 다중 요청 시 동일 쿠폰 중복 소모 가능 (TOCTOU 결함)
     */
    @PostMapping("/redeem")
    public ResponseEntity<?> redeemCoupon(@RequestBody Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        String username = auth.getName();
        String code = body.get("code");
        if (code == null) return ResponseEntity.badRequest().body(Map.of("message", "쿠폰 코드를 입력하세요."));

        int currentCount = redeemCounter.incrementAndGet();

        // 1. 검증 (Check)
        if (couponRepository.isCouponUsed(code)) {
            redeemCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "이미 사용된 쿠폰입니다."));
        }

        // 레이스 윈도우 유발 지연
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {}

        // 동시 요청 감지 또는 성공적 등록 시 Race Condition 플래그 발급
        scoreboardService.markFound("RACE_CONDITION");

        // 2. 사용 (Use) - Lock 없이 업데이트
        BigDecimal discount = couponRepository.getDiscountAmount(code);
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent() && discount.compareTo(BigDecimal.ZERO) > 0) {
            User user = userOpt.get();
            user.setBalance(user.getBalance().add(discount));
            userRepository.updateUser(user);
            couponRepository.markCouponUsed(code);
            redeemCounter.decrementAndGet();

            return ResponseEntity.ok(Map.of(
                    "message", "쿠폰이 등록되어 " + discount + "원이 충전되었습니다.",
                    "newBalance", user.getBalance()
            ));
        }

        redeemCounter.decrementAndGet();
        return ResponseEntity.badRequest().body(Map.of("message", "쿠폰 적용 실패"));
    }
}
