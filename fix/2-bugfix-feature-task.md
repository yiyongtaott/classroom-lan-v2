# ClassroomLAN v2 — Bug 修复 · 功能补全 · 性能优化 任务文档

> 阅读本文档前提：已完成上一份《WebSocket 架构全面对齐》任务。  
> 本文档所有任务均在该基础上叠加，不重复描述已有架构。

---

## BUG-01 · 重选主后旧 Host 页面未关闭 / 双主冲突

### 现象
选主完成后，新 Host 自动开启了 8080 端口页面，但原 Host 的页面没有被关掉。  
更危险的情况：原 Host 并未真正下线，两个节点同时认为自己是 Host，出现双主。

### 根因分析
1. **选主结果未广播给所有节点**：UDP 选举结束后只有新 Host 知道结果，旧 Host（如果还活着）没有收到"你已降级为 Client"的通知。
2. **旧 Host 没有执行降级逻辑**：即使收到通知，也没有代码路径让旧 Host 停止 Web 服务监听或通知其前端页面关闭/跳转。
3. **前端没有"你已不是 Host"的处理分支**。

### 修复要求

**后端 — UDP 选举层（`ElectionService` 或同等类）**

选举结束后，新 Host 必须向所有已知节点广播一条 UDP 消息：

```json
{ "type": "ELECTION_RESULT", "hostId": "xxx", "hostIp": "192.168.x.x", "hostPort": 8080 }
```

每个节点收到此消息后：

- 若 `hostId == self.nodeId`：确认自己是 Host，继续提供服务，**同时通过 WebSocket 向所有已连接前端推送** `{ "type": "HOST_CONFIRMED", "hostIp": "...", "hostPort": 8080 }`。
- 若 `hostId != self.nodeId` 且当前自己正在以 Host 模式运行：
  1. 停止接受新的 HTTP/WebSocket 连接（关闭 Tomcat 监听或拒绝新请求）。
  2. 向自己的前端页面（如果还有连接）推送 `{ "type": "HOST_CHANGED", "newHostIp": "...", "newHostPort": 8080 }`。
  3. 以 Client 模式重新连接新 Host 的 WebSocket。

**前端 — WebSocket 消息处理**

在全局 STOMP 消息处理器中新增：

```javascript
// 收到 HOST_CHANGED：当前页面是旧 Host 的页面
case 'HOST_CHANGED':
  // 给用户 2 秒提示："Host 已切换，即将跳转"
  setTimeout(() => {
    window.location.href = `http://${msg.newHostIp}:${msg.newHostPort}`
  }, 2000)
  break

// 收到 HOST_CONFIRMED：当前页面已经在新 Host 上，无需操作
case 'HOST_CONFIRMED':
  appStore.setHostInfo(msg.hostIp, msg.hostPort)
  break
```

**防双主保护（后端）**

选举期间加入互斥锁：节点在收到 `ELECTION_RESULT` 并确认不是自己之前，**不得**启动 HTTP 服务。  
增加选举超时（建议 5 秒）：超时后强制以最高优先级节点为 Host，防止网络抖动导致选举永远无法收敛。

---

## BUG-02 · 各类状态同步问题

### 现象
- 改名之后其他用户不同步，手动刷新才生效。
- 在线列表加人能实时同步，但改名不行（说明事件监听存在但不完整）。

### 根因分析
加入/离开触发了 WebSocket 广播，但"修改用户信息"的 HTTP 接口调用后**没有触发对应的 WebSocket 推送**，只更新了内存数据，导致其他客户端的 Pinia store 没有收到通知。

### 修复要求

**后端**

找到所有修改用户信息的接口（改名、改头像等），在持久化/内存更新完成后，统一调用：

```java
messagingTemplate.convertAndSend("/topic/user.update", updatedUserPayload);
// updatedUserPayload: { "userId": "...", "username": "新名字", "avatar": "..." }
```

**前端**

在 `useWebSocket.js` 的 `onConnect` 中增加订阅：

```javascript
client.subscribe('/topic/user.update', (msg) => {
  const user = JSON.parse(msg.body)
  userListStore.updateUser(user)  // 根据 userId 更新对应条目，不替换整个列表
})
```

`userListStore` 的 `updateUser` action 必须是**局部更新**（按 userId 查找后 patch），不能整体替换列表，否则会导致列表闪烁。

---

## BUG-03 · 游戏邀请界面慢、不稳定、交互逻辑错误

### 现象
1. 选择游戏后邀请界面出现慢，有时其他用户根本不显示。
2. 点击三个按钮后有好几秒延迟。
3. 发起人可以反悔（切换到拒绝），但其他用户也能来回切换。
4. 超过一半人拒绝时应自动关闭，目前没有实现。
5. "强制进入"应让接受的人一起进入，不是只有自己进。
6. 全员接受时应自动进入游戏。

### 根因分析
- 邀请消息发送走了 HTTP 而非 WebSocket，导致延迟 + 不可靠。
- 邀请状态（谁接受/谁拒绝）存在后端内存里但没有实时广播给所有人，导致客户端不知道当前投票结果。
- 缺少服务端的邀请状态机（收集响应 → 判断条件 → 触发结果）。

### 修复要求

**后端 — InvitationService（新增或重构）**

```
状态机：
  PENDING（等待中）→ ACCEPTED_ALL（全员接受）→ 自动进入
                   → REJECTED_MAJORITY（超半数拒绝）→ 自动关闭
                   → FORCE_ENTER（Host 强制）→ 让已接受的人进入
