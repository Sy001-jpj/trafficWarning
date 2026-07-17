package com.trafficwarning.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trafficwarning.backend.dto.TrafficNetworkView;
import com.trafficwarning.backend.dto.TrafficPointView;
import com.trafficwarning.backend.dto.TrafficRoadView;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AmapTrafficService {

    private static final String AMAP_TRAFFIC_URL = "https://restapi.amap.com/v3/traffic/status/rectangle";

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${traffic-warning.amap-key:}")
    private String amapKey;

    @Value("${traffic-warning.amap-rectangle:116.3000,39.8500;116.5200,40.0200}")
    private String amapRectangle;

    public AmapTrafficService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TrafficNetworkView getRoadNetwork() {
        if (amapKey != null && !amapKey.isBlank()) {
            try {
                TrafficNetworkView amap = fetchFromAmap();
                if (!amap.roads().isEmpty()) {
                    return amap;
                }
            } catch (Exception ignored) {
                // Fall back to local demo network when AMap is unavailable.
            }
        }
        return fallbackNetwork();
    }

    private TrafficNetworkView fetchFromAmap() throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(AMAP_TRAFFIC_URL)
                .queryParam("key", amapKey)
                .queryParam("rectangle", amapRectangle)
                .queryParam("extensions", "all")
                .build(false)
                .toUri();
        String body = restTemplate.getForObject(uri, String.class);
        JsonNode root = objectMapper.readTree(body);
        JsonNode roadsNode = root.path("trafficinfo").path("roads");
        List<TrafficRoadView> roads = new ArrayList<>();
        if (roadsNode.isArray()) {
            for (JsonNode road : roadsNode) {
                List<TrafficPointView> points = parsePolyline(road.path("polyline").asText(""));
                if (points.size() < 2) {
                    continue;
                }
                String status = road.path("status").asText("畅通");
                roads.add(new TrafficRoadView(
                        road.path("name").asText("未命名道路"),
                        mapLevel(status),
                        mapValue(status, road.path("speed").asDouble(0D)),
                        mapTone(status),
                        points
                ));
            }
        }
        roads.sort(Comparator.comparing(TrafficRoadView::value).reversed());
        return new TrafficNetworkView("amap", LocalDateTime.now().toString(), roads);
    }

    private List<TrafficPointView> parsePolyline(String polyline) {
        List<TrafficPointView> points = new ArrayList<>();
        for (String pair : polyline.split(";")) {
            String[] parts = pair.split(",");
            if (parts.length != 2) {
                continue;
            }
            try {
                points.add(new TrafficPointView(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
            } catch (NumberFormatException ignored) {
                // Skip malformed coordinate.
            }
        }
        return points;
    }

    private String mapLevel(String status) {
        return switch (status) {
            case "严重拥堵" -> "严重拥堵";
            case "拥堵" -> "拥堵";
            case "缓行" -> "缓行";
            default -> "畅通";
        };
    }

    private Integer mapValue(String status, Double speed) {
        return switch (status) {
            case "严重拥堵" -> 92;
            case "拥堵" -> 76;
            case "缓行" -> 52;
            default -> speed != null && speed > 0 ? Math.max(8, Math.min(35, (int) Math.round(40 - speed))) : 18;
        };
    }

    private String mapTone(String status) {
        return switch (status) {
            case "严重拥堵" -> "red";
            case "拥堵" -> "orange";
            case "缓行" -> "yellow";
            default -> "green";
        };
    }

    /**
     * 使用南京市真实 GPS 坐标的回退路网数据。
     * 当高德 API Key 未配置或调用失败时使用。
     * 坐标范围：lng 118.70-118.90, lat 31.95-32.12
     */
    private TrafficNetworkView fallbackNetwork() {
        List<TrafficRoadView> roads = List.of(
                // 中山东路 — 东西向主干道，严重拥堵
                new TrafficRoadView("中山东路", "严重拥堵", 88, "red", List.of(
                        new TrafficPointView(118.7700, 32.0420),
                        new TrafficPointView(118.7850, 32.0420),
                        new TrafficPointView(118.8000, 32.0415),
                        new TrafficPointView(118.8150, 32.0410),
                        new TrafficPointView(118.8300, 32.0405)
                )),
                // 玄武大道 — 东西向，拥堵
                new TrafficRoadView("玄武大道", "拥堵", 73, "orange", List.of(
                        new TrafficPointView(118.7900, 32.0780),
                        new TrafficPointView(118.8050, 32.0770),
                        new TrafficPointView(118.8200, 32.0760),
                        new TrafficPointView(118.8350, 32.0750),
                        new TrafficPointView(118.8500, 32.0740),
                        new TrafficPointView(118.8650, 32.0730)
                )),
                // 龙蟠中路 — 南北向，缓行
                new TrafficRoadView("龙蟠中路", "缓行", 52, "yellow", List.of(
                        new TrafficPointView(118.8050, 32.0100),
                        new TrafficPointView(118.8060, 32.0250),
                        new TrafficPointView(118.8070, 32.0400),
                        new TrafficPointView(118.8080, 32.0550),
                        new TrafficPointView(118.8090, 32.0700)
                )),
                // 中山南路 — 南北向，畅通
                new TrafficRoadView("中山南路", "畅通", 18, "green", List.of(
                        new TrafficPointView(118.7820, 31.9950),
                        new TrafficPointView(118.7825, 32.0050),
                        new TrafficPointView(118.7830, 32.0150),
                        new TrafficPointView(118.7835, 32.0250),
                        new TrafficPointView(118.7840, 32.0350)
                )),
                // 北京东路 — 东西向，畅通
                new TrafficRoadView("北京东路", "畅通", 22, "green", List.of(
                        new TrafficPointView(118.7850, 32.0580),
                        new TrafficPointView(118.7950, 32.0580),
                        new TrafficPointView(118.8050, 32.0575),
                        new TrafficPointView(118.8150, 32.0570)
                )),
                // 应天大街 — 快速路，缓行
                new TrafficRoadView("应天大街", "缓行", 49, "yellow", List.of(
                        new TrafficPointView(118.7500, 31.9980),
                        new TrafficPointView(118.7700, 31.9980),
                        new TrafficPointView(118.7900, 31.9975),
                        new TrafficPointView(118.8100, 31.9970),
                        new TrafficPointView(118.8300, 31.9965)
                ))
        );
        return new TrafficNetworkView("fallback", LocalDateTime.now().toString(), roads);
    }

    /** 保留旧的辅助方法，供后续测试使用 */
    @SuppressWarnings("unused")
    private TrafficRoadView road(String name, String level, Integer value, String tone, double x1, double y1, double x2, double y2) {
        return new TrafficRoadView(name, level, value, tone, List.of(
                new TrafficPointView(x1, y1),
                new TrafficPointView((x1 + x2) / 2, (y1 + y2) / 2),
                new TrafficPointView(x2, y2)
        ));
    }
}
