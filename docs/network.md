# 网络架构与服务重编排方案 v5

> 日期：2026-06-12 ｜ 基于全量延迟测试（含真正 HK 节点）+ 地理验证
>
> **v5 更新**：
> 1. 真正 HK VPS 已创建 (Azure East Asia, 104.214.176.143)，ipinfo 确认 `city: Hong Kong`
> 2. HK 初始化完成: Tailscale (100.96.116.54) + UFW + fail2ban + SSH hardening
> 3. OpenAI HK 测试: 返回 **403 Forbidden**（地区封锁确认）
> 4. OpenClaw 定位修正: 个人 AI 助手（非 API 网关），调用 metapi → 部署到 gz
> 5. 全量延迟矩阵更新（含 6 节点）

---

## 1. 关键发现

### 1.1 历史回顾: "hk" VPS 实际在新加坡

通过 `ipinfo.io` 验证（2026-06-10）：旧 "hk" (20.191.156.135) 实际在新加坡。
已于 2026-06-12 创建新 VPS，**真正部署在 Azure East Asia（香港）**：

```
$ ssh hk 'curl -s ipinfo.io | grep -E "ip|city|country"'
  "ip": "104.214.176.143",
  "city": "Hong Kong",
  "country": "HK",
```

### 1.2 当前 API 请求链路极度低效（待迁移解决）

```
用户 → sgp2(nginx:443)
     → 87ms → gz(metapi:4001)
     → gz(CPA:8317)
     → SOCKS5 → 87ms → sgp2(danted:1080)
     → OpenAI api.openai.com
     → 响应原路返回：OpenAI → sgp2 → 87ms → gz → 87ms → sgp2 → 用户
```

一次 API 请求（如 gpt-5.4 completion）涉及 **4 次跨国传输 ≈ 350ms** 纯地理开销。

**原因**：gz（广州）无法直接访问 OpenAI（`curl api.openai.com` 超时），CPA 必须通过 sgp2 的 SOCKS 代理出口。

### 1.3 OpenAI 在各节点的可达性

| 节点 | 位置 | 结果 | 详情 |
|------|------|------|------|
| sgp1 | 新加坡 | ✅ 401 (0.23s) | API 可达，仅认证失败 |
| sgp2 | 新加坡 | ✅ | 同上 |
| gz | 广州(中国) | ✘ 000 (5.0s) | GFW 阻断，curl 超时 |
| **hk** | **香港** | **✘ 403 (0.034s)** | **OpenAI 地区封锁** |

**结论**：
- metapi+CPA 必须部署在**新加坡节点 (sgp1)**
- **OpenClaw** 是个人 AI 助手（非 API 网关），通过调用 metapi 间接访问模型 → 可部署到 gz（gz→sgp1 RPC 延迟可接受）

---

## 2. 全量延迟矩阵（2026-06-12 实测，含真 HK 节点）

### 2.1 Tailscale 网络（`tailscale ping` 直连稳定值）

| 源 ＼ 目标 | gz | sgp1 | sgp2 | **hk** | doris |
|-----------|-----|------|------|--------|-------|
| **本机** | 17ms | 181ms | 82ms | **93ms** | 147ms |
| **gz** | — | 339ms | 87ms | **77ms** | 10ms |
| **sgp1** | 338ms | — | 4ms | **33ms** | 78ms |
| **sgp2** | 87ms | 2ms | — | **60ms** | 69ms |
| **hk** | **77ms** | **32ms** | **33ms** | — | **81ms** |
| **doris** | 148ms | 63ms | 52ms | **83ms** | — |

> 注: ICMP ping 可能因 DERP 预热偏高 2-3x，上表为 tailscale ping 直连稳定值

### 2.2 公网

| 源 ＼ 目标 | gz | sgp1 | sgp2 | **hk** |
|-----------|-----|------|------|--------|
| **本机** | 23ms | 353ms | ✘ | **✘** |
| **gz** | — | 340ms | ✘ | **✘** |
| **sgp1** | 340ms | — | ✘ | **✘** |
| **sgp2** | 90ms | 2ms | — | **✘** |
| **hk** | **77ms** | **32ms** | ✘ | — |
| **doris** | 10ms | 59ms | ✘ | **✘** |

> ✘ = Azure 屏蔽 ICMP（sgp2/hk 公网 ping 不通，但 TCP 服务正常）

### 2.3 延迟区域模型

