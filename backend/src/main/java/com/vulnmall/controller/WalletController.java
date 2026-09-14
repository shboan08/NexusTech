package com.vulnmall.controller;

import com.vulnmall.dto.WalletDto;
import com.vulnmall.entity.User;
import com.vulnmall.repository.UserRepository;
import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final UserRepository userRepository;
    private final ScoreboardService scoreboardService;
    // In-memory single-use voucher tracking for race condition demo
    private final ConcurrentHashMap<String, Boolean> usedVouchers = new ConcurrentHashMap<>();

    public WalletController(UserRepository userRepository, ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
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
     * 지갑 정보 및 현재 잔액 조회
     */
    @GetMapping("/balance")
    public ResponseEntity<?> getBalance() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "balance", user.getBalance()
        ));
    }

    /**
     * [WSTG-BUSL-09: Negative Amount Charge & PG Tampering]
     * 결제 금액 충전 API
     * 1) 음수 금액(amount < 0) 검증 누락: 음수 충전 시 비정상 차감/잔액 조작
     * 2) 클라이언트 PG 승인 금액 불일치: paidAmount = 100인데 amount = 1,000,000 요청 시 서버가 amount를 그대로 신뢰
     */
    @PostMapping("/charge")
    public ResponseEntity<?> chargeBalance(@RequestBody WalletDto.ChargeRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        if (request.getAmount() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "충전할 금액을 입력해주세요."));
        }

        BigDecimal amount = request.getAmount();
        BigDecimal paidAmount = request.getPaidAmount() != null ? request.getPaidAmount() : amount;

        // 취약점 탐지 트리거: 음수 충전 또는 PG 금액 변조
        if (amount.compareTo(BigDecimal.ZERO) < 0 || (paidAmount.compareTo(amount) < 0 && paidAmount.compareTo(BigDecimal.ZERO) > 0)) {
            scoreboardService.markFound("WALLET_NEGATIVE_CHARGE");
        }

        // 유효성 검사 미흡: 서버가 클라이언트 요청 금액(amount)을 그대로 잔액에 합산
        BigDecimal newBalance = user.getBalance().add(amount);
        user.setBalance(newBalance);
        userRepository.updateUser(user);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "chargedAmount", amount,
                "paidAmount", paidAmount,
                "currentBalance", newBalance,
                "message", "금액 충전이 정상 완료되었습니다."
        ));
    }

    /**
     * [WSTG-SESS-05: CSRF 취약점 - 계정 간 잔액 무단 송금]
     * CSRF 보호 없이 단순 POST 요청으로 타 사용자 계정으로 잔액 송금
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transferBalance(@RequestBody WalletDto.TransferRequest request) {
        User sender = getCurrentUser();
        if (sender == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "송금할 금액을 올바르게 입력해주세요."));
        }

        Optional<User> recipientOpt = userRepository.findByUsername(request.getToUsername());
        if (recipientOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "수신 대상 사용자를 찾을 수 없습니다."));
        }

        User recipient = recipientOpt.get();
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "잔액이 부족합니다."));
        }

        scoreboardService.markFound("CSRF_WALLET");

        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        recipient.setBalance(recipient.getBalance().add(request.getAmount()));

        userRepository.updateUser(sender);
        userRepository.updateUser(recipient);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "transferredAmount", request.getAmount(),
                "recipient", recipient.getUsername(),
                "remainingBalance", sender.getBalance(),
                "message", "송금이 완료되었습니다."
        ));
    }

    /**
     * [WSTG-BUSL-04: Concurrency Race Condition 바우처 충전]
     * 한 번만 사용되어야 하는 10만 원권 프로모션 바우처 'CYBER_BONUS_100K'
     * 동시 병렬 요청 시 TOCTOU 레이스 컨디션으로 다중 충전 성공
     */
    @PostMapping("/voucher")
    public ResponseEntity<?> redeemVoucher(@RequestBody WalletDto.VoucherRedeemRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        String code = request.getVoucherCode();
        if (code == null || !code.equalsIgnoreCase("CYBER_BONUS_100K")) {
            return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 바우처 코드입니다."));
        }

        // 인위적인 미세 지연(Race Condition Window)
        try {
            if (usedVouchers.containsKey(code)) {
                return ResponseEntity.badRequest().body(Map.of("message", "이미 사용 완료된 바우처입니다."));
            }
            Thread.sleep(80); // 동시성 윈도우
        } catch (InterruptedException ignored) {}

        scoreboardService.markFound("WALLET_RACE_CONDITION");
        usedVouchers.put(code, true);

        BigDecimal bonus = BigDecimal.valueOf(100000);
        user.setBalance(user.getBalance().add(bonus));
        userRepository.updateUser(user);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "creditedAmount", bonus,
                "currentBalance", user.getBalance(),
                "message", "프로모션 바우처가 적용되어 100,000원이 충전되었습니다."
        ));
    }
}
