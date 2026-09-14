package com.vulnmall.repository;

import com.vulnmall.entity.CustomDeck;
import com.vulnmall.entity.Product;
import com.vulnmall.entity.Wishlist;
import org.springframework.dao.EmptyResultDataAccessException;
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
public class WishlistRepository {

    private final JdbcTemplate jdbcTemplate;

    public WishlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Wishlist> wishlistRowMapper = new RowMapper<Wishlist>() {
        @Override
        public Wishlist mapRow(ResultSet rs, int rowNum) throws SQLException {
            Wishlist w = new Wishlist();
            w.setId(rs.getLong("id"));
            w.setUserId(rs.getLong("user_id"));
            w.setProductId(rs.getLong("product_id"));
            w.setProductName(rs.getString("product_name"));
            w.setProductPrice(rs.getBigDecimal("product_price"));
            w.setProductImageUrl(rs.getString("product_image_url"));
            w.setProductCategory(rs.getString("product_category"));
            w.setCreatedAt(rs.getTimestamp("created_at"));
            return w;
        }
    };

    private final RowMapper<CustomDeck> customDeckRowMapper = new RowMapper<CustomDeck>() {
        @Override
        public CustomDeck mapRow(ResultSet rs, int rowNum) throws SQLException {
            CustomDeck d = new CustomDeck();
            d.setId(rs.getLong("id"));
            d.setUserId(rs.getLong("user_id"));
            try {
                d.setUsername(rs.getString("username"));
            } catch (SQLException ignored) {}
            d.setDeckName(rs.getString("deck_name"));
            d.setDescription(rs.getString("description"));
            d.setSecretNote(rs.getString("secret_note"));
            d.setIsPublic(rs.getBoolean("is_public"));
            d.setShareToken(rs.getString("share_token"));
            d.setCreatedAt(rs.getTimestamp("created_at"));
            return d;
        }
    };

    private final RowMapper<Product> productRowMapper = new RowMapper<Product>() {
        @Override
        public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
            Product p = new Product();
            p.setId(rs.getLong("id"));
            p.setName(rs.getString("name"));
            p.setCategory(rs.getString("category"));
            p.setPrice(rs.getBigDecimal("price"));
            p.setStock(rs.getInt("stock"));
            p.setDescription(rs.getString("description"));
            p.setImageUrl(rs.getString("image_url"));
            p.setManualFilename(rs.getString("manual_filename"));
            p.setIsHidden(rs.getBoolean("is_hidden"));
            try {
                p.setIsExclusive(rs.getBoolean("is_exclusive"));
                p.setVipDiscountRate(rs.getInt("vip_discount_rate"));
            } catch (SQLException ignored) {}
            p.setCreatedAt(rs.getTimestamp("created_at"));
            return p;
        }
    };

    // ==========================================
    // 1. Wishlist (찜하기)
    // ==========================================

    public List<Wishlist> findWishlistsByUserId(Long userId) {
        String sql = "SELECT w.id, w.user_id, w.product_id, w.created_at, " +
                "p.name as product_name, p.price as product_price, p.image_url as product_image_url, p.category as product_category " +
                "FROM wishlists w JOIN products p ON w.product_id = p.id " +
                "WHERE w.user_id = ? ORDER BY w.id DESC";
        return jdbcTemplate.query(sql, wishlistRowMapper, userId);
    }

    public boolean isWishlisted(Long userId, Long productId) {
        String sql = "SELECT COUNT(*) FROM wishlists WHERE user_id = ? AND product_id = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, productId);
        return count != null && count > 0;
    }

    public void addWishlist(Long userId, Long productId) {
        if (!isWishlisted(userId, productId)) {
            String sql = "INSERT INTO wishlists (user_id, product_id) VALUES (?, ?)";
            jdbcTemplate.update(sql, userId, productId);
        }
    }

    public void removeWishlist(Long userId, Long productId) {
        String sql = "DELETE FROM wishlists WHERE user_id = ? AND product_id = ?";
        jdbcTemplate.update(sql, userId, productId);
    }

    // ==========================================
    // 2. Custom Decks (커스텀 덱)
    // ==========================================

    public Long createCustomDeck(Long userId, String deckName, String description, String secretNote, boolean isPublic, String shareToken) {
        String sql = "INSERT INTO custom_decks (user_id, deck_name, description, secret_note, is_public, share_token) VALUES (?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, deckName);
            ps.setString(3, description);
            ps.setString(4, secretNote);
            ps.setBoolean(5, isPublic);
            ps.setString(6, shareToken);
            return ps;
        }, keyHolder);
        return keyHolder.getKey() != null ? keyHolder.getKey().longValue() : null;
    }

    public void addDeckItem(Long deckId, Long productId) {
        String sql = "INSERT INTO custom_deck_items (deck_id, product_id) VALUES (?, ?)";
        jdbcTemplate.update(sql, deckId, productId);
    }

    public List<CustomDeck> findDecksByUserId(Long userId) {
        String sql = "SELECT d.*, u.username FROM custom_decks d JOIN users u ON d.user_id = u.id WHERE d.user_id = ? ORDER BY d.id DESC";
        List<CustomDeck> decks = jdbcTemplate.query(sql, customDeckRowMapper, userId);
        for (CustomDeck d : decks) {
            d.setProducts(findDeckProducts(d.getId()));
        }
        return decks;
    }

    public Optional<CustomDeck> findDeckById(Long deckId) {
        String sql = "SELECT d.*, u.username FROM custom_decks d JOIN users u ON d.user_id = u.id WHERE d.id = ?";
        try {
            CustomDeck deck = jdbcTemplate.queryForObject(sql, customDeckRowMapper, deckId);
            if (deck != null) {
                deck.setProducts(findDeckProducts(deck.getId()));
            }
            return Optional.ofNullable(deck);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<CustomDeck> findDeckByShareToken(String shareToken) {
        String sql = "SELECT d.*, u.username FROM custom_decks d JOIN users u ON d.user_id = u.id WHERE d.share_token = ?";
        try {
            CustomDeck deck = jdbcTemplate.queryForObject(sql, customDeckRowMapper, shareToken);
            if (deck != null) {
                deck.setProducts(findDeckProducts(deck.getId()));
            }
            return Optional.ofNullable(deck);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Product> findDeckProducts(Long deckId) {
        String sql = "SELECT p.* FROM products p JOIN custom_deck_items di ON p.id = di.product_id WHERE di.deck_id = ?";
        return jdbcTemplate.query(sql, productRowMapper, deckId);
    }

    public void deleteDeck(Long deckId) {
        jdbcTemplate.update("DELETE FROM custom_deck_items WHERE deck_id = ?", deckId);
        jdbcTemplate.update("DELETE FROM custom_decks WHERE id = ?", deckId);
    }
}
