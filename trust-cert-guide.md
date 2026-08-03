# 自签名 HTTPS 证书一键信任工具

从 HTTPS 服务器（如私有 Docker Registry、内部 Web 服务）抓取自签名证书，并自动配置操作系统及 Docker 信任，无需手动操作 OpenSSL 和证书存储。

---

## 快速开始

### Linux

```bash
chmod +x trust-cert.sh
./trust-cert.sh 10.35.79.185:5000
```

### Windows

```powershell
# 以管理员身份运行 PowerShell
powershell -File trust-cert.ps1 -HostPort "10.35.79.185:5000"

# 或双击 trust-cert.bat，交互式输入地址
```

---

## 典型场景：私有 Docker Registry

假设你用 `registry:3` 容器自建了一个带自签名证书的私仓：

```
┌──────────────────────────────────────────────────┐
│  registry:3 容器                                  │
│  - HTTPS on 10.35.79.185:5000                    │
│  - 自签名证书 (SAN: IP=10.35.79.185)              │
│                                                   │
│  问题: docker pull 报 x509 证书错误                │
│  解决: 运行 trust-cert.sh / trust-cert.ps1        │
└──────────────────────────────────────────────────┘
```

### 完整流程

```bash
# 1. 部署带 HTTPS 的 registry
mkdir -p certs data
# 生成自签名证书 (SAN 含 IP)
openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
  -keyout certs/registry.key -out certs/registry.crt \
  -subj "/CN=10.35.79.185" \
  -addext "subjectAltName=IP:10.35.79.185,DNS:localhost"

# 2. 启动 registry
docker run -d --name registry --restart always \
  -p 5000:5000 \
  -v "$(pwd)/certs:/certs:ro" \
  -v "$(pwd)/data:/var/lib/registry" \
  -e REGISTRY_HTTP_TLS_CERTIFICATE=/certs/registry.crt \
  -e REGISTRY_HTTP_TLS_KEY=/certs/registry.key \
  registry:3

# 3. 信任证书
./trust-cert.sh 10.35.79.185:5000

# 4. 验证
docker pull alpine
docker tag alpine 10.35.79.185:5000/alpine
docker push 10.35.79.185:5000/alpine       # 成功
```

---

## 脚本工作原理

### Linux (`trust-cert.sh`)

```
openssl s_client -connect  ← 连接到 HTTPS 服务器，抓取证书
        │
        ▼
  /etc/docker/certs.d/<host>:<port>/ca.crt   ← Docker 即时生效，无需重启
        │
        ▼
  /usr/local/share/ca-certificates/          ← curl / wget / 浏览器 即时生效
  update-ca-certificates
        │
        ▼
  ~/certs/<host>_<port>.crt                  ← 备份副本，方便分发
```

### Windows (`trust-cert.bat` + `trust-cert.ps1`)

```
PowerShell SslStream  ← 无视自签名错误，强行抓取服务器证书
        │
        ▼
  Windows 证书存储:
    LocalMachine\Root  (管理员)   ← 浏览器 / curl.exe 信任
    CurrentUser\Root   (兜底)
        │
        ▼
  桌面 .crt 文件                  ← 备份 + 后续手动塞入 WSL2
        │
        ▼
  ⚠ Docker Desktop 运行在 WSL2 VM 里，读不到 Windows 证书存储
  → 需手动写入 WSL2 的 /etc/docker/certs.d/
  → 脚本已输出具体命令
```

---

## 各平台 Docker 证书信任机制对比

