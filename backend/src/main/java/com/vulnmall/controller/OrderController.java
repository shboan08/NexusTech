package com.vulnmall.controller;

import com.vulnmall.dto.OrderDto;
import com.vulnmall.entity.CartItem;
import com.vulnmall.entity.Order;
import com.vulnmall.entity.User;
import com.vulnmall.repository.CartRepository;
import com.vulnmall.repository.OrderRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;

    public OrderController(OrderRepository orderRepository, CartRepository cartRepository, UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
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
     * 주문 결제 (Price Tampering 취약점)
     * 클라이언트가 보낸 totalAmount를 서버에서 재계산 없이 그대로 믿고 잔액 차감 및 주문 생성
     */
    @PostMapping
    public ResponseEntity<?> checkout(@RequestBody OrderDto.CheckoutRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<CartItem> cartItems = cartRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "장바구니가 비어 있습니다."));
        }

        // 클라이언트 전송 금액 신뢰 (가격 변조 결함)
        BigDecimal finalAmount = request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.valueOf(1000);

        if (user.getBalance().compareTo(finalAmount) < 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "잔액이 부족합니다. 현재 잔액: " + user.getBalance()));
        }

        // 잔액 차감
        user.setBalance(user.getBalance().subtract(finalAmount));
        userRepository.updateUser(user);

        // 주문 생성
        Long orderId = orderRepository.createOrder(
                user.getId(),
                finalAmount,
                request.getRecipientName(),
                request.getShippingAddress(),
                request.getPhone()
        );

        for (CartItem item : cartItems) {
            orderRepository.addOrderItem(orderId, item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice());
        }

        // 장바구니 비우기
        cartRepository.clearCart(user.getId());

        return ResponseEntity.ok(Map.of(
                "message", "주문 및 결제가 완료되었습니다.",
                "orderId", orderId,
                "chargedAmount", finalAmount,
                "remainingBalance", user.getBalance()
        ));
    }

    /**
     * 내 주문 내역 목록
     */
    @GetMapping
    public ResponseEntity<?> getMyOrders() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<Order> orders = orderRepository.findByUserId(user.getId());
        return ResponseEntity.ok(orders);
    }

    /**
     * [고난도 BOLA / IDOR 결함]
     * 상위 경로 권한 검사가 누락되어 타인의 주문 식별자({id})를 넣으면
     * VIP 고객의 기밀 주문 주소, 연락처, 품목, 배송 추적 코드가 그대로 노출됨.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrderDetail(@PathVariable("id") Long id) {
        // 인증 체크만 수행하고 소유권(ownership) 검증 누락
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(orderOpt.get());
    }

    /**
     * [BOLA / IDOR]: 타인의 주문 배송지 및 수령인 무단 변경
     */
    @PutMapping("/{id}/shipping")
    public ResponseEntity<?> updateShipping(@PathVariable("id") Long id,
                                            @RequestBody OrderDto.UpdateShippingRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();

        // 소유자 확인 없이 변경 실행
        orderRepository.updateShippingInfo(id, request.getShippingAddress(), request.getRecipientName(), request.getPhone());
        return ResponseEntity.ok(Map.of("message", "배송 정보가 성공적으로 변경되었습니다.", "orderId", id));
    }

    /**
     * [고난도 XXE (XML External Entity)]
     * 전자 영수증 XML 파싱 처리 시 외부 DTD 및 엔티티가 활성화되어 있어
     * file:///etc/passwd 또는 서버 내부 리소스 참조 가능
     */
    @PostMapping("/xml-receipt")
    public ResponseEntity<?> parseXmlReceipt(@RequestBody OrderDto.XmlReceiptRequest request) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 취약점: 보안 기능(Disallow Doctype / External Entities)을 비활성화하지 않음
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(request.getXmlData())));

            Element root = doc.getDocumentElement();
            NodeList titleNodes = root.getElementsByTagName("receiptTitle");
            String title = titleNodes.getLength() > 0 ? titleNodes.item(0).getTextContent() : "Electronic Receipt";

            NodeList memoNodes = root.getElementsByTagName("customMemo");
            String memo = memoNodes.getLength() > 0 ? memoNodes.item(0).getTextContent() : "";

            Map<String, Object> result = new HashMap<>();
            result.put("status", "SUCCESS");
            result.put("receiptTitle", title);
            result.put("customMemo", memo);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "XML Parsing Error",
                    "details", e.getMessage()
            ));
        }
    }

    /**
     * [WSTG-INPV-05: Time-Based Blind SQL Injection]
     * 비회원/회원 공용 배송 추적 조회 API
     * 'KR-LOGI-88219' AND SLEEP(3) -- ' 등을 통한 응답 지연 시간차 블라인드 인젝션
     */
    @GetMapping("/track")
    public ResponseEntity<?> trackOrder(@RequestParam("code") String code) {
        List<Order> orders = orderRepository.trackOrderByCode(code);
        if (orders.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(orders.get(0));
    }

    /**
     * [WSTG-BUSL-02: Workflow Step Skipping]
     * 내부 콜백용 결제 확정 API이나 인가 및 결제 검증 누락으로
     * 사용자가 결제 없이 임의의 주문을 'PAID'로 즉시 승인 처리 가능
     */
    @PostMapping("/{id}/direct-confirm")
    public ResponseEntity<?> directConfirmOrder(@PathVariable("id") Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();

        orderRepository.updateStatus(orderId, "PAID");
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "status", "PAID",
                "message", "주문이 결제 확인 완료 상태로 변경되었습니다."
        ));
    }

    /**
     * [WSTG-ATHZ-04: HTTP Parameter Pollution (HPP)]
     * ?status=PENDING&status=PAID 와 같이 중복 파라미터 전송 시
     * 프레임워크 바인딩 특성(마지막 인자 수용)을 악용하여 주문 상태 조작
     */
    @PostMapping("/{id}/status-update")
    public ResponseEntity<?> updateOrderStatusHpp(@PathVariable("id") Long orderId,
                                                  @RequestParam("status") java.util.List<String> statuses) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();

        String effectiveStatus = statuses.get(statuses.size() - 1);
        orderRepository.updateStatus(orderId, effectiveStatus);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "receivedStatuses", statuses,
                "appliedStatus", effectiveStatus,
                "message", "HPP 파라미터 오염을 통해 최종 상태 '" + effectiveStatus + "'가 적용되었습니다."
        ));
    }
}
