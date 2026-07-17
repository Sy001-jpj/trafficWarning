package com.trafficwarning.backend.repository;

import com.trafficwarning.backend.dto.UserView;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String DEFAULT_ROLE = "\u666e\u901a\u7528\u6237";
    private static final String DEFAULT_STATUS = "\u6b63\u5e38";
    private static final String DEFAULT_PASSWORD = "123456";

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserView> findAll() {
        String sql = """
                SELECT id, username, display_name, phone, email, role, status, created_at
                FROM sys_user
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, this::mapUser);
    }

    public Optional<UserView> findByUsernameAndPassword(String username, String password) {
        String sql = """
                SELECT id, username, display_name, phone, email, role, status, created_at
                FROM sys_user
                WHERE username = ? AND password = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapUser, username, password)
                .stream()
                .findFirst();
    }

    public Optional<UserView> findByUsername(String username) {
        String sql = """
                SELECT id, username, display_name, phone, email, role, status, created_at
                FROM sys_user
                WHERE username = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapUser, username)
                .stream()
                .findFirst();
    }

    public Optional<UserView> findById(Long id) {
        String sql = """
                SELECT id, username, display_name, phone, email, role, status, created_at
                FROM sys_user
                WHERE id = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapUser, id)
                .stream()
                .findFirst();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(*) FROM sys_user WHERE username = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
        return count != null && count > 0;
    }

    public UserView createUser(String username, String password) {
        return createUser(username, password, username, null, null, DEFAULT_ROLE, DEFAULT_STATUS);
    }

    public UserView createUser(
            String username,
            String password,
            String displayName,
            String phone,
            String email,
            String role,
            String status
    ) {
        String sql = """
                INSERT INTO sys_user (username, password, display_name, phone, email, role, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, displayName);
            ps.setString(4, phone);
            ps.setString(5, email);
            ps.setString(6, role);
            ps.setString(7, status);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create user");
        }

        long userId = key.longValue();
        syncRole(userId, role);
        return findByUsername(username).orElseThrow();
    }

    public UserView createManagedUser(
            String username,
            String displayName,
            String phone,
            String email,
            String role,
            String status
    ) {
        return createUser(username, DEFAULT_PASSWORD, displayName, phone, email, role, status);
    }

    public Optional<UserView> updateUser(
            Long id,
            String displayName,
            String phone,
            String email,
            String role,
            String status
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE sys_user
                SET display_name = ?, phone = ?, email = ?, role = ?, status = ?
                WHERE id = ?
                """, displayName, phone, email, role, status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        syncRole(id, role);
        return findById(id);
    }

    public Optional<UserView> updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?", status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public Optional<UserView> resetPassword(Long id) {
        int updated = jdbcTemplate.update("UPDATE sys_user SET password = ? WHERE id = ?", DEFAULT_PASSWORD, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id) > 0;
    }

    private void syncRole(Long userId, String role) {
        long roleId = "\u7ba1\u7406\u5458".equals(role) ? 1L : 2L;
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private UserView mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new UserView(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("role"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
