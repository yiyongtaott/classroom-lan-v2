package org.lanclassroom.net.ws;

import org.lanclassroom.core.service.Broadcaster;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.context.annotation.Bean;

/**
 * STOMP 消息总线配置。
 *
 * 端点：/ws （SockJS 兼容）
 * 客户端 → /app/*    应用入口
 * 客户端 ← /topic/*  广播频道
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String TOPIC_GAME_STATE = "/topic/game.state";
    public static final String TOPIC_CHAT = "/topic/chat";
    public static final String TOPIC_FILE_PROGRESS = "/topic/file.progress";

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * 把 SimpMessagingTemplate 适配为 core 模块的 Broadcaster 接口，
     * 使 GameSession 不依赖 spring-messaging。
     */
    @Bean
    public Broadcaster gameStateBroadcaster(SimpMessagingTemplate template) {
        return state -> template.convertAndSend(TOPIC_GAME_STATE, state);
    }
}
