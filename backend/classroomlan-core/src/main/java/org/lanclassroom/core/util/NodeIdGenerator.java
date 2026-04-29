package org.lanclassroom.core.util;

import java.util.UUID;

/**
 * 节点 ID 生成工具 - 生成唯一节点标识
 */
public class NodeIdGenerator {
    private static final String NODE_ID = UUID.randomUUID().toString();

    public static String getNodeId() {
        return NODE_ID;
    }
}
