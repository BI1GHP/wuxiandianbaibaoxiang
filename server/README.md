# CW练考通 · 进度同步服务

零依赖 Node 服务，提供两种搬运备份 JSON 的方式：

- **同步码**：不建账号，同步码本身就是取回数据的钥匙；
- **账号**：用户名 + 密码注册登录，密码只存加盐 scrypt 哈希，登录后按账号上传 / 下载；
- **会员开关**：默认关闭，由管理后台（`/admin.html`）控制开放 / 关闭，关闭需管理员密码。

## 目录结构

```
server/
├─ sync-server.mjs      服务端（Node 18+，无第三方依赖）
├─ public/index.html    网页同步页（同步码模式）
├─ public/account.html  网页账号页（注册 / 登录 / 上传 / 下载）
├─ public/coach.html    教练看板
├─ public/admin.html    管理后台（会员开关 / 手动开通会员）
├─ data/                运行时自动创建，按同步码存 <CODE>.json
│  ├─ accounts/         账号数据 <id>.json（哈希、token 哈希、会员到期、备份）
│  ├─ member.json       会员功能开关（不存在时默认关闭）
│  └─ codes.json        兑换码（卡密）与使用记录
└─ README.md
```

## 本地启动

```powershell
# 默认 8787
node server/sync-server.mjs

# 自定义端口
$env:PORT=9000; node server/sync-server.mjs

# 自定义保留期（天），0 = 永不过期，默认 180
$env:SYNC_TTL_DAYS=90; node server/sync-server.mjs

# 启用全局访问口令（可选），启用后 App / 网页都需填写同一口令
$env:SYNC_TOKEN="换成你的长口令"; node server/sync-server.mjs

# 启用教练口令（可选），教练看板需填写同一口令
$env:COACH_TOKEN="换成教练口令"; node server/sync-server.mjs

# 管理后台密码（可选，默认 Hz@18601044023，公网部署务必修改）
$env:ADMIN_PASSWORD="换成你的管理密码"; node server/sync-server.mjs
```

- 本机访问：<http://127.0.0.1:8787>
- 局域网访问：`http://<本机内网IP>:8787`，首次需在 Windows 防火墙放行该端口：

```powershell
New-NetFirewallRule -DisplayName "CW Sync 8787" -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow
```

查看内网 IP：`ipconfig`，取 IPv4 地址（如 `192.168.1.20`）。

App 端填写：设置 → 数据备份 → 进度同步 → 服务器地址填 `http://192.168.1.20:8787`，
同步码可点「换一个」重新生成，随后点「上传本机进度」。

账号模式：设置 → 数据备份 → 账号云同步，填同一服务器地址，注册或登录后即可上传 / 下载；
网页端打开 `/account.html` 使用同一账号。

