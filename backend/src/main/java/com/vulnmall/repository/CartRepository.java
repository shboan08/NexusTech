package com.vulnmall.repository;

import com.vulnmall.entity.CartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class CartRepository {

    private final JdbcTemplate jdbcTemplate;

    public CartRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<CartItem> cartRowMapper = new RowMapper<CartItem>() {
        @Override
        public CartItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            CartItem item = new CartItem();
            item.setId(rs.getLong("id"));
            item.setUserId(rs.getLong("user_id"));
            item.setProductId(rs.getLong("product_id"));
            item.setQuantity(rs.getInt("quantity"));
            item.setUnitPrice(rs.getBigDecimal("unit_price"));
            item.setProductName(rs.getString("product_name"));
            item.setProductImageUrl(rs.getString("product_image_url"));
            try {
                item.setNote(rs.getString("note"));
            } catch (Exception ignored) {}
            return item;
        }
    };

    public List<CartItem> findByUserId(Long userId) {
        String sql = "SELECT c.*, p.name AS product_name, p.image_url AS product_image_url " +
                "FROM cart_items c JOIN products p ON c.product_id = p.id " +
                "WHERE c.user_id = ? ORDER BY c.id ASC";
        return jdbcTemplate.query(sql, cartRowMapper, userId);
    }

    public java.util.Optional<CartItem> findById(Long cartItemId) {
        String sql = "SELECT c.*, p.name AS product_name, p.image_url AS product_image_url " +
                "FROM cart_items c JOIN products p ON c.product_id = p.id " +
                "WHERE c.id = ?";
        List<CartItem> list = jdbcTemplate.query(sql, cartRowMapper, cartItemId);
        return list.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(list.get(0));
    }

    public void addItem(Long userId, Long productId, int quantity, java.math.BigDecimal unitPrice, String note) {
        // 이미 담겨있는지 확인
        String checkSql = "SELECT id, quantity FROM cart_items WHERE user_id = ? AND product_id = ?";
        List<CartItem> existing = jdbcTemplate.query(checkSql, (rs, rowNum) -> {
            CartItem ci = new CartItem();
            ci.setId(rs.getLong("id"));
            ci.setQuantity(rs.getInt("quantity"));
            return ci;
        }, userId, productId);

        if (!existing.isEmpty()) {
            CartItem item = existing.get(0);
            int newQty = item.getQuantity() + quantity;
            jdbcTemplate.update("UPDATE cart_items SET quantity = ?, unit_price = ?, note = ? WHERE id = ?",
                    newQty, unitPrice, note, item.getId());
        } else {
            jdbcTemplate.update("INSERT INTO cart_items (user_id, product_id, quantity, unit_price, note) VALUES (?, ?, ?, ?, ?)",
                    userId, productId, quantity, unitPrice, note != null ? note : "");
        }
    }

    public void updateQuantity(Long cartItemId, int quantity) {
        jdbcTemplate.update("UPDATE cart_items SET quantity = ? WHERE id = ?", quantity, cartItemId);
    }

    public void updateNote(Long cartItemId, String note) {
        jdbcTemplate.update("UPDATE cart_items SET note = ? WHERE id = ?", note, cartItemId);
    }

    public void deleteItem(Long cartItemId) {
        jdbcTemplate.update("DELETE FROM cart_items WHERE id = ?", cartItemId);
    }

    public void clearCart(Long userId) {
        jdbcTemplate.update("DELETE FROM cart_items WHERE user_id = ?", userId);
    }
}
