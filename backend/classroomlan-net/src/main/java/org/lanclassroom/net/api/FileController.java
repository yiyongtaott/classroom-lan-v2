package org.lanclassroom.net.api;

import jakarta.servlet.http.HttpServletRequest;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.net.service.FileStorageService;
import org.lanclassroom.net.ws.ClientSessionRegistry;
import org.lanclassroom.net.ws.WebSocketConfig;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件传输 REST 接口。
 *
 * 上传成功后：
 *   - /topic/file.progress     - 兼容旧前端，仅触发列表刷新
 *   - /topic/file.uploaded     - FEATURE-03：携带上传者名 + 文件名 + 大小 + 下载 URL，全员可见
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService storage;
    private final SimpMessagingTemplate messaging;
    private final RoomManager roomManager;
    private final ClientSessionRegistry sessions;

    public FileController(FileStorageService storage,
                          SimpMessagingTemplate messaging,
                          RoomManager roomManager,
                          ClientSessionRegistry sessions) {
        this.storage = storage;
        this.messaging = messaging;
        this.roomManager = roomManager;
        this.sessions = sessions;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileStorageService.FileMeta> upload(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest req) throws IOException {
        FileStorageService.FileMeta meta = storage.store(file);

        String uploaderName = "匿名";
        String uploaderIp = req.getRemoteAddr();
        if (uploaderIp != null) {
            uploaderName = room().findByIp(uploaderIp).map(p -> p.getName()).orElse(uploaderName);
        }

        // 兼容旧前端
        messaging.convertAndSend(WebSocketConfig.TOPIC_FILE_PROGRESS, Map.of(
                "stage", "UPLOADED",
                "id", meta.id(),
                "name", meta.name(),
                "size", meta.size()));

        // FEATURE-03 全员可见提示
        Map<String, Object> notify = new HashMap<>();
        notify.put("uploaderName", uploaderName);
        notify.put("fileName", meta.name());
        notify.put("fileSize", meta.size());
        notify.put("downloadUrl", "/api/files/" + meta.id());
        notify.put("uploadedAt", meta.uploadedAt());
        messaging.convertAndSend(WebSocketConfig.TOPIC_FILE_UPLOADED, notify);

        return ResponseEntity.ok(meta);
    }

    @GetMapping
    public ResponseEntity<List<FileStorageService.FileMeta>> list() {
        return ResponseEntity.ok(storage.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable("id") String id) {
        FileStorageService.FileMeta meta = storage.get(id);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(Path.of(meta.path()));
        String filename = URLEncoder.encode(meta.name(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") String id) {
        boolean ok = storage.delete(id);
        if (ok) {
            messaging.convertAndSend(WebSocketConfig.TOPIC_FILE_PROGRESS, Map.of(
                    "stage", "DELETED", "id", id));
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
