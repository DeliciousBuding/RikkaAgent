# MASTER.md — RikkaAgent 重构进度

> 最后更新：2026-06-23
> 追踪模式：LOCAL_ONLY

## 项目状态

| 维度 | 状态 | 说明 |
|------|------|------|
| **编译** | ✅ 通过 | SDK 36, 每次增量验证 |
| **APK** | ✅ 24MB | `app-dev-debug.apk` |
| **基线** | ✅ | commit `99f1423` |
| **Phase 1** | ✅ 3 commits | MessagePart + ChatMessage + Room v5 |
| **Phase 2** | ✅ 3 commits | ExtendColors + Lucide Icons (6 屏 11 处) |
| **设计合规** | ✅ 3 commits | P0+P1+P2 全部修复 (78→0 Critical) |
| **ADB 验收** | ✅ | MuMu 127.0.0.1:5555，DPI 420，竖屏模式正常 |
| **Material Icons** | ✅ 4 commits | 全部替换为 Lucide Icons（全模块零残留） |
| **SshOutputMapper** | ✅ | SSH 输出→MessagePart 桥接，ChatViewModel 已集成 |
| **UI 验收** | ✅ | 首页/设置/配置编辑器 三屏截图通过 |
| **MeshGradient** | ✅ | Gemini 风格动态渐变背景，双屏已集成 |
| **ChatInput 重做** | ✅ | 圆角容器 + 边框 + 底部按钮行，对齐 RikkaHub |
| **标题比例** | ✅ | BasicText 18sp，绕过 LocalTextStyle |
| **差距分析** | ✅ | 完整 UI 差距文档，P0-P3 优先级排列 |

## 当前阶段

**Phase 0：干净基线建立** ✅

目标：确认原始代码在 SDK 36 下编译通过，APK 可构建。
结果：编译成功，APK 24MB，已安装到模拟器。

**Phase 1：数据模型升级** ✅

- 1A MessagePart sealed class ✅ (`ee9d0c9`)
- 1B ChatMessage.parts field ✅ (`4a200ba`)
- 1C Room v5 + TypeConverter + Migration ✅ (`6afa71b`)

**Phase 2：主题系统对齐** ✅
- ExtendColors + MaterialExpressiveTheme ✅
- Lucide Icons 全模块替换 ✅
- SshOutputMapper + ChatViewModel 集成 ✅
- UI 三屏截图验收 ✅

## 下一步

**Phase 3：Chat 完整流程验证**
- 创建 SSH 配置 → 连接 → 执行命令 → 查看 MessagePart 渲染
- Codex 模式 JSONL 流式解析验证
- MarkdownBlock / HighlightCodeBlock 实际效果验收

**Phase 4：完善与打磨**
- ReasoningCard 推理步骤展示
- DataTable 结构化数据渲染
- ChatInput 增强（多行、历史命令）
- 导出/分享功能端到端测试

每个 Phase 要求：逐文件增量添加 + 编译验证通过后 commit。

## 参考资料

- 分析报告：docs/analysis/（待恢复）
- 规划文档：docs/plan/（待恢复）
- 设计文档：docs/adr/, docs/architecture.md, docs/plan.md
- Spec 规范：docs/spec/
