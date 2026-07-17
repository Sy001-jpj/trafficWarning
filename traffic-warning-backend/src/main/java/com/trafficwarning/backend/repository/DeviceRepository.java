package com.trafficwarning.backend.repository;

import com.trafficwarning.backend.dto.DeviceView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {

    private static final String DEFAULT_STATUS = "\u79bb\u7ebf";
    private final JdbcTemplate jdbcTemplate;
    private final Random random = new Random();

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DeviceView> findAll() {
        String sql = """
                SELECT id, road_id, name, location, rtsp, video_path, creator, status, created_at
                FROM traffic_device
                ORDER BY id
                """;
        return jdbcTemplate.query(sql, this::mapDevice);
    }

    public Optional<DeviceView> findById(String id) {
        String sql = """
                SELECT id, road_id, name, location, rtsp, video_path, creator, status, created_at
                FROM traffic_device
                WHERE id = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapDevice, id).stream().findFirst();
    }

    public boolean existsById(String id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM traffic_device WHERE id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    public DeviceView create(
            String id,
            Long roadId,
            String name,
            String location,
            String rtsp,
            String videoPath,
            String creator,
            String status
    ) {
        jdbcTemplate.update("""
                INSERT INTO traffic_device (id, road_id, name, location, rtsp, video_path, creator, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, roadId, name, location, rtsp, videoPath, creator, status);
        return findById(id).orElseThrow();
    }

    public Optional<DeviceView> update(
            String id,
            Long roadId,
            String name,
            String location,
            String rtsp,
            String videoPath,
            String creator,
            String status
    ) {
        int updated = jdbcTemplate.update("""
                UPDATE traffic_device
                SET road_id = ?, name = ?, location = ?, rtsp = ?, video_path = ?, creator = ?, status = ?
                WHERE id = ?
                """, roadId, name, location, rtsp, videoPath, creator, status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public Optional<DeviceView> updateStatus(String id, String status) {
        int updated = jdbcTemplate.update("UPDATE traffic_device SET status = ? WHERE id = ?", status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public boolean deleteById(String id) {
        return jdbcTemplate.update("DELETE FROM traffic_device WHERE id = ?", id) > 0;
    }

    public List<DeviceView> checkStatus() {
        List<DeviceView> devices = findAll();
        for (DeviceView device : devices) {
            String status = random.nextDouble() > 0.4 ? "\u5728\u7ebf" : DEFAULT_STATUS;
            jdbcTemplate.update("UPDATE traffic_device SET status = ? WHERE id = ?", status, device.id());
        }
        return findAll();
    }

    private DeviceView mapDevice(ResultSet rs, int rowNum) throws SQLException {
        Long roadId = rs.getObject("road_id", Long.class);
        return new DeviceView(
                rs.getString("id"),
                roadId,
                rs.getString("name"),
                rs.getString("location"),
                rs.getString("rtsp"),
                rs.getString("video_path"),
                rs.getString("creator"),
                rs.getString("status"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}
