package com.vulnmall.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CouponRepository {

    private final JdbcTemplate jdbcTemplate;

    public CouponRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * [WSTG-INPV-05: Boolean-Based Blind SQL Injection]
     * 쿠폰 유효성 검사 쿼리에 문자열 직접 결합.
     * 에러 스택 트레이스는 은폐(catch)되고, 결과 카운트에 따라
     * 오직 'valid: true/false' 논리적 참/거짓 신호만 반환됨.
     */
    public boolean verifyCouponCode(String code) {
        try {
            String sql = "SELECT count(*) FROM coupons WHERE code = '" + code + "' AND is_used = FALSE";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null && count > 0;
        } catch (Exception e) {
            // 에러를 노출하지 않고 참/거짓 신호만 유지
            return false;
        }
    }

    public boolean isCouponUsed(String code) {
        String sql = "SELECT is_used FROM coupons WHERE code = ?";
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, code));
        } catch (Exception e) {
            return true;
        }
    }

    public java.math.BigDecimal getDiscountAmount(String code) {
        String sql = "SELECT discount_amount FROM coupons WHERE code = ?";
        try {
            return jdbcTemplate.queryForObject(sql, java.math.BigDecimal.class, code);
        } catch (Exception e) {
            return java.math.BigDecimal.ZERO;
        }
    }

    public void markCouponUsed(String code) {
        jdbcTemplate.update("UPDATE coupons SET is_used = TRUE WHERE code = ?", code);
    }

    public void createCoupon(String code, java.math.BigDecimal discountAmount) {
        String sql = "INSERT INTO coupons (code, discount_amount, is_used) VALUES (?, ?, FALSE)";
        jdbcTemplate.update(sql, code, discountAmount);
    }
}
