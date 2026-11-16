# 部署常见问题排查

排障第一步：先看**完整安装日志**（Shell 部署时脚本在开头与 summary box 里都打印了路径）：

```bash
ls -lt /tmp/jeepay-install-*.log | head -1
less $(ls -t /tmp/jeepay-install-*.log | head -1)
```

## 登录页验证码不出 / 任何 DB 操作报错

先区分连接失败、配置未加载与 JVM/依赖异常，不能只凭验证码不显示认定数据库故障。下面示例使用 Shell 安装的容器名；主 Compose 使用 `docker compose logs manager` 等服务名。

```bash
# 1) 三个应用是否 healthy
for c in jeepaymanager jeepaymerchant jeepaypayment; do
  echo -n "$c: "; docker inspect --format '{{.State.Health.Status}}' $c
done

# 2) manager 实际挂载路径（不要把 password 配置打印到公共排障日志）
docker inspect --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}' jeepaymanager

# 3) 从 manager 容器内验证 DB / Redis 连通
docker exec jeepaymanager getent hosts mysql redis
docker exec jeepaymanager sh -c "timeout 3 bash -c '</dev/tcp/mysql/3306' && echo MySQL_OK"
docker exec jeepaymanager sh -c "timeout 3 bash -c '</dev/tcp/redis/6379' && echo Redis_OK"
```

历史坑（V3.2.7 之前的老部署可能遇到）：
- `application.yml` 里 `host: redis` / `mysql`，但容器名是 `redis6` / `mysql8` → 新版 `install.sh` 已给容器加 `--network-alias`。
- `password: rootroot`（Compose 默认）与 `mysql_pwd=jeepaydb123456` 不一致 → 新版 `install.sh` 会自动 `sed` 替换。

`Could not create connection` / `Access denied` 应核对完整异常链、数据库是否存在、账号权限和配置加载目录。容器内用服务名 `mysql`，本机直接启动 Java 则需改为该机器可达的地址；旧的 `com.mysql.jdbc.Driver` 写法也不能解释所有连接失败，现代 Connector/J 对应 `com.mysql.cj.jdbc.Driver`，项目运行配置可让驱动自动发现。不要为了排障重建已有库。

旧 Issue #51 的 v2.2.0 / Java 8 日志中，Netty 初始化失败的根异常是 `availableProcessors : 0 (expected: > 0)`，不等于 MySQL 或 Redis 断连。当前 Dockerfile 使用 JRE 17；若旧镜像仍有此错误，应记录实际 Java、镜像和 CPU 限制，升级到配套版本后验证，不能给数据库改密码来修复 CPU 探测异常。

## 商户支付测试报 `Connection refused`

先查看 payment 启动日志并等待后再次确认它仍在运行；容器 `Up` 或脚本曾输出启动成功不等于应用已就绪。然后从**发起请求的商户后端**核对目标地址和端口。系统配置 `paySiteUrl` 默认 `http://127.0.0.1:9216`，在分容器部署中会连接商户容器自身；应按实际拓扑设置为所有调用者可达的公网支付根地址，且不带结尾 `/`。详细步骤见 [渠道接入](../integrate/payment-channels.md)。

## 默认 RocketMQ 却仍看到 ActiveMQ 重连

当前四份 `conf/` 模板已注释 `spring.activemq`，业务 MQ 由 `isys.mq.vender` 选择。旧安装应检查**实际挂载文件与实际镜像版本**，不要只查看新下载的模板。若确实选择 `activeMQ`，`tcp://127.0.0.1:61616` 在 Java 容器中不指向另一个 ActiveMQ 容器；应使用同网络内真实服务名并同步 MQ 配置和打包依赖。

阿里云 RocketMQ 当前按 `groupIdPrefix + 系统名 + _QUEUE/_BROADCAST` 分组，隔离不同系统和消息模式。使用该方案时须在云侧创建匹配的 Group/Topic，同一个消费者组的所有实例保持一致订阅；不要让不同系统共用同一个固定 Group ID。

## 业务触发 RocketMQ 时报 `connect to x.x.x.x:10911 failed`

两个可能原因：
1. **broker.conf 里 `brokerIP1` 是宿主的某个 Docker bridge 地址**（老版本用 `hostname -I` 挑错了）：
   ```bash
   rootDir=$(docker inspect mysql8 --format '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Source}}{{end}}{{end}}' | sed 's|/mysql/data$||')
   sed -i 's/^brokerIP1=.*/brokerIP1=rocketmq-broker/' $rootDir/rocketmq/broker/conf/broker.conf
   docker restart rocketmq-broker
   ```
