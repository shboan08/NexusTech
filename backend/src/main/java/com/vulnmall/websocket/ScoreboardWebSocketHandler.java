package com.vulnmall.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DAST 스캐너의 HTTP 트래픽 분석을 완벽하게 우회하기 위한
 * Out-of-Band(OOB) 실시간 스코어보드 웹소켓 핸들러.
 * HTTP 응답 본문/헤더에 플래그를 일절 노출하지 않고 웹소켓 채널로만 은밀하게 전송.
 */
@Component
public class ScoreboardWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ScoreboardWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        logger.info("[WebSocket] New Scoreboard client connected: {}", session.getId());
        try {
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"Scoreboard Live WebSocket Connected\"}"));
        } catch (IOException ignored) {}
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        logger.info("[WebSocket] Scoreboard client disconnected: {}", session.getId());
    }

    /**
     * 취약점 발견 시 웹소켓 클라이언트에 실시간 브로드캐스팅
     */
    public void broadcastVulnFound(String vulnKey, String wstgId, String name, String flag, int foundCount, int total, long percent, String foundAt) {
        String json = String.format(
                "{\"type\":\"VULN_FOUND\",\"vulnKey\":\"%s\",\"wstgId\":\"%s\",\"name\":\"%s\",\"flag\":\"%s\",\"foundCount\":%d,\"total\":%d,\"progressPercent\":%d,\"foundAt\":\"%s\"}",
                escapeJson(vulnKey), escapeJson(wstgId), escapeJson(name), escapeJson(flag), foundCount, total, percent, escapeJson(foundAt)
        );

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    logger.warn("[WebSocket] Failed to send message to session {}", session.getId(), e);
                }
            }
        }
    }

    /**
     * 스코어보드 초기화 시 웹소켓 클라이언트에 브로드캐스팅
     */
    public void broadcastReset() {
        TextMessage message = new TextMessage("{\"type\":\"RESET\",\"message\":\"Scoreboard has been reset\"}");
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {}
            }
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
