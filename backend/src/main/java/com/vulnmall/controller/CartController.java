package com.vulnmall.controller;

import com.vulnmall.dto.CartDto;
import com.vulnmall.entity.CartItem;
import com.vulnmall.entity.Product;
import com.vulnmall.entity.User;
import com.vulnmall.repository.CartRepository;
import com.vulnmall.repository.ProductRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;

    public CartController(CartRepository cartRepository, ProductRepository productRepository,
                          UserRepository userRepository, com.vulnmall.service.ScoreboardService scoreboardService) {
        this.cartRepository = cartRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.scoreboardService = scoreboardService;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByUsername(auth.getName());
        return userOpt.map(User::getId).orElse(null);
    }

    @GetMapping
    public ResponseEntity<?> getCart() {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<CartItem> items = cartRepository.findByUserId(userId);
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        return ResponseEntity.ok(Map.of(
                "items", items,
                "totalAmount", total
        ));
    }

    /**
     * [비즈니스 로직 결함 1]: 장바구니 추가 시 클라이언트가 제시한 unitPrice를 서버가 수용
     */
    @PostMapping("/add")
    public ResponseEntity<?> addToCart(@RequestBody CartDto.AddItemRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Product> prodOpt = productRepository.findById(request.getProductId());
        if (prodOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "상품이 존재하지 않습니다."));

        if (request.getUnitPrice() != null && request.getUnitPrice().compareTo(prodOpt.get().getPrice()) != 0) {
            scoreboardService.markFound("PRICE_TAMPER");
        }

        // 클라이언트가 임의의 가격(unitPrice)을 넘기면 DB 실제 가격 대신 해당 가격을 그대로 채택
        BigDecimal priceToUse = (request.getUnitPrice() != null) ? request.getUnitPrice() : prodOpt.get().getPrice();
        int qty = (request.getQuantity() != null) ? request.getQuantity() : 1;

        cartRepository.addItem(userId, request.getProductId(), qty, priceToUse);
        return ResponseEntity.ok(Map.of("message", "장바구니에 상품을 담았습니다."));
    }

    /**
     * [비즈니스 로직 결함 2]: 음수 수량 허용 (Negative Quantity Vulnerability)
     * quantity = -2 전송 시 장바구니 전체 결제 금액이 마이너스로 차감되어 결제 금액 왜곡
     */
    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateQuantity(@PathVariable("id") Long cartItemId,
                                           @RequestBody CartDto.UpdateQuantityRequest request) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        if (request.getQuantity() != null && request.getQuantity() < 0) {
            scoreboardService.markFound("NEG_QUANTITY");
        }

        // 유효성 검사 누락 (0 미만의 음수 수량 통과)
        cartRepository.updateQuantity(cartItemId, request.getQuantity());
        return ResponseEntity.ok(Map.of("message", "수량이 변경되었습니다."));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteItem(@PathVariable("id") Long cartItemId) {
        Long userId = getCurrentUserId();
        if (userId == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        cartRepository.deleteItem(cartItemId);
        return ResponseEntity.ok(Map.of("message", "상품이 삭제되었습니다."));
    }
}
