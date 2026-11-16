# Docker Compose 部署

适合本地 / 测试环境拉起完整集群（含前端）。

## 目录约定

```
jeepay-open/
├── jeepay/
└── jeepay-ui/
```

前端不在 jeepay 同级时，可在 `jeepay/.env` 覆盖 `UI_BASE_DIR`（参考 `.env.example`）。

## 启动

```bash
cd jeepay
mvn clean package -DskipTests          # 编译后端 JAR

docker compose up -d --build           # 首次：编译镜像 + 启动
docker compose up -d                   # 后续：跳过编译
docker compose restart payment         # 重启某个服务
```

后端 Dockerfile 位于 `jeepay-payment/`、`jeepay-manager/`、`jeepay-merchant/`，直接复制各模块 `target/` 中的 JAR。旧文档中的 `docker build -f docs/Dockerfile` 与 `jeepay-deps` 依赖镜像已不适用；无需创建同名空文件。

第三方基础镜像默认指向华为云 SWR 公开仓库（与 Shell 部署一致），三个后端和 UI 在这里从源码构建。服务器仍需能够访问实际使用的 Maven/npm/镜像仓库，默认地址不保证所有地区都可达。

首次建库只执行完整的 `docs/sql/init.sql`。`docs/sql/patch.sql` 是旧版本升级历史，其中包含当前完整结构已经存在的列，不能在新库初始化后全量重放。数据库已有数据时，备份后按实际版本选择升级步骤，不要重放带 `DROP TABLE` 的 `init.sql`。

## 暴露端口

| 组件 | 端口 |
|---|---|
| MySQL | `13306`（避开宿主 3306） |
| Redis | `6380` |
| RocketMQ | `9876` / `10909` / `10911` / `10912` |
| payment / manager / merchant | `9216` / `9217` / `9218` |
| 前端 | manager `9227` / merchant `9228` / cashier `9226` |

## 默认账号

- 运营平台 `http://localhost:9227`：`jeepay` / `jeepay123`
- 商户系统 `http://localhost:9228`：需运营平台创建商户用户（默认密码 `jeepay666`）

## 常用命令

```bash
docker compose ps
docker compose logs -f --tail=100 payment manager merchant
docker compose config                  # 启动前变量 / 语法校验
docker compose down                    # 停止 + 清网络（卷保留）
docker compose down -v                 # 连同卷一起删
```

## 配置说明

- 默认 MQ = RocketMQ；`conf/payment|manager|merchant/application.yml` 已切到 `rocketMQ`。
- 容器内 MySQL / Redis 主机名 = `mysql` / `redis`（compose 已配 network alias）。
- 改 `conf/*.yml` 后 `docker compose restart <service>` 即可。
- 要回到 ActiveMQ / RabbitMQ，需同时改 `docker-compose.yml` 和 `conf/*.yml`。
- 默认端口和账号面向本地测试。服务器面板部署需先调整数据库账号、密码与暴露端口，见 [1Panel 通用编排说明](./1panel.md)。
- 登录后把系统应用配置中的 `paySiteUrl` 设置为真实公网支付根地址（不带结尾 `/`）；默认 `127.0.0.1:9216` 在商户容器中指向自身，不能访问 payment，也不能供渠道回调。

---

- 出问题排查：[troubleshooting.md](./troubleshooting.md)
- 推镜像到 Docker Hub / 华为云 SWR（维护者流程）：[publish.md](./publish.md)
- 微信、支付宝与 PayPal 配置：[payment-channels.md](../integrate/payment-channels.md)
