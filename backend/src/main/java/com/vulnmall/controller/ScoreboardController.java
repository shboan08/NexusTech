package com.vulnmall.controller;

import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/scoreboard")
public class ScoreboardController {

    private final ScoreboardService scoreboardService;

    public ScoreboardController(ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    /**
     * 전체 스코어보드 현황 조회
     */
    @GetMapping
    public ResponseEntity<?> getScoreboard() {
        return ResponseEntity.ok(scoreboardService.getScoreboard());
    }

    /**
     * 취약점 수동 등록 또는 프론트엔드 발견 통보
     */
    @PostMapping("/trigger")
    public ResponseEntity<?> triggerVuln(@RequestBody Map<String, String> body) {
        String vulnKey = body.get("vulnKey");
        if (vulnKey == null || vulnKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "vulnKey is required"));
        }
        String flag = scoreboardService.markFound(vulnKey);
        if (flag == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown vulnKey: " + vulnKey));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "vulnKey", vulnKey,
                "flag", flag,
                "message", "취약점이 성공적으로 발견 처리되었습니다!"
        ));
    }

    /**
     * 스코어보드 초기화
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetScoreboard() {
        scoreboardService.reset();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "스코어보드가 성공적으로 초기화되었습니다."
        ));
    }
}
