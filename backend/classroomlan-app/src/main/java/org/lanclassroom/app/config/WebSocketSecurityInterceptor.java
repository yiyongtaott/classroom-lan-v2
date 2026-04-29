package org.lanclassroom.app.config;

import org.lanclassroom.net.discovery.HostElector;
import org.lanclassroom.net.discovery.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息通道拦截器 - 在消息传递前进行权限校验
 */
@Component
public class WebSocketSecurityInterceptor implements ChannelInterceptor {

    @Autowired
    private HostElector hostElector;

    @Autowired
    private TokenService tokenService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        StompCommand command = accessor.getCommand();

        // 对 CONNECT 帧进行鉴权
        if (StompCommand.CONNECT.equals(command)) {
            if (hostElector.isHost()) {
                String token = accessor.getFirstNativeHeader("token");
                String roomKey = accessor.getFirstNativeHeader("roomKey");
                if (roomKey == null || token == null || !tokenService.isValid(roomKey, token)) {
                    throw new IllegalArgumentException("Invalid token or roomKey");
                }
            }
            // TODO: 后续可添加用户认证逻辑（如用户名密码）
        }

        return message;
    }
}
