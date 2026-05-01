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
import java.util.Map;

/**
 * 游戏状态历史服务 - 内存缓冲 + 定时落盘到 ./data/game-history.json。
 *
 * 元素是 GameSession 广播出来的 envelope（{gameType, stage, data, ts, ...}）。
 * 不区分游戏轮次 — 简单线性日志。
 */
@Service
public class GameHistoryService {

    private static final Logger log = LoggerFactory.getLogger(GameHistoryService.class);
    public static final int MAX = 500;
    public static final long FLUSH_INTERVAL_MS = 30_000L;

    @Value("${app.history.dir:./data}")
    private String historyDir;

    private final List<Map<String, Object>> history = new LinkedList<>();
    private final Object lock = new Object();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean dirty = false;
    private Path file;

    @PostConstruct
    public void init() throws IOException {
        Path dir = Path.of(historyDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        file = dir.resolve("game-history.json");
        load();
        log.info("[GameHistory] file={} loaded={}", file, history.size());
    }

    @SuppressWarnings("unchecked")
    public void append(Object envelope) {
        if (envelope == null) return;
        Map<String, Object> entry;
        if (envelope instanceof Map<?, ?> m) {
            entry = (Map<String, Object>) m;
        } else {
            // 兜底：用 Jackson 转一遍
            entry = mapper.convertValue(envelope, new TypeReference<Map<String, Object>>() {});
        }
        synchronized (lock) {
            history.add(entry);
            while (history.size() > MAX) {
                history.remove(0);
            }
            dirty = true;
        }
    }

    public List<Map<String, Object>> all() {
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
        List<Map<String, Object>> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(history);
            dirty = false;
        }
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), snapshot);
            log.debug("[GameHistory] flushed {} entries", snapshot.size());
        } catch (Exception e) {
            log.warn("[GameHistory] flush failed: {}", e.getMessage());
            dirty = true;
        }
    }

    @PreDestroy
    public void onShutdown() {
        flush();
    }

    private void load() {
        if (!Files.exists(file)) return;
        try {
            List<Map<String, Object>> loaded = mapper.readValue(file.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {});
            if (loaded != null) {
                synchronized (lock) {
                    history.addAll(loaded);
                    while (history.size() > MAX) history.remove(0);
                }
            }
        } catch (Exception e) {
            log.warn("[GameHistory] load failed: {}", e.getMessage());
        }
    }
}
