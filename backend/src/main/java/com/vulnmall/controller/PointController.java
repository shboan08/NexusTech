package com.vulnmall.controller;

import com.vulnmall.entity.User;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/points")
public class PointController {

    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final com.vulnmall.service.ScoreboardService scoreboardService;
    private final AtomicInteger attendanceCounter = new AtomicInteger(0);

    public PointController(UserRepository userRepository, JdbcTemplate jdbcTemplate, com.vulnmall.service.ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
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
     * 현재 사용자의 보유 포인트 및 금일 출석 여부 조회
     */
    @GetMapping("/status")
    public ResponseEntity<?> getPointStatus() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        String today = LocalDate.now().toString();
        Integer attendedCount = 0;
        try {
            attendedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attendance_logs WHERE user_id = ? AND check_date = ?",
                    Integer.class, user.getId(), today);
        } catch (Exception ignored) {}

        boolean attended = attendedCount != null && attendedCount > 0;

        return ResponseEntity.ok(Map.of(
                "points", user.getPoints() != null ? user.getPoints() : 0,
                "balance", user.getBalance(),
                "attendedToday", attended,
                "todayDate", today
        ));
    }

    /**
     * [WSTG-BUSL-04: Attendance Date Manipulation & Multi-Claim Race]
     * 일일 출석체크 보상 (+1,000P)
     * 클라이언트가 요청 본문에 "customDate"를 지정하거나, 동시성 요청을 통해 하루에 여러 번 출석 보상 중복 수령
     */
    @PostMapping("/attendance")
    public ResponseEntity<?> claimAttendance(@RequestBody(required = false) Map<String, Object> body) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        int currentReqs = attendanceCounter.incrementAndGet();

        String checkDate = LocalDate.now().toString();
        boolean dateTampered = false;

        if (body != null && body.get("customDate") != null) {
            checkDate = body.get("customDate").toString();
            dateTampered = true;
            scoreboardService.markFound("ATTENDANCE_DATE_TAMPER");
        }

        // 중복 체크 검사 (Check)
        Integer existingCount = 0;
        try {
            existingCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM attendance_logs WHERE user_id = ? AND check_date = ?",
                    Integer.class, user.getId(), checkDate);
        } catch (Exception ignored) {}

        if (!dateTampered && existingCount != null && existingCount > 0) {
            attendanceCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "오늘 이미 출석체크를 완료하셨습니다."));
        }

        // 인위적 지연 (TOCTOU 레이스 윈도우)
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {}

        if (currentReqs > 1) {
            scoreboardService.markFound("ATTENDANCE_DATE_TAMPER");
        }

        int rewardPoints = 1000;
        try {
            jdbcTemplate.update("INSERT INTO attendance_logs (user_id, check_date, points_earned) VALUES (?, ?, ?)",
                    user.getId(), checkDate, rewardPoints);
        } catch (Exception ignored) {}

        userRepository.addPoints(user.getId(), rewardPoints);
        user.setPoints((user.getPoints() != null ? user.getPoints() : 0) + rewardPoints);
        attendanceCounter.decrementAndGet();

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "오늘의 출석체크가 완료되었습니다! (+1,000 P 적립)",
                "rewardPoints", rewardPoints,
                "checkDate", checkDate,
                "currentPoints", user.getPoints()
        ));
    }

    /**
     * [WSTG-CLNT-01: Roulette Client-Side Prize Manipulation]
     * 사이버 행운의 룰렛 이벤트
     * 서버 사이드 난수 검증 없이 클라이언트 전달 당첨 포인트(requestedPrizePoints)를 그대로 지급
     */
    @PostMapping("/roulette")
    public ResponseEntity<?> spinRoulette(@RequestBody(required = false) Map<String, Object> body) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        int[] defaultPrizes = {100, 300, 500, 1000, 2000, 5000};
        int selectedPrize = defaultPrizes[new Random().nextInt(defaultPrizes.length)];
        boolean clientTampered = false;

        if (body != null && body.get("requestedPrizePoints") != null) {
            try {
                int requested = Integer.parseInt(body.get("requestedPrizePoints").toString());
                if (requested > 0) {
                    selectedPrize = requested;
                    clientTampered = true;
                    scoreboardService.markFound("ROULETTE_CLIENT_TAMPER");
                }
            } catch (Exception ignored) {}
        }

        userRepository.addPoints(user.getId(), selectedPrize);
        int newPoints = (user.getPoints() != null ? user.getPoints() : 0) + selectedPrize;

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "prizePoints", selectedPrize,
                "message", "축하합니다! 룰렛에서 " + selectedPrize + " P 당첨되었습니다!",
                "currentPoints", newPoints,
                "tampered", clientTampered
        ));
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
        userRepository.addPoints(user.getId(), pointsToCredit);

        boolean exploited = krwToDeduct == 0 && pointsToCredit > 0;
        if (exploited) {
            scoreboardService.markFound("ROUNDING_ERROR");
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "creditedPoints", pointsToCredit,
                "deductedKrw", krwToDeduct,
                "remainingBalance", user.getBalance(),
                "remainingPoints", (user.getPoints() != null ? user.getPoints() : 0) + pointsToCredit,
                "arbitrageExploited", exploited
        ));
    }
}