```

数据结构：

```java
class InvitationSession {
    String invitationId;
    String gameType;
    String initiatorId;
    Map<String, InviteResponse> responses; // userId → ACCEPT/REJECT/PENDING
    InvitationStatus status;
}
// InviteResponse: ACCEPT | REJECT | PENDING（默认）
```

接口：

- `POST /app/game.invite`（STOMP）：Host 或任意用户发起邀请，服务端创建 `InvitationSession`，立即广播 `INVITATION_CREATED` 到 `/topic/invitation`。
- `POST /app/game.invite.respond`（STOMP）：任意用户响应，payload `{ invitationId, response: ACCEPT|REJECT }`。
  - 更新 `InvitationSession`。
  - 广播最新状态到 `/topic/invitation.state`：`{ invitationId, responses: { userId: response, ... }, total, acceptCount, rejectCount }`。
  - 服务端判断：
    - `rejectCount > total / 2` → 广播 `INVITATION_CLOSED`，清除 session。
    - `acceptCount == total` → 广播 `GAME_START`，携带接受者列表。
- `POST /app/game.invite.force`（STOMP，仅发起人可调用）：广播 `GAME_START`，携带**当前 ACCEPT 状态的用户列表**（不包括 REJECT 和 PENDING 的用户）。

**约束**：
- 发起人（initiator）和其他用户享有完全相同的权利：可以随时在 ACCEPT/REJECT 之间切换，直到游戏开始或邀请关闭。
- 响应切换不设限制，服务端每次收到新响应直接覆盖旧值并重新广播状态。

**前端 — InvitationPanel 组件（重构）**

订阅 `/topic/invitation` 和 `/topic/invitation.state`：

```javascript
client.subscribe('/topic/invitation', (msg) => {
  // INVITATION_CREATED：立即弹出邀请面板
  invitationStore.open(JSON.parse(msg.body))
})

client.subscribe('/topic/invitation.state', (msg) => {
  // 实时更新面板内的投票状态（谁接受/谁拒绝，实时显示头像+状态）
  invitationStore.updateState(JSON.parse(msg.body))
})

client.subscribe('/topic/game.start', (msg) => {
  const { gameType, players } = JSON.parse(msg.body)
  const myId = userStore.currentUserId
  if (players.includes(myId)) {
    invitationStore.close()
    router.push(`/game/${gameType}`)  // 仅接受者跳转
  }
})