```
                    ┌─────────────────────────────────┐
                    │     新加坡集群（2~4ms 互联）       │
                    │                                   │
                    │   sgp1 (DO, 2GB)  ←2ms→  sgp2    │
                    │     (Azure, 847MB)                │
                    └────────────────┬──────────────────┘
                                     │
                          ┌──── 33ms ─┼──── 87ms ─────┐
                          ↓           ↓                 ↓
                    hk (Azure HK)    (...)         gz (阿里云广州)
                    ← 77ms → gz      ← 10ms →     本机/doris
                    (真正香港!)       本机/doris    (中国境内)
```

**核心结论**：
- SGP集群内 2~4ms
- HK↔SGP 32~60ms（真实跨境）
- HK↔GZ 77ms
- GZ↔SGP 87~339ms（跨境+ISP路由差异）
- 本机/doris↔GZ 10~17ms（境内低延迟）

---

## 3. 当前资源清单

| 节点 | 提供商 | RAM | 磁盘 | 已用内存 | 主要服务 |
|------|--------|-----|------|----------|---------|
| gz | 阿里云 | 1.6GB | 40GB | 659MB | metapi(62MB)+CPA(54MB)+nginx+cron |
| sgp1 | DO | 2GB | 58GB | 1024MB | VectorControl Docker+danted |
| sgp2 | Azure | 847MB | — | 469MB | nginx edge(SSL终止) |
| hk | Azure HK | 1GiB | 30GB | ~400MB | **空闲**（已初始化 Tailscale+UFW+fail2ban） |

### 迁移后的目标拓扑

| 节点 | 位置 | RAM | 角色 |
|------|------|-----|------|
| sgp1 | DO 新加坡 | 2GB | **API 后端** (metapi+CPA+VectorControl) |
| sgp2 | Azure 新加坡 | 847MB | **国际边缘** (nginx SSL → proxy sgp1) |
| hk | Azure 香港 | 1GiB | **中国边缘** (nginx SSL → proxy sgp1) |
| gz | 阿里云广州 | 1.6GB | OpenClaw (个人AI助手) + 监控 cron + 备份 |

---

## 4. 方案分析

> sgp3 迁移至真 HK 后，OpenAI 不可达 → metapi+CPA 只能放在 sgp1 或 sgp2。

### 方案 A：metapi+CPA → sgp1（推荐 ✅）

```
sgp1 (2GB):  metapi Docker(62MB) + CPA Docker(54MB) + VectorControl
             OpenClaw 移走后: ~577MB → 加 116MB = ~693MB (35% of 2GB) ← 充裕!
sgp2 (847MB): nginx edge → proxy sgp1:4001 (2ms)
hk (真HK):   nginx edge + 中国入口 → proxy sgp1:4001 (33ms)
gz (1.6GB):  OpenClaw(447MB) + 监控 cron + 备份
```

| 指标 | 迁移前 | 迁移后 | 变化 |
|------|--------|--------|------|
| 国际 API 延迟 | 350ms (4×跨国+SOCKS) | **2ms** (sgp2→sgp1) | **-99.4%** |
| 中国 API 延迟 | ~370ms (sgp2→gz→SOCKS回sgp2) | **~53ms** (→HK→sgp1) | **-86%** |
| CPA→OpenAI | SOCKS5 代理 | 直连 (sgp1在新加坡) | 去除代理 |
| gz 内存 | 659MB | ~847MB (加OpenClaw) | +188MB |
| sgp1 内存 | 1024MB | ~693MB (移走OpenClaw) | **-331MB 释放!** |

**优势**：
- 最大内存节点 (2GB)，能容纳全部 API 服务
- CPA 直连 OpenAI（不需要 SOCKS 代理）
- sgp2→sgp1 仅 2ms
- 真 HK→sgp1 ≈35ms（远优于当前 350ms）
- gz 彻底释放 API 负载
- 全部 API 服务 Docker 化，compose 声明式管理

**风险**：
- sgp1 已有 297MB swap 使用，加 116MB 后需要监控
- 建议给 Docker 容器设 `mem_limit`（metapi 128MB, CPA 96MB）
- 所有 API 在一台机器 = 单点故障（但 Docker 恢复到其他 SGP 节点 <5min）

### 方案 B：metapi+CPA → sgp2 alongside nginx

