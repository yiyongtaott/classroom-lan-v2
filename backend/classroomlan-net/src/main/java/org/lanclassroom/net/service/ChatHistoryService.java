package org.lanclassroom.net.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 聊天历史服务 - 内存缓冲 + 定时落盘到 ./data/chat-history.json。
 *
 * 设计：
 *   - 内存最大保留 {@link #MAX} 条（FIFO）
 *   - dirty 标志位避免无变化时空写文件
 *   - @Scheduled 30 秒刷一次；@PreDestroy 时再刷一次防止丢数据
 *
 * 与持久化数据库的差异：进程重启后内存载回 → 历史可见；
 * 但 Host 切换到不同物理机时数据不会迁移（业务文档 §3.4 允许）。
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    public static final int MAX = 500;
    public static final long FLUSH_INTERVAL_MS = 30_000L;

    @Value("${app.history.dir:./data}")
    private String historyDir;

    private final List<ChatMessage> history = new LinkedList<>();
    private final Object lock = new Object();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean dirty = false;
    private Path file;

    @PostConstruct
    public void init() throws IOException {
        Path dir = Path.of(historyDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        file = dir.resolve("chat-history.json");
        load();
        log.info("[ChatHistory] file={} loaded={}", file, history.size());
    }

    public void append(ChatMessage msg) {
        if (msg == null) return;
        synchronized (lock) {
            history.add(msg);
            while (history.size() > MAX) {
                history.remove(0);
            }
            dirty = true;
        }
    }

    public List<ChatMessage> all() {
        synchronized (lock) {
            return new ArrayList<>(history);
        }
    }

    public void clear() {
        synchronized (lock) {
            history.clear();
            dirty = true;
        }
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void flush() {
        if (!dirty) return;
        List<ChatMessage> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(history);
            dirty = false;
        }
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), snapshot);
            log.debug("[ChatHistory] flushed {} entries", snapshot.size());
        } catch (Exception e) {
            log.warn("[ChatHistory] flush failed: {}", e.getMessage());
            dirty = true; // retry next tick
        }
    }

    @PreDestroy
    public void onShutdown() {
        flush();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            List<ChatMessage> loaded = mapper.readValue(file.toFile(),
                    new TypeReference<List<ChatMessage>>() {});
            if (loaded != null) {
                synchronized (lock) {
                    history.addAll(loaded);
                    while (history.size() > MAX) history.remove(0);
                }
            }
        } catch (Exception e) {
            log.warn("[ChatHistory] load failed: {}", e.getMessage());
        }
    }
}