client.subscribe('/topic/invitation.closed', () => {
  invitationStore.close()  // 超半数拒绝，关闭面板
})
```

按钮点击逻辑：

```javascript
// 三个按钮：接受 / 拒绝 / （强制进入，仅发起人可见）
// 点击即发 STOMP 消息，不等待 HTTP 响应，无延迟
function respond(response) {
  stompClient.publish({
    destination: '/app/game.invite.respond',
    body: JSON.stringify({ invitationId: currentInvitationId, response })
  })
  // 本地 UI 立即更新自己的按钮状态（不等服务端回包）
  invitationStore.setMyResponse(response)
}
```

**关键**：按钮点击走 STOMP 发送（无 HTTP 往返），UI 立即更新本地状态，服务端广播回来后同步其他人状态。这样从"点击"到"UI 响应"不超过 50ms。

---

## BUG-04 · 页面刚打开或刷新后顶部菜单点击无反应

### 根因分析
菜单点击依赖某个异步初始化完成（WebSocket 连接建立、或 Pinia store 中某个标志位为 true），但组件在这个异步完成之前就已经渲染并挂载了，点击事件被吞掉或跳转被 `router.beforeEach` 中的守卫拦截（因为 `appStore.connected` 还是 `false`）。

### 修复要求

**前端 — 路由守卫**

检查 `router/index.js` 中的 `beforeEach`，确认是否有类似：

```javascript
if (!appStore.initialized) return false  // ← 这会导致刷新后菜单失效
```

修改为：初始化未完成时**不拦截路由跳转**，但在页面组件内部用 `v-if` 或 loading 状态控制内容渲染。

**前端 — 菜单组件**

菜单链接不使用 `router.push()`（程序式导航会受守卫影响），改用 `<RouterLink>` 声明式导航，或确保 `router.push()` 调用时不处于守卫的拦截路径中。

**游戏选择同理**：游戏列表的点击如果也有这个问题，排查是否绑定了 `@click` 但在 WebSocket 未连接时 handler 直接 return。统一改为：WebSocket 未连接时显示 loading 遮罩，而不是静默拦截点击。

---

## FEATURE-01 · 完成"你画我猜"游戏

### 游戏规则
- 每轮一人画画，其他人猜词。
- 画图者从系统给出的 3 个词中选 1 个，不告诉其他人。
- 其他人在聊天框输入猜测，猜对者和画图者同时得分。
- 每轮限时（默认 60 秒），超时或有人猜对则换下一人画。

### 后端要求

新增 `DrawAndGuessGameSession implements GameSession`，包含：

```java
// 状态字段
String currentDrawerId;
String currentWord;
List<String> wordOptions;       // 给 drawer 选的三个词
int roundTimeSeconds = 60;
Map<String, Integer> scores;    // userId → 得分
GamePhase phase;                // WAITING / SELECTING / DRAWING / ROUND_END / GAME_OVER