```
sgp2 (847MB): nginx edge + metapi(62MB) + CPA(54MB) = ~585MB (69%)
```

API 请求在 sgp2 上本地完成 (0ms)，但 847MB 上同时跑 nginx+metapi+CPA 偏紧，无余量处理流量峰值。**不推荐**。

### 方案对比

| | A (sgp1) | B (sgp2) |
|-|----------|----------|
| 可用内存 | 2GB ✅ | 847MB ✘ |
| API 延迟 (国际) | 2ms | 0ms (本地) |
| API 延迟 (中国) | ~55ms | ~55ms |
| 内存裕度 | ~860MB 空闲 | ~260MB 空闲 |
| 推荐 | **✅** | ✘ |

---

## 5. 推荐方案 A 执行计划

### 5.1 阶段一：Docker 化 CPA

```dockerfile
# Dockerfile.cpa
FROM ubuntu:24.04
COPY CLIProxyAPI /opt/cliproxy/CLIProxyAPI
RUN chmod +x /opt/cliproxy/CLIProxyAPI
WORKDIR /opt/cliproxy
EXPOSE 8317
CMD ["./CLIProxyAPI"]
```

CPA `config.yaml` 需修改：
- **删除** `proxy-url: socks5://100.82.99.84:1080`（sgp1 可直连 OpenAI）
- 其余配置不变（api-keys, claude-api-key, auth-dir 保持）

### 5.2 阶段二：数据备份

**metapi 需保留的数据**（hub.db）：
- accounts (上游账户 + api_token)
- sites (上游站点配置)
- token_routes (模型路由规则)
- route_channels (路由通道映射)
- downstream_api_keys (下游 API 密钥)
- proxy_logs (代理日志 - 可选，可清空后迁移以减小体积)
- settings (系统设置)

```bash
# 1. gz 上导出 metapi 数据
ssh gz 'docker exec metapi cp /app/data/hub.db /app/data/hub.db.export'
scp gz:/root/metapi-data/hub.db.export /tmp/hub.db.export

# 2. 导出 CPA 二进制 + 配置
scp gz:/opt/cliproxy/CLIProxyAPI /tmp/CLIProxyAPI
scp gz:/opt/cliproxy/config.yaml /tmp/cpa-config.yaml
scp -r gz:/opt/cliproxy/auth/ /tmp/cpa-auth/

# 3. 修改 CPA config（删除 proxy-url 行）
```

### 5.3 阶段三：sgp1 部署

```bash
# 在 sgp1 上准备目录结构
ssh sgp1 'mkdir -p /opt/services/{metapi-data,cpa}'

# 传输数据
scp /tmp/hub.db.export sgp1:/opt/services/metapi-data/hub.db
scp /tmp/CLIProxyAPI sgp1:/opt/services/cpa/
scp /tmp/cpa-config.yaml sgp1:/opt/services/cpa/config.yaml
scp -r /tmp/cpa-auth/ sgp1:/opt/services/cpa/auth/
```

```yaml
# sgp1:/opt/services/docker-compose.yml
services:
  metapi:
    image: 1467078763/metapi:latest
    network_mode: host  # port 4001
    mem_limit: 128m
    environment:
      - AUTH_TOKEN=<same-token>
      - PORT=4001
      - NODE_ENV=production
      - DATA_DIR=/app/data
    volumes:
      - ./metapi-data:/app/data
    restart: unless-stopped

  cpa:
    build:
      context: ./cpa
      dockerfile: Dockerfile
    network_mode: host  # port 8317
    mem_limit: 96m
    volumes:
      - ./cpa/config.yaml:/opt/cliproxy/config.yaml:ro
      - ./cpa/auth:/opt/cliproxy/auth
    restart: unless-stopped
```

```bash
# 启动
ssh sgp1 'cd /opt/services && docker compose up -d'

# 验证本地
ssh sgp1 'curl -s localhost:4001/v1/models | head -c 200'
ssh sgp1 'curl -s localhost:8317 | head -c 200'
```

### 5.4 阶段四：切流

```bash
# sgp2 nginx 配置：upstream 从 gz → sgp1
# /etc/nginx/conf.d/vectorcontrol.tech.conf:
#   proxy_pass http://100.96.101.24:4001  →  proxy_pass http://100.117.129.78:4001
#   proxy_pass http://100.96.101.24:8317  →  proxy_pass http://100.117.129.78:8317

ssh sgp2 'nginx -t && systemctl reload nginx'
```

