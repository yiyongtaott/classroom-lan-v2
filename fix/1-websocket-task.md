# ClassroomLAN v2 — WebSocket 架构全面对齐任务文档

## 项目背景与本质

本项目是"局域网内自动选主的单后端实时游戏系统"，**不是**分布式系统。  
启动流程：多节点 UDP 广播 → 选举唯一 Host → 其他节点作为客户端连接 Host 的 HTTP/WebSocket 服务。

技术栈：Java 17 · Spring Boot 3.2 · WebSocket (STOMP) · Vue 3 · Vite · Pinia · stompjs  
无鉴权、无 Token、无加密，所有用户可信。

---

## 当前问题清单

1. 前端通过 `setInterval` 轮询 `GET /api/status` 和 `GET /api/room`，用 HTTP RemoteAddr 识别客户端 IP。
2. 业务文档定义的 STOMP topic（`/topic/chat`、`/topic/game.state` 等）与实际代码实现未完全对齐。
3. Host 故障重选后前端无感知重连机制缺失。
4. 游戏状态 `GameSession` 抽象存在但未串联到 WebSocket 广播链路。

---

## 任务目标

在不改变任何游戏逻辑和聊天逻辑的前提下：

1. 用 WebSocket 推送替换 `/api/status` 和 `/api/room` 的前端轮询。
2. 将 IP 识别从"每次 HTTP 请求读取"迁移到"握手阶段一次性绑定"。
3. 补全业务文档中定义的全部 STOMP 路由，确保前后端协议一致。
4. 实现前端 WebSocket 断线感知与自动重连（覆盖 Host 重选场景）。

---

## 后端任务（Java / Spring Boot）

### 1. IpHandshakeInterceptor（新增）

在 `backend/src/main/java/.../net/ws/IpHandshakeInterceptor.java` 创建：

```java
// 实现 HandshakeInterceptor
// beforeHandshake：从 ServerHttpRequest.getRemoteAddress() 读取 IP，
//   写入 attributes.put("clientIp", ip)
// afterHandshake：空实现
```

### 2. WebSocketConfig（修改）

注册 `IpHandshakeInterceptor`，确保 STOMP 端点和 topic 前缀与业务文档一致：

| 配置项 | 值 |
|---|---|
| STOMP 端点 | `/ws` |
| App destination prefix | `/app` |
| Topic prefix | `/topic` |
| User queue prefix | `/user` |
| 注册 Interceptor | `IpHandshakeInterceptor` |

### 3. ClientSessionRegistry（新增）

内存 Map，管理 `clientIp → sessionId` 的双向映射，提供：

```
register(sessionId, clientIp)
unregister(sessionId)
getIpBySession(sessionId): String
getSessionByIp(ip): String
```

### 4. WebSocket 生命周期监听器（新增）

**SessionConnectedEventListener**：
- 从 `SimpMessageHeaderAccessor` 取出 `clientIp`（握手阶段存入）。
- 调用 `ClientSessionRegistry.register()`。
- 向 `/user/{sessionId}/queue/init` 推送初始快照（包含原 `/api/status` 和 `/api/room` 的完整数据）。

**SessionDisconnectEventListener**：
- 调用 `ClientSessionRegistry.unregister()`，清理内存条目。

### 5. StatusService 和 RoomService（修改）

数据发生变更时，除原有逻辑外，新增：

```java
// 状态变更时
messagingTemplate.convertAndSend("/topic/status", statusPayload);

// 房间变更时
messagingTemplate.convertAndSend("/topic/room", roomPayload);
```

原 `GET /api/status` 和 `GET /api/room` HTTP 接口**保留但不再由前端主动调用**（保留用于调试）。

### 6. STOMP 路由完整实现（补全）

按业务文档补全以下路由（已有的确认存在，缺失的新增）：

| 方向 | 路由 | 用途 |
|---|---|---|
| 客户端发送 | `/app/chat` | 发送聊天消息 |
| 服务端广播 | `/topic/chat` | 广播聊天消息 |
| 客户端发送 | `/app/game.action` | 发送游戏操作 |
| 服务端广播 | `/topic/game.state` | 广播游戏状态 |
| 服务端广播 | `/topic/status` | 推送系统状态变更 |
| 服务端广播 | `/topic/room` | 推送房间信息变更 |
| 服务端单播 | `/user/queue/init` | 连接后推送初始快照 |
| 服务端单播 | `/user/queue/error` | 推送个人错误消息（可选） |

