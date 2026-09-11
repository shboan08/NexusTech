package com.vulnmall.repository;

import com.vulnmall.entity.User;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<User> userRowMapper = new RowMapper<User>() {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password"));
            u.setEmail(rs.getString("email"));
            u.setRole(rs.getString("role"));
            u.setBalance(rs.getBigDecimal("balance"));
            u.setAvatarUrl(rs.getString("avatar_url"));
            u.setSecurityQuestion(rs.getString("security_question"));
            u.setSecurityAnswer(rs.getString("security_answer"));
            u.setResetToken(rs.getString("reset_token"));
            u.setCreatedAt(rs.getTimestamp("created_at"));
            return u;
        }
    };

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, username);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(Long id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try {
            User user = jdbcTemplate.queryForObject(sql, userRowMapper, id);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Long createUser(User user) {
        String sql = "INSERT INTO users (username, password, email, role, balance, security_question, security_answer) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                user.getUsername(),
                user.getPassword(),
                user.getEmail(),
                user.getRole() != null ? user.getRole() : "USER",
                user.getBalance() != null ? user.getBalance() : 100000.0,
                user.getSecurityQuestion(),
                user.getSecurityAnswer()
        );
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    public void updateUser(User user) {
        String sql = "UPDATE users SET email = ?, security_question = ?, security_answer = ?, role = ?, balance = ?, avatar_url = ? WHERE id = ?";
        jdbcTemplate.update(sql,
                user.getEmail(),
                user.getSecurityQuestion(),
                user.getSecurityAnswer(),
                user.getRole(),
                user.getBalance(),
                user.getAvatarUrl(),
                user.getId()
        );
    }

    public void updatePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        jdbcTemplate.update(sql, newPassword, username);
    }

    public List<User> findAll() {
        return jdbcTemplate.query("SELECT * FROM users ORDER BY id ASC", userRowMapper);
    }
}
