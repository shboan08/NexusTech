package com.vulnmall.controller;

import com.vulnmall.dto.OrderDto;
import com.vulnmall.dto.RefundDto;
import com.vulnmall.entity.CartItem;
import com.vulnmall.entity.Order;
import com.vulnmall.entity.OrderItem;
import com.vulnmall.entity.Product;
import com.vulnmall.entity.User;
import com.vulnmall.repository.CartRepository;
import com.vulnmall.repository.OrderRepository;
import com.vulnmall.repository.ProductRepository;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final com.vulnmall.service.ScoreboardService scoreboardService;
    private final AtomicInteger refundCounter = new AtomicInteger(0);
    private final AtomicInteger checkoutCounter = new AtomicInteger(0);

    public OrderController(OrderRepository orderRepository, CartRepository cartRepository,
                           UserRepository userRepository, ProductRepository productRepository,
                           com.vulnmall.service.ScoreboardService scoreboardService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
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
     * 주문 결제 (Price Tampering, Negative Points, Stock Race Condition 취약점)
     */
    @PostMapping
    public ResponseEntity<?> checkout(@RequestBody OrderDto.CheckoutRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        List<CartItem> cartItems = cartRepository.findByUserId(user.getId());
        if (cartItems.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "장바구니가 비어 있습니다."));
        }

        checkoutCounter.incrementAndGet();

        // 1. 포인트 사용 처리 및 [WSTG-BUSL-09: Negative Points Usage]
        Integer pointsUsed = request.getPointsUsed();
        BigDecimal pointsDiscount = BigDecimal.ZERO;
        if (pointsUsed != null) {
            if (pointsUsed < 0) {
                scoreboardService.markFound("POINTS_NEGATIVE_EXPLOIT");
            }
            if (user.getPoints() != null && pointsUsed > 0 && pointsUsed > user.getPoints()) {
                checkoutCounter.decrementAndGet();
                return ResponseEntity.badRequest().body(Map.of("message", "보유 포인트(" + user.getPoints() + " P)를 초과하여 사용할 수 없습니다."));
            }
            pointsDiscount = BigDecimal.valueOf(pointsUsed);
            userRepository.addPoints(user.getId(), -pointsUsed);
        }

        // 클라이언트 전송 금액 신뢰 (가격 변조 결함)
        BigDecimal finalAmount = request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.valueOf(1000);
        finalAmount = finalAmount.subtract(pointsDiscount);
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }

        // 2. 재고 확인 및 [WSTG-BUSL-04: Stock Overselling Concurrency Race Condition]
        for (CartItem item : cartItems) {
            Optional<Product> prodOpt = productRepository.findById(item.getProductId());
            if (prodOpt.isPresent() && prodOpt.get().getStock() <= 0) {
                checkoutCounter.decrementAndGet();
                return ResponseEntity.badRequest().body(Map.of("message", "선택하신 상품 '" + prodOpt.get().getName() + "'은(는) 현재 품절되었습니다."));
            }
        }

        // TOCTOU 인위적 지연
        try {
            Thread.sleep(60);
        } catch (InterruptedException ignored) {}

        if (user.getBalance().compareTo(finalAmount) < 0) {
            checkoutCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "잔액이 부족합니다. 현재 잔액: ₩" + user.getBalance()));
        }

        // 재고 차감 실행
        for (CartItem item : cartItems) {
            productRepository.deductStock(item.getProductId(), item.getQuantity());
            Optional<Product> prodOpt = productRepository.findById(item.getProductId());
            if (prodOpt.isPresent() && prodOpt.get().getStock() < 0) {
                scoreboardService.markFound("STOCK_RACE_CONDITION");
            }
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

        // 1% 캐시백 자동 적립
        int cashback = finalAmount.multiply(BigDecimal.valueOf(0.01)).intValue();
        if (cashback > 0) {
            userRepository.addPoints(user.getId(), cashback);
        }

        boolean isVip = Boolean.TRUE.equals(user.getMembershipActive());
        BigDecimal shippingFee = isVip ? BigDecimal.ZERO : BigDecimal.valueOf(3000);
        checkoutCounter.decrementAndGet();

        return ResponseEntity.ok(Map.of(
                "message", "주문 및 결제가 완료되었습니다." + (isVip ? " [NEXUS PRIME 무료 배송 혜택 적용]" : "") + (cashback > 0 ? " [+" + cashback + " P 캐시백 적립]" : ""),
                "orderId", orderId,
                "chargedAmount", finalAmount,
                "pointsUsed", pointsUsed != null ? pointsUsed : 0,
                "cashbackEarned", cashback,
                "shippingFee", shippingFee,
                "membershipBenefitApplied", isVip,
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

        Order order = orderOpt.get();
        if (!order.getUserId().equals(user.getId())) {
            scoreboardService.markFound("BOLA_READ");
        }

        return ResponseEntity.ok(order);
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

        Order order = orderOpt.get();
        if (!order.getUserId().equals(user.getId())) {
            scoreboardService.markFound("BOLA_WRITE");
        }

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
        if (request.getXmlData() != null && (request.getXmlData().contains("<!ENTITY") || request.getXmlData().contains("SYSTEM"))) {
            scoreboardService.markFound("XXE");
        }

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
        if (code != null && (code.toUpperCase().contains("SLEEP") || code.toUpperCase().contains("WAITFOR") || code.contains("'"))) {
            scoreboardService.markFound("SQLI_TIME_BLIND");
        }

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

        scoreboardService.markFound("WORKFLOW_SKIP");
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

        if (statuses.size() > 1) {
            scoreboardService.markFound("HPP");
        }

        String effectiveStatus = statuses.get(statuses.size() - 1);
        orderRepository.updateStatus(orderId, effectiveStatus);

        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "receivedStatuses", statuses,
                "appliedStatus", effectiveStatus,
                "message", "HPP 파라미터 오염을 통해 최종 상태 '" + effectiveStatus + "'가 적용되었습니다."
        ));
    }

    /**
     * [WSTG-BUSL-04: Double Refund Concurrency Race Condition]
     * [WSTG-BUSL-02: Workflow Step Skipping (반품 검수 우회)]
     * [WSTG-INPV-02: Stored XSS in Refund Reason]
     * 주문 취소 및 환불 신청 처리 API
     */
    @PostMapping("/{id}/refund")
    public ResponseEntity<?> requestRefund(@PathVariable("id") Long orderId,
                                           @RequestBody(required = false) RefundDto.RefundRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();
        Order order = orderOpt.get();

        int currentRefundReqs = refundCounter.incrementAndGet();

        // 1. [WSTG-BUSL-04: Race Condition] 검증(Check) 단계
        if ("REFUNDED".equalsIgnoreCase(order.getStatus())) {
            refundCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "이미 환불 처리가 완료된 주문입니다."));
        }

        // 2. [WSTG-BUSL-02: Workflow Step Skipping]
        boolean isDelivered = "DELIVERED".equalsIgnoreCase(order.getStatus());
        boolean directBypass = (request != null && Boolean.TRUE.equals(request.getDirect()));
        if (isDelivered && directBypass) {
            scoreboardService.markFound("REFUND_WORKFLOW_BYPASS");
        } else if (isDelivered && !directBypass) {
            refundCounter.decrementAndGet();
            return ResponseEntity.badRequest().body(Map.of("message", "배송 완료된 상품은 회수 및 검수 절차가 필요합니다. (direct=true 우회 필요)"));
        }

        // 3. [WSTG-INPV-02: Stored XSS]
        String reason = (request != null && request.getReason() != null) ? request.getReason() : "고객 변심";
        String details = (request != null && request.getReasonDetails() != null) ? request.getReasonDetails() : "";
        String combinedReason = reason + (details.isEmpty() ? "" : " - " + details);
        String reasonLower = combinedReason.toLowerCase();
        if (reasonLower.contains("<script") || reasonLower.contains("<img") || reasonLower.contains("onerror") || reasonLower.contains("javascript:")) {
            scoreboardService.markFound("REFUND_STORED_XSS");
        }

        // TOCTOU 레이스 윈도우 인위적 지연
        try {
            Thread.sleep(70);
        } catch (InterruptedException ignored) {}

        if (currentRefundReqs > 1) {
            scoreboardService.markFound("REFUND_RACE_CONDITION");
        }

        // 4. 환불 실행 (Act)
        BigDecimal refundAmount = (request != null && request.getRefundAmount() != null) ? request.getRefundAmount() : order.getTotalAmount();
        user.setBalance(user.getBalance().add(refundAmount));
        userRepository.updateUser(user);
        orderRepository.processRefund(orderId, refundAmount, combinedReason);
        refundCounter.decrementAndGet();

        return ResponseEntity.ok(Map.of(
                "message", "환불 처리가 성공적으로 완료되었습니다!",
                "orderId", orderId,
                "refundedAmount", refundAmount,
                "newBalance", user.getBalance(),
                "status", "REFUNDED",
                "refundReason", combinedReason
        ));
    }

    /**
     * [전자 영수증 / 세금계산서 원장 조회]
     * 공식 거래명세서 데이터 및 공급자 인감 정보 반환
     */
    @GetMapping("/{id}/receipt")
    public ResponseEntity<?> getOrderReceipt(@PathVariable("id") Long id) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();

        Order order = orderOpt.get();
        List<OrderItem> items = orderRepository.findItemsByOrderId(id);

        BigDecimal total = order.getTotalAmount();
        BigDecimal supplyValue = total.divide(BigDecimal.valueOf(1.1), 0, java.math.RoundingMode.HALF_UP);
        BigDecimal vat = total.subtract(supplyValue);

        return ResponseEntity.ok(Map.of(
                "receiptNumber", "NEXUS-TAX-" + order.getId() + "-2026",
                "issueDate", order.getCreatedAt() != null ? order.getCreatedAt().toString().substring(0, 10) : LocalDate.now().toString(),
                "supplier", Map.of(
                        "companyName", "NEXUS TECH (주)",
                        "ceo", "ALONA & CJ",
                        "businessNumber", "220-81-99882",
                        "address", "서울특별시 강남구 테헤란로 152 강남파이낸스센터 12층",
                        "tel", "02-1588-9988"
                ),
                "order", order,
                "items", items,
                "financials", Map.of(
                        "supplyValue", supplyValue,
                        "vat", vat,
                        "totalAmount", total
                ),
                "digitalSignature", "SHA256-DIGITAL-SIGN-NEXUS-" + Long.toHexString(order.getId() * 9973L)
        ));
    }
}
