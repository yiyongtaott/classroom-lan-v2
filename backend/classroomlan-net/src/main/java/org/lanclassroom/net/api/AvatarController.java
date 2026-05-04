package org.lanclassroom.net.api;

import org.lanclassroom.core.model.Player;
import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.service.RoomManager;
import org.lanclassroom.net.service.AvatarStorageService;
import org.lanclassroom.net.service.StatusBroadcastService;
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
 * 头像 REST 接口。
 *
 * 上传：POST /api/avatars/{playerId}    -- 文件按"原名同名覆盖"存储；自动绑到 player.name；
 *                                           上传后广播 /topic/user.update（BUG-02）。
 * 读取：GET  /api/avatars/file/{name}   -- 直接按文件名读取
 * 用户名查询：GET /api/avatars/by-name?name=Bob
 */
@RestController
@RequestMapping("/api/avatars")
public class AvatarController {

    private final AvatarStorageService storage;
    private final RoomManager roomManager;
    private final StatusBroadcastService statusBroadcast;

    public AvatarController(AvatarStorageService storage, RoomManager roomManager,
                            StatusBroadcastService statusBroadcast) {
        this.storage = storage;
        this.roomManager = roomManager;
        this.statusBroadcast = statusBroadcast;
    }

    @PostMapping("/{playerId}")
    public ResponseEntity<Map<String, String>> upload(
            @PathVariable("playerId") String playerId,
            @RequestParam("file") MultipartFile file) throws IOException {
        Player p = room().findById(playerId).orElse(null);
        if (p == null) {
            return ResponseEntity.notFound().build();
        }
        String url = storage.storeFor(p.getName(), file);
        p.setAvatar(url);
        statusBroadcast.broadcastUserUpdate(p);
        statusBroadcast.broadcastRoom();
        return ResponseEntity.ok(Map.of("avatar", url));
    }

    @GetMapping("/file/{filename:.+}")
    public ResponseEntity<Resource> getByFilename(@PathVariable("filename") String filename) {
        return storage.findByFilename(filename)
                .map(this::toImageResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-name")
    public ResponseEntity<Map<String, String>> getByName(@RequestParam("name") String name) {
        return storage.avatarUrlOf(name)
                .map(url -> ResponseEntity.ok(Map.of("avatar", url)))
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
        if (n.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private Room room() {
        Room r = roomManager.getRoom("default");
        if (r == null) r = roomManager.createRoom("default");
        return r;
    }
}