管理后台：浏览器打开 `/admin.html`，默认管理员密码 `Hz@18601044023`，
可随时开启 / 关闭会员功能（关闭时需再次输入密码），也可按账号手动开通会员。
App 端入口：设置 → 会员与支付 → 会员中心。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/sync` | 写入，body：`{"code":"K7M2QX","payload":{...备份 JSON...}}` |
| GET | `/api/sync?code=K7M2QX` | 读取，返回 `{ok, code, updatedAt, payload}` |
| POST | `/api/account/register` | 注册，body：`{"username":"bg1abc","password":"******"}`，返回 `{ok, username, token}` |
| POST | `/api/account/login` | 登录，body 同上，返回 `{ok, username, token, updatedAt, hasPayload}` |
| POST | `/api/account/push` | 上传，body：`{"payload":{...}}`，需请求头 `x-account-token` |
| GET | `/api/account/pull` | 下载，需请求头 `x-account-token`，返回 `{ok, username, updatedAt, payload}` |
| GET | `/api/member/config` | 会员开关（公开）：`{ok, enabled, price, updatedAt}` |
| GET | `/api/member/status` | 当前账号会员状态，需 `x-account-token` |
| POST | `/api/member/checkout` | 下单入口：关闭时 403，支付未接入时 501，不扣费 |
| POST | `/api/member/redeem` | 兑换码开通，body `{"code":"..."}`，需 `x-account-token` |
| POST | `/api/admin/login` | 管理员登录，body `{"password":"..."}`，返回 `{ok, token, expiresAt}` |
| GET | `/api/admin/member` | 读取会员开关，需 `x-admin-token` |
| POST | `/api/admin/member` | `{"enabled":true/false,"password":"..."}`；关闭必须带密码 |
| POST | `/api/admin/grant` | `{"username":"bg1abc","days":30}` 开通 / 续期；`days=0` 取消 |
| POST | `/api/admin/codes` | `{"days":30,"count":5}` 批量生成兑换码（最多 100 个） |
| GET | `/api/admin/codes` | 兑换码列表、未使用数量与使用者 |
| POST | `/api/coach/summary` | 教练看板：body `{"codes":["K7M2QX","AB12CD"]}`，只返回进度摘要 |
| GET | `/health` | 健康检查 |
| GET | `/` | 网页同步页 |
| GET | `/account.html` | 网页账号页 |
| GET | `/coach.html` | 教练看板页 |
| GET | `/admin.html` | 管理后台 |

## 教练看板

俱乐部 / 教练可在网页打开 `/coach.html`，把学员的同步码一行一个填入即可汇总：

- 学员呼号、等级、最近同步时间、连续训练天数、近 7 天训练量；
- 背码题数、听抄最佳、赛事最佳、最易混字符 Top 3；
- 名单保存在浏览器本地，不上传服务器；页面每 60 秒自动刷新。

设置 `COACH_TOKEN` 后，接口需带请求头 `x-coach-token`，网页对应填写「教练口令」。
单次最多汇总 50 个同步码；接口只读取已上传的备份并抽取摘要，不返回完整备份内容。

## 会员与支付开关

P2-2 的会员体系与支付入口已写入代码，但**默认不开放**，由管理后台统一控制：

1. 打开 `/admin.html`，输入管理员密码（默认 `Hz@18601044023`，可用 `ADMIN_PASSWORD` 覆盖）；
2. 点「开启会员功能」后，App 会员中心与 `/api/member/config` 立即变为已开放；
3. 点「关闭会员功能」会要求**再次输入管理员密码**，验证通过才关闭；
4. 「账号会员」区域可按用户名手动开通 / 续期（30 天等），`days=0` 表示取消；
5. 「兑换码」区域可批量生成卡密（天数 + 数量），复制后分发；用户在 App「会员中心」
   或网页 `/account.html` 输入兑换码即可自动开通，每个码只能用一次，重复使用返回 409。

支付渠道尚未接入：开放后 App 仍可看到「开通 PLUS」入口，但下单接口返回
`501 支付渠道尚未接入，可使用兑换码开通或联系管理员`，不会产生任何扣费。
会员开关落盘在 `data/member.json`，兑换码落盘在 `data/codes.json`，重启不丢失；
管理员 token 只存内存，重启后需重新登录。

```bash
# 读取开关（公开）
curl http://127.0.0.1:8787/api/member/config

# 管理员登录
curl -X POST http://127.0.0.1:8787/api/admin/login \
  -H "content-type: application/json" \
  -d '{"password":"Hz@18601044023"}'

# 关闭（必须带密码）
curl -X POST http://127.0.0.1:8787/api/admin/member \
  -H "content-type: application/json" \
  -H "x-admin-token: <token>" \
  -d '{"enabled":false,"password":"Hz@18601044023"}'
```

```bash
# 写入
curl -X POST http://127.0.0.1:8787/api/sync \
  -H "content-type: application/json" \
  -d '{"code":"K7M2QX","payload":{"app":"CW练考通","prefs":{}}}'

