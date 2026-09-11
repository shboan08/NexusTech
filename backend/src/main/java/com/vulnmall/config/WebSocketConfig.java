package com.vulnmall.config;

import com.vulnmall.websocket.ScoreboardWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ScoreboardWebSocketHandler scoreboardWebSocketHandler;

    public WebSocketConfig(ScoreboardWebSocketHandler scoreboardWebSocketHandler) {
        this.scoreboardWebSocketHandler = scoreboardWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(scoreboardWebSocketHandler, "/ws/scoreboard")
                .setAllowedOriginPatterns("*");
    }
}
