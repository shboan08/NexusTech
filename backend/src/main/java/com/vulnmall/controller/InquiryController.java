package com.vulnmall.controller;

import com.vulnmall.entity.Inquiry;
import com.vulnmall.entity.User;
import com.vulnmall.repository.InquiryRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/inquiries")
public class InquiryController {

    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public InquiryController(InquiryRepository inquiryRepository, UserRepository userRepository,
                             com.vulnmall.service.ScoreboardService scoreboardService) {
        this.inquiryRepository = inquiryRepository;
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
     * [WSTG-INPV-02: Stored XSS]
     * 고객 지원 Q&A 문의글 목록 조회
     */
    @GetMapping
    public ResponseEntity<List<Inquiry>> getInquiries() {
        return ResponseEntity.ok(inquiryRepository.findAll());
    }

    /**
     * [Second-Order SQL Injection 1단계: 안전한 데이터 저장]
     * 작성 시점에는 Prepared Statement를 사용하여 SQL Injection이 발생하지 않음
     */
    @PostMapping
    public ResponseEntity<?> createInquiry(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        String username = user != null ? user.getUsername() : "guest";
        Long userId = user != null ? user.getId() : 0L;

        String title = (String) body.get("title");
        String content = (String) body.get("content");
        Boolean isSecret = body.get("isSecret") != null ? (Boolean) body.get("isSecret") : false;

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "제목과 내용을 입력해주세요."));
        }

        String combined = (title + " " + content).toUpperCase();
        if (combined.contains("<SCRIPT") || combined.contains("<IMG") || combined.contains("ONERROR=") || combined.contains("<SVG") || combined.contains("JAVASCRIPT:")) {
            scoreboardService.markFound("XSS_STORED_INQ");
        }

        Long id = inquiryRepository.createInquiry(userId, username, title, content, isSecret);
        return ResponseEntity.ok(Map.of("message", "문의글이 등록되었습니다.", "id", id));
    }

    /**
     * [WSTG-INPV-05: Second-Order SQL Injection 2단계: 저장된 데이터로 2차 쿼리 실행]
     * 관리자 또는 감사 시스템이 기저장된 문의글의 제목을 읽어와
     * 감사 로그(Audit Logs) 조회 시 문자열 직접 결합으로 쿼리를 동적 실행
     */
    @GetMapping("/{id}/audit")
    public ResponseEntity<?> getInquiryAuditLogs(@PathVariable("id") Long id) {
        Optional<Inquiry> inqOpt = inquiryRepository.findById(id);
        if (inqOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Inquiry inquiry = inqOpt.get();
        // 2차 인젝션 발현 지점
        if (inquiry.getTitle() != null && (inquiry.getTitle().contains("'") || inquiry.getTitle().toUpperCase().contains("UNION"))) {
            scoreboardService.markFound("SQLI_SECOND");
        }

        List<Map<String, Object>> logs = inquiryRepository.searchAuditLogsByTitle(inquiry.getTitle());

        return ResponseEntity.ok(Map.of(
                "inquiryId", id,
                "inquiryTitle", inquiry.getTitle(),
                "matchedAuditLogs", logs
        ));
    }
}
