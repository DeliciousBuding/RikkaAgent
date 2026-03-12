# STATE — rikka-agent 基础事实

> 用途：集中记录项目和基础设施的**静态事实**（地址、配置、架构、决策）。不含进度和历史。
>
> 进度 → `ROADMAP.md` ｜ 历史 → `ARCHIVE.md`

---

## 1. rikka-agent 关键决策（SSOT）

| 决策项 | 值 |
|--------|-----|
| 产品形态 | Android App（Kotlin + Jetpack Compose） |
| 交互模式 | Mode A（非交互 exec channel；不做 PTY/ANSI 渲染） |
| 连接方式 | Android 原生 SSH 直连；不引入"服务端 HTTP 远程执行中转" |
| 安全原则 | Known-hosts 默认开启；host key mismatch 默认阻断；密钥加密存储 |
| 开源协议 | Apache-2.0 |
| Clean-room | 参考 UI "感觉"，不拷贝参考项目代码 |
| SSH 库 | sshj 0.39.0 (BSD-2-Clause)，支持 Ed25519/RSA/ECDSA |
| Markdown 解析 | commonmark-java 0.22.0 (BSD-2-Clause) + GFM tables/strikethrough |
| 密钥存储 | EncryptedFile (AES-256-GCM via AndroidX Security Crypto) |
| Codex 集成 | `codex exec --full-auto` via SSH (exec channel) |
| 国际化 | 中英双语 (values/strings.xml + values-zh/strings.xml)，中文优先 |
| 持久化 | Room DB v3 (聊天/配置/Codex字段) + DataStore (偏好) |
| DI | Koin |
| Spec 索引 | `docs/spec/00-index.md` |

---

## 2. 服务器清单

### 2.1 gz — 阿里云广州

| 条目 | 值 |
|------|-----|
| 公网 IP | 8.163.12.208 |
| Tailscale IP | 100.96.101.24 |
| SSH 别名 | `gz` |
| OS | Ubuntu, kernel 6.8.0-63 |
| 资源 | 1.6GB RAM, 40GB disk |
| 服务 | metapi (Docker :4001, --network host), CPA v6.8.51 (systemd :8317), nginx (80/8443) |
| 限制 | 443 被 ISP 拦截（无 ICP 备案），公网流量必须经 sgp2 |
| 安全 | fail2ban, unattended-upgrades, SSH 99-delicious-hardening.conf (PermitRootLogin no, PasswordAuth no) |
| Cron | */5 measure-snapshot.py, * token-stats.py |

### 2.2 sgp2 — Azure 新加坡（公网边缘）

| 条目 | 值 |
|------|-----|
| 公网 IP | 20.195.40.11 |
| Tailscale IP | 100.82.99.84 |
| SSH | `root@100.82.99.84` |
| OS | Ubuntu, kernel 6.14.0-1017-azure |
| 资源 | 847MB RAM |
| 角色 | **公网入口**：DNS A 记录指向此处 |
| 服务 | nginx 443 → 静态门户 + 反代 gz metapi/CPA |
| SSL | vectorcontrol.tech + www, certbot.timer 自动续期, nginx authenticator |
| 安全 | fail2ban, unattended-upgrades, UFW policy DROP (22/80/443 + TS SOCKS 1080) |

### 2.3 sgp1 — DigitalOcean 新加坡

| 条目 | 值 |
|------|-----|
| 公网 IP | 103.253.145.251 |
| Tailscale IP | 100.117.129.78 |
| SSH 别名 | `sgp1`（user: ding） |
| OS | Ubuntu |
| 资源 | 2GB RAM, 58GB disk |
| 服务 | Docker VectorControl (nginx/backend/postgres on 443), danted SOCKS (TS-only 1080) |
| 注意 | OpenClaw 将迁至 gz；迁走后释放 ~447MB，sgp1 专注 API 后端 |

### 2.4 hk — Azure 香港（East Asia）

