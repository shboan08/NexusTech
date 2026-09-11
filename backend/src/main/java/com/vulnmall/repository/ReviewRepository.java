package com.vulnmall.repository;

import com.vulnmall.entity.Review;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ReviewRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReviewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Review> reviewRowMapper = new RowMapper<Review>() {
        @Override
        public Review mapRow(ResultSet rs, int rowNum) throws SQLException {
            Review r = new Review();
            r.setId(rs.getLong("id"));
            r.setProductId(rs.getLong("product_id"));
            r.setUserId(rs.getLong("user_id"));
            r.setUsername(rs.getString("username"));
            r.setRating(rs.getInt("rating"));
            r.setComment(rs.getString("comment")); // Raw unescaped string
            r.setImagePath(rs.getString("image_path"));
            r.setCreatedAt(rs.getTimestamp("created_at"));
            return r;
        }
    };

    public List<Review> findByProductId(Long productId) {
        String sql = "SELECT * FROM reviews WHERE product_id = ? ORDER BY id DESC";
        return jdbcTemplate.query(sql, reviewRowMapper, productId);
    }

    public void addReview(Long productId, Long userId, String username, int rating, String comment, String imagePath) {
        String sql = "INSERT INTO reviews (product_id, user_id, username, rating, comment, image_path) VALUES (?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, productId, userId, username, rating, comment, imagePath);
    }
}
