package com.vulnmall.controller;

import com.vulnmall.entity.Product;
import com.vulnmall.entity.Review;
import com.vulnmall.repository.ProductRepository;
import com.vulnmall.repository.ReviewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ProductController(ProductRepository productRepository, ReviewRepository reviewRepository,
                             com.vulnmall.service.ScoreboardService scoreboardService,
                             org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.scoreboardService = scoreboardService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 상품 검색 및 목록 조회 (SQL Injection 취약점 포함)
     */
    @GetMapping
    public ResponseEntity<?> getProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "sortBy", required = false, defaultValue = "id") String sortBy,
            @RequestParam(value = "sortOrder", required = false, defaultValue = "ASC") String sortOrder) {

        // SQLi / XSS 탐지 시 백엔드 스코어보드 웹소켓으로만 OOB 전송 (HTTP 응답 오염 방지)
        if (keyword != null) {
            String kwUpper = keyword.toUpperCase();
            if (kwUpper.contains("<SCRIPT") || kwUpper.contains("<IMG") || kwUpper.contains("JAVASCRIPT:") || kwUpper.contains("ONERROR=")) {
                scoreboardService.markFound("XSS_REFLECTED");
            } else if (kwUpper.contains("SLEEP(") || kwUpper.contains("WAITFOR") || kwUpper.contains("BENCHMARK")) {
                scoreboardService.markFound("SQLI_TIME_BLIND");
            } else if (kwUpper.contains(" AND ") || (kwUpper.contains("'") && (kwUpper.contains("1=1") || kwUpper.contains("1=2")))) {
                scoreboardService.markFound("SQLI_BOOL_BLIND");
            } else if (kwUpper.contains("'") || kwUpper.contains("--") || kwUpper.contains(" OR ")) {
                scoreboardService.markFound("SQLI_LIKE");
            }
        }

        if (sortBy != null) {
            String sbUpper = sortBy.toUpperCase();
            if (sbUpper.contains("CASE") || sbUpper.contains("SELECT") || sbUpper.contains("WHEN") || sbUpper.contains("(")) {
                scoreboardService.markFound("SQLI_ORDERBY");
            }
        }

        List<Product> products = productRepository.searchProducts(keyword, category, sortBy, sortOrder);

        // 순수한 이커머스 응답 반환
        Map<String, Object> result = new HashMap<>();
        result.put("queryKeyword", keyword != null ? keyword : "");
        result.put("totalCount", products.size());
        result.put("products", products);

        return ResponseEntity.ok(result);
    }

    /**
     * 단일 상품 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProductDetail(@PathVariable("id") Long id) {
        Optional<Product> productOpt = productRepository.findById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<Review> reviews = reviewRepository.findByProductId(id);

        Map<String, Object> response = new HashMap<>();
        response.put("product", productOpt.get());
        response.put("reviews", reviews);

        return ResponseEntity.ok(response);
    }

    /**
     * [WSTG-INPV-05: Union-Based SQL Injection]
     * 카테고리 필터링 조회 엔드포인트
     */
    @GetMapping("/filter")
    public ResponseEntity<?> filterByCategory(@RequestParam("category") String category) {
        if (category != null && (category.toUpperCase().contains("UNION") || category.contains("'"))) {
            scoreboardService.markFound("SQLI_UNION");
        }
        List<Map<String, Object>> products = productRepository.filterByCategoryUnion(category);
        return ResponseEntity.ok(products);
    }

    /**
     * [WSTG-INPV-19: Server-Side Request Forgery (SSRF)]
     * 품절 상품 재입고 알림 신청 (Webhook URL 및 이메일 등록)
     * Webhook URL에 대한 내부 사설망/클라우드 메타데이터 IP 검증 부재로 SSRF 취약점 노출
     */
    @PostMapping("/{id}/notify-restock")
    public ResponseEntity<?> subscribeRestock(
            @PathVariable("id") Long productId,
            @RequestBody Map<String, Object> body) {

        Optional<Product> prodOpt = productRepository.findById(productId);
        if (prodOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String email = body.get("email") != null ? body.get("email").toString().trim() : "";
        String webhookUrl = body.get("webhookUrl") != null ? body.get("webhookUrl").toString().trim() : "";

        if (email.isEmpty() && webhookUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "이메일 또는 Webhook URL 중 하나는 입력해야 합니다."));
        }

        // SSRF 검증 및 탐지
        String testResult = null;
        if (!webhookUrl.isEmpty()) {
            String lowerUrl = webhookUrl.toLowerCase();
            if (lowerUrl.contains("127.0.0.1") || lowerUrl.contains("localhost") || lowerUrl.contains("169.254")
                    || lowerUrl.contains("0.0.0.0") || lowerUrl.contains("::1") || lowerUrl.contains("10.")
                    || lowerUrl.contains("192.168.") || lowerUrl.contains("172.16.") || lowerUrl.contains("internal")
                    || lowerUrl.contains("admin")) {
                scoreboardService.markFound("RESTOCK_WEBHOOK_SSRF");
            }

            // 실제 서버 사이드 HTTP Webhook Ping 테스트 수행 (SSRF 실제 응답 반환)
            try {
                java.net.URL url = new java.net.URL(webhookUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2500);
                conn.setReadTimeout(2500);
                conn.setRequestProperty("User-Agent", "NexusTech-RestockNotifier/2.0");

                int code = conn.getResponseCode();
                java.io.InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                String preview = "";
                if (is != null) {
                    byte[] buf = is.readNBytes(256);
                    preview = new String(buf, java.nio.charset.StandardCharsets.UTF_8);
                }
                testResult = "HTTP " + code + (preview.isEmpty() ? "" : ": " + preview.trim());
            } catch (Exception e) {
                testResult = "Webhook Ping Result: " + e.getMessage();
            }
        }

        try {
            jdbcTemplate.update(
                "INSERT INTO restock_subscriptions (product_id, webhook_url, email, is_notified, created_at) VALUES (?, ?, ?, false, CURRENT_TIMESTAMP)",
                productId, webhookUrl, email);
        } catch (Exception ignored) {}

        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("message", "재입고 알림 신청이 완료되었습니다.");
        resp.put("productId", productId);
        resp.put("productName", prodOpt.get().getName());
        if (testResult != null) {
            resp.put("webhookVerification", testResult);
        }
        return ResponseEntity.ok(resp);
    }
}
