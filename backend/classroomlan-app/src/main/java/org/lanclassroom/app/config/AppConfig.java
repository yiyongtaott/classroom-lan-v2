package org.lanclassroom.app.config;

import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.discovery.DiscoveryService;
import org.lanclassroom.net.discovery.HostElector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

/**
 * 应用层装配 - 仅声明跨模块共享 Bean。
 * 不再含任何安全 / Token 配置（业务文档 §1.2 完全移除）。
 */
@Configuration
public class AppConfig {

    @Value("${app.discovery.peer-ttl-ms:6000}")
    private long peerTtlMs;

    @Bean
    public RoomManager roomManager() {
        RoomManager manager = new RoomManager();
        manager.createRoom("default");
        return manager;
    }

    @Bean
    public HostElector hostElector() {
        return new HostElector(NodeIdGenerator.getNodeId(), DiscoveryService.VERSION, peerTtlMs);
    }
}
