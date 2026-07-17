package com.trafficwarning.backend.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trafficwarning.backend.dto.AnalysisView;
import com.trafficwarning.backend.dto.MonitorDeviceView;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class AnalysisRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AnalysisRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<AnalysisView> findAll() {
        String sql = """
                SELECT id, device_id, road_id, motor_count, non_motor_count, avg_speed,
                       congestion_level, events, analysis_time
                FROM traffic_analysis
                ORDER BY analysis_time DESC, id DESC
                """;
        return jdbcTemplate.query(sql, this::mapAnalysis);
    }

    public List<MonitorDeviceView> findMonitorDevices() {
        String sql = """
                SELECT d.id, d.name, d.location, d.status, d.video_path,
                       a.motor_count, a.non_motor_count, a.avg_speed, a.congestion_level,
                       a.events, a.analysis_time, a.snapshot_url
                FROM traffic_device d
                LEFT JOIN (
                    SELECT a1.*
                    FROM traffic_analysis a1
                    INNER JOIN (
                        SELECT device_id, MAX(analysis_time) AS latest_time
                        FROM traffic_analysis
                        WHERE device_id IS NOT NULL
                        GROUP BY device_id
                    ) latest
                    ON a1.device_id = latest.device_id
                    AND a1.analysis_time = latest.latest_time
                ) a ON d.id = a.device_id
                ORDER BY d.id
                """;
        return jdbcTemplate.query(sql, this::mapMonitorDevice);
    }

    public List<AnalysisView> findLatestByDeviceId(String deviceId) {
        String sql = """
                SELECT id, device_id, road_id, motor_count, non_motor_count, avg_speed,
                       congestion_level, events, analysis_time
                FROM traffic_analysis
                WHERE device_id = ?
                ORDER BY analysis_time DESC, id DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, this::mapAnalysis, deviceId);
    }

    private AnalysisView mapAnalysis(ResultSet rs, int rowNum) throws SQLException {
        return new AnalysisView(
                rs.getLong("id"),
                rs.getString("device_id"),
                rs.getObject("road_id", Long.class),
                rs.getObject("motor_count", Integer.class),
                rs.getObject("non_motor_count", Integer.class),
                rs.getObject("avg_speed", Double.class),
                rs.getString("congestion_level"),
                parseEvents(rs.getString("events")),
                rs.getTimestamp("analysis_time").toLocalDateTime()
        );
    }

    private MonitorDeviceView mapMonitorDevice(ResultSet rs, int rowNum) throws SQLException {
        Timestamp analysisTime = rs.getTimestamp("analysis_time");
        LocalDateTime lastUpdate = analysisTime == null ? null : analysisTime.toLocalDateTime();
        Integer motorCount = defaultZero(rs.getObject("motor_count", Integer.class));
        Integer nonMotorCount = defaultZero(rs.getObject("non_motor_count", Integer.class));
        Double avgSpeed = rs.getObject("avg_speed", Double.class);
        return new MonitorDeviceView(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("location"),
                rs.getString("status"),
                rs.getString("video_path"),
                motorCount,
                nonMotorCount,
                avgSpeed == null ? 0D : avgSpeed,
                rs.getString("congestion_level"),
                parseEvents(rs.getString("events")),
                lastUpdate,
                rs.getString("snapshot_url")
        );
    }

    private Integer defaultZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> parseEvents(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public void save(
            String deviceId,
            Long roadId,
            int motorCount,
            int nonMotorCount,
            double avgSpeed,
            String congestionLevel,
            String eventsJson,
            String snapshotUrl
    ) {
        String sql = """
                INSERT INTO traffic_analysis
                    (device_id, road_id, motor_count, non_motor_count, avg_speed,
                     congestion_level, events, snapshot_url, analysis_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, deviceId);
            ps.setObject(2, roadId);
            ps.setInt(3, motorCount);
            ps.setInt(4, nonMotorCount);
            ps.setDouble(5, avgSpeed);
            ps.setString(6, congestionLevel);
            ps.setString(7, eventsJson);
            ps.setString(8, snapshotUrl);
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        });
    }

    /**
     * Return historical analysis records for a device within the given time window.
     */
    public List<AnalysisView> findHistory(String deviceId, int minutes) {
        String sql = """
                SELECT id, device_id, road_id, motor_count, non_motor_count, avg_speed,
                       congestion_level, events, analysis_time
                FROM traffic_analysis
                WHERE device_id = ?
                  AND analysis_time >= ?
                ORDER BY analysis_time DESC, id DESC
                LIMIT 200
                """;
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        return jdbcTemplate.query(sql, this::mapAnalysis, deviceId, Timestamp.valueOf(since));
    }
}