### 5.5 阶段五：端到端验证 + gz 清理

```bash
# 公网验证
curl -s https://www.vectorcontrol.tech/v1/models  # → 401（需认证）
curl -s -H "Authorization: Bearer <key>" \
  https://www.vectorcontrol.tech/v1/chat/completions \
  -d '{"model":"gpt-5.4","messages":[{"role":"user","content":"Hi"}]}'

# 确认无误后停止 gz 旧服务
ssh gz 'docker stop metapi; systemctl stop cliproxyapi; systemctl disable cliproxyapi'
# 保留 gz 上的 hub.db 备份，不立即删除
```

### 5.6 阶段六：HK 边缘入口配置（HK 已就绪）

HK VPS 已初始化 (2026-06-12): Tailscale + UFW + fail2ban + SSH hardening

```bash
# 1. 安装 nginx + certbot
ssh hk 'sudo apt-get install -y nginx certbot python3-certbot-nginx'

# 2. 配置 nginx → proxy sgp1:4001 via Tailscale
# /etc/nginx/sites-available/vectorcontrol.conf:
#   server_name vectorcontrol.tech www.vectorcontrol.tech;
#   location /api/ { proxy_pass http://100.117.129.78:4001; }

# 3. 申请 SSL 证书
ssh hk 'sudo certbot --nginx -d vectorcontrol.tech -d www.vectorcontrol.tech'

# 4. DNS: 添加 HK A 记录（或 Cloudflare 地理路由 CN→HK）
```

中国用户完整路径: 用户(~20ms)→HK(nginx:443)→Tailscale(33ms)→sgp1(metapi) = **~53ms**

### 5.7 后续：OpenClaw 部署到 gz

OpenClaw 是**个人 AI 助手**（非 API 网关），主要调用 metapi 间接访问模型。
放在 gz 优势：
- gz 迁走 metapi+CPA 后有 ~900MB 空闲（1.6GB - 659MB + 116MB freed）
- OpenClaw(447MB) 放 gz 后: ~847MB / 1.6GB = 53%
- OpenClaw → metapi 调用路径: gz → sgp1 (339ms Tailscale / 87ms via sgp2)

```bash
# gz 上直接用 npm 或 Docker
ssh gz 'npm install -g openclaw@2026.3.8'
# 配置 openclaw.json 的 metapi endpoint 指向 sgp1
```

---

## 6. 域名绑定策略

### 当前
- `vectorcontrol.tech` / `www.vectorcontrol.tech` → A: 20.195.40.11 (sgp2)

### 迁移后选项

| 指标 | 仅 sgp2 | sgp2为主+HK备用 | 双 A 记录 | Cloudflare 地理路由 |
|------|---------|---------------|----------|-------------------|
| DNS 配置 | 不变 | A: sgp2, 手动切 | A: sgp2 + hk | CNAME → CF |
| SSL 管理 | sgp2 一台 | 两台 certbot | 两台 certbot | CF 边缘 SSL |
| 中国用户延迟 | ~90ms→sgp2 | ~20ms→HK | 混合 | ~20ms→HK |
| 高可用 | 无 | 手动 | DNS 轮询 | 自动故障转移 |
| 复杂度 | **最低** | 低 | 中 | 高 |

**阶段性推荐**：
1. **立即**：保持 sgp2 单入口（先完成 metapi 迁移，不增加变量）
2. **真 HK 就绪后**：在 HK 配 nginx + certbot，加为备用入口
3. **未来可选**：Cloudflare 地理路由（CN→HK, 其他→SGP），实现最优路径

---

## 7. Docker 全面化评估

### 当前 Docker 化状态

| 服务 | 当前运行方式 | Docker 化难度 | 收益 |
|------|-------------|-------------|------|
| metapi | ✅ 已 Docker | — | — |
| CPA | systemd 二进制 | 低（单二进制+config） | 可迁移性 |
| OpenClaw | npm global + systemd | 中（Node.js + config） | 隔离+可迁移 |
| VectorControl | ✅ 已 Docker (compose) | — | — |
| nginx edge | systemd | 低但不推荐 | 微弱 |
| danted SOCKS | systemd | 低 | 微弱 |
| Tailscale | systemd | **不推荐** | 无 |

### 推荐 Docker 化路径

