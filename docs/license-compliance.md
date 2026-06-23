# RikkaAgent License Compliance Report

审计日期：2026-06-23
审计范围：RikkaAgent 项目全部模块（app、core/model、core/ssh、core/storage、core/ui）

---

## 1. 主 License

| 项目 | 值 |
|---|---|
| License 文件 | `LICENSE` (项目根目录) |
| License 类型 | Apache License 2.0 |
| 合规状态 | **PASS** |

Apache-2.0 是业界标准的宽松开源许可证，允许商业使用、修改和再分发，无传染性。

---

## 2. 依赖 License 清单

### 2.1 Apache-2.0 依赖（与主 License 完全兼容）

| 依赖 | 模块 | 版本 |
|---|---|---|
| AndroidX Core KTX | androidx.core:core-ktx | 1.13.1 |
| AndroidX Activity Compose | androidx.activity:activity-compose | 1.9.2 |
| AndroidX Lifecycle Runtime KTX | androidx.lifecycle:lifecycle-runtime-ktx | 2.8.5 |
| AndroidX Lifecycle Runtime Compose | androidx.lifecycle:lifecycle-runtime-compose | 2.8.5 |
| AndroidX Lifecycle ViewModel Compose | androidx.lifecycle:lifecycle-viewmodel-compose | 2.8.5 |
| AndroidX Navigation Compose | androidx.navigation:navigation-compose | 2.8.0 |
| Jetpack Compose BOM | androidx.compose:compose-bom | 2024.09.00 |
| Material3 | androidx.compose.material3:material3 | 1.3.0 |
| Material Icons Extended | androidx.compose.material:material-icons-extended | (BOM) |
| AndroidX Room Runtime | androidx.room:room-runtime | 2.6.1 |
| AndroidX Room KTX | androidx.room:room-ktx | 2.6.1 |
| AndroidX DataStore Preferences | androidx.datastore:datastore-preferences | 1.1.1 |
| AndroidX Security Crypto | androidx.security:security-crypto | 1.1.0-alpha06 |
| Kotlin Stdlib | org.jetbrains.kotlin.* | 2.1.0 |
| KotlinX Serialization JSON | org.jetbrains.kotlinx:kotlinx-serialization-json | 1.6.3 |
| KotlinX Coroutines Core | org.jetbrains.kotlinx:kotlinx-coroutines-core | 1.8.1 |
| JetBrains Markdown | org.jetbrains:markdown | 0.7.3 |
| Koin BOM | io.insert-koin:koin-bom | 3.5.6 |
| Koin Android | io.insert-koin:koin-android | (BOM) |
| Koin Compose | io.insert-koin:koin-androidx-compose | (BOM) |
| Coil Compose | io.coil-kt:coil-compose | 2.7.0 |
| Okio | com.squareup.okio:okio | 3.9.1 |
| SSHJ | com.hierynomus:sshj | 0.39.0 |
| Turbine | app.cash.turbine:turbine | 1.1.0 |
| Robolectric | org.robolectric:robolectric | 4.14.1 |
| AGP (build plugin) | com.android.application | 8.8.0 |
| KSP (build plugin) | com.google.devtools.ksp | 2.1.0-1.0.29 |

### 2.2 MIT License 依赖（与 Apache-2.0 兼容）

| 依赖 | 版本 | 说明 |
|---|---|---|
| Bouncy Castle (bcprov-jdk18on) | 1.78.1 | 加密库，MIT License |
| SLF4J API | 2.0.13 | 日志门面，MIT License |

### 2.3 BSD-2-Clause 依赖（与 Apache-2.0 兼容）

| 依赖 | 版本 | 说明 |
|---|---|---|
| CommonMark | 0.22.0 | Markdown 解析器，BSD-2-Clause |
| CommonMark GFM Tables | 0.22.0 | 同上 |
| CommonMark GFM Strikethrough | 0.22.0 | 同上 |

### 2.4 ISC License 依赖（与 Apache-2.0 兼容）

| 依赖 | 版本 | 说明 |
|---|---|---|
| Lucide Icons (composables) | 1.1.0 | 图标库，ISC License |

### 2.5 EPL-2.0 依赖（测试范围，兼容）

| 依赖 | 版本 | 范围 | 说明 |
|---|---|---|---|
| JUnit 4 | 4.13.2 | testImplementation | EPL-2.0，仅测试 |
| JaCoCo | (Gradle plugin) | build tool | EPL-2.0，仅构建工具 |

### 2.6 AndroidX Test 依赖（Apache-2.0，仅测试）

