package com.vulnmall.controller;

import com.vulnmall.entity.Order;
import com.vulnmall.entity.Product;
import com.vulnmall.entity.User;
import com.vulnmall.entity.UserAddress;
import com.vulnmall.repository.AddressRepository;
import com.vulnmall.repository.OrderRepository;
import com.vulnmall.repository.ProductRepository;
import com.vulnmall.repository.UserRepository;
import com.vulnmall.service.ScoreboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final AddressRepository addressRepository;
    private final com.vulnmall.repository.InquiryRepository inquiryRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final ScoreboardService scoreboardService;

    public AdminController(UserRepository userRepository,
                           OrderRepository orderRepository,
                           ProductRepository productRepository,
                           AddressRepository addressRepository,
                           com.vulnmall.repository.InquiryRepository inquiryRepository,
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                           ScoreboardService scoreboardService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.addressRepository = addressRepository;
        this.inquiryRepository = inquiryRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.scoreboardService = scoreboardService;
    }

    /**
     * [WSTG-ATHZ-02: Broken Access Control / Admin Portal Bypass]
     * 관리자 권한 확인 및 바이패스 이벤트 기록
     */
    @GetMapping("/check")
    public ResponseEntity<?> checkAdminAccess(@RequestHeader(value = "X-Admin-Role", required = false) String adminHeader,
                                              @RequestHeader(value = "X-Forwarded-For", required = false) String xff) {
        if ("true".equalsIgnoreCase(adminHeader) || "127.0.0.1".equals(xff)) {
            scoreboardService.markFound("ADMIN_BYPASS");
        }
        return ResponseEntity.ok(Map.of("status", "AUTHORIZED", "role", "ADMIN"));
    }

    /**
     * 시스템 상태 및 비즈니스 핵심 지표 진단
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("heapMemoryUsage", memoryBean.getHeapMemoryUsage().getUsed());
        metrics.put("maxHeap", memoryBean.getHeapMemoryUsage().getMax());
        metrics.put("activeThreads", Thread.activeCount());
        metrics.put("osName", System.getProperty("os.name"));
        metrics.put("javaVersion", System.getProperty("java.version"));

        List<User> users = userRepository.findAll();
        List<Order> orders = orderRepository.findAll();
        List<Product> products = productRepository.findAllIncludingHidden();

        BigDecimal totalSales = BigDecimal.ZERO;
        for (Order o : orders) {
            if ("PAID".equalsIgnoreCase(o.getStatus()) || "DELIVERED".equalsIgnoreCase(o.getStatus()) || "SHIPPED".equalsIgnoreCase(o.getStatus())) {
                totalSales = totalSales.add(o.getTotalAmount());
            }
        }

        metrics.put("totalUsers", users.size());
        metrics.put("totalOrders", orders.size());
        metrics.put("totalProducts", products.size());
        metrics.put("totalSales", totalSales);

        return ResponseEntity.ok(metrics);
    }

    // ==========================================
    // 1. 상품 관리 (Product Management)
    // ==========================================

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productRepository.findAllIncludingHidden());
    }

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        if (product.getName() == null || product.getPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "상품명과 가격은 필수 입력 항목입니다."));
        }
        Long id = productRepository.createProduct(product);
        return ResponseEntity.ok(Map.of("message", "상품이 등록되었습니다.", "productId", id));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable("id") Long id, @RequestBody Product product) {
        Optional<Product> prodOpt = productRepository.findById(id);
        if (prodOpt.isEmpty()) return ResponseEntity.notFound().build();

        productRepository.updateProduct(id, product);
        return ResponseEntity.ok(Map.of("message", "상품 정보가 수정되었습니다.", "productId", id));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable("id") Long id) {
        productRepository.deleteProduct(id);
        return ResponseEntity.ok(Map.of("message", "상품이 삭제되었습니다."));
    }

    // ==========================================
    // 2. 회원 관리 (User Management)
    // ==========================================

    /**
     * 전체 회원 민감 정보 조회 (해시되지 않은 비밀번호 및 보안 질문 포함)
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        String newRole = body.get("role");
        if (newRole == null) return ResponseEntity.badRequest().body(Map.of("message", "역할(role)을 입력하세요."));

        userRepository.updateRoleAndBalance(id, newRole, userOpt.get().getBalance());
        return ResponseEntity.ok(Map.of("message", "회원 등급이 '" + newRole + "'로 변경되었습니다.", "userId", id));
    }

    @PutMapping("/users/{id}/balance")
    public ResponseEntity<?> updateUserBalance(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) return ResponseEntity.notFound().build();

        if (body.get("balance") == null) return ResponseEntity.badRequest().body(Map.of("message", "잔액(balance)을 입력하세요."));
        BigDecimal balance = new BigDecimal(body.get("balance").toString());

        userRepository.updateRoleAndBalance(id, userOpt.get().getRole(), balance);
        return ResponseEntity.ok(Map.of("message", "회원 잔액이 " + balance + "원으로 조정되었습니다.", "userId", id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable("id") Long id) {
        userRepository.deleteUser(id);
        return ResponseEntity.ok(Map.of("message", "회원이 삭제되었습니다."));
    }

    // ==========================================
    // 3. 주문 관리 (Order Management)
    // ==========================================

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<?> updateOrderStatus(@PathVariable("id") Long id, @RequestBody Map<String, Object> body) {
        String status = body.get("status") != null ? body.get("status").toString() : null;
        if (status == null) return ResponseEntity.badRequest().body(Map.of("message", "상태(status)를 입력하세요."));

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) return ResponseEntity.notFound().build();
        Order order = orderOpt.get();

        String refundReason = body.get("refundReason") != null ? body.get("refundReason").toString() : order.getRefundReason();
        BigDecimal refundAmount = order.getRefundAmount();
        if (body.get("refundAmount") != null && !body.get("refundAmount").toString().trim().isEmpty()) {
            try {
                refundAmount = new BigDecimal(body.get("refundAmount").toString().trim());
            } catch (Exception ignored) {}
        } else if ("REFUNDED".equalsIgnoreCase(status) && refundAmount == null) {
            refundAmount = order.getTotalAmount();
        }

        if ("REFUNDED".equalsIgnoreCase(status)) {
            if (refundReason == null || refundReason.isEmpty()) {
                refundReason = "관리자 직권 환불 승인";
            }
            orderRepository.processRefund(id, refundAmount != null ? refundAmount : order.getTotalAmount(), refundReason);
        } else {
            orderRepository.updateStatus(id, status);
        }

        return ResponseEntity.ok(Map.of("message", "주문 상태가 '" + status + "'로 변경되었습니다.", "orderId", id));
    }

    @PutMapping("/orders/{id}/shipping")
    public ResponseEntity<?> updateOrderShipping(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String address = body.get("shippingAddress");
        String recipient = body.get("recipientName");
        String phone = body.get("phone");

        orderRepository.updateShippingInfo(id, address, recipient, phone);
        return ResponseEntity.ok(Map.of("message", "배송 정보가 수정되었습니다.", "orderId", id));
    }

    // ==========================================
    // 4. 배송지 원장 관리 (Address Registry)
    // ==========================================

    @GetMapping("/addresses")
    public ResponseEntity<List<UserAddress>> getAllAddresses() {
        return ResponseEntity.ok(addressRepository.findAll());
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<?> deleteAddress(@PathVariable("id") Long id) {
        addressRepository.deleteAddress(id);
        return ResponseEntity.ok(Map.of("message", "배송지 데이터가 삭제되었습니다."));
    }

    // ==========================================
    // 4-1. 1:1 고객지원 헬프데스크 관리 (Helpdesk Tickets)
    // ==========================================

    @GetMapping("/inquiries")
    public ResponseEntity<List<com.vulnmall.entity.Inquiry>> getAllInquiries() {
        return ResponseEntity.ok(inquiryRepository.findAll());
    }

    @PutMapping("/inquiries/{id}/reply")
    public ResponseEntity<?> replyInquiry(@PathVariable("id") Long id, @RequestBody Map<String, String> body) {
        String reply = body.get("adminReply");
        String status = body.getOrDefault("status", "RESOLVED");
        if (reply == null || reply.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "답변 내용을 입력하세요."));
        }

        inquiryRepository.updateAdminReply(id, reply, status);
        return ResponseEntity.ok(Map.of("message", "답변이 등록되었으며 티켓이 종결 처리되었습니다.", "inquiryId", id));
    }

    // ==========================================
    // 4-2. 품절 상품 재고 보충 및 재입고 알림 일괄 발송 (Restock Webhook SSRF)
    // ==========================================

    @PostMapping("/products/{id}/restock-notify")
    public ResponseEntity<?> triggerRestockNotification(
            @PathVariable("id") Long productId,
            @RequestBody(required = false) Map<String, Object> body) {

        Optional<Product> prodOpt = productRepository.findById(productId);
        if (prodOpt.isEmpty()) return ResponseEntity.notFound().build();

        // 등록된 웹훅 구독 목록 조회
        List<Map<String, Object>> subs = jdbcTemplate.queryForList(
                "SELECT * FROM restock_subscriptions WHERE product_id = ? AND is_notified = false",
                productId);

        int sentCount = 0;
        StringBuilder logBuilder = new StringBuilder();

        for (Map<String, Object> sub : subs) {
            String webhookUrl = (String) sub.get("webhook_url");
            Long subId = ((Number) sub.get("id")).longValue();

            if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
                String lower = webhookUrl.toLowerCase();
                if (lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("169.254")
                        || lower.contains("0.0.0.0") || lower.contains("::1") || lower.contains("10.")
                        || lower.contains("192.168.") || lower.contains("172.16.") || lower.contains("internal")
                        || lower.contains("admin")) {
                    scoreboardService.markFound("RESTOCK_WEBHOOK_SSRF");
                }

                // SSRF Webhook HTTP 발송
                try {
                    java.net.URL url = new java.net.URL(webhookUrl);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(2500);
                    conn.setReadTimeout(2500);
                    conn.setRequestProperty("Content-Type", "application/json");
                    String payload = "{\"event\":\"PRODUCT_RESTOCKED\",\"productId\":" + productId + ",\"productName\":\"" + prodOpt.get().getName() + "\"}";
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    int code = conn.getResponseCode();
                    logBuilder.append("Webhook #").append(subId).append(" (").append(webhookUrl).append("): HTTP ").append(code).append("\n");
                } catch (Exception e) {
                    logBuilder.append("Webhook #").append(subId).append(" Error: ").append(e.getMessage()).append("\n");
                }
            }

            jdbcTemplate.update("UPDATE restock_subscriptions SET is_notified = true WHERE id = ?", subId);
            sentCount++;
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "재입고 알림 발송이 완료되었습니다. (총 " + sentCount + "건)",
                "details", logBuilder.toString()
        ));
    }

    // ==========================================
    // 5. 시스템 진단 도구 및 OS 커맨드 실행 (Command Injection)
    // ==========================================

    /**
     * [WSTG-INPV-12: OS Command Injection]
     * 서버 진단 및 네트워크 테스트 도구
     * 입력값에 대한 검증 없이 쉘 명령어를 직접 실행
     */
    @PostMapping("/system/diagnostic")
    public ResponseEntity<?> runSystemDiagnostic(@RequestBody Map<String, String> body) {
        String host = body.get("host");
        String tool = body.get("tool"); // e.g. ping, nslookup, traceroute
        if (host == null || host.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "호스트(host)를 입력하세요."));
        }

        if (tool == null || tool.trim().isEmpty()) {
            tool = "ping";
        }

        if (host.contains(";") || host.contains("|") || host.contains("&") || host.contains("`") || host.contains("$")) {
            scoreboardService.markFound("CMD_INJECTION");
        }

        String isWindows = System.getProperty("os.name").toLowerCase().contains("win") ? "true" : "false";
        StringBuilder output = new StringBuilder();

        try {
            Process process;
            if ("true".equals(isWindows)) {
                String cmd = tool + " -n 2 " + host;
                process = Runtime.getRuntime().exec(new String[]{"cmd.exe", "/c", cmd});
            } else {
                String cmd = tool + " -c 2 " + host;
                process = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", cmd});
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errReader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "command", tool + " " + host,
                    "output", output.toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "ERROR",
                    "error", e.getMessage()
            ));
        }
    }
}
