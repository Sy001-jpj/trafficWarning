package com.trafficwarning.backend.config;

import com.trafficwarning.backend.websocket.MonitorWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MonitorWebSocketHandler monitorHandler;

    public WebSocketConfig(MonitorWebSocketHandler monitorHandler) {
        this.monitorHandler = monitorHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitorHandler, "/ws/monitor")
                .setAllowedOriginPatterns("*");
    }
}
