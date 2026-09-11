package com.vulnmall.controller;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/orders/receipt")
public class ReceiptTemplateController {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public ReceiptTemplateController(com.vulnmall.service.ScoreboardService scoreboardService) {
        this.scoreboardService = scoreboardService;
    }

    /**
     * [WSTG-INPV-18: Server-Side Template Injection (SpEL SSTI)]
     * 사용자 맞춤형 전자 영수증 안내 문구 템플릿 처리기
     * #{7*7} 또는 #{T(java.lang.Runtime).getRuntime().exec('whoami')} 입력 시 서버 사이드 표현식 실행
     */
    @PostMapping("/template")
    public ResponseEntity<?> previewReceiptTemplate(@RequestBody Map<String, String> body) {
        String template = body.get("template");
        if (template == null) return ResponseEntity.badRequest().body(Map.of("message", "template 문구를 입력하세요."));

        String flag = null;
        if (template.contains("#{") && template.contains("}")) {
            flag = scoreboardService.markFound("SSTI");
        }

        try {
            // Spring Expression Language (SpEL) 취약한 동적 평가
            StringBuilder result = new StringBuilder();
            int startIdx = 0;
            while (true) {
                int openIdx = template.indexOf("#{", startIdx);
                if (openIdx == -1) {
                    result.append(template.substring(startIdx));
                    break;
                }
                result.append(template, startIdx, openIdx);
                int closeIdx = template.indexOf("}", openIdx);
                if (closeIdx == -1) {
                    result.append(template.substring(openIdx));
                    break;
                }
                String exprStr = template.substring(openIdx + 2, closeIdx);
                Expression exp = parser.parseExpression(exprStr);
                Object val = exp.getValue();
                result.append(val != null ? val.toString() : "");
                startIdx = closeIdx + 1;
            }

            Map<String, Object> resp = new java.util.HashMap<>(Map.of(
                    "status", "SUCCESS",
                    "renderedMessage", result.toString()
            ));
            var res = ResponseEntity.ok();
            if (flag != null) {
                resp.put("flag", flag);
                res.header("X-Vuln-Flag", flag);
            }
            return res.body(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Template Evaluation Error",
                    "details", e.getMessage()
            ));
        }
    }
}
