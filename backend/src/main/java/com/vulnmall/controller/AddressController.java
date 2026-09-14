package com.vulnmall.controller;

import com.vulnmall.dto.AddressDto;
import com.vulnmall.entity.User;
import com.vulnmall.entity.UserAddress;
import com.vulnmall.repository.AddressRepository;
import com.vulnmall.repository.UserRepository;
import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user/addresses")
public class AddressController {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final ScoreboardService scoreboardService;

    public AddressController(AddressRepository addressRepository, UserRepository userRepository, ScoreboardService scoreboardService) {
        this.addressRepository = addressRepository;
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
     * 내 배송지 목록 조회
     */
    @GetMapping
    public ResponseEntity<?> getMyAddresses() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<UserAddress> addresses = addressRepository.findByUserId(user.getId());
        return ResponseEntity.ok(addresses);
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR 결함]
     * 특정 배송지 조회 시 타 사용자의 배송지 ID({id})를 요청해도
     * 소유권 검증 없이 그대로 VIP/타인의 실명, 연락처, 주소, 배송 메모 반환
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getAddressDetail(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<UserAddress> addrOpt = addressRepository.findById(id);
        if (addrOpt.isEmpty()) return ResponseEntity.notFound().build();

        UserAddress addr = addrOpt.get();
        if (!addr.getUserId().equals(user.getId())) {
            scoreboardService.markFound("BOLA_ADDRESS");
        }

        return ResponseEntity.ok(addr);
    }

    /**
     * 배송지 신규 등록
     * [WSTG-INPV-02: Stored XSS 결함]
     * addressLine2, deliveryMemo, recipientName에 악성 HTML/자바스크립트 미필터링 삽입
     */
    @PostMapping
    public ResponseEntity<?> createAddress(@RequestBody AddressDto.CreateRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        String memo = request.getDeliveryMemo() != null ? request.getDeliveryMemo() : "";
        String addr2 = request.getAddressLine2() != null ? request.getAddressLine2() : "";
        String recipient = request.getRecipientName() != null ? request.getRecipientName() : "";

        if (memo.contains("<script") || memo.contains("<img") || memo.contains("onerror") ||
            addr2.contains("<script") || addr2.contains("<img") || addr2.contains("onerror") ||
            recipient.contains("<script") || recipient.contains("<img") || recipient.contains("onerror")) {
            scoreboardService.markFound("XSS_STORED_ADDRESS");
        }

        Long newId = addressRepository.createAddress(
                user.getId(),
                recipient,
                request.getPhone(),
                request.getPostalCode(),
                request.getAddressLine1(),
                addr2,
                memo,
                Boolean.TRUE.equals(request.getIsDefault())
        );

        return ResponseEntity.ok(Map.of("message", "배송지가 등록되었습니다.", "addressId", newId));
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR 결함]
     * 타인의 배송지 ID를 지정하여 임의로 배송지/연락처/메모 변조
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateAddress(@PathVariable("id") Long id, @RequestBody AddressDto.UpdateRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<UserAddress> addrOpt = addressRepository.findById(id);
        if (addrOpt.isEmpty()) return ResponseEntity.notFound().build();

        UserAddress addr = addrOpt.get();
        if (!addr.getUserId().equals(user.getId())) {
            scoreboardService.markFound("BOLA_ADDRESS");
        }

        String memo = request.getDeliveryMemo() != null ? request.getDeliveryMemo() : "";
        String addr2 = request.getAddressLine2() != null ? request.getAddressLine2() : "";
        String recipient = request.getRecipientName() != null ? request.getRecipientName() : "";

        if (memo.contains("<script") || memo.contains("<img") || memo.contains("onerror") ||
            addr2.contains("<script") || addr2.contains("<img") || addr2.contains("onerror") ||
            recipient.contains("<script") || recipient.contains("<img") || recipient.contains("onerror")) {
            scoreboardService.markFound("XSS_STORED_ADDRESS");
        }

        addressRepository.updateAddress(
                id,
                recipient,
                request.getPhone(),
                request.getPostalCode(),
                request.getAddressLine1(),
                addr2,
                memo,
                Boolean.TRUE.equals(request.getIsDefault()),
                user.getId()
        );

        return ResponseEntity.ok(Map.of("message", "배송지가 수정되었습니다.", "addressId", id));
    }

    /**
     * [WSTG-ATHZ-04: BOLA / IDOR 결함]
     * 타인의 배송지 삭제
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAddress(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<UserAddress> addrOpt = addressRepository.findById(id);
        if (addrOpt.isEmpty()) return ResponseEntity.notFound().build();

        if (!addrOpt.get().getUserId().equals(user.getId())) {
            scoreboardService.markFound("BOLA_ADDRESS");
        }

        addressRepository.deleteAddress(id);
        return ResponseEntity.ok(Map.of("message", "배송지가 삭제되었습니다."));
    }

    /**
     * [WSTG-INPV-05: SQL Injection 취약점]
     * 도로명/우편번호 주소 검색 API
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchAddresses(@RequestParam("keyword") String keyword) {
        if (keyword != null && (keyword.contains("'") || keyword.toUpperCase().contains("UNION") || keyword.contains("--"))) {
            scoreboardService.markFound("SQLI_ADDRESS");
        }
        try {
            List<UserAddress> list = addressRepository.searchAddressesSql(keyword);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Database Error", "details", e.getMessage()));
        }
    }
}
