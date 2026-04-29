package org.lanclassroom.net.discovery;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 服务 - 生成并验证房间访问 Token
 * Token = HMAC(roomKey, secret)
 */
@Service
public class TokenService {

    private final Map<String, String> roomKeyToToken = new ConcurrentHashMap<>();

    /**
     * 根据 roomKey 生成 Token
     */
    public String generateToken(String roomKey) {
        try {
            String secret = roomKey + System.currentTimeMillis();
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(secret.getBytes());
            String token = Base64.getEncoder().encodeToString(hash);
            roomKeyToToken.put(roomKey, token);
            return token;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate token", e);
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean isValid(String roomKey, String token) {
        String expected = roomKeyToToken.get(roomKey);
        return expected != null && expected.equals(token);
    }

    /**
     * 移除 Token（房间销毁时）
     */
    public void removeToken(String roomKey) {
        roomKeyToToken.remove(roomKey);
    }
}
