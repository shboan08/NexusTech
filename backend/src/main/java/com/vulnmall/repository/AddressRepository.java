package com.vulnmall.repository;

import com.vulnmall.entity.UserAddress;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class AddressRepository {

    private final JdbcTemplate jdbcTemplate;

    public AddressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<UserAddress> addressRowMapper = new RowMapper<UserAddress>() {
        @Override
        public UserAddress mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserAddress addr = new UserAddress();
            addr.setId(rs.getLong("id"));
            addr.setUserId(rs.getLong("user_id"));
            addr.setRecipientName(rs.getString("recipient_name"));
            addr.setPhone(rs.getString("phone"));
            addr.setPostalCode(rs.getString("postal_code"));
            addr.setAddressLine1(rs.getString("address_line1"));
            addr.setAddressLine2(rs.getString("address_line2"));
            addr.setDeliveryMemo(rs.getString("delivery_memo"));
            addr.setDefault(rs.getBoolean("is_default"));
            addr.setCreatedAt(rs.getTimestamp("created_at"));
            return addr;
        }
    };

    public List<UserAddress> findByUserId(Long userId) {
        String sql = "SELECT * FROM user_addresses WHERE user_id = ? ORDER BY is_default DESC, id DESC";
        return jdbcTemplate.query(sql, addressRowMapper, userId);
    }

    public Optional<UserAddress> findById(Long id) {
        String sql = "SELECT * FROM user_addresses WHERE id = ?";
        List<UserAddress> list = jdbcTemplate.query(sql, addressRowMapper, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public List<UserAddress> findAll() {
        String sql = "SELECT * FROM user_addresses ORDER BY id DESC";
        return jdbcTemplate.query(sql, addressRowMapper);
    }

    public Long createAddress(Long userId, String recipientName, String phone, String postalCode,
                              String addressLine1, String addressLine2, String deliveryMemo, boolean isDefault) {
        if (isDefault) {
            jdbcTemplate.update("UPDATE user_addresses SET is_default = FALSE WHERE user_id = ?", userId);
        }
        String sql = "INSERT INTO user_addresses (user_id, recipient_name, phone, postal_code, address_line1, address_line2, delivery_memo, is_default) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, recipientName);
            ps.setString(3, phone);
            ps.setString(4, postalCode);
            ps.setString(5, addressLine1);
            ps.setString(6, addressLine2);
            ps.setString(7, deliveryMemo);
            ps.setBoolean(8, isDefault);
            return ps;
        }, keyHolder);

        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public void updateAddress(Long id, String recipientName, String phone, String postalCode,
                              String addressLine1, String addressLine2, String deliveryMemo, boolean isDefault, Long userId) {
        if (isDefault && userId != null) {
            jdbcTemplate.update("UPDATE user_addresses SET is_default = FALSE WHERE user_id = ?", userId);
        }
        String sql = "UPDATE user_addresses SET recipient_name = ?, phone = ?, postal_code = ?, address_line1 = ?, " +
                "address_line2 = ?, delivery_memo = ?, is_default = ? WHERE id = ?";
        jdbcTemplate.update(sql, recipientName, phone, postalCode, addressLine1, addressLine2, deliveryMemo, isDefault, id);
    }

    public void deleteAddress(Long id) {
        jdbcTemplate.update("DELETE FROM user_addresses WHERE id = ?", id);
    }

    /**
     * 주소 및 우편번호 검색 (WSTG-INPV-05: SQL Injection 취약점)
     */
    public List<UserAddress> searchAddressesSql(String keyword) {
        // 문자열 단순 결합으로 SQL Injection 발생
        String sql = "SELECT * FROM user_addresses WHERE address_line1 LIKE '%" + keyword + "%' OR postal_code LIKE '%" + keyword + "%'";
        return jdbcTemplate.query(sql, addressRowMapper);
    }
}
