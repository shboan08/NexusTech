package com.vulnmall.controller;

import com.vulnmall.dto.ReviewDto;
import com.vulnmall.entity.Review;
import com.vulnmall.entity.User;
import com.vulnmall.repository.ReviewRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewController(ReviewRepository reviewRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    /**
     * [Stored XSS 취약점]:
     * 사용자가 입력한 comment에 악성 스크립트(<script>, <img onerror>, SVG 등)가 포함되어도
     * HTML 인코딩이나 Sanitization 없이 DB에 그대로 영속화
     */
    @PostMapping
    public ResponseEntity<?> addReview(@RequestBody ReviewDto.CreateReviewRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        if (request.getProductId() == null || request.getComment() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수 항목이 누락되었습니다."));
        }

        reviewRepository.addReview(
                request.getProductId(),
                user.getId(),
                user.getUsername(),
                request.getRating() != null ? request.getRating() : 5,
                request.getComment(), // Unescaped raw string
                request.getImagePath()
        );

        return ResponseEntity.ok(Map.of("message", "리뷰가 성공적으로 등록되었습니다."));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Review>> getProductReviews(@PathVariable("productId") Long productId) {
        List<Review> reviews = reviewRepository.findByProductId(productId);
        return ResponseEntity.ok(reviews);
    }
}
