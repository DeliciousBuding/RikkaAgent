# RikkaAgent × RikkaHub UI 对齐差距分析

> 分析日期：2026-06-23
> 基准：RikkaHub 2.3.2 (re-ovo/rikkahub)
> 目标：RikkaAgent 1:1 复刻 UI/前端层，适配 SSH 场景

## 已完成对齐

| 维度 | 状态 | 说明 |
|------|------|------|
| MeshGradientBackground | ✅ | Gemini 风格动态渐变，4 光斑漂移 |
| 透明 TopBar/Scaffold | ✅ | 渐变穿透 |
| ChatInput 圆角容器 | ✅ | 边框 + 底部按钮行 |
| Lucide Icons | ✅ | 全模块替换，零 Material Icons |
| Component 比例 | ✅ | ChatInput/Bubble/Card 尺寸对齐 |
| MessagePart 渲染 | ✅ | 8 类型 sealed class + ChatBubble 集成 |
| SshOutputMapper | ✅ | SSH stdout/stderr → MessagePart 桥接 |
| Portrait 锁定 | ✅ | AndroidManifest screenOrientation |

## 待完成对齐

### P0 — 核心体验
| # | 差距 | RikkaHub 参考 | 当前状态 |
|---|------|--------------|---------|
| 1 | **Haze 毛玻璃输入栏** | `dev.chrisbanes.haze` 毛玻璃效果 | 仅普通 Surface |
| 2 | **ChatScreen 渐变背景** | 已加但未验证聊天场景 | 需端到端测试 |
| 3 | **ChatInput 底部按钮行** | 模型选择/搜索/推理开关/附件 | 仅发送按钮 |
| 4 | **EmptySessionState** | 建议 chip + 渐变中心 | 已有 chip 但样式粗糙 |

### P1 — 视觉精修
| # | 差距 | 说明 |
|---|------|------|
| 5 | **SessionDrawer** | RikkaHub 有用户头像/搜索/历史，我们只有简单列表 |
| 6 | **ProfileCard 样式** | RikkaHub 用 ListItem + surfaceColorAtElevation |
| 7 | **SettingsScreen 渐变** | 未加 MeshGradientBackground |
| 8 | **ProfileEditor 卡片** | 边框线/阴影/圆角需微调 |

### P2 — 功能组件
| # | 差距 | 说明 |
|---|------|------|
| 9 | **ReasoningCard** | 推理步骤折叠展示 |
| 10 | **DataTable** | 结构化数据渲染 |
| 11 | **Mermaid 渲染** | 已有 fallback 但未集成 |
| 12 | **Codex JSONL** | 流式解析已部分实现 |

### P3 — 文档与安全
| # | 差距 | 说明 |
|---|------|------|
| 13 | **PRD 文档** | 缺失 |
| 14 | **架构文档** | docs/architecture.md 待更新 |
| 15 | **安全审查** | 敏感信息/隐私/SSH 密钥处理 |
| 16 | **README 优化** | grill-me skill 已做部分 |

## 实施优先级

1. **Phase A**: P0 核心体验（Haze 毛玻璃、ChatInput 按钮行、端到端测试）
2. **Phase B**: P1 视觉精修（Drawer、ProfileCard、Settings 渐变）
3. **Phase C**: P2 功能组件（ReasoningCard、DataTable）
4. **Phase D**: P3 文档与安全（PRD、架构、安全审查、README）
