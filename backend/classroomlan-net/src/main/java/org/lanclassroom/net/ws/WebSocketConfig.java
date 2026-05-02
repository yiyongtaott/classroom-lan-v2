package org.lanclassroom.net.ws;

import org.lanclassroom.core.service.Broadcaster;
import org.lanclassroom.net.service.GameHistoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 消息总线配置。
 *
 * 端点：/ws （纯 WebSocket，与 stompjs 直连）
 * 客户端 → /app/*    应用入口
 * 客户端 ← /topic/*  广播频道
 * 客户端 ← /user/*   单播频道（init 快照、错误等）
 *
 * 握手阶段由 {@link IpHandshakeInterceptor} 抓取 clientIp，写入 session attributes。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // 广播频道
    public static final String TOPIC_GAME_STATE = "/topic/game.state";
    public static final String TOPIC_CHAT = "/topic/chat";
    public static final String TOPIC_FILE_PROGRESS = "/topic/file.progress";
    public static final String TOPIC_FILE_UPLOADED = "/topic/file.uploaded";
    public static final String TOPIC_STATUS = "/topic/status";
    public static final String TOPIC_ROOM = "/topic/room";
    public static final String TOPIC_USER_UPDATE = "/topic/user.update";
    public static final String TOPIC_USER_STATUS = "/topic/user.status";
    public static final String TOPIC_INVITATION = "/topic/game.invitation";
    public static final String TOPIC_INVITATION_STATE = "/topic/game.invitation.state";
    public static final String TOPIC_GAME_START = "/topic/game.start";
    public static final String TOPIC_HOST_CHANGED = "/topic/host";
    public static final String TOPIC_PLAYERS = "/topic/players";

    // 单播频道
    public static final String QUEUE_INIT = "/queue/init";
    public static final String QUEUE_PRIVATE_CHAT = "/queue/private.chat";
    public static final String QUEUE_PRIVATE_INVITE = "/queue/private.invite";
    public static final String QUEUE_DRAW_PRIVATE = "/queue/game.draw";

    private final IpHandshakeInterceptor ipHandshakeInterceptor;

    public WebSocketConfig(IpHandshakeInterceptor ipHandshakeInterceptor) {
        this.ipHandshakeInterceptor = ipHandshakeInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 纯 WebSocket（前端 stompjs 默认协议）。
        // 不使用 SockJS：避免与 stompjs 的裸 ws:// 连接握手不一致。
        registry.addEndpoint("/ws")
                .addInterceptors(ipHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }

    /**
     * 把 SimpMessagingTemplate 适配为 core 模块的 Broadcaster 接口。
     * 同时记入 GameHistoryService（写历史 + 广播）。
     */
    @Bean
    public Broadcaster gameStateBroadcaster(SimpMessagingTemplate template,
                                            GameHistoryService history) {
        return state -> {
            history.append(state);
            template.convertAndSend(TOPIC_GAME_STATE, state);
        };
    }
}
