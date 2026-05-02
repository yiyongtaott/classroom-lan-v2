# ClassroomLAN v2 — 在线列表用户三状态圆形标记 任务文档 v3

> 替换上一版文档（v2），以下是修正后的状态定义与实现方案。  
> 前置条件：已完成《WebSocket 架构全面对齐》和《Bug 修复·功能补全·性能优化》两份文档的任务。

---

## 状态定义（最终版）

正常业务中，所有用户（无论是否为 Host 身份）访问的都是同一个 Host 的页面。不存在用户打开非 Host IP 页面仍参与业务的场景，因此"地址栏 IP 是否等于 Host IP"在正常运行时对所有用户恒成立，无需单独检测。

**第三个圆的实际语义是：该用户的浏览器标签页当前处于 active 状态（未被切走或最小化）。**

| 位置 | 名称 | 绿色条件 | 灰色条件 |
|---|---|---|---|
| 第一个圆 | **后端进程存活** | 该节点的 UDP HELLO 心跳仍在广播 | 超过阈值时间未收到该节点的 UDP 包 |
| 第二个圆 | **前端 WebSocket 存活** | Host 的 `ClientSessionRegistry` 中存在该用户的 WebSocket 连接 | 连接不存在或已断开 |
| 第三个圆 | **标签页处于 active** | 该用户浏览器标签页的 `visibilityState === 'visible'` | 标签页被切走、最小化，或 WebSocket 断开（联动置灰） |

---

## 三个状态的数据来源与采集方案

### 状态一：后端进程存活（UDP 层，纯后端）

**采集方**：Host 后端，已有 UDP 收包逻辑。  
**方案**：每次收到任意节点的 HELLO 包时更新该节点的最后心跳时间戳；定时任务扫描超时节点。  
**无需前端参与。**

### 状态二：前端 WebSocket 存活（后端直接感知）

**采集方**：Host 后端，`ClientSessionRegistry` 已维护 sessionId 映射。  
**方案**：`SessionConnectedEvent` / `SessionDisconnectEvent` 触发时直接更新并广播，无需前端额外上报。  
**无需前端参与。**

### 状态三：标签页 active（前端上报）

这是唯一需要前端主动上报的状态，原因：后端无法感知浏览器 Page Visibility，只有前端能通过 `document.visibilityState` 获取。

上报的条件仅为 `document.visibilityState === 'visible'`，**不再需要检查地址栏 IP**，因为正常业务中所有用户访问的都是同一个 Host 页面，地址栏条件恒成立。

---

## 后端任务

### 1. UserStatusRecord（新增数据结构）

```java
class UserStatusRecord {
    String userId;          // nodeId（等同于节点 IP）
    String username;

    // 状态一：UDP 心跳
    Instant lastUdpHeartbeat;
    boolean backendAlive;   // 定时计算：now - lastUdpHeartbeat < UDP_TIMEOUT

    // 状态二：WebSocket 连接
    boolean wsAlive;        // ClientSessionRegistry.sessionExists(userId)

    // 状态三：标签页 active
    boolean pageActive;  // 由前端上报，true = visibilityState === 'visible'
}
```

`UDP_TIMEOUT` = UDP 广播间隔 × 3（例如心跳 2s 则超时 6s，容忍 2 次丢包）。

---

### 2. 状态一：UDP 心跳检测

在 `NodeDiscovery`（或 `ElectionService`）收到 HELLO 包处，新增调用：

```java
userStatusService.updateUdpHeartbeat(senderId, Instant.now());
```

新增定时任务，每 3 秒执行一次，扫描所有记录：

```java
@Scheduled(fixedDelay = 3000)
public void checkUdpAliveness() {
    Instant threshold = Instant.now().minusMillis(UDP_TIMEOUT_MS);
    for (UserStatusRecord r : userStatusService.allRecords()) {
        boolean newVal = r.lastUdpHeartbeat != null
                         && r.lastUdpHeartbeat.isAfter(threshold);
        if (r.backendAlive != newVal) {
            r.backendAlive = newVal;
            broadcastStatusUpdate(r);
        }
    }
}
```

---

### 3. 状态二：WebSocket 连接变更

在已有的 `SessionConnectedEventListener` 和 `SessionDisconnectEventListener` 中，连接状态变更后：

```java
// 连接建立
userStatusService.setWsAlive(userId, true);
broadcastStatusUpdate(userStatusService.getRecord(userId));

// 连接断开
userStatusService.setWsAlive(userId, false);
// 断开时同步将 pageActive 置 false（连接都没了，页面状态必然无效）
userStatusService.setPageActive(userId, false);
broadcastStatusUpdate(userStatusService.getRecord(userId));
```

---

### 4. 状态三：前端上报标签页激活状态

新增 STOMP 消息映射：

```java
@MessageMapping("/user.page-active")
public void handlePageActive(
    @Payload PageActivePayload payload,  // { "active": true/false }
    SimpMessageHeaderAccessor accessor
) {
    String userId = getUserIdFromSession(accessor);
    boolean newVal = payload.isActive();
    boolean oldVal = userStatusService.getPageActive(userId);
    if (oldVal != newVal) {
        userStatusService.setPageActive(userId, newVal);
        broadcastStatusUpdate(userStatusService.getRecord(userId));
    }
}
```

---

### 5. 广播格式

所有三种状态变更，统一广播到 `/topic/user.status`：

