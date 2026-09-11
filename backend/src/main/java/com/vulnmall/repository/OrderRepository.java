package com.vulnmall.repository;

import com.vulnmall.entity.Order;
import com.vulnmall.entity.OrderItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Order> orderRowMapper = new RowMapper<Order>() {
        @Override
        public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
            Order o = new Order();
            o.setId(rs.getLong("id"));
            o.setUserId(rs.getLong("user_id"));
            o.setTotalAmount(rs.getBigDecimal("total_amount"));
            o.setRecipientName(rs.getString("recipient_name"));
            o.setShippingAddress(rs.getString("shipping_address"));
            o.setPhone(rs.getString("phone"));
            o.setStatus(rs.getString("status"));
            o.setTrackingCode(rs.getString("tracking_code"));
            o.setCreatedAt(rs.getTimestamp("created_at"));
            return o;
        }
    };

    private final RowMapper<OrderItem> orderItemRowMapper = new RowMapper<OrderItem>() {
        @Override
        public OrderItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            OrderItem oi = new OrderItem();
            oi.setId(rs.getLong("id"));
            oi.setOrderId(rs.getLong("order_id"));
            oi.setProductId(rs.getLong("product_id"));
            oi.setProductName(rs.getString("product_name"));
            oi.setQuantity(rs.getInt("quantity"));
            oi.setUnitPrice(rs.getBigDecimal("unit_price"));
            return oi;
        }
    };

    public Long createOrder(Long userId, BigDecimal totalAmount, String recipientName, String address, String phone) {
        String trackingCode = "KR-LOGI-" + (System.currentTimeMillis() % 1000000);
        String sql = "INSERT INTO orders (user_id, total_amount, recipient_name, shipping_address, phone, status, tracking_code) VALUES (?, ?, ?, ?, ?, 'PAID', ?)";
        jdbcTemplate.update(sql, userId, totalAmount, recipientName, address, phone, trackingCode);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void addOrderItem(Long orderId, Long productId, String productName, int quantity, BigDecimal unitPrice) {
        String sql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, orderId, productId, productName, quantity, unitPrice);
    }

    public Optional<Order> findById(Long orderId) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try {
            Order order = jdbcTemplate.queryForObject(sql, orderRowMapper, orderId);
            if (order != null) {
                order.setItems(findItemsByOrderId(orderId));
            }
            return Optional.ofNullable(order);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<OrderItem> findItemsByOrderId(Long orderId) {
        String sql = "SELECT * FROM order_items WHERE order_id = ?";
        return jdbcTemplate.query(sql, orderItemRowMapper, orderId);
    }

    public List<Order> findByUserId(Long userId) {
        String sql = "SELECT * FROM orders WHERE user_id = ? ORDER BY id DESC";
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper, userId);
        for (Order o : orders) {
            o.setItems(findItemsByOrderId(o.getId()));
        }
        return orders;
    }

    public List<Order> findAll() {
        String sql = "SELECT * FROM orders ORDER BY id DESC";
        List<Order> orders = jdbcTemplate.query(sql, orderRowMapper);
        for (Order o : orders) {
            o.setItems(findItemsByOrderId(o.getId()));
        }
        return orders;
    }

    public void updateShippingInfo(Long orderId, String address, String recipientName, String phone) {
        String sql = "UPDATE orders SET shipping_address = ?, recipient_name = ?, phone = ? WHERE id = ?";
        jdbcTemplate.update(sql, address, recipientName, phone, orderId);
    }

    public void updateStatus(Long orderId, String status) {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        jdbcTemplate.update(sql, status, orderId);
    }

    /**
     * [WSTG-INPV-05: Time-Based Blind SQL Injection]
     * 배송 추적 코드 조회 시 문자열 직접 결합 및 지연 함수(SLEEP 등) 실행 허용
     */
    public List<Order> trackOrderByCode(String code) {
        String sql = "SELECT * FROM orders WHERE tracking_code = '" + code + "'";
        return jdbcTemplate.query(sql, orderRowMapper);
    }
}
