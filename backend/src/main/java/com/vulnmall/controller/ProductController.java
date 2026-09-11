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

    public ProductController(ProductRepository productRepository, ReviewRepository reviewRepository) {
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
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

        List<Product> products = productRepository.searchProducts(keyword, category, sortBy, sortOrder);

        // 검색어 반영 응답 (Reflected XSS 연계용 메타데이터 제공)
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
        List<Map<String, Object>> products = productRepository.filterByCategoryUnion(category);
        return ResponseEntity.ok(products);
    }
}
