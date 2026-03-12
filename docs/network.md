# 网络架构与服务重编排方案 v4

> 日期：2026-06-10 ｜ 基于全量延迟测试 + 实际地理验证
>
> **v4 更新**：sgp3(原假HK)正在迁移至真正的 Azure East Asia（香港）区域。
> OpenAI 不在香港提供服务 → metapi+CPA 必须部署在新加坡节点。

---

## 1. 关键发现

### 1.1 "hk" VPS 实际在新加坡

通过 `ipinfo.io` 验证：
- **hk (20.191.156.135)**：`city: Singapore, region: Singapore, country: SG`
- **sgp2 (20.195.40.11)**：`city: Singapore, region: Singapore, country: SG`

"hk" 并非在香港，而是 Azure 新加坡的另一台机器。以下统一改称 **sgp3**。

**这解释了 0.6ms 的"奇迹延迟"——三台新加坡机器之间的内网级延迟。**

### 1.2 当前 API 请求链路极度低效

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

### 1.3 sgp3 可以直接访问 OpenAI（但迁移后将不能）

```
$ ssh sgp3 'curl -s -o /dev/null -w "%{http_code} %{time_total}s" https://api.openai.com/v1/models'
401 0.228947s   ← 目前在新加坡，API 可达
```

**但 sgp3 正在迁移到真正的 Azure 香港 (East Asia)**。香港不在 OpenAI 支持区域列表中，迁移后将无法访问 OpenAI。

**因此：metapi+CPA 必须部署在新加坡节点（sgp1）。**

---

## 2. 全量延迟矩阵（2026-06-10 实测）

### 2.1 Tailscale 网络

| 源 ＼ 目标 | gz | sgp1 | sgp2 | sgp3 | doris |
|-----------|-----|------|------|----|-------|
| **本机** | 62ms | 253ms* | 95ms | 80ms | 292ms* |
| **gz** | — | 341ms | 87ms | 110ms | 10ms |
| **sgp1** | 345ms | — | 5ms | 4ms | 200ms |
| **sgp2** | 87ms | 2ms | — | 2ms | 95ms |
| **sgp3** | 110ms | 2ms | 0.6ms | — | 96ms |
| **doris** | 202ms | 196ms | 99ms | 73ms | — |

> \* 高抖动（sgp1-ts: 59-352ms, doris-ts: 104-666ms）

### 2.2 公网

| 源 ＼ 目标 | gz | sgp1 | sgp2 | sgp3 |
|-----------|-----|------|------|------|
| **本机** | 22ms | 391ms | ✘ | ✘ |
| **gz** | — | 340ms | ✘ | ✘ |
| **sgp1** | 340ms | — | ✘ | ✘ |
| **sgp2** | 90ms | 2ms | — | ✘ |
| **sgp3** | 84ms | 3ms | ✘ | — |
| **doris** | 10ms | 59ms | ✘ | ✘ |

> ✘ = Azure 屏蔽 ICMP（sgp2/sgp3 公网 ping 不通，但 TCP 服务正常）

### 2.3 延迟区域模型

```
┌─────────────────────────────────────────────────┐
│             新加坡集群（<5ms 互联）                │
│                                                   │
│   sgp1 (DO, 2GB)  ←2ms→  sgp2 (Azure, 847MB)    │
│       ↕ 4ms                    ↕ 0.6ms           │
│              sgp3 (Azure, 847MB)                  │
└────────────────────┬────────────────────────────┘
                     │ 87~110ms
                     ↓
              gz (阿里云广州, 1.6GB)
              ↕ 10ms (公网)
              本机/doris (中国境内)
```

**核心结论**：新加坡三节点形成 **<5ms 低延迟集群**，gz 是地理孤岛。本机/doris 距 gz 10~22ms（境内），距 SGP 集群 60~253ms。

---

## 3. 当前资源清单

| 节点 | 提供商 | RAM | 磁盘 | 已用内存 | 主要服务 |
|------|--------|-----|------|----------|---------|
| gz | 阿里云 | 1.6GB | 40GB | 659MB | metapi(62MB)+CPA(54MB)+nginx+cron |
| sgp1 | DO | 2GB | 58GB | 1024MB | OpenClaw(447MB)+VectorControl Docker+danted |
| sgp2 | Azure | 847MB | — | 469MB | nginx edge(SSL终止) |
| sgp3/hk | Azure | 847MB | 29GB | ~200MB | **空闲**（正迁移至真HK） |

### 迁移后的将来拓扑

| 节点 | 位置 | RAM | 角色 |
|------|------|-----|------|
| sgp1 | DO 新加坡 | 2GB | **API 后端** (metapi+CPA+OpenClaw+VectorControl) |
| sgp2 | Azure 新加坡 | 847MB | **国际边缘** (nginx SSL → proxy sgp1) |
| hk | Azure 香港(真) | 847MB | **中国边缘** (nginx SSL → proxy sgp1) + 监控 |
| gz | 阿里云广州 | 1.6GB | 备份存储 + 监控 cron（减负） |

