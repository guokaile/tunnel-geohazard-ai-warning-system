#!/bin/bash
# =============================================================================
# TGAWS 内网 CA + HTTPS 服务器证书生成脚本（开发/打包期用，OpenSSL 3.x）
# 生产安装向导使用同逻辑的 PowerShell/.bat 变体（gen-cert.cmd），口径一致：
#   - 内网 CA（自建，10 年）→ 服务器证书由 CA 签发（10 年）
#   - SAN 覆盖 DNS:localhost + IP:127.0.0.1 + 本机局域网 IP（移动 H5 需按
#     https://<局域网IP> 访问，Service Worker 安全上下文要求证书与访问主机名匹配）
#   - 禁止 HTTP 降级（NFR-S5 决策：FR-104/FR-503 离线能力依赖安全上下文）
# 用法：gen-cert.sh <输出目录> [额外SAN,逗号分隔] [证书天数(默认3650)]
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*"   # 防 MSYS 把 -subj 的 /C=CN 转成 Windows 路径

OUT_DIR="${1:?用法: gen-cert.sh <输出目录> [额外SAN] [天数]}"
# 统一为 Windows 路径（MSYS_NO_PATHCONV=1 下 openssl 不认 /d/... 形式）
OUT_DIR=$(cygpath -w "$OUT_DIR" 2>/dev/null || echo "$OUT_DIR")
EXTRA_SAN="${2:-}"
DAYS="${3:-3650}"
mkdir -p "$OUT_DIR"

# 本机局域网 IPv4（取第一块非回环网卡）
LAN_IP=$(ipconfig | iconv -f GBK -t UTF-8 2>/dev/null | awk '/IPv4/{gsub(/\r/,"");print $NF}' | grep -v '^127\.' | head -1)
[ -z "$LAN_IP" ] && LAN_IP=127.0.0.1
echo "LAN_IP=$LAN_IP"

SAN_LIST="DNS:localhost,IP:127.0.0.1,IP:${LAN_IP}"
[ -n "$EXTRA_SAN" ] && SAN_LIST="${SAN_LIST},${EXTRA_SAN}"
echo "SAN=$SAN_LIST"

# ---------- 1. 内网 CA ----------
if [ ! -f "$OUT_DIR/ca.crt" ]; then
  openssl req -x509 -newkey rsa:2048 -sha256 -days "$DAYS" -nodes \
    -keyout "$OUT_DIR/ca.key" -out "$OUT_DIR/ca.crt" \
    -subj "/C=CN/O=TGAWS Internal CA/CN=TGAWS-Tunnel-Geohazard-CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" 2>/dev/null
  echo "CA 已生成: $OUT_DIR/ca.crt"
fi

# ---------- 2. 服务器证书（由 CA 签发） ----------
openssl req -newkey rsa:2048 -sha256 -nodes \
  -keyout "$OUT_DIR/server.key" -out "$OUT_DIR/server.csr" \
  -subj "/C=CN/O=TGAWS/CN=tgaws-server" 2>/dev/null

cat > "$OUT_DIR/server.ext" <<EOF
basicConstraints=CA:FALSE
keyUsage=digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=${SAN_LIST}
EOF

openssl x509 -req -in "$OUT_DIR/server.csr" -CA "$OUT_DIR/ca.crt" -CAkey "$OUT_DIR/ca.key" \
  -CAcreateserial -out "$OUT_DIR/server.crt" -days "$DAYS" -sha256 \
  -extfile "$OUT_DIR/server.ext" 2>/dev/null

rm -f "$OUT_DIR/server.csr" "$OUT_DIR/server.ext"
echo "server.crt 已生成（含 SAN）:"
openssl x509 -in "$OUT_DIR/server.crt" -noout -subject -ext subjectAltName
echo "输出目录: $OUT_DIR"
