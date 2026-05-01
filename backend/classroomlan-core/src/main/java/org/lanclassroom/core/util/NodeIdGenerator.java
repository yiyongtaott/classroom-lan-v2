package org.lanclassroom.core.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 节点身份工具 - 进程级单例。
 *
 * nodeId   = 本机首选 IPv4 地址（业务文档 §1.1：节点 = 一台机器）
 * hostname = 操作系统主机名（用作默认昵称）
 *
 * 同一 LAN 中两台不同机器 IP 不冲突 → 选举时按 IP 字典序保证唯一 Host。
 */
public final class NodeIdGenerator {

    private static final String NODE_ID = detectIp();
    private static final String HOSTNAME = detectHostname();

    private NodeIdGenerator() {}

    /** 返回本机首选 IPv4 地址（即 nodeId）。 */
    public static String getNodeId() {
        return NODE_ID;
    }

    /** 返回本机主机名（系统名）。 */
    public static String getHostname() {
        return HOSTNAME;
    }

    private static String detectIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 优先返回常见的 LAN 段（避免选到 docker0/vmware 网段）
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")
                                || ip.startsWith("172.")) {
                            return ip;
                        }
                        if (fallback == null) fallback = ip;
                    }
                }
            }
            if (fallback != null) return fallback;
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private static String detectHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }
}
