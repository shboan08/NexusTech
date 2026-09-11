package com.vulnmall.repository;

import com.vulnmall.entity.Product;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
            p.setCreatedAt(rs.getTimestamp("created_at"));
            return p;
        }
    };

    /**
     * [고난도 실전 SQL Injection]
     * 1. keyword 검색: 단순 LIKE문 직접 연결 (UNION-based / Error-based SQLi)
     * 2. sortBy 정렬: JDBC 바인딩 불가로 인한 동적 ORDER BY 연결.
     *    불완전한 블랙리스트 검사(drop, delete만 차단)로 인해
     *    '(CASE WHEN (SELECT 1)=1 THEN price ELSE id END)' 또는 서브쿼리 기반 Blind SQLi 가능.
     */
    public List<Product> searchProducts(String keyword, String category, String sortBy, String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT * FROM products WHERE is_hidden = FALSE");

        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            sql.append(" AND category = '").append(category.replace("'", "''")).append("'");
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            // 취약한 문자열 연결
            sql.append(" AND (name LIKE '%").append(keyword).append("%' OR description LIKE '%").append(keyword).append("%')");
        }

        // 정렬 취약점
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            // 미흡한 블랙리스트 검증 (시니어 모의해커 분석 대상)
            String lowerSort = sortBy.toLowerCase();
            if (!lowerSort.contains("drop ") && !lowerSort.contains("delete ")) {
                String direction = "DESC".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
                sql.append(" ORDER BY ").append(sortBy).append(" ").append(direction);
            } else {
                sql.append(" ORDER BY id ASC");
            }
        } else {
            sql.append(" ORDER BY id ASC");
        }

        return jdbcTemplate.query(sql.toString(), productRowMapper);
    }

    public Optional<Product> findById(Long id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try {
            Product p = jdbcTemplate.queryForObject(sql, productRowMapper, id);
            return Optional.ofNullable(p);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Product> findAllIncludingHidden() {
        return jdbcTemplate.query("SELECT * FROM products ORDER BY id ASC", productRowMapper);
    }

    /**
     * [WSTG-INPV-05: Union-Based SQL Injection]
     * 카테고리 필터링 쿼리에 직접 결합하여 타 테이블(users, coupons 등)과 UNION 결합 허용
     * 5개 컬럼(id, name, category, price, description)을 추출하는 쿼리
     */
    public List<java.util.Map<String, Object>> filterByCategoryUnion(String category) {
        String sql = "SELECT id, name, category, price, description FROM products WHERE is_hidden = FALSE AND category = '" + category + "'";
        return jdbcTemplate.queryForList(sql);
    }
}