2. **broker 启动好了但 Spring Boot RocketMQ producer 缓存了坏连接**——重启三个业务应用让客户端重新从 nameserver 拉路由：
   ```bash
   docker restart jeepaymanager jeepaymerchant jeepaypayment
   ```

## RocketMQ Broker 启动失败 NullPointerException

Broker 日志出现 `ScheduleMessageService.configFilePath` 相关 NPE，通常是 volume 权限问题。RocketMQ 5.x 镜像以 `rocketmq`（uid=3000）用户运行，而 Docker volume 默认 root 权限：

```bash
docker run --rm -u root -v jeepay_rocketmq_broker_store:/data alpine chown -R 3000:3000 /data
docker run --rm -u root -v jeepay_rocketmq_broker_logs:/data alpine chown -R 3000:3000 /data
```

Compose 已通过 `user: "0:0"` 规避；Shell 部署命中需手工修。

## WebSocket 握手 101 但收不到消息

商户端支付测试页 `ws://.../api/anon/ws/payOrder/...` 订阅订单状态，握手显示 `101 Switching Protocols` 但后续没数据。外层 nginx 必须配：

```nginx
proxy_http_version 1.1;                # 必须，HTTP/1.0 下 Connection: close 隐式生效
proxy_set_header Upgrade   $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_read_timeout  3600s;             # 默认 60s，长连接必断
proxy_send_timeout  3600s;
```

内层 jeepay 内置 nginx 已从 V3.2.7 起全量补齐，外层反代若自己维护需同步修。

## `application.yml` 变成目录而不是文件

Spring Boot 容器起来但拿不到配置。原因：某次 `cp` 失败后 `docker run -v <path>:<container>` 把 host 不存在的 path 自动建成目录。先通过 `docker inspect` 的 Mounts 确认实际宿主路径，停止受影响应用，备份该目录，补齐正确的 `application.yml` 文件后重建应用容器；保留数据库与 uploads。不要通过卸载全栈或删除整份配置目录处理单个挂载错误。

V3.2.8+ 的 `install.sh` 每个 `cp` 都加了返回值 + 文件类型校验，失败会 `exit 1` 明确指引，不再留半成品。

## Apple Silicon（M1/M2/M3）

RocketMQ 官方镜像只发布 `linux/amd64`，在 Apple Silicon 上通过 Rosetta 2 模拟运行，启动略慢属正常。Docker Desktop → Settings → 开启 **Use Rosetta for x86_64/amd64 emulation**。

## 镜像拉取失败（403 / 超时）

默认 SWR 镜像偶尔有地区限制，先确认：

```bash
docker pull swr.cn-south-1.myhuaweicloud.com/jeepay/mysql:8.0.25
```

如持续失败，可临时回 Docker Hub 上游（安装前 export）：

```bash
export mysqlImage=mysql:8.0.25
export redisImage=redis:6.2.14
export rocketmqImage=apache/rocketmq:5.3.1
export nginxImage=nginx:1.18.0
export managerImage=jeepay/jeepay-manager:3.2.0
export merchantImage=jeepay/jeepay-merchant:3.2.0
export paymentImage=jeepay/jeepay-payment:3.2.0
bash install.sh
```

## 前端镜像构建失败（Docker Compose 场景）

- `jeepay-ui` 目录是否存在且在 `jeepay` 同级？
- `UI_BASE_DIR` 是否正确？
- npm 源是否可达？必要时在 `jeepay-ui` 的 Dockerfile 里切淘宝源。

## 打包排障现场给维护者

```bash
ts=$(date +%Y%m%d-%H%M%S)
out=/tmp/jeepay-debug-$ts
mkdir -p $out
docker ps -a > $out/docker-ps.txt
for c in mysql8 redis6 rocketmq-namesrv rocketmq-broker jeepaymanager jeepaymerchant jeepaypayment nginx118; do
    docker logs --tail 200 $c > $out/log-$c.txt 2>&1
done
docker network inspect jeepay-net > $out/network.json 2>&1
cp $(ls -t /tmp/jeepay-install-*.log | head -1) $out/install.log 2>/dev/null
tar -czf $out.tar.gz -C /tmp "$(basename $out)"
echo "已打包到：$out.tar.gz"
```

日志和安装日志仍可能含商户信息、token 或历史敏感内容。分享前逐项去敏，公共 Issue 只保留与错误相关的片段；不要附带实际 `application.yml`、完整 `docker inspect` 或未经检查的压缩包。尚无经确认的私密漏洞报告入口时，遵守 [贡献指南的安全问题说明](../../CONTRIBUTING.md#九安全问题)。
