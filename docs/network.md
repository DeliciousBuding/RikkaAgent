# 网络架构与服务重编排方案 v3

> 日期：2026-06-10 ｜ 基于全量延迟测试 + 实际地理验证

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

### 1.3 sgp3 可以直接访问 OpenAI

```
$ ssh sgp3 'curl -s -o /dev/null -w "%{http_code} %{time_total}s" https://api.openai.com/v1/models'
401 0.228947s   ← API 可达，仅缺认证
```

将 metapi+CPA 迁至新加坡后，CPA 不再需要 SOCKS 代理，可直连 OpenAI。

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
| sgp3 | Azure | 847MB | 29GB | ~200MB | **空闲**（刚初始化） |

---

## 4. 方案对比

### 方案 A：metapi+CPA → sgp3（推荐 ✅）

```
sgp3 (847MB):  metapi Docker(62MB) + CPA Docker(54MB) = ~116MB
sgp1 (2GB):    OpenClaw(447MB) + VectorControl Docker + danted
sgp2 (847MB):  nginx edge → proxy sgp3:4001(0.6ms)
gz (1.6GB):    monitoring cron + 备份存储（释放metapi+CPA后仅~400MB）
```

| 指标 | 迁移前 | 迁移后 | 变化 |
|------|--------|--------|------|
| API 延迟 (sgp2→backend) | 87ms + SOCKS回程87ms | 0.6ms 直连 | **-99.3%** |
| CPA→OpenAI | SOCKS proxy | 直连 | 去除代理 |
| gz 内存占用 | 659MB | ~400MB | -40% |
| sgp3 内存占用 | ~200MB | ~316MB | +116MB（剩余500+MB） |
| sgp1 变化 | — | — | 无影响 |

**优势**：
- sgp3 完全空闲，无服务冲突
- CPA 不再需要 SOCKS 代理（新加坡直连 OpenAI）
- sgp2→sgp3 仅 0.6ms，几乎零延迟
- gz 变轻，只跑监控脚本和备份
- 三个 SGP 节点 <5ms，未来服务漂移几乎无感

**劣势**：
- sgp3 仅 847MB，但 116MB 服务占用留有充足余量
- 需要构建 CPA Docker 镜像

### 方案 B：metapi+CPA → sgp1（集中式）

```
sgp1 (2GB):  metapi+CPA(116MB) + OpenClaw(447MB) + VectorControl = ~600MB+
```

| 指标 | 值 |
|------|-----|
| API 延迟 | sgp2→sgp1 = 2ms |
| sgp1 内存 | 1024MB → ~1140MB（swap 风险上升） |

**劣势**：sgp1 已在内存压力下（1024MB/2GB + 297MB swap），集中更多服务加剧风险。单点故障。

### 方案 C：全服务集中到 sgp1，VectorControl 迁出到 sgp3

```
sgp1 (2GB):  metapi+CPA+OpenClaw
sgp3 (847MB): VectorControl Docker
```

需要拆分和迁移 VectorControl Docker 的 postgres 数据。增加复杂度，收益不大。

### 方案对比总结

| | 方案 A (sgp3) | 方案 B (sgp1) | 方案 C (拆分) |
|-|---------------|---------------|---------------|
| API 延迟 | **0.6ms** | 2ms | 2ms |
| 内存风险 | 低 | **高** | 中 |
| 单点故障 | 低 | **高** | 中 |
| 复杂度 | 低 | 低 | **高** |
| 推荐 | **✅** | ✘ | ✘ |

---

## 5. 推荐方案 A 详细执行计划

### 5.1 阶段一：Docker 化 CPA（在 gz 上构建验证）

```dockerfile
# Dockerfile.cpa
FROM ubuntu:24.04
COPY CLIProxyAPI /opt/cliproxy/CLIProxyAPI
COPY config.yaml /opt/cliproxy/config.yaml
COPY auth/ /opt/cliproxy/auth/
WORKDIR /opt/cliproxy
EXPOSE 8317
CMD ["./CLIProxyAPI"]
```

需修改 `config.yaml`:
- **删除** `proxy-url: socks5://100.82.99.84:1080`（新加坡不需要代理）
- 其余配置不变（api-keys, claude-api-key, auth-dir 保持）

### 5.2 阶段二：数据备份与迁移

**metapi 需要保留的数据**：
```
hub.db 中：
  - accounts (上游账户 + api_token)
  - sites (上游站点配置)
  - token_routes (模型路由规则)
  - route_channels (路由通道映射)
  - downstream_api_keys (下游 API 密钥)
  - proxy_logs (代理日志 - 可选)
  - settings (系统设置)
```

操作步骤：
```bash
# 1. gz 上导出
ssh gz 'docker exec metapi cp /app/data/hub.db /app/data/hub.db.export'
scp gz:/root/metapi-data/hub.db.export /tmp/hub.db.export

# 2. 导出 CPA 配置
scp -r gz:/opt/cliproxy/ /tmp/cliproxy-backup/

# 3. 修改 CPA config（删除 proxy-url）
# 4. 传输到 sgp3
scp /tmp/hub.db.export sgp3:/root/metapi-data/
scp -r /tmp/cliproxy-backup/ sgp3:/root/cpa/
```

### 5.3 阶段三：sgp3 部署

```yaml
# sgp3:/root/docker-compose.yml
services:
  metapi:
    image: 1467078763/metapi:latest
    network_mode: host  # port 4001
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
    volumes:
      - ./cpa/config.yaml:/opt/cliproxy/config.yaml
      - ./cpa/auth:/opt/cliproxy/auth
    restart: unless-stopped
```

