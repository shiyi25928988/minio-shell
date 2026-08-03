#!/usr/bin/env bash
# ============================================================
#  trust-cert.sh — 从 HTTPS 服务器抓取自签名证书并信任
#
#  用法:
#    ./trust-cert.sh 192.168.1.100:5000
#    ./trust-cert.sh registry.example.com
#    ./trust-cert.sh          (交互式输入)
# ============================================================
set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
GRAY='\033[0;90m'
NC='\033[0m'

# ── 解析参数 ──────────────────────────────────────────
INPUT="${1:-}"
if [[ -z "$INPUT" ]]; then
    read -r -p "请输入 HTTPS 站点地址 (例 192.168.1.100:5000): " INPUT
fi

# 去掉 https:// 前缀和尾部斜杠
INPUT="${INPUT#https://}"
INPUT="${INPUT#http://}"
INPUT="${INPUT%/}"

# 分离 host 和 port
if [[ "$INPUT" == *:* ]]; then
    HOST="${INPUT%:*}"
    PORT="${INPUT##*:}"
else
    HOST="$INPUT"
    PORT="443"
fi

echo ""
echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  抓取自签名证书并导入系统 & Docker 信任${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""
echo -e "  目标: ${GREEN}${HOST}:${PORT}${NC}"
echo ""

# ── 检查依赖 ──────────────────────────────────────────
for cmd in openssl curl; do
    if ! command -v "$cmd" &>/dev/null; then
        echo -e "${RED}[错误] 缺少依赖: $cmd${NC}"
        echo "  Debian/Ubuntu: sudo apt install $cmd"
        echo "  RHEL/CentOS : sudo yum install $cmd"
        exit 1
    fi
done

# ── [1/4] 抓取证书 ────────────────────────────────────
echo -e "${YELLOW}  [1/4] 连接 ${HOST}:${PORT} 抓取证书...${NC}"

CERT_PEM=$(openssl s_client -connect "${HOST}:${PORT}" -showcerts </dev/null 2>/dev/null | \
           awk '/-----BEGIN CERTIFICATE-----/,/-----END CERTIFICATE-----/')

if [[ -z "$CERT_PEM" ]]; then
    echo ""
    echo -e "${RED}  [失败] 无法从 ${HOST}:${PORT} 获取证书${NC}"
    echo ""
    echo "  可能的原因:"
    echo "    1. 服务器未运行"
    echo "    2. 端口未放行 / 防火墙拦截"
    echo "    3. 目标不是 HTTPS 服务"
    echo "    4. 网络不通: ping ${HOST}"
    exit 1
fi

# 解析证书信息
CERT_FILE="/tmp/trusted-cert-$$.crt"
echo "$CERT_PEM" > "$CERT_FILE"

SUBJECT=$(openssl x509 -in "$CERT_FILE" -noout -subject 2>/dev/null | sed 's/.*= //')
ISSUER=$(openssl x509 -in "$CERT_FILE" -noout -issuer 2>/dev/null | sed 's/.*= //')
NOT_BEFORE=$(openssl x509 -in "$CERT_FILE" -noout -startdate 2>/dev/null | cut -d= -f2)
NOT_AFTER=$(openssl x509 -in "$CERT_FILE" -noout -enddate 2>/dev/null | cut -d= -f2)
FINGERPRINT=$(openssl x509 -in "$CERT_FILE" -noout -fingerprint -sha256 2>/dev/null | cut -d= -f2 | tr -d ':')
SAN=$(openssl x509 -in "$CERT_FILE" -noout -ext subjectAltName 2>/dev/null | tail -n +2 | sed 's/^[[:space:]]*//')

echo ""
echo -e "${CYAN}  ┌─ 证书信息 ─────────────────────────────────────┐${NC}"
echo -e "${CYAN}  │${NC}  Subject   : ${SUBJECT}"
echo -e "${CYAN}  │${NC}  Issuer    : ${ISSUER}"
echo -e "${CYAN}  │${NC}  Valid     : ${NOT_BEFORE} ~ ${NOT_AFTER}"
echo -e "${CYAN}  │${NC}  SHA256    : ${FINGERPRINT:0:16}..."
if [[ -n "$SAN" ]]; then
    echo -e "${CYAN}  │${NC}  SAN       : ${SAN}"
else
    echo -e "${CYAN}  │${NC}  SAN       : ${RED}(未设置 — 可能导致校验失败!)${NC}"
fi
if [[ "$SUBJECT" == "$ISSUER" ]]; then
    echo -e "${CYAN}  │${NC}  类型      : ${YELLOW}自签名证书${NC}"
else
    echo -e "${CYAN}  │${NC}  类型      : ${YELLOW}CA 签发 (应该导入 CA 根证书而非此证书)${NC}"
fi
echo -e "${CYAN}  └─────────────────────────────────────────────────┘${NC}"
echo ""

# ── 平台检测 ──────────────────────────────────────────
if [[ -f /etc/os-release ]]; then
    . /etc/os-release
    OS_ID="${ID}"
else
    OS_ID="unknown"
fi

DOCKER_CERT_DIR="/etc/docker/certs.d/${HOST}"
if [[ "$PORT" != "443" ]]; then
    DOCKER_CERT_DIR="/etc/docker/certs.d/${HOST}:${PORT}"
fi

# ── [2/4] Docker 证书目录 ─────────────────────────────
echo -e "${YELLOW}  [2/4] 配置 Docker 信任...${NC}"

sudo mkdir -p "$DOCKER_CERT_DIR"
sudo cp "$CERT_FILE" "${DOCKER_CERT_DIR}/ca.crt"
sudo chmod 644 "${DOCKER_CERT_DIR}/ca.crt"

echo -e "        已写入: ${GREEN}${DOCKER_CERT_DIR}/ca.crt${NC}"

# ── [3/4] 系统证书信任 ────────────────────────────────
echo -e "${YELLOW}  [3/4] 导入系统证书信任...${NC}"

case "$OS_ID" in
    ubuntu|debian)
        sudo cp "$CERT_FILE" "/usr/local/share/ca-certificates/${HOST}-${PORT}.crt"
        sudo update-ca-certificates
        echo "        已导入 (update-ca-certificates)"
        ;;
    centos|rhel|fedora|rocky|almalinux)
        sudo cp "$CERT_FILE" "/etc/pki/ca-trust/source/anchors/${HOST}-${PORT}.crt"
        sudo update-ca-trust
        echo "        已导入 (update-ca-trust)"
        ;;
    arch|manjaro)
        sudo cp "$CERT_FILE" "/etc/ca-certificates/trust-source/anchors/${HOST}-${PORT}.crt"
        sudo trust extract-compat
        echo "        已导入 (trust extract-compat)"
        ;;
    alpine)
        sudo cp "$CERT_FILE" "/usr/local/share/ca-certificates/${HOST}-${PORT}.crt"
        sudo update-ca-certificates
        echo "        已导入 (update-ca-certificates)"
        ;;
    *)
        echo -e "        ${YELLOW}(未识别发行版, 跳过系统证书导入)${NC}"
        echo "        证书路径: ${DOCKER_CERT_DIR}/ca.crt"
        ;;
esac

# ── [4/4] 保存副本 ────────────────────────────────────
echo -e "${YELLOW}  [4/4] 保存证书副本...${NC}"

BACKUP_DIR="${HOME}/certs"
mkdir -p "$BACKUP_DIR"
BACKUP_FILE="${BACKUP_DIR}/${HOST}_${PORT}.crt"
cp "$CERT_FILE" "$BACKUP_FILE"
echo -e "        已保存: ${GREEN}${BACKUP_FILE}${NC}"

# 清理临时文件
rm -f "$CERT_FILE"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  完成!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "  Docker 无需重启, 现在可以直接使用:"
echo ""
echo -e "    ${CYAN}docker pull ${HOST}:${PORT}/busybox:latest${NC}"
echo -e "    ${CYAN}docker login ${HOST}:${PORT}${NC}"
echo ""
echo -e "  验证 Docker 信任状态:"
echo ""
echo -e "    ${GRAY}docker run --rm -v /etc/docker/certs.d:/certs alpine ls -R /certs${NC}"
echo ""
echo -e "  浏览器/curl 也已信任此证书 (系统级导入)"
echo ""