| 条目 | 值 |
|------|-----|
| 公网 IP | 104.214.176.143 |
| Tailscale IP | 100.96.116.54 |
| SSH 别名 | `hk`（user: azureuser） |
| OS | Ubuntu 24.04, kernel 6.14.0-1017-azure |
| 资源 | 2核, 1GiB RAM (843MB), Standard B2ats v2 |
| 地理 | **香港**（ipinfo.io 验证: `city: Hong Kong, country: HK`） |
| 目标角色 | 中国用户边缘入口（nginx → proxy sgp1） |
| 限制 | **OpenAI 返回 403 Forbidden**（地区封锁）→ 不能运行 metapi/CPA |
| 安全 | UFW (22/80/443/41641, Tailscale 100.64.0.0/10), fail2ban, SSH hardening (PasswordAuth no) |
| 创建日期 | 2026-06-12 |
| 注意 | 本机公网 ICMP 不通（Azure 或路由问题），需通过 Tailscale 或 ProxyJump=gz |

### 2.5 doris — 本地 WSL

| 条目 | 值 |
|------|-----|
| Tailscale IP | 100.76.47.6 |
| 角色 | 开发/测试，运行 OpenClaw gateway |

---

## 3. VectorControl Portal 部署架构

```
用户 → DNS (vectorcontrol.tech / www.vectorcontrol.tech)
     → A 记录 20.195.40.11
     → sgp2 (Azure Singapore)
       ├─ nginx 443 → 静态门户 /var/www/vectorcontrol-portal/
       ├─ /management → proxy_pass sgp1:8317 (2ms, 迁移后)
       ├─ /fund/ → alias /var/www/vectorcontrol-fund/ (React SPA 投研系统)
       ├─ /playground/ → proxy_pass sgp1:4001 (2ms, 迁移后)
       ├─ /api/, /v1/ → proxy_pass sgp1:4001 (2ms, 迁移后)
       └─ SSL: /etc/letsencrypt/live/www.vectorcontrol.tech/ (certbot + nginx)

sgp1 (DigitalOcean Singapore) [迁移后的 API 后端]
  ├─ metapi Docker (port 4001) = OpenAI 兼容 API 网关
  ├─ CPA Docker (port 8317) = API 密钥管理（直连 OpenAI，无需 SOCKS）
  └─ VectorControl Docker (nginx+backend+postgres on 443)

gz (Alibaba Cloud) [当前 API 后端 → 迁移后变为 OpenClaw + 监控]
  ├─ metapi Docker (--network host, port 4001) ← 将迁走
  ├─ CPA v6.8.51 (binary, port 8317) ← 将迁走
  ├─ OpenClaw (迁移后) ← 个人 AI 助手, 调用 metapi
  └─ cron 监控脚本 (measure-snapshot.py, token-stats.py)

hk (Azure East Asia / 真正香港) [中国边缘入口]
  └─ nginx → proxy sgp1 (中国用户 ~20ms → HK → 33ms → SGP1 = ~53ms)

Tailscale 组网: gz ↔ sgp1 ↔ sgp2 ↔ hk ↔ doris ↔ 本机
```

### 关键事实