### 5.4 阶段四：切流

```bash
# sgp2 nginx 配置修改
# 将 upstream 从 gz Tailscale IP 改为 sgp3 Tailscale IP
# /etc/nginx/conf.d/vectorcontrol.tech.conf:
#   proxy_pass http://100.96.101.24:4001  →  proxy_pass http://100.79.22.119:4001
#   proxy_pass http://100.96.101.24:8317  →  proxy_pass http://100.79.22.119:8317

ssh sgp2 'nginx -t && systemctl reload nginx'
```

### 5.5 阶段五：验证 + 清理

```bash
# 端到端测试
curl -s https://www.vectorcontrol.tech/v1/models  # 应返回 401（需认证）
curl -s -H "Authorization: Bearer <key>" https://www.vectorcontrol.tech/v1/chat/completions \
  -d '{"model":"gpt-5.4","messages":[{"role":"user","content":"Hi"}]}'

# 确认无误后停止 gz 的 metapi+CPA
ssh gz 'docker stop metapi; systemctl stop cliproxyapi'
```

### 5.6 后续：OpenClaw Docker 化（sgp1）

```dockerfile
# Dockerfile.openclaw
FROM node:20-slim
RUN npm install -g openclaw@2026.3.8
COPY openclaw.json /root/.openclaw/openclaw.json
EXPOSE 18789 18791 18792
CMD ["openclaw-gateway"]
```

---

## 6. 域名绑定分析

### 当前
- `vectorcontrol.tech` / `www.vectorcontrol.tech` → A: 20.195.40.11 (sgp2)

### 选项：sgp2 vs sgp3 作为公网入口

| 指标 | 保持sgp2 | 改为sgp3 | 双A记录(sgp2+sgp3) |
|------|---------|---------|-------------------|
| 配置变更 | 无 | DNS+SSL迁移 | 两台都要nginx+SSL |
| SSL 管理 | 1台certbot | 1台certbot | 2台certbot |
| 到后端延迟 | 0.6ms→sgp3 | localhost | 混合 |
| 故障恢复 | 手动切DNS | 手动切DNS | DNS轮询自动 |
| 复杂度 | **低** | 中 | **高** |

**推荐**：保持 sgp2 作为唯一公网入口。

理由：
1. sgp2→sgp3 仅 0.6ms，再加一层代理的延迟可忽略
2. sgp3 作为纯后端不暴露公网，减少攻击面
3. 避免双机 SSL 管理的复杂度
4. sgp2 已有完善的 nginx + certbot + UFW 配置

如果未来需要高可用：再在 sgp3 配置 nginx + certbot，通过 Cloudflare/DNS 健康检查实现自动故障转移。

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
sgp3/docker-compose.yml:  metapi + cpa
sgp1/docker-compose.yml:  openclaw + vectorcontrol (已有)
sgp2: host-level nginx only
gz:   host-level cron scripts only
```

---

## 8. 备份与可迁移性

Docker 化后的备份策略：

```bash
# 每日 cron
# sgp3 上:
docker exec metapi-1 cp /app/data/hub.db /app/data/hub.db.daily
tar czf /backup/sgp3-$(date +%F).tar.gz metapi-data/ cpa/ docker-compose.yml

# sgp1 上:
tar czf /backup/sgp1-$(date +%F).tar.gz openclaw/ vectorcontrol/ docker-compose.yml
```

**紧急迁移流程**（任意 SGP 节点故障时）：
1. 在存活节点上 `docker-compose up -d`（compose 文件 + 数据卷）
2. 修改 sgp2 nginx upstream 指向新节点
3. 整个过程 <5 分钟（因为 SGP 集群内 <5ms）

---

## 9. 目标架构总览

```
                    ┌─ DNS: vectorcontrol.tech ─┐
                    ↓                            
              sgp2 (847MB)                      
         nginx edge + SSL termination           
           ├── /api, /v1 → sgp3:4001 (0.6ms)  
           ├── /management → sgp3:8317 (0.6ms) 
           ├── /fund → static files             
           └── / → static portal                
                    │                            
                    │ 0.6ms                      
                    ↓                            
              sgp3 (847MB)                      
         metapi Docker + CPA Docker             
           ├── metapi:4001 → CPA:8317 (localhost)
           └── CPA → api.openai.com (直连!)     
                                                 
              sgp1 (2GB)                        
         OpenClaw + VectorControl Docker        
           ├── OpenClaw 18789/18791/18792       
           └── VectorControl (nginx+backend+pg) 
                                                 
              gz (1.6GB)                        
         监控 + 备份                             
           ├── cron: measure-snapshot.py (5min) 
           ├── cron: token-stats.py (1min)      
           └── hub.db 备份存档                   
```

### 迁移前后对比

| 指标 | 迁移前 | 迁移后 |
|------|--------|--------|
| API 首字节延迟 | ~350ms（4×跨国） | **~1ms**（SGP 内网） |
| CPA 出口方式 | SOCKS5 代理 | 直连 OpenAI |
| gz 负载 | metapi+CPA+nginx+cron | 仅 cron+备份 |
| 服务可迁移性 | 二进制+手动部署 | Docker compose 一键 |
| 备份恢复时间 | ~30min（手动配置） | **<5min**（compose up） |

---

## 10. 重命名建议

将 ssh alias `hk` 改为 `sgp3`，反映实际地理位置：

```ssh-config
# ~/.ssh/config
Host sgp3
    HostName 100.79.22.119
    User azureuser
```

同步更新：STATE.md, ROADMAP.md, service-reshuffle-plan.md 中的 "hk" 引用。