---

## 4. 方案分析

> sgp3 迁移至真 HK 后，OpenAI 不可达 → metapi+CPA 只能放在 sgp1 或 sgp2。

### 方案 A：metapi+CPA → sgp1（推荐 ✅）

```
sgp1 (2GB):  metapi Docker(62MB) + CPA Docker(54MB) + OpenClaw(447MB) + VectorControl
             预计内存: 1024MB + 116MB = ~1140MB (56% of 2GB)
sgp2 (847MB): nginx edge → proxy sgp1:4001 (2ms)
hk (真HK):   nginx edge + 中国入口 → proxy sgp1:4001 (~35ms)
gz (1.6GB):  监控 cron + 备份（释放 metapi+CPA 后 ~400MB）
```

| 指标 | 迁移前 | 迁移后 | 变化 |
|------|--------|--------|------|
| 国际 API 延迟 | 350ms (4×跨国+SOCKS) | **2ms** (sgp2→sgp1) | **-99.4%** |
| 中国 API 延迟 | ~370ms (sgp2→gz→SOCKS回sgp2) | **~55ms** (→HK→sgp1) | **-85%** |
| CPA→OpenAI | SOCKS5 代理 | 直连 (sgp1在新加坡) | 去除代理 |
| gz 内存 | 659MB | ~400MB | -40% |
| sgp1 内存 | 1024MB | ~1140MB (56%) | +116MB |

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

### 5.6 后续阶段：真 HK 就绪后

当 Azure 资源迁移到真 HK 完成后：

```bash
# 1. 初始化真 HK（Tailscale + UFW + hardening）
# 2. 安装 nginx + certbot（申请 vectorcontrol.tech 或子域名证书）
# 3. 配置 nginx → proxy sgp1:4001 via Tailscale
# 4. 可选：DNS 添加 HK A 记录（或 Cloudflare 地理路由）
```

真 HK 作为中国用户边缘入口：
- 中国→HK: ~20ms → sgp1: ~35ms → OpenAI 直连
- 比当前 350ms 提升 **85%+**

### 5.7 后续：OpenClaw Docker 化（sgp1）

```dockerfile
# Dockerfile.openclaw
FROM node:20-slim
RUN npm install -g openclaw@2026.3.8
COPY openclaw.json /root/.openclaw/openclaw.json
EXPOSE 18789 18791 18792
CMD ["openclaw-gateway"]
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

1. **立即**：CPA Docker 化（配合迁移到 sgp3）
2. **短期**：OpenClaw Docker 化（sgp1 上）
3. **保持 host**：nginx (edge proxy 最好直接跑 host), Tailscale (系统级网络), danted

### 每节点 docker-compose 策略

```
sgp1/docker-compose.yml:  metapi + cpa + openclaw(后续) + vectorcontrol(已有)
sgp2: host-level nginx only
hk:   host-level nginx (中国边缘) — 真HK就绪后
gz:   host-level cron scripts only
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
         metapi Docker + CPA Docker + OpenClaw  
           ├── metapi:4001 → CPA:8317 (localhost)
           ├── CPA → api.openai.com (直连!)     
           ├── OpenClaw 18789/18791/18792       
           └── VectorControl (nginx+backend+pg) 
                                                 
              gz (1.6GB)                        
         监控 + 备份                             
           ├── cron: measure-snapshot.py (5min) 
           ├── cron: token-stats.py (1min)      
           └── hub.db 异地备份                   
```

### 阶段二：真 HK 作为中国边缘（HK 就绪后）

```
中国用户 → hk (nginx:443, ~20ms)
         → Tailscale → sgp1:4001 (~35ms)
         → metapi → CPA → OpenAI (直连)

国际用户 → sgp2 (nginx:443)
         → Tailscale → sgp1:4001 (2ms)
         → metapi → CPA → OpenAI (直连)
```

### 迁移前后总结

| 指标 | 当前 | 阶段一 | 阶段二 (真HK) |
|------|------|--------|---------------|
| 国际 API 延迟 | ~350ms | **2ms** | 2ms |
| 中国 API 延迟 | ~370ms | ~90ms | **~55ms** |
| CPA 出口 | SOCKS5 代理 | 直连 | 直连 |
| gz 负载 | metapi+CPA | cron+备份 | cron+备份 |
| Docker 化 | metapi only | metapi+CPA | metapi+CPA |
| 中国入口 | 无 | 无 | **HK nginx** |

---

## 10. SSH 别名建议

真 HK 迁移完成后更新 `.ssh/config`：

```ssh-config
# 当前 sgp3 (实际在新加坡) 迁移到真 HK 后：
Host hk
    HostName <新Tailscale IP>  # 迁移后会变
    User azureuser
```

待迁移完成后获取新 Tailscale IP 并更新。