| 依赖 | 版本 | 范围 |
|---|---|---|
| androidx.test:core | 1.6.1 | testImplementation / androidTestImplementation |
| androidx.test.ext:junit | 1.2.1 | androidTestImplementation |
| androidx.test:rules | 1.6.1 | androidTestImplementation |
| androidx.test:runner | 1.6.2 | androidTestImplementation |
| androidx.test.espresso:espresso-intents | 3.6.1 | androidTestImplementation |
| Room Testing | 2.6.1 | androidTestImplementation |

---

## 3. AGPL/GPL 依赖检查

| 检查项 | 结果 |
|---|---|
| AGPL 依赖 | **无** |
| GPL 依赖 | **无** |
| LGPL 依赖 | **无** |

`build.gradle.kts` 中的 `excludes += "/META-INF/{AL2.0,LGPL2.1}"` 是 Android 项目的标准打包配置，用于移除 META-INF 下重复的 License 声明文件。该排除不涉及任何实际 LGPL 代码的引入，仅为避免打包冲突。Bouncy Castle 从 1.78 版本起已采用纯 MIT 许可。

---

## 4. License 兼容性总览

| 许可证 | 数量 | 与 Apache-2.0 兼容 |
|---|---|---|
| Apache-2.0 | ~28 | 是（同类） |
| MIT | 2 | 是 |
| BSD-2-Clause | 3 | 是 |
| ISC | 1 | 是 |
| EPL-2.0 | 2 | 是（测试/构建工具） |

所有依赖许可证均与 Apache-2.0 兼容，无许可证冲突。

---

## 5. NOTICE 文件

**状态：缺失**

Apache-2.0 Section 4(d) 规定：

> If the Work includes a "NOTICE" text file as part of its distribution, then any Derivative Works that You distribute must include a readable copy of the attribution notices contained within such NOTICE file.

虽然 Apache-2.0 本身不强制要求创建 NOTICE 文件，但最佳实践建议：

1. 如果项目包含或修改了其他 Apache-2.0 项目代码，应创建 NOTICE 文件记录原始归属。
2. 作为下游分发者，应汇总所有依赖的版权和许可声明。

**建议：** 创建 `NOTICE` 文件，列出项目版权信息和主要依赖归属。

---

## 6. 第三方 Attribution 完整性

**状态：不完整**

当前项目缺少以下合规文件：

| 文件 | 状态 | 说明 |
|---|---|---|
| `NOTICE` | 缺失 | Apache-2.0 最佳实践要求 |
| `THIRD_PARTY_LICENSES.md` | 缺失 | 列出所有依赖的许可证文本 |

Android 应用通常在 APK 的 `META-INF/` 中包含依赖的 License 文件，但项目显式排除了 `META-INF/{AL2.0,LGPL2.1}`。建议通过以下方式补充 attribution：

1. 创建 `NOTICE` 文件，包含项目版权和 Apache-2.0 标准声明。
2. 创建 `THIRD_PARTY_LICENSES.md`，列出所有非 Apache-2.0 依赖（MIT、BSD、ISC、EPL）的完整许可证文本和版权归属。

---

## 7. 合规建议清单

| 优先级 | 建议 | 说明 |
|---|---|---|
| 建议 | 创建 `NOTICE` 文件 | 列出项目版权和 Apache-2.0 归属声明 |
| 建议 | 创建 `THIRD_PARTY_LICENSES.md` | 汇总 Bouncy Castle (MIT)、SLF4J (MIT)、CommonMark (BSD-2-Clause)、Lucide (ISC)、JUnit (EPL-2.0) 的许可证文本 |
| 可选 | 在 About 页面展示开源许可 | Android 应用常见做法，通过 `libs.versions.toml` 即可生成 |
| 无需操作 | META-INF 排除规则 | 标准 Android 配置，不引入合规风险 |

---

## 8. 结论

RikkaAgent 项目在 License 层面**基本合规**：

- 主 License 为 Apache-2.0，无歧义。
- 所有 36 个依赖的许可证均与 Apache-2.0 兼容。
- 无 AGPL/GPL/LGPL 传染性依赖。
- 无许可证冲突。

需要改进的方面：

- 缺少 `NOTICE` 文件和第三方许可证汇总文件，建议在正式发布前补充。
- `META-INF` 排除规则不影响合规性，但意味着需要在其他地方提供 attribution。

---

*本报告基于项目源码中的 `LICENSE`、`build.gradle.kts` 和 `libs.versions.toml` 进行分析。未对传递依赖做深度递归审计。如需更严格的合规审查，建议使用 `./gradlew dependencies` 生成完整依赖树并逐一核实。*