1. **立即**：CPA Docker 化（配合迁移到 sgp1）
2. **短期**：OpenClaw 部署到 gz (npm 或 Docker)
3. **保持 host**：nginx (edge proxy 最好直接跑 host), Tailscale (系统级网络), danted

### 每节点 docker-compose 策略

```
sgp1/docker-compose.yml:  metapi + cpa + vectorcontrol(已有)
sgp2: host-level nginx only
hk:   host-level nginx (中国边缘)
gz:   OpenClaw (npm/systemd) + host-level cron scripts
```

---

## 8. 备份与可迁移性

Docker 化后的备份策略：

```bash
# 每日 cron (sgp1 上)
docker exec metapi cp /app/data/hub.db /app/data/hub.db.daily
tar czf /backup/sgp1-$(date +%F).tar.gz \
  /opt/services/metapi-data/ \
  /opt/services/cpa/ \
  /opt/services/docker-compose.yml
# 异地备份到 gz
scp /backup/sgp1-$(date +%F).tar.gz gz:/backup/
```

**紧急迁移流程**（sgp1 故障时）：
1. 从 gz 备份恢复到 sgp2（或真 HK 如果能访问 OpenAI 的备选路径，需要 SOCKS 代理）
2. `docker compose up -d`
3. 修改 sgp2 nginx upstream 为 localhost 或新节点
4. 整个流程 <10 分钟

---

## 9. 目标架构总览

### 阶段一：metapi+CPA → sgp1（立即执行）

```
                    ┌─ DNS: vectorcontrol.tech ─┐
                    ↓                            
              sgp2 (847MB)                      
         nginx edge + SSL termination           
           ├── /api, /v1 → sgp1:4001 (2ms)    
           ├── /management → sgp1:8317 (2ms)   
           ├── /fund → static files             
           └── / → static portal                
                    │                            
                    │ 2ms (Tailscale)            
                    ↓                            
              sgp1 (2GB)                        
         metapi Docker + CPA Docker             
           ├── metapi:4001 → CPA:8317 (localhost)
           ├── CPA → api.openai.com (直连!)     
           └── VectorControl (nginx+backend+pg) 
                                                 
              gz (1.6GB)                        
         OpenClaw + 监控 + 备份                  
           ├── OpenClaw (个人AI助手, 调用 metapi)
           ├── cron: measure-snapshot.py (5min) 
           ├── cron: token-stats.py (1min)      
           └── hub.db 异地备份                   
```

### 阶段二：HK 中国边缘入口（HK 已就绪，待配 nginx）

```
中国用户 → hk (nginx:443, ~20ms)
         → Tailscale → sgp1:4001 (33ms)
         → metapi → CPA → OpenAI (直连)
                                            = 总计 ~53ms

国际用户 → sgp2 (nginx:443)
         → Tailscale → sgp1:4001 (2ms)
         → metapi → CPA → OpenAI (直连)
                                            = 总计 ~2ms
```

### 迁移前后总结

| 指标 | 当前 | 阶段一 | 阶段二 (HK边缘) |
|------|------|--------|---------------|
| 国际 API 延迟 | ~350ms | **2ms** | 2ms |
| 中国 API 延迟 | ~370ms | ~90ms | **~53ms** |
| CPA 出口 | SOCKS5 代理 | 直连 | 直连 |
| gz 负载 | metapi+CPA | OpenClaw+cron | OpenClaw+cron |
| sgp1 负载 | OpenClaw+VectorControl | metapi+CPA+VectorControl | metapi+CPA+VectorControl |
| Docker 化 | metapi only | metapi+CPA | metapi+CPA |
| 中国入口 | 无 | 无 | **HK nginx** |

---

## 10. 已完成事项

- [x] HK VPS 创建 (Azure East Asia, 104.214.176.143) — 2026-06-12
- [x] HK 初始化: Tailscale(100.96.116.54) + UFW + fail2ban + SSH hardening
- [x] ipinfo 地理确认: `city: Hong Kong, country: HK`
- [x] OpenAI HK 测试: 403 Forbidden（地区封锁确认）
- [x] 全量延迟矩阵更新（6 节点）
- [x] .ssh/config 更新 hk 别名 → 100.96.116.54
- [ ] 执行 metapi+CPA → sgp1 迁移
- [ ] HK nginx + certbot 边缘配置
- [ ] OpenClaw 迁移到 gz
- [ ] DNS 地理路由（CN→HK, 其他→SGP）