# 读取
curl "http://127.0.0.1:8787/api/sync?code=K7M2QX"
```

限制：同步码 4–12 位字母数字；用户名 3–20 位字母 / 数字 / 下划线，密码 6–64 位；
请求体 ≤ 512KB；每 IP 每分钟 60 次；同一 IP 10 分钟内 15 次查不到同步码或登录失败会封禁 10 分钟；
设置 `SYNC_TOKEN` 后，同步码接口需带请求头 `x-sync-token`，否则返回 401。

## 部署到服务器

### 方式一：直接运行（systemd）

```bash
# 1. 上传 server 目录到 /opt/cw-sync
sudo mkdir -p /opt/cw-sync && sudo cp -r server/* /opt/cw-sync/

# 2. 建服务用户
sudo useradd -r -s /usr/sbin/nologin cwsync || true
sudo chown -R cwsync:cwsync /opt/cw-sync

# 3. systemd
sudo tee /etc/systemd/system/cw-sync.service >/dev/null <<'EOF'
[Unit]
Description=CW Trainer Sync Server
After=network.target

[Service]
Type=simple
User=cwsync
WorkingDirectory=/opt/cw-sync
Environment=PORT=8787
Environment=SYNC_TTL_DAYS=180
# 反代后建议只监听本机
# Environment=HOST=127.0.0.1
# 可选：启用访问口令
# Environment=SYNC_TOKEN=换成你的长口令
ExecStart=/usr/bin/node /opt/cw-sync/sync-server.mjs
Restart=on-failure
RestartSec=3
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now cw-sync
sudo systemctl status cw-sync
```

### 方式二：Docker

无需 Dockerfile，直接挂载目录：

```bash
docker run -d --name cw-sync \
  -p 8787:8787 \
  -e PORT=8787 \
  -e SYNC_TTL_DAYS=180 \
  -e SYNC_TOKEN=换成你的长口令 \
  -v /srv/cw-sync/data:/app/data \
  -v /srv/cw-sync/public:/app/public \
  -v /srv/cw-sync/sync-server.mjs:/app/sync-server.mjs:ro \
  -w /app \
  --restart unless-stopped \
  node:22-alpine node sync-server.mjs
```

### Nginx 反向代理 + HTTPS

同步数据含呼号与成绩，公网部署务必上 HTTPS。下面以 Debian / Ubuntu 的 Nginx 为例，
域名统一用 `sync.example.com`，请替换成自己的。

**第 1 步：让 Node 只监听本机**

```bash
# systemd 的 [Service] 里加一行，或在命令行前加环境变量
Environment=HOST=127.0.0.1
```

这样 8787 端口不会直接暴露到公网，只能经 Nginx 访问。

**第 2 步：先拿到证书**

```bash
sudo apt install -y nginx certbot python3-certbot-nginx
# 临时放行 80，用 standalone 或 nginx 插件签发
sudo certbot certonly --nginx -d sync.example.com
# 证书路径：/etc/letsencrypt/live/sync.example.com/{fullchain,privkey}.pem
```

**第 3 步：写入站点配置** `/etc/nginx/conf.d/cw-sync.conf`

```nginx
# 应用层已有每 IP 60 次/分钟的限流，这里再加一层代理层限流
limit_req_zone $binary_remote_addr zone=cw_sync:10m rate=60r/m;

# 80 全部跳转 HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name sync.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    http2 on;                      # nginx < 1.25.1 改为 listen 443 ssl http2;
    server_name sync.example.com;

    ssl_certificate     /etc/letsencrypt/live/sync.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/sync.example.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;

    # 备份 JSON 上限 512KB，留出余量
    client_max_body_size 1m;

    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy no-referrer always;

    location / {
        limit_req zone=cw_sync burst=20 nodelay;
        limit_req_status 429;

        proxy_pass http://127.0.0.1:8787;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_connect_timeout 5s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }
}
```

若只想允许特定网段访问，在 `location /` 里加：

```nginx
allow 192.168.0.0/16;
allow 203.0.113.0/24;   # 换成自己的公网出口
deny all;
```

**第 4 步：校验并重载**

```bash
sudo nginx -t && sudo systemctl reload nginx
```

**第 5 步：验证**

```bash
curl -s https://sync.example.com/health
# {"ok":true,"service":"cw-sync",...}

curl -sI https://sync.example.com/
# HTTP/2 200，content-type: text/html

# 启用了 SYNC_TOKEN 时：
curl -s -H "x-sync-token: 你的口令" \
  "https://sync.example.com/api/sync?code=K7M2QX"

# 证书自动续期演练
sudo certbot renew --dry-run
```

启用 HTTPS 后，App 里的服务器地址改为 `https://sync.example.com`（不要带 `/api`）。

**HTTPS 常见问题**

| 现象 | 处理 |
| --- | --- |
| 502 Bad Gateway | Node 未启动、端口不对，或 `HOST=127.0.0.1` 后 Nginx 仍代理到其他地址；`curl http://127.0.0.1:8787/health` 自查 |
| 413 Request Entity Too Large | 调大 `client_max_body_size`（默认已给 1m） |
| 504 Gateway Timeout | 检查 Node 是否卡死；代理超时已设 30s，正常备份远小于此 |
| 429 且日志显示 `limiting requests` | 触发 Nginx 层限流，调大 `rate` / `burst` 或确认是否被恶意请求 |
| 浏览器提示证书无效 | 域名未解析到本机、证书域名不匹配，或未执行第 2 步 |
| 手机连不上但浏览器正常 | 确认 App 地址为 `https://`，且未额外带 `/api` 或末尾斜杠 |

## 安全建议

- **同步码即密钥**：服务端已内置枚举防护（10 分钟 15 次未命中即封禁 10 分钟）。公网部署仍建议把同步码改长（App 支持 4–12 位），并启用 `SYNC_TOKEN`。
- **访问口令**：`SYNC_TOKEN` 为全局口令，App 的「进度同步」页与网页都有对应输入框；口令只存在本机（网页存 sessionStorage，关标签页即失效）。
- **账号密码**：服务端用 scrypt 加盐哈希保存密码（每个账号独立随机盐），登录签发的 token 只存 SHA-256 哈希；服务端记录文件权限 0600。用户名不区分大小写，不支持找回密码，忘记只能重新注册。
- **账号 token**：一台设备登录一次签发一个 token，最多保留 5 个（支持多设备）；App 退出登录即从本机清除，服务端不主动过期。
- **管理后台**：默认密码为 `Hz@18601044023`，公网部署必须用 `ADMIN_PASSWORD` 覆盖；登录失败计入封禁（10 分钟 15 次）；admin token 存内存、2 小时过期，关闭会员功能还需再次校验密码。不要在公开页面暴露 `/admin.html` 链接。
- **文件权限**：`data/` 目录以 0700、记录文件以 0600 创建（Linux 生效），仅服务账号可读。
- **定期备份** `server/data/`，其中是明文备份 JSON（含呼号、成绩，不含支付信息）。
- **过期清理**：服务端内置 TTL，默认 180 天，启动时与每 6 小时各清理一次；读取到过期记录会直接删除并返回 404。用 `SYNC_TTL_DAYS` 调整，设为 `0` 关闭。仍可保留一条手动兜底命令：

```bash
find /opt/cw-sync/data -name '*.json' -mtime +180 -delete
```

- **传输加密**：Android 端已允许明文 HTTP（`usesCleartextTraffic="true"`），仅建议在可信局域网使用；公网必须 HTTPS。
- **覆盖风险**：知道同步码的人可以覆盖数据；App 端每次下载都会二次确认，网页端上传需手动选择文件。

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| App 提示「请先填写服务器地址」 | 地址需以 `http://` 或 `https://` 开头，且不要带 `/api` 后缀 |
| 下载报 404 | 同步码尚未上传过进度，或已超过保留期被清理；先在 App 点「上传本机进度」 |
| 下载报 401 | 服务器启用了 `SYNC_TOKEN`，需在 App / 网页填写相同口令 |
| 下载报 429 | 触发限流（每分钟 60 次），或 10 分钟内 15 次未命中导致封禁 10 分钟 |
| 账号登录报 401 | 用户名或密码不正确；已登录但报「登录已失效」时重新登录即可（服务端最多保留 5 个 token） |
| 账号下载报 404 | 该账号还没有上传过进度，先在 App「账号云同步」点「上传本机进度」 |
| App 会员页显示「暂未开放」 | 服务端会员开关默认关闭；到 `/admin.html` 开启后，在会员中心点「刷新状态」 |
| 关闭会员功能报 403 | 关闭必须再次输入管理员密码，密码错误或被限流都会失败 |
| 兑换码提示已被使用 | 每个卡密只能兑换一次；在管理后台重新生成或换一个未使用的码 |
| 管理后台一直要求登录 | admin token 仅存内存、2 小时过期；服务重启后需重新输入密码 |
| 局域网连不上 | 检查手机与电脑是否同一网段、防火墙是否放行端口、服务是否在运行（`curl http://IP:8787/health`） |
| 网页打开空白 | 确认 `public/index.html` 存在；静态资源缺失时接口仍可用 |
| 上传报「请求体过大」 | 备份超过 512KB，通常不会发生；如确实需要可调大 `MAX_BODY` |
