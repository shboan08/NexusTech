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
     * 내 문의 및 기술 지원 티켓 목록 조회
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyInquiries() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        return ResponseEntity.ok(inquiryRepository.findByUserId(user.getId()));
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR on Support Tickets]
     * 타인의 비공개(Secret) 기술 지원 티켓 및 불량 접수 내용 무단 열람
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getInquiryDetail(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Inquiry> inqOpt = inquiryRepository.findById(id);
        if (inqOpt.isEmpty()) return ResponseEntity.notFound().build();

        Inquiry inquiry = inqOpt.get();
        if (!inquiry.getUserId().equals(user.getId()) && Boolean.TRUE.equals(inquiry.getIsSecret())) {
            scoreboardService.markFound("INQUIRY_BOLA_IDOR");
        }

        return ResponseEntity.ok(inquiry);
    }

    /**
     * [Second-Order SQL Injection & Stored XSS]
     * 1:1 고객지원 헬프데스크 티켓 접수
     */
    @PostMapping
    public ResponseEntity<?> createInquiry(@RequestBody Map<String, Object> body) {
        User user = getCurrentUser();
        String username = user != null ? user.getUsername() : "guest";
        Long userId = user != null ? user.getId() : 0L;

        String title = (String) body.get("title");
        String content = (String) body.get("content");
        String category = body.get("category") != null ? body.get("category").toString() : "GENERAL";
        Long orderId = body.get("orderId") != null && !body.get("orderId").toString().isEmpty() ? Long.parseLong(body.get("orderId").toString()) : null;
        String attachmentUrl = (String) body.get("attachmentUrl");
        Boolean isSecret = body.get("isSecret") != null ? (Boolean) body.get("isSecret") : false;

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "제목과 내용을 입력해주세요."));
        }

        String combined = (title + " " + content).toUpperCase();
        if (combined.contains("<SCRIPT") || combined.contains("<IMG") || combined.contains("ONERROR=") || combined.contains("<SVG") || combined.contains("JAVASCRIPT:")) {
            scoreboardService.markFound("XSS_STORED_INQ");
        }

        Long id = inquiryRepository.createSupportTicket(userId, username, title, content, category, orderId, attachmentUrl, isSecret);
        return ResponseEntity.ok(Map.of("message", "고객지원 문의 티켓이 성공적으로 접수되었습니다.", "id", id));
    }

    /**
     * [WSTG-INPV-12: Unrestricted File Upload on Support Tickets]
     * 문의 증빙 사진 및 시스템 로그 첨부파일 업로드
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadTicketFile(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "파일이 비어 있습니다."));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) originalFilename = "proof_" + System.currentTimeMillis() + ".dat";

            String lower = originalFilename.toLowerCase();
            if (lower.endsWith(".jsp") || lower.endsWith(".html") || lower.endsWith(".svg") || lower.endsWith(".sh") || lower.endsWith(".php") || lower.endsWith(".exe")) {
                scoreboardService.markFound("TICKET_FILE_UPLOAD");
            }

            java.io.File uploadFolder = new java.io.File("uploads");
            if (!uploadFolder.exists()) {
                uploadFolder.mkdirs();
            }

            java.nio.file.Path targetLocation = uploadFolder.toPath().resolve(originalFilename);
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "filename", originalFilename,
                    "fileUrl", "/uploads/" + originalFilename,
                    "message", "증빙 파일이 업로드되었습니다."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
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
