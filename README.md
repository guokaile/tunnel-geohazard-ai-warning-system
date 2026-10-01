# 隧道地质灾害AI预警系统（TGAWS）

面向隧道施工/运营期的地质灾害（瓦斯、涌水、围岩变形等）**多源监测数据采集 → 实时分析 → 分级预警 → 处置闭环**的一体化系统。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 17 + Spring Boot 3.x 模块化单体（`common` / `access` / `compute` / `business` / `web`） |
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts |
| 数据库 | MySQL 8（主库 `tgaws`，测试库 `tgaws_test`） |
| 设备接入 | Netty TCP + 内嵌 MQTT（Moquette），网关 PSK 认证、密文凭据存储 |
| 部署 | Nginx（HTTPS 反代）+ WinSW 服务 + Inno Setup 6 安装包（Windows） |

## 目录结构

```
├── tunnel-geohazard-ai-warning-system/   # 后端（Spring Boot 多模块）
│   ├── tgaws-common/  tgaws-access/  tgaws-compute/
│   ├── tgaws-business/  tgaws-web/
│   └── scripts/db/                       # 建库/建表/种子数据/补丁（幂等）
├── frontend/                             # 前端 H5（登录/实时监控/预警中心/巡检/报表/系统管理）
├── deploy/
│   ├── templates/                        # 安装向导模板（Inno Setup .iss、Nginx、WinSW、建库/备份/恢复脚本）
│   └── scripts/                          # 辅助脚本（自签证书等）
├── 系统设计文档/                          # 需求/架构/数据库/接口/算法/详细设计/测试计划/评审报告
└── 部署安装包/                            # 安装包产出（不入库，见下）
```

> 不入库：安装包 exe（`部署安装包/`）、外部依赖（`deploy/downloads/`，需自备 MySQL 8.0.46 / nginx 1.26.3 / JRE 17）、构建产物与日志。

## 快速上手（本地开发）

前置要求：JDK 17、Maven 3.9（compiler 插件要求 ≥ 3.6.3）、MySQL 8、Node 18+。

### 1. 初始化开发库

```bash
# ① 建库 + 应用账号：先编辑 scripts/db/schema/00_create_db.sql，
#    把 __APP_DB_PASSWORD__ 替换为本地开发口令，再执行
mysql -uroot -p < tunnel-geohazard-ai-warning-system/scripts/db/schema/00_create_db.sql
```

建库脚本（按序执行，均在 `tunnel-geohazard-ai-warning-system/scripts/db/` 下）：
`schema/00_create_db.sql` → `schema/01_tables.sql` → `data/01_dict.sql` → `data/02_init_admin.sql` → `data/03_default_rule.sql` → `data/04_init_roles.sql` → `data/05_init_config.sql` → `patches/t*.sql`（按编号）。

### 2. 启动后端

```bash
cd tunnel-geohazard-ai-warning-system
# 设置环境变量（见下表），然后：
mvn -pl tgaws-web -am spring-boot:run   # 默认 8080
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev                              # 默认 5173，代理 /api 到 8080
```

默认管理员：`admin` / `Tgaws@2026`（**开发占位口令，首次登录强制改密**，改密前不发 token，机制见下）。

## 环境变量说明（敏感信息一律环境变量注入，仓库零硬编码）

| 变量 | 用途 | 说明 |
| --- | --- | --- |
| `DB_URL` | 数据库连接串 | 如 `jdbc:mysql://127.0.0.1:3306/tgaws?...` |
| `DB_USERNAME` | 应用库账号 | 开发默认 `tgaws` / `tgaws_app` |
| `DB_PASSWORD` | 应用库口令 | 本地自定；生产由安装向导注入 |
| `JWT_SECRET` | 接口令牌签名密钥 | 随机生成，如 `openssl rand -hex 32` |
| `CONFIG_ENC_KEY` | 对称加密密钥（网关/第三方应用凭据密文，`CryptoUtil`） | 随机生成，如 `openssl rand -hex 16` |
| `ADMIN_INIT_PASSWORD` | 首次启动注入的管理员口令 | 仅当 admin 仍处 1970 占位态时生效，注入后即转正常态 |
| `RPT_DIR` | 分析报告落盘目录 | 默认 `./reports` |
| `FILE_DIR` | 附件/文件目录 | 部署时按需指定 |

## 敏感信息占位符说明

仓库遵守「源码零硬编码凭据」纪律，以下占位符需在本地/部署时替换或生成：

| 占位符 | 位置 | 替换方式 |
| --- | --- | --- |
| `__APP_DB_PASSWORD__` | `scripts/db/schema/00_create_db.sql` | 本地开发前替换为自定口令 |
| `__REGENERATE_WITH_CRYPTOUTIL__` | `scripts/db/t2_seed.sql`（网关 secret / 第三方 app_secret） | 用 `CryptoUtil.encrypt(明文, CONFIG_ENC_KEY)` 生成密文后回填 |
| `@PLACEHOLDER@` | `deploy/templates/*` | 安装向导运行时随机生成并注入（口令/密钥均安装时生成） |

管理员种子口令 `Tgaws@2026` 为开发占位值：`pwd_update_time` 置 `1970-01-01` 占位态，后端强制**首次登录改密**、改密前不签发 token，因此占位口令公开安全。生产安装由安装向导经 `ADMIN_INIT_PASSWORD` 注入后重新哈希。

## 安装包构建（Windows）

`deploy/templates/tgaws-installer.iss` 为 Inno Setup 6.7 脚本：

1. 自备依赖放 `deploy/downloads/`：MySQL 8.0.46、nginx 1.26.3、JRE 17（离线物料，不入库）；
2. 用 ISCC（Inno Setup 编译器）编译 `.iss`，产出 `TGAWS-Setup-*.exe`；
3. 安装向导全自动完成：MySQL 初始化 → 建库建号 → 口令/密钥随机生成注入 → 三服务注册（MySQL 原生服务 + WinSW 应用/网关服务）→ HTTPS 自签证书 → 健康检查。

## 测试

```bash
# 后端（集成测试需 tgaws_test 库，凭据经环境变量注入）
DB_USERNAME=tgaws_app DB_PASSWORD='<本地口令>' mvn clean verify

# 前端
cd frontend && npm run build
```