### 7. GameSession 广播串联（补全）

确保所有 `GameSession` 实现类在 `outputState()` 时调用：

```java
messagingTemplate.convertAndSend("/topic/game.state", gameStatePayload);
```

`gameStatePayload` 结构：`{ "gameType": "xxx", "state": { ... } }`

---

## 前端任务（Vue 3 + stompjs）

### 1. 删除轮询逻辑

找到所有对 `/api/status` 和 `/api/room` 的 `setInterval` 调用，**完整删除**（包括 `clearInterval` 的清理逻辑一并删除）。

### 2. useWebSocket composable（新增或修改）

在 `frontend/src/composables/useWebSocket.js`（新增）或修改现有 WebSocket 初始化逻辑：

```javascript
// 连接配置
const client = new Client({
  brokerURL: `ws://${hostIp}:${port}/ws`,
  reconnectDelay: 3000,      // 3秒后自动重连，覆盖 Host 重选场景
  onConnect: () => {
    // 订阅初始快照（仅需接收一次，但写在 onConnect 里可覆盖重连）
    client.subscribe('/user/queue/init', (msg) => {
      const { status, room } = JSON.parse(msg.body)
      statusStore.setAll(status)
      roomStore.setAll(room)
    })
    // 订阅增量推送
    client.subscribe('/topic/status', (msg) => {
      statusStore.setAll(JSON.parse(msg.body))
    })
    client.subscribe('/topic/room', (msg) => {
      roomStore.setAll(JSON.parse(msg.body))
    })
    // 订阅聊天和游戏状态（确认已有逻辑在此处）
    client.subscribe('/topic/chat', ...)
    client.subscribe('/topic/game.state', ...)
  },
  onDisconnect: () => {
    // 更新 Pinia 中的连接状态，前端显示"连接已断开，重连中..."
    appStore.setConnected(false)
  },
  onStompError: (frame) => {
    console.error('STOMP error', frame)
  }
})
```

### 3. Pinia Store 修改

**statusStore**：新增 `setAll(payload)` action，接受完整对象替换 state。  
**roomStore**：同上。  
**appStore**（新增或已有）：维护 `connected: boolean` 字段，在 `onConnect` 时设为 `true`，`onDisconnect` 时设为 `false`。

### 4. 断线 UI 提示

在根组件或 Layout 中监听 `appStore.connected`，为 `false` 时展示全局提示条：

```
"与 Host 的连接已断开，正在重新连接..."
```

Host 重选完成、重连成功后提示自动消失（`onConnect` 触发 `appStore.setConnected(true)`）。

### 5. hostIp 获取方式

删除依赖轮询获取 hostIp 的逻辑，改为：从 URL 参数、localStorage 或 UDP 选举结果注入（具体方式与现有节点发现逻辑保持一致，不引入新机制）。

---

## 不允许修改的范围

- 所有游戏逻辑类（`GameSession` 及其实现类的内部逻辑）。
- 聊天消息的收发逻辑。
- 文件上传接口（HTTP Multipart，保持不变）。
- UDP 节点发现与选举逻辑。
- `pom.xml` 依赖（不引入新依赖，`spring-boot-starter-websocket` 已存在）。

---

## 交付物要求

每个新增或修改的文件给出**完整内容**，不允许省略任何方法体或用注释代替实现。  
文件列表：

**后端：**
- `IpHandshakeInterceptor.java`（新增）
- `WebSocketConfig.java`（完整修改后版本）
- `ClientSessionRegistry.java`（新增）
- `SessionConnectedEventListener.java`（新增）
- `SessionDisconnectEventListener.java`（新增）
- `StatusService.java`（完整修改后版本）
- `RoomService.java`（完整修改后版本）
- 任何新增或修改的 `GameSession` 相关类

**前端：**
- `useWebSocket.js`（新增或完整修改后版本）
- 修改的 store 文件（完整版本）
- 删除了轮询逻辑的 `.vue` 文件（完整版本）

---

## 验证标准

1. 新用户打开页面后，无需轮询，5 秒内收到初始快照并正确显示房间状态。
2. Host 模拟下线后，前端显示断线提示；Host 重启并重选完成后，前端自动重连并恢复显示。
3. 服务端 `clientIp` 在连接期间始终可通过 `ClientSessionRegistry` 查到，与原轮询方案行为一致。
4. 浏览器 Network 面板中不再出现对 `/api/status` 和 `/api/room` 的周期性请求。
