# RikkaHub Root 集成设计

## 目标
在 `rikkahub` 主 app 内直接集成 root 能力，实现以下目标：

- 全局 Root 总开关
- Root 状态检测与展示
- AI 全局可用的 `shell_exec` 工具
- App 内置交互式 Root 终端页
- `shell_exec` 与 MCP 工具同时挂载、同时可用
- MCP 离线时，AI 仍可直接执行 root 命令

## 总体架构

### Root 能力形态
- 直接集成到 `rikkahub` 主 app
- 使用 `libsu` 作为 root 执行层
- 在 `RikkaHubApp.kt` 完成初始化
- root 授权由 Magisk / KernelSU / APatch 处理，app 侧展示实时状态

### 新增核心模块
- `RootManager`
  - 检测 `su` 可用性
  - 检测当前 root 授权状态
  - 提供一次性命令执行接口
- `RootTerminalSessionManager`
  - 维护交互式 root shell 会话
  - 提供 `start / send / read / stop`
  - 供 UI 终端页使用
- `RootToolBridge`
  - 把 root 执行能力包装成 AI 工具 `shell_exec`
  - 返回 `stdout / stderr / exitCode / durationMs`

### 工具挂载策略
- `shell_exec` 作为全局本地工具
- 只要全局 root 开关打开，全部 assistant 自动获得该工具
- MCP 工具继续正常挂载
- ChatService 组装工具时形成：
  - MCP tools
  - 现有 local tools
  - 全局 root tool

### UI 入口
- 全局设置页新增 Root 集成入口
- Root 设置页展示：
  - 总开关
  - root 状态
  - `su` 可用状态
  - 当前授权状态
  - shell 就绪状态
  - 当前 root 管理器类型
  - 终端入口按钮
- 新增交互式 Root 终端页
  - 长会话
  - 输入框 + 输出区
  - 会话重连 / 清屏 / 结束会话

## 运行时数据流与状态模型

### 配置与状态分层
持久化配置放入 `SettingsStore`：
- `rootEnabled: Boolean`

运行时状态放入 `RootManager`：
- `suAvailable`
- `grantState`: `Checking / Granted / Denied / Error`
- `managerType`: `Magisk / KernelSU / APatch / Unknown`
- `shellReady`
- `activeTerminalSession`

### Root 状态检测流程
触发时机：
- App 启动后做一次轻量探测
- 进入设置页时刷新一次
- 用户点击刷新状态时即时刷新
- 打开终端页前再做一次会话准备

检测顺序：
1. 探测 `su` 可执行入口
2. 建立一次短连接 shell
3. 读取 `id` / `whoami` / shell flags
4. 归一化成 UI 状态对象

UI 展示字段：
- `su` 是否存在
- 当前授权状态
- 当前 shell 是否已就绪
- 当前 root 管理器类型
- 当前终端会话是否活跃

### AI 工具数据流
`ChatService` 组装工具时，工具列表变成：
- MCP tools
- 现有 local tools
- 全局 root tool: `shell_exec`

行为规则：
- 全局开关开启：`shell_exec` 挂载给全部 assistant
- 全局开关关闭：`shell_exec` 从工具列表移除
- MCP 在线：AI 同时看到 MCP 工具和 `shell_exec`
- MCP 离线：AI 继续看到 `shell_exec`

`shell_exec` 输入：
```json
{
  "command": "id && whoami",
  "timeout": 30,
  "interactive": false
}
```

返回：
```json
{
  "stdout": "...",
  "stderr": "...",
  "exitCode": 0,
  "durationMs": 123,
  "root": true
}
```

### 交互式 Root 终端数据流
终端页走独立长会话：
- 打开页面 → `RootTerminalSessionManager.startSession()`
- 用户输入命令 → `send(command + "\\n")`
- 输出流持续推送到 UI
- 页面退出或点击关闭 → `stopSession()`

