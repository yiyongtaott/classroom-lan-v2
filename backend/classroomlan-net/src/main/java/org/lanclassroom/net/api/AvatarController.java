package org.lanclassroom.net.api;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.net.service.AvatarStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * 头像上传 / 读取 REST 接口。
 * 文件存于 Host 本地 ./avatars/ 目录（Bug 7 要求"项目同目录"）。
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    private final AvatarStorageService storage;
    private final Room room;

    public AvatarController(AvatarStorageService storage, Room room) {
        this.storage = storage;
        this.room = room;
    }

    @PostMapping("/{playerId}")
    public ResponseEntity<Map<String, String>> upload(
            @PathVariable("playerId") String playerId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String url = storage.store(playerId, file);
        // 回写到 Player 对象
        Player p = room.findById(playerId).orElse(null);
        if (p != null) {
            p.setAvatar(url);
        }
        return ResponseEntity.ok(Map.of("avatar", url));
    }

    @GetMapping("/{playerId}")
    public ResponseEntity<Resource> get(@PathVariable("playerId") String playerId) {
        return storage.findByPlayerId(playerId)
                .map(this::toImageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> toImageResponse(Path path) {
        MediaType type = guess(path.getFileName().toString());
        return ResponseEntity.ok()
                .contentType(type)
                .body(new FileSystemResource(path));
    }

    private static MediaType guess(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (n.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (n.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
