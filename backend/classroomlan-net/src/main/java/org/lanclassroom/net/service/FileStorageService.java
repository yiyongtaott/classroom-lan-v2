package org.lanclassroom.net.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 文件存储服务 - Host 本地磁盘。
 * 不做持久化数据库；进程重启即丢失元数据（与"无持久保证"一致）。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${app.files.dir:./files}")
    private String storageDir;

    private Path root;
    private final Map<String, FileMeta> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("[FileStorage] root = {}", root);
    }

    public FileMeta store(MultipartFile upload) throws IOException {
        String id = UUID.randomUUID().toString();
        String original = upload.getOriginalFilename() == null ? "unnamed" : upload.getOriginalFilename();
        String safeName = sanitize(original);
        Path target = root.resolve(id + "_" + safeName);
        Files.copy(upload.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        FileMeta meta = new FileMeta(id, original, target.toString(), upload.getSize(), System.currentTimeMillis());
        registry.put(id, meta);
        return meta;
    }

    public List<FileMeta> list() {
        return List.copyOf(registry.values());
    }

    public FileMeta get(String id) {
        return registry.get(id);
    }

    public boolean delete(String id) {
        FileMeta m = registry.remove(id);
        if (m == null) return false;
        try {
            Files.deleteIfExists(Path.of(m.path()));
            return true;
        } catch (IOException e) {
            log.warn("[FileStorage] delete failed: {}", e.getMessage());
            return false;
        }
    }

    public Stream<FileMeta> stream() {
        return registry.values().stream();
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public record FileMeta(String id, String name, String path, long size, long uploadedAt) {}
}
