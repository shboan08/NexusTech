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

    public ProductController(ProductRepository productRepository, ReviewRepository reviewRepository,
                             com.vulnmall.service.ScoreboardService scoreboardService) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.scoreboardService = scoreboardService;
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
}
