package org.lanclassroom.net.discovery;

import org.lanclassroom.core.model.Room;
import org.lanclassroom.core.util.NodeIdGenerator;
import org.lanclassroom.net.api.DiscoveryMessage;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host 选举器 - 决定当前节点是否成为 Host
 * 优先级：版本号（高）> 系统负载（低）> nodeId（最后）
 */
@Component
public class HostElector {

    private static final String VERSION = "2.0.0";

    private final AtomicBoolean isHost = new AtomicBoolean(false);
    private final AtomicReference<String> currentNodeId = new AtomicReference<>(NodeIdGenerator.getNodeId());
    private final AtomicReference<String> currentRoomKey = new AtomicReference<>();
    private final AtomicReference<Long> lastBeatTimestamp = new AtomicReference<>(System.currentTimeMillis());

    private volatile Room room;

    /**
     * 处理收到的消息
     */
    public void onMessage(DiscoveryMessage msg) {
        if (msg == null) {
            // 本地触发：检查心跳超时，降级为非 Host
            if (isHost.get()) {
                isHost.set(false);
                System.out.println("[HostElector] Host timeout, stepping down");
            }
            return;
        }

        String type = msg.getType();
        if ("HELLO".equals(type)) {
            evaluateHello(msg);
        } else if ("BEAT".equals(type)) {
            updateHostBeat(msg);
        }
    }

    /**
     * 评估 HELLO 消息 - 决定是否成为 Host
     * 优先级：版本号 > systemLoad > nodeId（字母序）
     */
    private synchronized void evaluateHello(DiscoveryMessage msg) {
        boolean msgIsHost = msg.isHost();
        String msgNodeId = msg.getNodeId();

        if (isHost.get()) {
            // 自己已是 Host
            if (!msgIsHost) {
                // 收到非 Host 的 HELLO，维持 Host 身份
                // 发送 BEAT 宣告
            } else {
                // 收到其他 Host 的 HELLO - 比较优先级
                if (isHigherPriority(msgNodeId)) {
                    isHost.set(false);
                }
            }
        } else {
            // 自己不是 Host
            if (!msgIsHost) {
                // 如果收到另一个非 Host 的 HELLO，比较决定谁成为 Host
                if (shouldBecomeHost(msgNodeId)) {
                    isHost.set(true);
                    currentRoomKey.set(generateRoomKey());
                    System.out.println("[HostElector] This node becomes Host, roomKey=" + currentRoomKey.get());
                }
            } else {
                // 已存在 Host，跟随
            }
        }
    }

    /**
     * 判断自己是否应该成为 Host（比较 nodeId 和版本）
     */
    private boolean shouldBecomeHost(String otherNodeId) {
        // 简单策略：nodeId 较小者胜出（字母序）
        // 版本相同的情况下
        String myId = currentNodeId.get();
        return myId.compareTo(otherNodeId) < 0;
    }

    /**
     * 判断 msgNodeId 是否比当前 nodeId 优先级更高
     */
    private boolean isHigherPriority(String msgNodeId) {
        // 版本相同，nodeId 更小者优先
        String myId = currentNodeId.get();
        return msgNodeId.compareTo(myId) < 0;
    }

    /**
     * 更新 Host 心跳
     */
    private synchronized void updateHostBeat(DiscoveryMessage msg) {
        lastBeatTimestamp.set(System.currentTimeMillis());

        if (isHost.get() && !msg.getNodeId().equals(currentNodeId.get())) {
            // 自己是 Host，但收到其他节点的 BEAT（可能是版本更高）
            isHost.set(false);
        }
    }

    public boolean isHost() {
        return isHost.get();
    }

    public String getNodeId() {
        return currentNodeId.get();
    }

    public String getRoomKey() {
        return currentRoomKey.get();
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public long getLastBeatTimestamp() {
        return lastBeatTimestamp.get();
    }

    private String generateRoomKey() {
        // 16 位十六进制随机串
        return Long.toHexString(Double.doubleToLongBits(Math.random()));
    }
}
