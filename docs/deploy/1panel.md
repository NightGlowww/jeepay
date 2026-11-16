# 使用 1Panel 的容器编排安装 Jeepay

本页使用仓库已有的源码 Compose，由 1Panel 管理容器；不是 1Panel 应用商店安装包，不会自动同步已有宝塔部署。适用前提是独立部署目录、Docker Compose v2、已打包的后端 JAR 和配套的 `jeepay-ui` 源码。

本页依据 [1Panel v2 编排说明](https://1panel-dev.github.io/docs/v2/user_manual/containers/compose/) 编写。当前只完成离线检查，尚未在 1Panel 上完成安装、升级或支付联调；面板版本不同，菜单名称可能不同。

## 1. 准备与固定版本

在服务器准备同级的 `jeepay/` 与 `jeepay-ui/`，分别检出选定版本并记录 commit。后端与 UI 版本需要配套验证；不能仅凭版本号相同认定兼容。

在 `jeepay/` 中用 JDK 17 执行 `mvn clean package -DskipTests`，确认三个模块的 `target/jeepay-*.jar` 存在。这个命令只打包，不代表测试通过。前端镜像会从 `../jeepay-ui/Dockerfile` 构建；也可在 `.env` 设置 `UI_BASE_DIR` 为包含 `jeepay-ui` 的父目录绝对路径。

保留本仓库目录结构，复制 `docker-compose.yml` 为同目录下的 `docker-compose.1panel.yml`，只修改副本。不要把 YAML 单独粘贴到另一个目录，否则相对挂载路径、Dockerfile 和前端路径会改变。不要执行 `docs/install/install.sh` 叠加安装另一套同名服务。

## 2. 创建前修改配置

仓库主 Compose 面向本地测试。导入 1Panel 前，在副本完成下面的调整：

| 位置 | 调整 |
|---|---|
| `mysql.environment` | `MYSQL_ROOT_PASSWORD` 改为 `${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD}`，`MYSQL_PASSWORD` 改为 `${MYSQL_PASSWORD:?set MYSQL_PASSWORD}`，保留 `MYSQL_USER: jeepay` 与 `MYSQL_DATABASE: jeepaydb` |
| 三个 Java 服务的 `environment` | 增加下面的 datasource 覆盖，使用应用账户，不使用 root |
| MySQL、Redis、NameServer、Broker、三个 Java 服务 | 删除各自完整的 `ports` 配置段；同一网络内仍可通过服务名连接，不需要发布端口 |
| 三个 UI 服务的 `ports` | 分别改为 `127.0.0.1:9226:80`、`127.0.0.1:9227:80`、`127.0.0.1:9228:80`，只供宿主机代理访问 |
| `networks.jeepay` | 确认 `172.20.0.0/16` 不与现有网络重叠；若要换子网，须一并修改静态 IP 与各 UI 的 `BACKEND_HOST` |
| `container_name` | 确认当前服务器没有同名容器；不要删除其他安装的数据或容器来腾名字 |

三个 Java 服务分别加入：

```yaml
environment:
  SPRING_DATASOURCE_USERNAME: jeepay
  SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD:?set MYSQL_PASSWORD}
```

在同目录 `.env` 中自行填写两个不同的随机强密码并设置 `chmod 600 .env`；保留原来的 `UI_BASE_DIR`。不要把实际 `.env`、渲染后的 Compose 或带环境变量的 `docker inspect` 输出提交到仓库、公开 Issue 或分享模板。已有 MySQL 数据卷不会因修改 `MYSQL_PASSWORD` 自动修改数据库账户密码，升级时需单独处理。

保留三份 `conf/<service>/application.yml` 中的 `mysql:3306`、`redis:6379`、`rocketmq-namesrv:9876` 和 `isys.mq.vender: rocketMQ`。`127.0.0.1` 在 Java 容器内指向该容器，不能用它连接另一容器。

首次建库只挂载 `docs/sql/init.sql`。它是完整结构和初始数据，包含 `DROP TABLE`；不要手工对现有库重放。`patch.sql` 是跨多个旧版本的增量历史，不可在完整初始化后再整份执行。已有数据库按实际起始版本和 [升级说明](../../upgrade.md) 选择迁移，并先备份。

启动前只校验配置，不把含密码的结果打印到终端：

```bash
docker compose --env-file .env -f docker-compose.1panel.yml config --quiet
```

镜像来源使用仓库当前默认值，也可通过 `MYSQL_IMAGE`、`REDIS_IMAGE`、`ROCKETMQ_IMAGE` 覆盖。镜像能否拉取取决于服务器到仓库的连接；不要把第三方加速器当成一定可用的依赖。正式运行应固定已验证的镜像 tag 或 digest，并保留上一个版本。

## 3. 在 1Panel 创建编排

在「容器 → 编排 → 创建编排」选择「路径选择」，指定服务器上刚准备的 `jeepay/docker-compose.1panel.yml`。确认面板使用该目录的 `.env`，再创建并构建。若面板不支持当前 Compose 字段或不能读取环境文件，先按其报错修正，不要删掉健康依赖或密码必填约束来绕过。

在编排详情查看 MySQL、Redis、RocketMQ 和三个 Java 服务的健康状态，再查看 UI；应用还需通过登录和渠道验收。MySQL 数据保存在该编排的 named volume 中；换编排名可能得到全新数据卷，不能把它误认为数据库丢失。

## 4. 配置网站与支付地址

用三个自有域名分别代理收银台、运营端和商户端，并配置 HTTPS：

| 服务 | 宿主机代理的上游 | 同网络内的容器代理上游 |
|---|---|---|
| 收银台 | `http://127.0.0.1:9226` | `http://ui-payment:80` |
| 运营端 | `http://127.0.0.1:9227` | `http://ui-manager:80` |
| 商户端 | `http://127.0.0.1:9228` | `http://ui-merchant:80` |

如果 1Panel 的 OpenResty 运行在独立容器中，它的 `127.0.0.1` 不是宿主机。此时需显式把代理加入本编排网络，并用上表服务名回源；在面板的持久配置中维护网络接入，确保升级或重建后仍保留。不要为解决代理连不通而直接把数据库、MQ 或所有后台端口开放到公网。发布端口的规则见 [Docker 官方说明](https://docs.docker.com/engine/network/port-publishing/)。

HTTPS 转发头和 WebSocket 设置参见 [https.md](./https.md)。本文使用的主 Compose 的 UI/nginx 来自配套 `jeepay-ui`，需要核对该前端版本的 nginx 模板；仓库 `docs/install/include/nginx.conf` 属于 Shell/宝塔拓扑，不能假定它会自动覆盖前端镜像。

首次登录后修改运营账号默认密码，在系统配置中把 `paySiteUrl` 改为实际公网支付域名（不带结尾 `/`），再配置渠道。这个地址用于构造渠道回调和回跳，必须同时让浏览器、渠道服务器与商户端后端可访问。后续按 [支付渠道接入](../integrate/payment-channels.md) 验收。

## 5. 升级与回退

升级前保存当前后端/UI commit、镜像 digest、配置、数据库备份和 uploads；确认备份可恢复。只替换选定版本的源码、JAR 和配套前端，再在同一编排重建。数据库若发生不兼容迁移，必须按升级说明恢复备份，不能只降镜像版本。

暂停/删除编排时确认数据卷保留。`docker compose down -v` 会删除卷，不是普通重启或升级步骤。不要用卸载脚本诊断登录失败；先按 [troubleshooting.md](./troubleshooting.md) 定位连接或配置问题。
