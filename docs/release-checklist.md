# Release Checklist — rikka-agent

> 发布前逐项执行。

## 构建验证

- [ ] `./gradlew assembleRelease` 成功，无编译错误
- [ ] `./gradlew :core:model:testDebugUnitTest :core:storage:testDebugUnitTest :core:ssh:testDebugUnitTest :core:ui:testDebugUnitTest :app:testDevDebugUnitTest` 成功
- [ ] `./gradlew :app:lintDevDebug :app:assembleDevDebug` 成功
- [ ] ProGuard 混淆后 APK 功能正常（连接测试）
- [ ] APK 签名正确（release keystore）
- [ ] minSdk / targetSdk 值确认

## 功能冒烟测试

- [ ] 创建新 SSH profile（密码认证）→ 连接成功 → 执行命令 → 输出正常
- [ ] 创建新 SSH profile（公钥认证）→ 连接成功 → 执行命令 → 输出正常
- [ ] Host key 首次信任流程正常
- [ ] Host key 变更警告正常
- [ ] Host key 替换信任二次确认流程正常
- [ ] Ed25519 密钥生成 → 导出公钥 → 正常使用
- [ ] Codex 模式开启 → 任务执行 → JSONL 输出正常渲染
- [ ] Mermaid 开关开启/关闭均正常，失败时可降级显示源码
- [ ] Codex API Key 密码字段 → 遮盖/显示切换正常
- [ ] 会话管理：新建/切换/删除/导出
- [ ] Markdown 渲染：标题/列表/代码块/表格/删除线
- [ ] 中文语言环境下所有 UI 显示正确
- [ ] 英文语言环境下所有 UI 显示正确
- [ ] AMOLED/Dark/Light 主题切换正常
- [ ] 设置页面：主题/Shell/Known Hosts/About 正常

## 安全检查

- [ ] 隐私审计清单全部通过 (`docs/privacy-audit.md`)
- [ ] 无敏感信息硬编码（grep 搜索 `password|secret|token|api_key` 等）
- [ ] `network_security_config.xml` 禁止明文
- [ ] Release 构建不含调试日志

## 文档

- [ ] README.md 更新（功能列表、截图、构建说明）
- [ ] SECURITY.md 已有联系方式
- [ ] CONTRIBUTING.md 已有贡献指南
- [ ] CODE_OF_CONDUCT.md 已有行为准则
- [ ] LICENSE 文件正确

## 发布

- [ ] 版本号 `versionName` / `versionCode` 已更新
- [ ] Git tag 已创建
- [ ] GitHub Release 已创建（附 APK + changelog）
- [ ] F-Droid metadata（如适用）
