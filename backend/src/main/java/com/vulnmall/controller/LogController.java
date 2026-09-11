package com.vulnmall.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/support/log")
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger("AUDIT_LOGGER");
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public LogController(com.vulnmall.service.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    /**
     * [WSTG-INPV-15: CRLF / Log Injection]
     * 클라이언트 디버그/피드백 메시지 로깅
     * 개행 문자(\r, \n)를 필터링하지 않고 로그에 그대로 출력하여
     * 가짜 감사(Audit) 로그 및 관리자 이벤트 조작 가능
     */
    @PostMapping
    public ResponseEntity<?> recordLog(@RequestBody Map<String, String> body) {
        String clientMsg = body.get("message");
        if (clientMsg == null) clientMsg = "";

        String flag = null;
        if (clientMsg.contains("\r") || clientMsg.contains("\n") || clientMsg.contains("%0d") || clientMsg.contains("%0a")) {
            flag = scoreboardService.markFound("CRLF_LOG");
        }

        // 취약점: \r\n 미필터링으로 인한 로그 위조
        logger.info("[CLIENT_EVENT] User feedback received: " + clientMsg);

        Map<String, Object> resp = new java.util.HashMap<>(Map.of(
                "status", "RECORDED",
                "loggedMessage", clientMsg
        ));
        var res = ResponseEntity.ok();
        if (flag != null) {
            resp.put("flag", flag);
            res.header("X-Vuln-Flag", flag);
        }
        return res.body(resp);
    }
}