这条链路和聊天工具链路相互独立，实现：
- 终端会话持续运行
- AI 同时调用 `shell_exec`
- MCP 同时在线参与工具调用

## UI、并行执行与授权细节

### 设置入口
新增独立页面 `SettingRootPage`，从现有 `SettingPage` 进入。

页面包含：
- 全局 Root 总开关
- Root 状态卡片
- 刷新状态按钮
- 打开 Root 终端按钮

### 交互式 Root 终端页
新增 `RootTerminalPage`，包含：
- 自动建立交互式 root shell 会话
- 输出区持续显示 shell 回显
- 输入框发送命令
- 顶部展示会话状态
- 操作按钮：清屏、重建会话、结束会话

页面生命周期：
- 进入页面时准备会话
- 离开页面时关闭会话
- 页面内保持持续交互

### AI 与终端并行策略
- AI `shell_exec`
  - 使用一次性 root 命令执行通道
  - 每次调用独立执行
  - 返回 `stdout / stderr / exitCode / durationMs`
- UI Root 终端
  - 使用独立长会话通道
  - 服务交互式终端页面
- MCP
  - 保持现有链路
  - 与本地 root 工具同时挂载

并行结果：
- MCP 在线可用
- AI 本地 `shell_exec` 可用
- UI 交互式 Root 终端可用

### 授权模型
- 全局 Root 开关打开后，全部 assistant 自动获得 `shell_exec`
- 首次真正执行 root shell 时，由 root 管理器完成授权弹窗
- 授权结果写回运行时状态，UI 立即刷新
- 后续 AI 调用按全局策略自动执行

### 工具行为边界
`shell_exec` 作为明确的 root 命令工具，参数精简：
```json
{
  "command": "id && whoami",
  "timeout": 30
}
```

返回：
```json
{
  "stdout": "uid=0(root) gid=0(root)\nroot\n",
  "stderr": "",
  "exitCode": 0,
  "durationMs": 58,
  "root": true
}
```

## 错误处理、测试策略与首版范围

### 错误处理模型
统一结构化状态：
- `su_unavailable`
- `permission_denied`
- `timeout`
- `session_lost`
- `exec_failed`

错误返回：
```json
{
  "stdout": "",
  "stderr": "permission denied",
  "exitCode": -1,
  "durationMs": 35,
  "root": true,
  "errorType": "permission_denied",
  "message": "Root authorization was denied by the manager"
}
```

### 测试策略
抽象执行接口：
- `ShellRuntime`
- `LibsuShellRuntime`
- `FakeShellRuntime`

测试分层：

#### 单元测试
覆盖：
- `Settings` 新增 `rootEnabled` 默认值与持久化
- `LocalTools` 在全局开关开启时挂载 `shell_exec`
- `shell_exec` 返回结构化结果
- Root 状态映射逻辑
- 超时、拒绝授权、命令失败结果序列化

#### Compose / Android 测试
覆盖：
- `SettingRootPage` 显示 root 状态
- 全局开关切换后状态刷新
- 终端页显示输出、发送输入、重建会话按钮可用
- root 状态卡片内容与 fake runtime 一致

#### 真机验证
在 rooted 设备上验证：
1. 打开全局 Root 开关
2. 首次触发授权
3. AI 调用 `shell_exec`
4. 打开交互式 Root 终端
5. MCP 可用时同时执行 MCP 工具和 `shell_exec`
6. MCP 离线时继续执行 `shell_exec`
7. 终端页持续会话下，AI 同时执行一次性 root 命令
8. 超时命令与拒绝授权场景

### 首版范围
首版包含：
- 全局 Root 开关
- Root 状态检测
- Root 管理器类型展示
- AI `shell_exec`
- 交互式 Root 终端页
- 与 MCP 同时挂载、同时执行
- 真机验证与基础测试覆盖

预留扩展点：
- 命令历史持久化
- 多终端会话
- 命令模板
- root 文件管理
- 长任务后台驻留
