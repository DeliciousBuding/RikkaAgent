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
| 安全原则 | Known-hosts 默认开启；host key mismatch 默认阻断；密钥不写日志 |
| 开源协议 | Apache-2.0 |
| Clean-room | 参考 UI "感觉"，不拷贝参考项目代码 |
| SSH 库 | sshj 0.39.0 (BSD-2-Clause)，支持 Ed25519/RSA/ECDSA |
| 持久化 | Room DB (聊天/配置) + DataStore (偏好) |
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
| 服务 | Docker VectorControl (nginx/backend/postgres on 443), OpenClaw (npm global, Node.js, 447MB RSS), danted SOCKS (TS-only 1080) |
| 注意 | 内存压力 (1.0G/1.9G + 297M swap, disk 55%) |

### 2.4 sgp3 — Azure 新加坡（原命名 "hk"，实际位于新加坡）

| 条目 | 值 |
|------|-----|
| 公网 IP | 20.191.156.135 |
| Tailscale IP | 100.79.22.119 |
| SSH 别名 | `sgp3`（user: azureuser）← 原 `hk`，建议改名 |
| OS | Ubuntu 24.04, kernel 6.14.0-1017-azure |
| 资源 | 2核, 847MB RAM, 29GB disk |
| 实际地理 | **新加坡**（ipinfo.io 验证: city=Singapore, country=SG） |
| 角色 | API 后端候选（metapi+CPA 迁移目标） |
| 安全 | UFW (80/443/41641, SSH 仅 Tailscale), fail2ban, unattended-upgrades, SSH hardening |

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
       ├─ /management → proxy_pass gz:8317/management.html (CPA 管理界面)
       ├─ /fund/ → alias /var/www/vectorcontrol-fund/ (React SPA 投研系统)
       ├─ /playground/ → proxy_pass gz:4001/ (metapi Playground)
       ├─ /api/, /v1/ → proxy_pass gz:4001 (metapi OpenAI API)
       └─ SSL: /etc/letsencrypt/live/www.vectorcontrol.tech/ (certbot + nginx)

gz (Alibaba Cloud)
  ├─ metapi Docker (--network host, port 4001) = OpenAI 兼容 API 网关
  ├─ CPA v6.8.51 (binary, port 8317) = API 密钥管理 + 路由配置
  └─ nginx 443 → /playground/ proxy to metapi

sgp3 (Azure Singapore, 原名"hk") [备用]
  └─ 已初始化, Tailscale + UFW + fail2ban, 待分配 API 后端角色

Tailscale 组网: gz ↔ sgp1 ↔ sgp2 ↔ sgp3 ↔ doris ↔ 本机
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

## 4. 延迟矩阵（2026-06-10 全量实测）

### 4.1 Tailscale 网络延迟

| 源 ＼ 目标 | gz | sgp1 | sgp2 | sgp3 | doris |
|-----------|-----|------|------|----|-------|
| **Local** | 62ms | 253ms* | 95ms | 80ms | 292ms* |
| **gz** | — | 341ms | 87ms | 110ms | 10ms |
| **sgp1** | 345ms | — | 5ms | 4ms | 200ms |
| **sgp2** | 87ms | 2ms | — | 2ms | 95ms |
| **sgp3** | 110ms | 2ms | 0.6ms | — | 96ms |
| **doris** | 202ms | 196ms | 99ms | 73ms | — |

> \* 抖动大（sgp1: 59-352ms, doris: 104-666ms）

### 4.2 公网延迟

| 源 ＼ 目标 | gz (8.163.12.208) | sgp1 (103.253.145.251) | sgp2 (20.195.40.11) | sgp3 (20.191.156.135) |
|-----------|-------------------|----------------------|--------------------|--------------------|
| **Local** | 22ms | 391ms | TIMEOUT | TIMEOUT |
| **gz** | — | 340ms | TIMEOUT | TIMEOUT |
| **sgp1** | 340ms | — | TIMEOUT | TIMEOUT |
| **sgp2** | 90ms | 2ms | — | TIMEOUT |
| **sgp3** | 84ms | 3ms | TIMEOUT | — |
| **doris** | 10ms | 59ms | TIMEOUT | TIMEOUT |

> sgp2/sgp3 公网 ICMP 被 Azure 阻断；三台 SGP 节点均在新加坡，<5ms 互联

### 4.3 延迟分析

- **SGP 集群**（sgp1 ↔ sgp2 ↔ sgp3）：0.6~5ms，均在新加坡
- **gz 孤岛**：至 SGP 集群 87~345ms；至本机/doris 10~22ms（均在中国境内）
- **最优 API 路径**：用户 → sgp2(0ms)→ sgp1/hk(2ms) 远优于现有 sgp2→gz(87ms)
- **结论**：metapi + CPA 迁至 sgp1 后，API 延迟 87ms → 2ms（-97%）

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
