package com.trafficwarning.backend.repository;

import com.trafficwarning.backend.dto.EventAnalysisStatsView;
import com.trafficwarning.backend.dto.EventView;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class EventRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureAnalysisTypeColumn();
    }

    public List<EventView> findAll() {
        String sql = """
                SELECT id, device_id, camera_name, road_id, road_name, event_type, event_time,
                       snapshot_url, status, analysis_type, congestion_level, vehicles, avg_speed, description
                FROM traffic_event
                ORDER BY event_time DESC, id DESC
                """;
        return jdbcTemplate.query(sql, this::mapEvent);
    }

    public Optional<EventView> findById(Long id) {
        String sql = """
                SELECT id, device_id, camera_name, road_id, road_name, event_type, event_time,
                       snapshot_url, status, analysis_type, congestion_level, vehicles, avg_speed, description
                FROM traffic_event
                WHERE id = ?
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapEvent, id).stream().findFirst();
    }

    public EventView create(
            String deviceId,
            String cameraName,
            Long roadId,
            String roadName,
            String eventType,
            LocalDateTime eventTime,
            String snapshotUrl,
            String status,
            String analysisType,
            String congestionLevel,
            Integer vehicles,
            Double avgSpeed,
            String description
    ) {
        String sql = """
                INSERT INTO traffic_event
                    (device_id, camera_name, road_id, road_name, event_type, event_time,
                     snapshot_url, status, analysis_type, congestion_level, vehicles, avg_speed, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deviceId);
            ps.setString(2, cameraName);
            ps.setObject(3, roadId);
            ps.setString(4, roadName);
            ps.setString(5, eventType);
            ps.setTimestamp(6, Timestamp.valueOf(eventTime));
            ps.setString(7, snapshotUrl);
            ps.setString(8, status);
            ps.setString(9, analysisType);
            ps.setString(10, congestionLevel);
            ps.setObject(11, vehicles);
            ps.setObject(12, avgSpeed);
            ps.setString(13, description);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to create event");
        }
        return findById(key.longValue()).orElseThrow();
    }

    public Optional<EventView> updateStatus(Long id, String status) {
        int updated = jdbcTemplate.update("UPDATE traffic_event SET status = ? WHERE id = ?", status, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    public Optional<EventView> updateAnalysisType(Long id, String analysisType) {
        int updated = jdbcTemplate.update("UPDATE traffic_event SET analysis_type = ? WHERE id = ?", analysisType, id);
        if (updated == 0) {
            return Optional.empty();
        }
        return findById(id);
    }

    /**
     * Check if a recent duplicate event exists for deduplication.
     * @param deviceId device identifier
     * @param eventType type of the event
     * @param withinMinutes time window in minutes
     * @return true if a matching event was created recently
     */
    public boolean existsRecentDuplicate(String deviceId, String eventType, int withinMinutes) {
        String sql = """
                SELECT COUNT(*)
                FROM traffic_event
                WHERE device_id = ?
                  AND event_type = ?
                  AND event_time >= DATE_SUB(NOW(), INTERVAL ? MINUTE)
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, deviceId, eventType, withinMinutes);
        return count != null && count > 0;
    }

    public EventAnalysisStatsView getAnalysisStats() {
        String sql = """
                SELECT
                    SUM(CASE WHEN analysis_type = 'AI分析' THEN 1 ELSE 0 END) AS ai_count,
                    SUM(CASE WHEN analysis_type = '人工分析' THEN 1 ELSE 0 END) AS manual_count,
                    COUNT(*) AS total
                FROM traffic_event
                """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new EventAnalysisStatsView(
                rs.getLong("ai_count"),
                rs.getLong("manual_count"),
                rs.getLong("total")
        ));
    }

    public boolean deleteById(Long id) {
        return jdbcTemplate.update("DELETE FROM traffic_event WHERE id = ?", id) > 0;
    }

    private void ensureAnalysisTypeColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'traffic_event'
                  AND COLUMN_NAME = 'analysis_type'
                """, Integer.class);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE traffic_event ADD COLUMN analysis_type VARCHAR(20) NOT NULL DEFAULT 'AI分析' AFTER status");
        }
    }

    private EventView mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new EventView(
                rs.getLong("id"),
                rs.getString("device_id"),
                rs.getString("camera_name"),
                rs.getObject("road_id", Long.class),
                rs.getString("road_name"),
                rs.getString("event_type"),
                rs.getTimestamp("event_time").toLocalDateTime(),
                rs.getString("snapshot_url"),
                rs.getString("status"),
                rs.getString("analysis_type"),
                rs.getString("congestion_level"),
                rs.getObject("vehicles", Integer.class),
                rs.getObject("avg_speed", Double.class),
                rs.getString("description")
        );
    }
}
