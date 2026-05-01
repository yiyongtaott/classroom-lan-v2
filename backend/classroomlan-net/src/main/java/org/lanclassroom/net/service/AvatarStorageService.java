package org.lanclassroom.net.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 头像存储服务 - Host 本地磁盘 ./avatars/。
 * 文件名 = playerId 原文（保证一对一），扩展名取自上传文件。
 * 进程重启后头像仍在磁盘上，但 Player 对象会丢失（Host 无持久化）。
 */
@Service
public class AvatarStorageService {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorageService.class);

    @Value("${app.avatars.dir:./avatars}")
    private String storageDir;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        this.root = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("[AvatarStorage] root = {}", root);
    }

    /** 保存上传头像，返回访问 URL（相对路径）。 */
    public String store(String playerId, MultipartFile upload) throws IOException {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId required");
        }
        // 清理同 id 旧文件（不同扩展名）
        deleteByPlayerId(playerId);

        String ext = extOf(upload.getOriginalFilename());
        Path target = root.resolve(playerId + ext);
        Files.copy(upload.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return "/api/avatars/" + playerId;
    }

    public Optional<Path> findByPlayerId(String playerId) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, playerId + ".*")) {
            for (Path p : ds) return Optional.of(p);
        } catch (IOException e) {
            log.warn("[AvatarStorage] find failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    public void deleteByPlayerId(String playerId) {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root, playerId + ".*")) {
            for (Path p : ds) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    private static String extOf(String filename) {
        if (filename == null) return ".png";
        int idx = filename.lastIndexOf('.');
        if (idx < 0) return ".png";
        String ext = filename.substring(idx).toLowerCase();
        return switch (ext) {
            case ".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp" -> ext;
            default -> ".png";
        };
    }
}
