package org.lanclassroom.core.util;

import java.security.SecureRandom;

/**
 * 节点 ID 生成器 - 进程级单例。
 * 节点 ID = 12 位十六进制随机串，进程生命周期内不变。
 */
public final class NodeIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String NODE_ID = generate();

    private NodeIdGenerator() {}

    public static String getNodeId() {
        return NODE_ID;
    }

    private static String generate() {
        byte[] buf = new byte[6];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(12);
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
