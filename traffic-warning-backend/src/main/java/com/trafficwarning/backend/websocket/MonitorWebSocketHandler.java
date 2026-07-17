package com.trafficwarning.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for real-time monitor updates.
 * Broadcasts analysis results to all connected frontend clients.
 */
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);
    private static final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    private final ObjectMapper objectMapper;

    public MonitorWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("Monitor WebSocket connected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Monitor WebSocket disconnected: {} (total: {})", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Client pings or requests — echo back or ignore
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error: {}", exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Broadcast a JSON message to all connected clients.
     */
    public void broadcast(Object payload) {
        if (sessions.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.warn("Failed to send to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to serialize WebSocket broadcast", e);
        }
    }

    /**
     * Return the number of connected clients.
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