| 平台 | Docker 证书目录 | 额外要求 |
|------|----------------|----------|
| **Linux** (原生) | `/etc/docker/certs.d/<host>:<port>/ca.crt` | 无，即时生效 |
| **Docker Desktop (Mac)** | `~/.docker/certs.d/<host>:<port>/ca.crt` | 需导入 macOS Keychain |
| **Docker Desktop (Windows)** | WSL2 VM 内的 `/etc/docker/certs.d/` | 需导入 Windows 证书存储 + 手动塞 WSL2 |
| **Windows Server** | `C:\ProgramData\docker\certs.d\` | 需导入 Windows 证书存储 |

> **核心规则：** 目录名必须与 `docker pull` 时用的地址完全一致。非 443 端口必须带端口号，443 端口不要加。

### Windows Docker Desktop 额外步骤

脚本完成后执行：

```powershell
# 1. 把证书塞进 WSL2 虚拟机
wsl -d docker-desktop -- bash -c "
  sudo mkdir -p /etc/docker/certs.d/10.35.79.185:5000
  sudo cp /mnt/c/Users/$env:USERNAME/Desktop/10.35.79.185_5000.crt \
           /etc/docker/certs.d/10.35.79.185:5000/ca.crt
"

# 2. 确认
wsl -d docker-desktop -- bash -c "ls -la /etc/docker/certs.d/10.35.79.185:5000/"

# 3. 重启 Docker Desktop (托盘图标 → Restart)

# 4. 测试
docker pull 10.35.79.185:5000/busybox:latest
```

也可以直接通过文件资源管理器操作：

```
地址栏输入: \\wsl$\docker-desktop\etc\docker\certs.d
在里面创建文件夹: 10.35.79.185:5000
把 .crt 文件复制进去，重命名为 ca.crt
```

---

## 验证

### 检查证书内容

```bash
# 查看证书 SAN
openssl x509 -in ca.crt -noout -text | grep -A3 "Subject Alternative"

# 期望看到你的 IP
#   IP Address:10.35.79.185
```

### 确认 Docker 已信任

```bash
# 查看 Docker 是否加载了证书目录
docker run --rm -v /etc/docker/certs.d:/certs alpine ls -R /certs

# 直接测试拉取
docker pull <host>:<port>/busybox:latest

# 测试登录
docker login <host>:<port>
```

### 确认系统已信任

```bash
# Linux
curl -v https://10.35.79.185:5000/v2/   # 不应报 SSL 错误

# Windows
curl.exe -v https://10.35.79.185:5000/v2/
# 浏览器访问 https://10.35.79.185:5000/v2/ → 不应有安全警告
```

---

## 故障排查

### `x509: certificate signed by unknown authority`

```
可能原因:
1. 证书未正确放入 certs.d 目录       → 检查目录名是否与 pull 地址完全一致
2. 目录名缺少端口号 (非 443 时)      → 确认是 host:port 格式
3. Docker Desktop 未重启             → 托盘图标 → Restart
4. Windows: 证书未塞入 WSL2 VM       → 运行上方 wsl -d docker-desktop 命令
```

### `x509: cannot validate certificate for <IP>`

```
证书 SAN 中不包含你访问的 IP 地址。
→ 重新生成证书，确保 SAN 中包含 IP.x=<你的IP>
→ 重新部署 registry 容器
→ 重新运行信任脚本
```

### `http: server gave HTTP response to HTTPS client`

```
Registry 容器未挂载 TLS 证书，运行在 HTTP 模式
→ 检查 REGISTRY_HTTP_TLS_CERTIFICATE / REGISTRY_HTTP_TLS_KEY 环境变量
→ 或直接 curl http://host:port/v2/ 确认
```

### 浏览器仍报警告

```
Windows: 确认已导入到 LocalMachine\Root (非 CurrentUser)
Linux:   确认运行了 update-ca-certificates / update-ca-trust
两者:    重启浏览器 (Chrome 使用独立证书存储)
```

---

## 文件清单

| 文件 | 平台 | 用途 |
|------|------|------|
| `trust-cert.sh` | Linux / macOS | 抓取证书 → Docker 信任 → 系统信任 |
| `trust-cert.ps1` | Windows | 抓取证书 → Windows 证书存储 → 导出 PEM |
| `trust-cert.bat` | Windows | .bat 入口，避免 CMD 编码问题 |
