package com.vulnmall.repository;

import com.vulnmall.entity.Inquiry;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InquiryRepository {

    private final JdbcTemplate jdbcTemplate;

    public InquiryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Inquiry> inquiryRowMapper = new RowMapper<Inquiry>() {
        @Override
        public Inquiry mapRow(ResultSet rs, int rowNum) throws SQLException {
            Inquiry inq = new Inquiry();
            inq.setId(rs.getLong("id"));
            inq.setUserId(rs.getLong("user_id"));
            inq.setUsername(rs.getString("username"));
            inq.setTitle(rs.getString("title"));
            inq.setContent(rs.getString("content"));
            inq.setIsSecret(rs.getBoolean("is_secret"));
            try {
                inq.setCategory(rs.getString("category"));
                inq.setOrderId(rs.getObject("order_id") != null ? rs.getLong("order_id") : null);
                inq.setAttachmentUrl(rs.getString("attachment_url"));
                inq.setStatus(rs.getString("status"));
                inq.setAdminReply(rs.getString("admin_reply"));
                inq.setRepliedAt(rs.getTimestamp("replied_at"));
            } catch (SQLException ignored) {}
            inq.setCreatedAt(rs.getTimestamp("created_at"));
            return inq;
        }
    };

    /**
     * 1단계 저장: Prepared Statement로 안전하게 저장 (이 시점에는 SQLi가 발생하지 않음)
     */
    public Long createInquiry(Long userId, String username, String title, String content, boolean isSecret) {
        String sql = "INSERT INTO inquiries (user_id, username, title, content, is_secret) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, userId, username, title, content, isSecret);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public Long createSupportTicket(Long userId, String username, String title, String content, String category, Long orderId, String attachmentUrl, boolean isSecret) {
        String sql = "INSERT INTO inquiries (user_id, username, title, content, category, order_id, attachment_url, status, is_secret) VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)";
        jdbcTemplate.update(sql, userId, username, title, content, category, orderId, attachmentUrl, isSecret);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public List<Inquiry> findAll() {
        return jdbcTemplate.query("SELECT * FROM inquiries ORDER BY id DESC", inquiryRowMapper);
    }

    public List<Inquiry> findByUserId(Long userId) {
        return jdbcTemplate.query("SELECT * FROM inquiries WHERE user_id = ? ORDER BY id DESC", inquiryRowMapper, userId);
    }

    public Optional<Inquiry> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("SELECT * FROM inquiries WHERE id = ?", inquiryRowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void updateAdminReply(Long id, String reply, String status) {
        String sql = "UPDATE inquiries SET admin_reply = ?, status = ?, replied_at = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(sql, reply, status != null ? status : "RESOLVED", id);
    }

    /**
     * [Second-Order SQL Injection Trigger]:
     * 데이터베이스에 이미 저장되어 있던 문의글의 제목(title)을 안전하다고 오인하고
     * 관리자 감사 로그 검색 쿼리에 문자열 직접 결합 실행
     */
    public List<Map<String, Object>> searchAuditLogsByTitle(String title) {
        String sql = "SELECT * FROM audit_logs WHERE details LIKE '%" + title + "%'";
        return jdbcTemplate.queryForList(sql);
    }
}