// STOMP 路由
@MessageMapping("/game.draw.stroke")   // 接收笔画数据
@MessageMapping("/game.draw.guess")    // 接收猜词
@MessageMapping("/game.draw.select")   // drawer 选词
```

笔画数据结构（广播到 `/topic/game.draw.canvas`）：

```json
{
  "type": "STROKE",
  "points": [{"x": 100, "y": 200}, ...],
  "color": "#ff0000",
  "lineWidth": 4,
  "tool": "pen"  // pen | eraser
}
```

猜词正确判断：服务端比对，正确时广播 `GUESS_CORRECT`，不向 drawer 发送明文答案（drawer 不能看其他人的猜词，否则提示不公平）。

**向 drawer 发送的消息**和**向 guesser 发送的消息**通过 `/user/queue/game.draw` 单播区分（drawer 看到"已有 N 人猜对"，guesser 看到正常猜词反馈）。

### 前端要求

新增 `/game/draw-and-guess` 路由和 `DrawAndGuessView.vue`，包含：

- **Canvas 画板**（仅 drawer 可操作）：支持画笔、橡皮擦、颜色选择、粗细调节、清空。
- **同步画板**（guesser 只读）：接收笔画数据实时渲染，用 `requestAnimationFrame` 批量绘制，不每条笔画都 render。
- **猜词输入框**：仅 guesser 可见，输入后通过 STOMP 发送。
- **计时器**：倒计时显示，服务端广播 `TICK` 事件（每秒一次）或前端本地倒计时（以服务端 `ROUND_START` 携带的时间戳为准，避免时钟不同步）。
- **得分面板**：实时显示所有玩家得分，猜对时动画高亮。

---

## FEATURE-02 · 在线人数列表用户可点击 — 详查界面

### 要求

点击在线列表中任意用户，弹出详查面板（Modal 或侧边栏），布局参考主界面右侧的账号界面，内容包括：

- 头像（大图）
- 用户名
- 当前状态（在线 / 游戏中）
- 加入时间
- 本场累计得分（如有）
- 私聊入口按钮（见 FEATURE-04）

点击自己时：显示编辑界面（改名、换头像），与原有账号界面功能一致，不重复开发，复用同一组件。

---

## FEATURE-03 · 文件上传成功提示全员可见

### 根因
当前上传成功的 Toast 只在上传者本地触发（前端 `axios` 回调里调 `ElMessage.success()`），其他人不知道。

### 修复要求

**后端**：文件上传接口（`POST /api/files/upload`）成功后，额外调用：

```java
messagingTemplate.convertAndSend("/topic/file.uploaded", fileUploadedPayload);
// payload: { "uploaderName": "xxx", "fileName": "yyy.pdf", "fileSize": 12345, "downloadUrl": "/api/files/yyy.pdf" }
```

**前端**：订阅 `/topic/file.uploaded`，收到消息时所有用户显示 Toast：

```
"[用户名] 上传了文件：yyy.pdf（12KB）  [点击下载]"
```

Toast 持续时间建议 5 秒，点击"下载"直接触发 `window.open(downloadUrl)`。

---

## FEATURE-04 · 私聊功能

### 规则
- 点击其他用户详查界面中的"私聊"按钮，向对方发送弹窗通知。
- 被私聊方弹出提示，三个选项：**打开**（显示聊天窗口）/ **销毁**（拒绝并通知对方）/ **小化**（最小化到角落，可随时展开）。
- 聊天记录不持久化，存在前端内存/Pinia store 中。
- 双方都关闭详查界面后，记录自动清除。

### 后端要求

私聊消息通过 STOMP 单播，**不经过服务端存储**：

```
发送方 → /app/private.chat.send → 服务端转发 → /user/{receiverId}/queue/private.chat
```

服务端只做转发，不持久化：

```java
@MessageMapping("/private.chat.send")
public void handlePrivateChat(PrivateChatMessage msg, Principal principal) {
    String senderId = principal.getName(); // 即 clientIp 或 sessionId
    messagingTemplate.convertAndSendToUser(
        msg.getReceiverId(),
        "/queue/private.chat",
        new PrivateChatMessage(senderId, msg.getContent(), System.currentTimeMillis())
    );
}
```

私聊邀请（弹窗通知）同理，走 `/user/{receiverId}/queue/private.invite`。

### 前端要求

**PrivateChatStore**：

```javascript
// 结构
{
  sessions: {
    [peerId]: {
      messages: [],     // { senderId, content, timestamp }
      minimized: false,
      open: false
    }
  }
}

// actions
openSession(peerId)
minimizeSession(peerId)
closeSession(peerId)       // 调用时清空 messages
addMessage(peerId, msg)
```

**PrivateChatWindow 组件**：浮层窗口，支持拖拽，最小化时收缩为右下角图标+未读数角标。

**生命周期绑定**：`UserDetailPanel`（详查界面）unmount 时调用 `privateChatStore.closeSession(peerId)`，清除记录。若对方的详查界面也已关闭（服务端不知道，前端无法感知），则记录在本地保留到自己关闭详查界面为止。

---

## PERF-01 · 性能优化方案

经过对项目架构的整体审视，以下是按优先级排列的优化项。

---

### P0（必须修）

**1. 画板笔画数据节流**

你画我猜的 Canvas 事件（`mousemove`）每秒可能触发 60+ 次，每次都走 STOMP 发送会打爆局域网。

```javascript
// 前端：对笔画数据节流，每 50ms 最多发送一次，合并区间内的所有点
const throttledSend = throttle((points) => {
  stompClient.publish({ destination: '/app/game.draw.stroke', body: JSON.stringify({ points }) })
}, 50)
```

后端接收后**批量广播**，不逐条广播。

**2. WebSocket 消息去重广播**

当前如果多个地方调用 `convertAndSend("/topic/room", ...)` 可能在同一个事务里触发多次（比如房间成员加入时先更新列表再更新计数，触发两次广播）。统一改为：业务操作完成后，**只广播一次完整的最终状态**，而非多次增量。

---

### P1（强烈建议）

**3. 前端 Pinia store 避免整体替换**

目前每次收到 `/topic/room` 或 `/topic/status` 都可能整体替换 store，导致依赖这些数据的所有组件重新渲染。

改为**局部 patch**：只更新变更的字段，Vue 的响应式系统会自动只重渲变更的组件。

```javascript
// 差：整体替换
roomStore.$state = newRoomData

