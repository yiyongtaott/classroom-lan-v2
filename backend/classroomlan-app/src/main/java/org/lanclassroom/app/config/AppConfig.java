package org.lanclassroom.app.config;

import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.service.ChatService;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.discovery.HostElector;
import org.lanclassroom.net.discovery.TokenService;
import org.lanclassroom.net.service.GameEngine;
import org.lanclassroom.net.ws.ChatWebSocketHandler;
import org.lanclassroom.net.ws.GameWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * 应用配置类 - 装配核心 Service 和 WebSocket 支持
 */
@Configuration
@EnableWebSocketMessageBroker
public class AppConfig implements WebSocketMessageBrokerConfigurer {

    @Bean
    public TokenService tokenService() {
        return new TokenService();
    }

    @Bean
    public HostElector hostElector() {
        return new HostElector();
    }

    @Bean
    public DiscoveryService discoveryService(TokenService tokenService, HostElector hostElector, Room room) throws Exception {
        return new DiscoveryService(hostElector, tokenService, room);
    }

    @Bean
    public GameEngine gameEngine() {
        return new GameEngine();
    }

    @Bean
    public ChatService chatService() {
        return new ChatService();
    }

    @Bean
    public ChatWebSocketHandler chatWebSocketHandler() {
        return new ChatWebSocketHandler();
    }

    @Bean
    public GameWebSocketHandler gameWebSocketHandler() {
        return new GameWebSocketHandler();
    }

    @Bean
    public WebSocketSecurityInterceptor webSocketSecurityInterceptor() {
        return new WebSocketSecurityInterceptor();
    }

    @Bean
    public Room room() {
        return new Room();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(webSocketSecurityInterceptor());
    }

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
}
