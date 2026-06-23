# 设计规范合规审查报告

> 审查日期：2026-06-23
> 审查依据：docs/design-system.md
> 审查范围：9 个 Screen 文件 + 3 个 Component 文件 + 主题定义

## 合规总览

| 维度 | 通过项 | 问题数 | 最高严重度 |
|------|--------|--------|------------|
| 颜色 | 屏幕文件无硬编码颜色 | 5 | P0 — primary/secondary 语义反转 |
| 间距 | SettingsScreen、KnownHostsScreen 全合规 | 12 | Critical — ChatBubble 内边距违反规范 |
| 圆角 | FAB、Avatar、Card 默认值合规 | 8 | P0 — ChatBubble 18dp、ChatInput 24dp 非标准 |
| 内容 | EN 侧无 "please" 违规 | 30 | P0 — 禁用词 "请" 6 处、Toast 尾句号 3 处 |
| 动效 | LinearProgressIndicator 可接受 | 9 | P0 — infiniteRepeatable 循环动画 |
| 组件 | 等宽字体正确引用 | 14 | P0 — ChatBubble 颜色 token 错误 |

**总计：78 处问题，P0/Critical 16 处，P1 24 处，P2/P3 38 处。**

## 修复优先级

| # | 问题 | 影响范围 | 难度 |
|---|------|----------|------|
| 1 | C1+C2: primary 语义反转 + 缺失 token | 全局 | 低 |
| 2 | C9+C10: 禁用词 + 尾句号 | 所有文案 | 低 |
| 3 | C3+C11: ChatBubble 圆角 + 内边距 | 核心体验 | 低 |
| 4 | C4+C5: ChatBubble 颜色 token | 核心体验 | 低 |
| 5 | M13: 自定义 Shapes 主题覆盖 | 全局 | 中 |
| 6 | C6: ChatInput 圆角 | 输入体验 | 低 |
| 7 | C7: StreamingDots 循环动画 | 加载状态 | 低 |
| 8 | M9: IconButton 交互态 | 全局 | 中 |
| 9 | M3: 错误消息补全操作指引 | 错误场景 | 低 |
| 10 | M5: animateContentSize 300→150ms | 动效 | 低 |
