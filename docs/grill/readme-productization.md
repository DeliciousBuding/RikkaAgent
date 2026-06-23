# Grill: README 产品化与仓库展示面优化

Date: 2026-06-23

## Intent
将 RikkaAgent 的 README 从"技术项目文档"升级为"产品展示页面"——面向开发者（目标受众），突出"最漂亮的 Android SSH 客户端"定位，用 RikkaHub 的设计语言作为背书而非包袱。

## Constraints
- 保持现有 README 结构（表格为主，不引入 HTML/CSS 卡片）
- 保持 Hero SVG 占位图（后续替换为真实截图）
- 不放 GIF/视频（A 方案：截图+文字）
- 仓库不再归档，保持公开

## Key decisions
- **定位**: "开发者的瑞士军刀"——手机端 SSH + AI CLI 结合体。主场景：运维快速响应（掏出手机→SSH→诊断命令→漂亮输出）
- **Hero 情绪**: "优雅工具"——Material You 设计美感作为第一印象
- **RikkaHub 引用**: 强化背书——放在独立的 Design 区域，标注"5.5k ⭐"，强调设计语言对齐而非代码复制
- **FAQ 增强**: 新增 Q1（vs Termux/ConnectBot/JuiceSSH 比较）和 Q6（RikkaHub 关系说明）
- **Docs Hub**: 按 Product/Security/Testing/Engineering/Roadmap/Analysis 六类重新组织

## Surfaced assumptions
- 用户认为 README 是开源仓库的核心展示面，不仅仅是技术文档
- "产品化"意味着 README 应该让来访者在 10 秒内理解这个项目是什么、为什么好、和替代品有什么不同
- RikkaHub 的关联应该被正视并转化为优势，而非回避

## Open questions
- Android SDK 未安装，构建未能完成
- ADB 截图验收未执行（依赖 SDK + Mumu 模拟器）
- Hero SVG 仍是占位图，需替换为真实应用截图

## Out of scope
- 引入 GIF/视频 Demo
- 在线 Playground
- 完全移除 RikkaHub 引用