// 好：局部更新
Object.assign(roomStore, newRoomData)  // 或 roomStore.$patch(newRoomData)
```

**4. 前端路由懒加载**

游戏页面（尤其是你画我猜这类有 Canvas 的）应使用动态 import：

```javascript
{
  path: '/game/draw-and-guess',
  component: () => import('../views/games/DrawAndGuessView.vue')
}
```

避免首屏加载所有游戏代码。

**5. 文件下载流量优化**

当前文件存在 Host 本地磁盘，所有 Client 下载都经过 Host。在局域网内这不是问题，但如果文件大（>10MB），Host 的带宽会成为瓶颈。

建议在 `application.properties` 中配置 Spring Boot 的静态资源缓存头：

```properties
spring.web.resources.cache.cachecontrol.max-age=3600
```

局域网内浏览器命中缓存后不再重复请求。

---

### P2（有余力时做）

**6. UDP 心跳频率自适应**

当前 UDP HELLO 包是固定频率广播，建议在选主稳定后降低广播频率（从 1 秒降到 5 秒），仅在检测到网络变化时恢复高频，减少局域网广播风暴。

**7. Canvas 画板渲染批处理**

Canvas 渲染改用 `requestAnimationFrame`，将一帧内收到的所有笔画数据批量绘制，而不是每收到一条消息就立即 `ctx.stroke()`：

```javascript
let pendingStrokes = []
let rafPending = false

function onStrokeReceived(stroke) {
  pendingStrokes.push(stroke)
  if (!rafPending) {
    rafPending = true
    requestAnimationFrame(() => {
      pendingStrokes.forEach(s => drawStroke(ctx, s))
      pendingStrokes = []
      rafPending = false
    })
  }
}
```

**8. 私聊消息体压缩**

私聊消息 payload 不需要携带 `senderName`、`avatar` 等冗余字段，只携带 `senderId + content + timestamp`，前端从 `userListStore` 里查名字和头像。减少单条消息体积约 60%。

---

## 交付物要求

每个修复项和功能项，提供修改后的**完整文件**，不允许省略。文件范围：

**后端新增/修改类**：`ElectionService`、`InvitationService`、`DrawAndGuessGameSession`、`PrivateChatController`、`FileUploadController`（广播部分）、`UserService`（改名广播部分）

**前端新增/修改文件**：`useWebSocket.js`（新增消息处理分支）、`InvitationPanel.vue`、`UserDetailPanel.vue`、`PrivateChatWindow.vue`、`DrawAndGuessView.vue`、`router/index.js`（路由守卫修复 + 懒加载）、所有涉及的 Pinia store 文件

---

## 验证标准

| 编号 | 验证方式 |
|---|---|
| BUG-01 | 手动 kill 当前 Host 进程，旧前端页面 2 秒内自动跳转到新 Host 地址 |
| BUG-01 双主 | 网络正常时，永远只有一个节点的 `isHost` 为 true |
| BUG-02 | 改名后，其他用户的在线列表无需刷新，3 秒内自动同步 |
| BUG-03 | 从发起游戏到邀请面板弹出，延迟不超过 200ms；点击按钮到按钮状态更新不超过 100ms |
| BUG-04 | 页面刷新后，立即点击顶部菜单能成功跳转（无需多次点击） |
| FEATURE-01 | 两名用户能完整跑完一局你画我猜（选词→画画→猜词→计分→换人） |
| FEATURE-02 | 点击在线列表用户，详查面板 500ms 内弹出 |
| FEATURE-03 | 任意用户上传文件后，所有在线用户 2 秒内看到 Toast 提示 |
| FEATURE-04 | 私聊发出到对方收到弹窗，延迟不超过 200ms；双方关闭详查界面后记录消失 |
| PERF-01 | 你画我猜画图时，Network 面板 WebSocket 帧率不超过 20fps |
