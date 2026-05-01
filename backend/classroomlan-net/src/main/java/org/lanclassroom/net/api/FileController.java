package org.lanclassroom.net.api;

import org.lanclassroom.net.service.FileStorageService;
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
import java.util.List;
import java.util.Map;

/**
 * 文件传输 REST 接口。
 * 上传完成后通过 STOMP /topic/file.progress 通知所有客户端刷新列表。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService storage;
    private final SimpMessagingTemplate messaging;

    public FileController(FileStorageService storage, SimpMessagingTemplate messaging) {
        this.storage = storage;
        this.messaging = messaging;
    }

    @PostMapping("/upload")
    public ResponseEntity<FileStorageService.FileMeta> upload(
            @RequestParam("file") MultipartFile file) throws IOException {
        FileStorageService.FileMeta meta = storage.store(file);
        messaging.convertAndSend(WebSocketConfig.TOPIC_FILE_PROGRESS, Map.of(
                "stage", "UPLOADED",
                "id", meta.id(),
                "name", meta.name(),
                "size", meta.size()));
        return ResponseEntity.ok(meta);
    }

    @GetMapping
    public ResponseEntity<List<FileStorageService.FileMeta>> list() {
        return ResponseEntity.ok(storage.list());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable String id) {
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
    public ResponseEntity<Void> delete(@PathVariable String id) {
        boolean ok = storage.delete(id);
        if (ok) {
            messaging.convertAndSend(WebSocketConfig.TOPIC_FILE_PROGRESS, Map.of(
                    "stage", "DELETED", "id", id));
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