| 条目 | 值 |
|------|-----|
| 公网入口 | https://www.vectorcontrol.tech |
| DNS A 记录 | 20.195.40.11 (sgp2) |
| 静态文件（服务端） | sgp2:/var/www/vectorcontrol-portal/ |
| 静态文件（本地） | D:\Code\Projects\VectorControl-Portal |
| 后端 | gz:4001 metapi (Docker `1467078763/metapi:latest`) |
| SSL 覆盖域名 | vectorcontrol.tech + www.vectorcontrol.tech |
| SSL 续期 | certbot.timer 自动, 30 天提前续期 |
| 阿里云限制 | gz 443 被拦截（无 ICP），公网流量必须经 sgp2 |
| 部署方式 | SCP → sgp2 via Tailscale (root@100.82.99.84) |
| 主题 | 翡翠绿深色极简 (accent #10b981), Hero 辐射光晕 + 玻璃拟态 |
| GitHub 仓库 | DeliciousBuding/VectorControl-Portal (private) |

---

## 4. 延迟矩阵（2026-06-12 全量实测，含真正 HK 节点）

### 4.1 Tailscale 网络延迟（`tailscale ping` 直连值）

| 源 ＼ 目标 | gz | sgp1 | sgp2 | **hk** | doris |
|-----------|-----|------|------|--------|-------|
| **Local** | 17ms | 181ms | 82ms | **93ms** | 147ms |
| **gz** | — | 339ms | 87ms | **77ms** | 10ms |
| **sgp1** | 338ms | — | 4ms | **33ms** | 78ms |
| **sgp2** | 87ms | 2ms | — | **60ms** | 69ms |
| **hk** | **77ms** | **32ms** | **33ms** | — | **81ms** |
| **doris** | 148ms | 63ms | 52ms | **83ms** | — |

> 注: gz/本机 ICMP 首测可能含 DERP 预热（偏高 2-3x），上表为 tailscale ping 直连稳定值

### 4.2 公网延迟

| 源 ＼ 目标 | gz (8.163.12.208) | sgp1 (103.253.145.251) | sgp2 (20.195.40.11) | **hk (104.214.176.143)** |
|-----------|-------------------|----------------------|---------------------|--------------------------|
| **Local** | 23ms | 353ms | TIMEOUT | **TIMEOUT** |
| **gz** | — | 340ms | TIMEOUT | **TIMEOUT** |
| **sgp1** | 340ms | — | TIMEOUT | **TIMEOUT** |
| **sgp2** | 90ms | 2ms | — | **TIMEOUT** |
| **hk** | **77ms** | **32ms** | TIMEOUT | — |
| **doris** | 10ms | 59ms | TIMEOUT | **TIMEOUT** |

> Azure VM (sgp2/hk) 公网 ICMP 被阻断；hk→gz 公网 77ms 与 Tailscale 一致

### 4.3 延迟分析

- **SGP 集群**（sgp1 ↔ sgp2）：2~4ms，均在新加坡
- **HK ↔ SGP**：32~60ms（真正香港到新加坡的物理距离 ~2500km）
- **HK ↔ GZ**：77ms（香港到广州 ~150km，但经公网路由偏高）
- **gz 孤岛**：至 SGP 集群 87~339ms；至本机/doris 10~17ms（均在中国境内）
- **OpenAI 可达性**: gz=✘(GFW), sgp1/sgp2=✅(401), hk=✘(403地区封锁)
- **最优 API 路径**：
  - 国际: 用户→sgp2(0ms)→sgp1(2ms) = **2ms**
  - 中国: 用户→hk(~20ms)→sgp1(33ms) = **~53ms**
  - 当前: sgp2(87ms)→gz(87ms)→SOCKS→sgp2(87ms)→OpenAI = **350ms+**
- **结论**：metapi+CPA 迁至 sgp1 后，国际 87ms→2ms(-97%), 中国加 HK 边缘后 350ms→53ms(-85%)

---

## 5. 代码模块状态

| 模块 | 完成度 | 说明 |
|------|--------|------|
| `:app` | 55% M1 | Navigation (7 screens), Koin DI, ViewModels |
| `:core:model` | 60% | SshProfile, ChatMessage, enums |
| `:core:ssh` | 已实现 | SshjExecRunner (真实 SSH exec), KnownHostsStore, HostKeyCallback |
| `:core:storage` | 已实现 | Room DB v2 (ChatThread+Message+SshProfile), DataStore, DataStoreKnownHostsStore |
| `:core:ui` | MVP | Theme (亮/暗/AMOLED), ChatBubble, CodeCard, ChatInput, AnsiStripper |

---

## 6. 门户性能基线（2026-03-12）

| 资源 | 原始 | gzip | TTFB |
|------|------|------|------|
| HTML | 8.5KB | 2.5KB | 0.22s |
| CSS | 18.7KB | 4.1KB | 0.22s |
| JS | 12.8KB | 3.7KB | 0.24s |
| favicon | 0.5KB | — | 0.23s |
| **总计** | **40.5KB** | **~10.8KB** | |

- gzip 全类型启用, 静态资源 7 天缓存, 安全头 (HSTS/nosniff/DENY/no-referrer)
- SEO: robots.txt + sitemap.xml + OG 标签 ✅
