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
| **Phase 2** | 🔄 进行中 | 2A ExtendColors ✅ 2C Lucide ✅ 2B/2D ⏳ |
| **Phase 3** | ⏳ | UI 组件增量复刻 |
| **ADB 验收** | ⏳ | Mumu ADB 端口冲突，待手动恢复 |

## 当前阶段

**Phase 0：干净基线建立** ✅

目标：确认原始代码在 SDK 36 下编译通过，APK 可构建。
结果：编译成功，APK 24MB，已安装到模拟器。

## 下一步

**Phase 1：数据模型升级** ✅

- 1A MessagePart sealed class ✅ (`ee9d0c9`)
- 1B ChatMessage.parts field ✅ (`4a200ba`)
- 1C Room v5 + TypeConverter + Migration ✅ (`6afa71b`)

**Phase 2：主题系统对齐** ⏳
- ExtendColors + MaterialExpressiveTheme
- 预设主题（Sakura/Ocean/Spring/Autumn/Black）
- Lucide Icons 替换

**Phase 3：UI 组件复刻** ⏳
- ChatBubble → MessagePartsBlock
- MarkdownBlock → IntelliJ MarkdownParser
- HighlightCodeBlock → 语法高亮
- ChatInput 增强

每个 Phase 要求：逐文件增量添加 + 编译验证通过后 commit。

## 参考资料

- 分析报告：docs/analysis/（待恢复）
- 规划文档：docs/plan/（待恢复）
- 设计文档：docs/adr/, docs/architecture.md, docs/plan.md
- Spec 规范：docs/spec/
