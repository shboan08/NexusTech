package com.vulnmall.controller;

import com.vulnmall.dto.WishlistDto;
import com.vulnmall.entity.CustomDeck;
import com.vulnmall.entity.User;
import com.vulnmall.entity.Wishlist;
import com.vulnmall.repository.UserRepository;
import com.vulnmall.repository.WishlistRepository;
import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/wishlist")
public class WishlistController {

    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final ScoreboardService scoreboardService;

    public WishlistController(WishlistRepository wishlistRepository, UserRepository userRepository,
                              ScoreboardService scoreboardService) {
        this.wishlistRepository = wishlistRepository;
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
     * 내 찜(위시리스트) 목록 조회
     */
    @GetMapping
    public ResponseEntity<?> getMyWishlist() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<Wishlist> items = wishlistRepository.findWishlistsByUserId(user.getId());
        return ResponseEntity.ok(Map.of(
                "totalCount", items.size(),
                "items", items
        ));
    }

    /**
     * 위시리스트 상품 찜 등록/해제 토글
     */
    @PostMapping("/toggle")
    public ResponseEntity<?> toggleWishlist(@RequestBody WishlistDto.ToggleRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Long productId = request.getProductId();
        if (productId == null) return ResponseEntity.badRequest().body(Map.of("message", "상품 ID가 필요합니다."));

        boolean currentlyWishlisted = wishlistRepository.isWishlisted(user.getId(), productId);
        if (currentlyWishlisted) {
            wishlistRepository.removeWishlist(user.getId(), productId);
            return ResponseEntity.ok(Map.of("wishlisted", false, "message", "위시리스트에서 제거되었습니다."));
        } else {
            wishlistRepository.addWishlist(user.getId(), productId);
            return ResponseEntity.ok(Map.of("wishlisted", true, "message", "위시리스트에 추가되었습니다."));
        }
    }

    /**
     * 내 커스텀 덱 목록 조회
     */
    @GetMapping("/decks")
    public ResponseEntity<?> getMyDecks() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<CustomDeck> decks = wishlistRepository.findDecksByUserId(user.getId());
        return ResponseEntity.ok(decks);
    }

    /**
     * [WSTG-INPV-02: Stored XSS in Custom Deck]
     * 나만의 커스텀 사이버 덱 생성 API
     * deckName 또는 description 필드에 XSS 필터링 누락
     */
    @PostMapping("/decks")
    public ResponseEntity<?> createCustomDeck(@RequestBody WishlistDto.CreateDeckRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        String name = request.getDeckName() != null ? request.getDeckName() : "신규 하드웨어 덱";
        String desc = request.getDescription() != null ? request.getDescription() : "";
        String secret = request.getSecretNote() != null ? request.getSecretNote() : "";
        boolean isPub = request.getIsPublic() != null ? request.getIsPublic() : true;

        // XSS 취약점 감지
        String checkStr = (name + " " + desc).toLowerCase();
        if (checkStr.contains("<script") || checkStr.contains("<img") || checkStr.contains("onerror") || checkStr.contains("javascript:")) {
            scoreboardService.markFound("WISHLIST_STORED_XSS");
        }

        String shareToken = "DECK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Long deckId = wishlistRepository.createCustomDeck(user.getId(), name, desc, secret, isPub, shareToken);

        if (request.getProductIds() != null) {
            for (Long pid : request.getProductIds()) {
                wishlistRepository.addDeckItem(deckId, pid);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "커스텀 덱이 성공적으로 생성되었습니다!",
                "deckId", deckId,
                "shareToken", shareToken,
                "shareUrl", "/?deckId=" + deckId
        ));
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR 결함]
     * 특정 커스텀 덱 상세 조회 API
     * 타인(특히 VIP 김피해)의 비공개(is_public=false) 덱 ID를 요청해도
     * 소유권 검증 없이 기밀 메모(secretNote)와 덱 구성 품목을 그대로 반환
     */
    @GetMapping("/decks/{id}")
    public ResponseEntity<?> getDeckDetail(@PathVariable("id") Long deckId) {
        User user = getCurrentUser();
        Optional<CustomDeck> deckOpt = wishlistRepository.findDeckById(deckId);
        if (deckOpt.isEmpty()) return ResponseEntity.notFound().build();

        CustomDeck deck = deckOpt.get();

        // BOLA / IDOR 결함 감지: 타인의 비공개 덱에 접근하거나 타인 덱 열람 시
        if (user != null && !deck.getUserId().equals(user.getId())) {
            scoreboardService.markFound("WISHLIST_BOLA_IDOR");
        } else if (user == null && Boolean.FALSE.equals(deck.getIsPublic())) {
            scoreboardService.markFound("WISHLIST_BOLA_IDOR");
        }

        return ResponseEntity.ok(deck);
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR 결함]
     * 타인의 커스텀 덱 무단 삭제 API
     */
    @DeleteMapping("/decks/{id}")
    public ResponseEntity<?> deleteDeck(@PathVariable("id") Long deckId) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<CustomDeck> deckOpt = wishlistRepository.findDeckById(deckId);
        if (deckOpt.isEmpty()) return ResponseEntity.notFound().build();

        CustomDeck deck = deckOpt.get();
        if (!deck.getUserId().equals(user.getId())) {
            scoreboardService.markFound("WISHLIST_BOLA_IDOR");
        }

        wishlistRepository.deleteDeck(deckId);
        return ResponseEntity.ok(Map.of("message", "커스텀 덱이 삭제되었습니다."));
    }

    /**
     * 공개 공유 토큰 기반 덱 조회
     */
    @GetMapping("/share/{token}")
    public ResponseEntity<?> getSharedDeck(@PathVariable("token") String token) {
        Optional<CustomDeck> deckOpt = wishlistRepository.findDeckByShareToken(token);
        if (deckOpt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(deckOpt.get());
    }
}
