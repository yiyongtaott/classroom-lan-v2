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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 头像存储服务（Bug 1 调整）：
 *   - 文件存到 ./avatars/&lt;原文件名&gt;，同名直接覆盖
 *   - 维护用户名 → 文件名映射，持久化到 ./data/avatar-map.json
 *   - 改名时调用 {@link #renameUser(String, String)} 把映射迁移
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);
    public static final long FLUSH_INTERVAL_MS = 30_000L;

    @Value("${app.avatars.dir:./avatars}")
    private String storageDir;

    @Value("${app.history.dir:./data}")
    private String mapDir;

    private Path root;
    private Path mapFile;
    private final Map<String, String> nameToFile = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile boolean dirty = false;

    @PostConstruct
    public void init() throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path dataDir = Path.of(mapDir).toAbsolutePath().normalize();
        Files.createDirectories(dataDir);
        this.mapFile = dataDir.resolve("avatar-map.json");
        load();
        log.info("[AvatarStorage] root={} mapFile={} loadedEntries={}", root, mapFile, nameToFile.size());
    }

    /** 用上传文件原名保存，同名覆盖。 */
    public String storeFor(String userName, MultipartFile upload) throws IOException {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName required");
        }
        String original = upload.getOriginalFilename();
        if (original == null || original.isBlank()) {
            original = "avatar.png";
        }
        String safe = sanitize(original);
        Path target = root.resolve(safe);
        Files.copy(upload.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        nameToFile.put(userName, safe);
        dirty = true;
        return urlOf(safe);
    }

    /** 改名时迁移头像绑定。 */
    public void renameUser(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.equals(newName)) return;
        String fname = nameToFile.remove(oldName);
        if (fname != null) {
            nameToFile.put(newName, fname);
            dirty = true;
        }
    }

    /** 按用户名查头像 URL。 */
    public Optional<String> avatarUrlOf(String userName) {
        String fname = nameToFile.get(userName);
        if (fname == null) return Optional.empty();
        Path p = root.resolve(fname);
        return Files.exists(p) ? Optional.of(urlOf(fname)) : Optional.empty();
    }

    /** 按文件名读取磁盘文件。 */
    public Optional<Path> findByFilename(String filename) {
        if (filename == null) return Optional.empty();
        Path p = root.resolve(sanitize(filename));
        if (!p.normalize().startsWith(root)) return Optional.empty(); // 防穿越
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }

    /** 解绑（用户移除头像）。 */
    public void clearFor(String userName) {
        if (userName == null) return;
        if (nameToFile.remove(userName) != null) {
            dirty = true;
        }
    }

    private static String urlOf(String safeFilename) {
        return "/api/avatars/file/" + safeFilename;
    }

    private static String sanitize(String name) {
        // 去掉路径分隔符 + 控制字符；保留中文、字母、数字、点、空格、连字符、下划线
        String n = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").trim();
        if (n.isEmpty()) n = "avatar.png";
        return n;
    }

    @Scheduled(fixedRate = FLUSH_INTERVAL_MS)
    public void flush() {
        if (!dirty) return;
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(mapFile.toFile(), nameToFile);
            dirty = false;
        } catch (Exception e) {
            log.warn("[AvatarStorage] flush failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void onShutdown() {
        flush();
    }

    private void load() {
        if (!Files.exists(mapFile)) return;
        try {
            Map<String, String> loaded = mapper.readValue(mapFile.toFile(),
                    new TypeReference<Map<String, String>>() {});
            if (loaded != null) nameToFile.putAll(loaded);
        } catch (Exception e) {
            log.warn("[AvatarStorage] load failed: {}", e.getMessage());
        }
    }
}