```json
{
  "userId": "192.168.1.5",
  "backendAlive": true,
  "wsAlive": true,
  "pageActive": false
}
```

初始快照（`/user/queue/init`）的 `users` 数组中，每个用户对象也必须包含这三个字段。

---

## 前端任务

### 1. 上报标签页激活状态

前端在以下时机上报：

- WebSocket 连接建立后（`onConnect`）立即上报一次当前状态
- `document.visibilityState` 发生变化时（`visibilitychange` 事件）

```javascript
// useWebSocket.js 或单独的 usePageActive.js composable

function reportPageActive() {
  if (!stompClient.connected) return
  const active = document.visibilityState === 'visible'
  stompClient.publish({
    destination: '/app/user.page-active',
    body: JSON.stringify({ active })
  })
}

function initPageActiveReporting() {
  document.addEventListener('visibilitychange', reportPageActive)
}

function cleanupPageActiveReporting() {
  document.removeEventListener('visibilitychange', reportPageActive)
}
```

在 `onConnect` 回调末尾调用：

```javascript
reportPageActive()          // 连接建立后立即上报一次当前状态
initPageActiveReporting()   // 注册后续变化监听
```

在组件卸载（`onUnmounted`）时调用 `cleanupPageActiveReporting()`，防止内存泄漏和重复注册。

**注意**：不需要监听 `appStore.hostIp` 变化，因为第三圆与 Host 身份无关，只反映标签页自身的 active 状态。

---

### 2. 订阅状态更新

在 `onConnect` 中增加订阅：

```javascript
client.subscribe('/topic/user.status', (msg) => {
  const update = JSON.parse(msg.body)
  userListStore.updateStatus(update)
})
```

---

### 3. userListStore 修改

新增 `updateStatus` action，**局部更新**三个字段，不替换整个用户对象：

```javascript
updateStatus(update) {
  const user = this.users.find(u => u.userId === update.userId)
  if (user) {
    user.backendAlive = update.backendAlive
    user.wsAlive      = update.wsAlive
    user.pageActive   = update.pageActive
  }
}
```

初始快照处理时，每个 user 对象的默认值：

```javascript
backendAlive: false,
wsAlive:      false,
pageActive:   false
```

---

### 4. UserListItem 组件

三个圆点，固定顺序，hover 时显示 tooltip：

```html
<div class="user-status-dots">
  <span
    class="status-dot"
    :class="user.backendAlive ? 'dot-green' : 'dot-gray'"
    title="后端进程存活（UDP 心跳）"
  />
  <span
    class="status-dot"
    :class="user.wsAlive ? 'dot-green' : 'dot-gray'"
    title="前端 WebSocket 连接存活"
  />
  <span
    class="status-dot"
    :class="user.pageActive ? 'dot-green' : 'dot-gray'"
    title="标签页处于激活状态"
  />
</div>
```

CSS：

```css
.user-status-dots {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  margin-left: 6px;
  vertical-align: middle;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  cursor: default;
}

.dot-green { background-color: #4ade80; }
.dot-gray  { background-color: #6b7280; }
```

三个圆均使用绿/灰两态，语义统一：绿色代表"正常/存活/激活"，灰色代表"离线/断开/未激活"。

---

## 边界情况处理

| 场景 | 预期行为 |
|---|---|
| 任意用户正常在线且标签页激活 | 三圆全绿（绿绿绿） |
| 任意用户切走标签页 | 第三圆立即变灰，第一、二圆不变 |
| 任意用户切回标签页 | 第三圆立即变绿 |
| WebSocket 断开 | 第二圆变灰，第三圆联动变灰（后端断开时强制置 false），第一圆不变 |
| 用户进程被 kill（UDP 停广播） | 第一圆 6 秒内变灰；WebSocket 也同时断开，第二、三圆联动变灰 |
| 节点刚启动，WebSocket 尚未连接 | 第一圆先亮（UDP 已在广播），第二、三圆仍灰——合法状态，表示后端在跑但前端还没打开 |
| 断线重连后 | `onConnect` 中立即上报当前 `visibilityState`，三圆恢复准确状态 |
| Host 重选期间前端断线 | 三圆均灰；重连到新 Host 后恢复 |

---

## 交付物要求

以下文件给出完整内容，不允许省略任何方法体：

**后端**：
- `UserStatusService.java`（新增，包含全部三个状态的读写逻辑和广播调用）
- `NodeDiscovery.java` 或 `ElectionService.java`（仅展示新增的 `updateUdpHeartbeat` 调用处，其余保持不变）
- `SessionConnectedEventListener.java` 和 `SessionDisconnectEventListener.java`（新增状态二的更新调用）
- 新增 `@MessageMapping("/user.page-active")` 的 Controller 方法所在文件完整版

**前端**：
- `useWebSocket.js`（新增状态三上报逻辑及 `/topic/user.status` 订阅）
- `stores/userListStore.js`（新增 `updateStatus` action 及字段初始化，完整文件）
- `components/UserListItem.vue`（新增三圆点渲染，完整文件）

---

## 验证标准

| 验证场景 | 预期结果 |
|---|---|
| 任意用户正常在线且标签页激活 | 三圆全绿 |
| 任意用户切走标签页 | 第三圆 500ms 内变灰 |
| 任意用户切回标签页 | 第三圆 500ms 内变绿 |
| kill 某节点进程 | 该节点三圆在 6 秒内全部变灰 |
| WebSocket 断开后重连 | 三圆状态与实际一致，无残留错误状态 |
