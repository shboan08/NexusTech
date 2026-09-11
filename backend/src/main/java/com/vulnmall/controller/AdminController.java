package com.vulnmall.controller;

import com.vulnmall.entity.Order;
import com.vulnmall.entity.User;
import com.vulnmall.repository.OrderRepository;
import com.vulnmall.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public AdminController(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 전체 회원 민감 정보 조회 (관리자 전용)
     * WAF 우회(헤더 스푸핑 또는 JWT Key Confusion 권한 상승) 시 모든 회원의 해시되지 않은 비밀번호와 보안질문 답변 유출
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    /**
     * 전체 주문 및 결제 내역 조회 (기밀 배송지 및 연락처 포함)
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    /**
     * 시스템 상태 진단
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
        return ResponseEntity.ok(metrics);
    }
}
