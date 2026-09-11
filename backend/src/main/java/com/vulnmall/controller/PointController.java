package com.vulnmall.controller;

import com.vulnmall.entity.User;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/points")
public class PointController {

    private final UserRepository userRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public PointController(UserRepository userRepository, com.vulnmall.service.ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
        this.scoreboardService = scoreboardService;
    }

    /**
     * [WSTG-BUSL-03: Rounding Error & Precision Truncation Arbitrage]
     * 잔액(KRW)을 VIP 멤버십 포인트로 환전하는 기능
     * 소액 입력 시 부동소수점 절삭 오류로 잔액은 0원 차감되지만 포인트는 1포인트씩 무한 누적
     */
    @PostMapping("/exchange")
    public ResponseEntity<?> exchangePoints(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        double krwAmount = 0.0;
        if (body.get("amount") != null) {
            krwAmount = Double.parseDouble(body.get("amount").toString());
        }

        if (krwAmount <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "0원보다 큰 금액을 입력하세요."));
        }

        // [비즈니스 로직 결함]: 정수 변환 및 소수점 버림 오차
        int pointsToCredit = (int) Math.ceil(krwAmount * 0.1); // 올림 처리로 소액도 최소 1포인트 지급
        long krwToDeduct = (long) krwAmount; // 소수점 버림으로 0.9원 등은 0원 차감

        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        User user = userOpt.get();
        if (user.getBalance().compareTo(BigDecimal.valueOf(krwToDeduct)) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "잔액이 부족합니다."));
        }

        user.setBalance(user.getBalance().subtract(BigDecimal.valueOf(krwToDeduct)));
        userRepository.updateUser(user);

        boolean exploited = krwToDeduct == 0 && pointsToCredit > 0;
        if (exploited) {
            scoreboardService.markFound("ROUNDING_ERROR");
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "creditedPoints", pointsToCredit,
                "deductedKrw", krwToDeduct,
                "remainingBalance", user.getBalance(),
                "arbitrageExploited", exploited
        ));
    }
}
