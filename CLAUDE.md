# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Classroom LAN v2 - 一个基于 Spring Boot 3.2 + Vue 3 的分布式局域网教室应用重构项目。该项目使用 UDP 组播进行服务发现、WebSocket (STOMP) 进行实时通信、多 Leader 选举机制实现高可用性。

### 核心特性
- **去中心化架构**：所有节点平等，通过 UDP 组播选举 Host，支持故障转移
- **实时通信**：统一 8080 端口，STOMP over WebSocket
- **会话安全**：动态 RoomKey + Token 鉴权
- **插件化游戏引擎**：GameSession 接口支持扩展多种游戏
- **前后端分离**：前端独立部署（Vite + Vue 3），后端纯 API

## Architecture

### 技术栈
- **后端**：Java 17, Spring Boot 3.2, Spring WebSocket (STOMP), Maven 多模块
- **前端**：Vue 3, Vite, Pinia, Vue Router, @stomp/stompjs
- **网络**：UDP Multicast (230.0.0.1:9999), STOMP over WebSocket
- **交付**：Spring Boot 打包为单一 JAR，前端构建为静态资源

### 项目结构
```
classroom-lan-v2/
├── backend/                          # 后端 Maven 多模块
│   ├── pom.xml                      # 父 POM（聚合模块）
│   ├── classroomlan-core/           # 核心领域模型
│   │   └── src/main/java/org/lanclassroom/core/
│   │       ├── model/               # Room, Player, GameType 等实体
│   │       ├── service/             # 业务接口定义
│   │       └── util/                # 工具类
│   ├── classroomlan-net/            # 网络层
│   │   └── src/main/java/org/lanclassroom/net/
│   │       ├── discovery/           # UDP 发现、Host 选举
│   │       ├── ws/                  # WebSocket 配置、STOMP 拦截器
│   │       └── api/                 # REST 控制器
│   └── classroomlan-app/            # 应用启动模块
│       ├── pom.xml
│       └── src/main/java/org/lanclassroom/app/
│           ├── ClassroomLanApplication.java  # SpringBoot 启动类
│           └── config/              # Bean 装配配置
├── frontend/                        # 前端 Vue 3 项目
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── main.js
│       ├── App.vue
│       ├── router/                  # Vue Router 路由（含房间密钥守卫）
│       ├── stores/                  # Pinia 状态管理
│       ├── composables/             # useStomp 等组合式函数
│       ├── views/                   # 页面视图
│       └── components/              # 公共组件
└── alert.bat                        # 开发提醒脚本
```

### 分层架构
1. **Core 层**：领域模型（Room, Player）、服务接口（GameSession）
2. **Net 层**：UDP 发现、WebSocket 配置、STOMP 消息路由、REST API
3. **App 层**：Spring Boot 应用组装、配置类、启动入口

### 核心流程
- **Host 选举**：节点启动 → UDP 组播发送 HELLO → 比较优先级（版本 > 负载 > nodeId）→ 选出 Host
- **客户端连接**：用户输入 roomKey → 前端连接 `/ws` 携带 roomKey → 后端验证 Token → 订阅频道
- **消息路由**：
  - `/app/chat` → 发送聊天 → `/topic/chat` 广播
  - `/app/game.action` → 游戏动作 → `/topic/game.state` 状态推送
  - `/user/queue/private` → 私信
- **故障迁移**：Host 下线 → 其他节点检测心跳丢失 → 重新选举 → 客户端重连

## Common Development Tasks

### 环境要求
- JDK 17+
- Node.js 18+
- Maven 3.8+

### 后端开发
```bash
# 进入后端目录
cd backend

# 安装依赖并编译
mvn clean install

# 运行单个模块（开发模式）
mvn spring-boot:run -pl classroomlan-app -am

# 运行测试
mvn test -pl classroomlan-core

# 打包
mvn package -pl classroomlan-app
```

### 前端开发
```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 开发服务器（热重载）
npm run dev

# 构建生产版本
npm run build

# 预览构建结果
npm run preview
```

### 完整启动流程
```bash
# 1. 构建前端
cd frontend && npm run build

# 2. 前端资源复制到后端（需配置 Maven 插件或手动复制）
# 3. 运行后端 JAR
cd backend/classroomlan-app/target
java -jar classroomlan-app.jar
```

### 调试命令
- **查看 UDP 发现包**：Wireshark 过滤 `udp port 9999 and ip host 230.0.0.1`
- **WebSocket 测试**：浏览器控制台连接 `new WebSocket('ws://localhost:8080/ws')`
- **查看 Host 状态**：访问 `http://localhost:8080/api/status`

## Key Files

### 后端核心文件
- `backend/classroomlan-app/src/main/java/org/lanclassroom/app/ClassroomLanApplication.java` - SpringBoot 启动入口
- `backend/classroomlan-net/src/main/java/org/lanclassroom/net/ws/WebSocketConfig.java` - STOMP WebSocket 配置
- `backend/classroomlan-net/src/main/java/org/lanclassroom/net/discovery/DiscoveryService.java` - UDP 组播发现与 Host 选举
- `backend/classroomlan-core/src/main/java/org/lanclassroom/core/model/Room.java` - 房间实体
- `backend/classroomlan-core/src/main/java/org/lanclassroom/core/GameSession.java` - 游戏会话接口

### 前端核心文件
- `frontend/src/composables/useStomp.js` - STOMP 连接封装
- `frontend/src/router/index.js` - 路由守卫（roomKey 校验）
- `frontend/src/stores/room.js` - Pinia 房间状态管理
- `frontend/src/views/ChatView.vue` - 聊天界面
- `frontend/src/views/GameView.vue` - 游戏界面

## Design Notes

### Host 选举策略
优先级：版本号（高优先）> 系统负载（低优先）> nodeId（最后比较）。避免羊群效应，确保选举收敛。

### 安全性
- 启动时生成唯一的 `roomKey`（十六进制随机串）和 `token`
- URL 需携带 `?key=xxx` 才能进入房间
- Host 的 WebSocket CONNECT 帧必须携带有效 token，否则拒绝

### 状态同步
- 客户端断线重连后，主动拉取 Host 的完整房间快照（REST `/api/room/snapshot`）
- 游戏状态通过 `/topic/game.state` 实时推送

### 消息格式（JSON）
```json
// HELLO 消息
{ "type": "HELLO", "host": false, "id": "node-uuid", "nodeId": "node-uuid" }

// BEAT 消息（Host 发送）
{ "type": "BEAT", "hostId": "host-node-id", "roomKey": "xxx", "timestamp": 1234567890 }

// 聊天消息
{ "type": "CHAT", "sender": "player-name", "content": "hello", "timestamp": 1234567890 }

// 游戏动作
{ "type": "GAME_ACTION", "gameType": "DRAW", "payload": { ... } }
```

### 端口配置
- HTTP + WebSocket：8080（统一端口）
- UDP 发现：9999（组播地址 230.0.0.1）
- 前端 Dev Server：5173（开发时）

## 注意事项
- 文件暂存于 Host 本地临时目录，重启会清空
- 聊天历史在 Host 切换时会丢失（设计如此）
- 所有节点需在同一局域网（UDP 组播限制）
- 前端构建产物需手动复制到后端的 `src/main/resources/static/`（或配置 Maven 插件自动处理）
