# 2026-11-16 fork 升级说明

本轮以 JDK 17、Spring Boot 3.3.7 和 MySQL 8 为整合基线，不创建新的官方版本号或镜像标签。部署必须固定本 fork 的已验证 commit 和配套前端 commit，自行构建制品；上游现有同名版本、公共镜像和前序亿科豆体验环境不包含本轮全部修复。

## 配套与顺序

1. 备份数据库、实际部署配置、上传的证书/公钥文件和当前前后端制品，记录原 commit。凭据不提交到 git。
2. 新库仅执行 `docs/sql/init.sql`；Compose 已移除重放历史 `patch.sql` 的挂载。存量库禁止重新运行初始化或整份历史补丁。
3. 存量库执行 `docs/sql/upgrade/20261116-channel-params.sql`。它只追加缺少的支付宝/微信参数定义，不修改商户实际配置。末尾查询应为空；有结果则按提示处理无效、非数组或超长定义，不能当作迁移全部成功。
4. 如启用 StarPOS，存量库另执行 `docs/sql/patch.sql` 中 `20260726 => STARPOS` 段的两条注册语句。它们可重复执行，不覆盖已有同名种子。无需支付表扩容，EPAY 不在本次实现范围。具体配置与能力边界见 [StarPOS 说明](../deploy/starpos.md)。
5. 先核对下面的配置变化，再部署同批后端和 [配套前端](https://github.com/NightGlowww/jeepay-ui)。在隔离环境验证登录权限、应用/渠道配置、订单及通知后再决定正式渠道启用。

## 行为与配置变化

- 支付宝：三处普通商户/服务商配置页增加 `userIdType`，`USER_ID` 是缺省旧模式；新版 OpenID 应用选择 `OPEN_ID`，OAuth 与 JSAPI 下单共同使用此设置。收银台 `buyerUserId` 字符串协议不变。不要把旧 `user_id` 值作为新模式的 `open_id`。
- 微信：参数定义补齐 `wxpayPublicKeyId` 和 `wxpayPublicKey`；公钥模式上传官方匹配的 ID 和 `.pem`，旧证书模式仍保留。共享子商户配置不再被单次 OAuth 调用修改。
- 授权：两应用的方法权限现在实际生效；商户配置、应用及操作员操作受租户/系统归属约束。原先越权可访问的接口会拒绝，正常角色应配置实际需要的权限。
- 签名与金额：UTF-8 完整编码参数值；重复标量保留末值；hash 路由 query 位置保持；元转分使用精确十进制及 `HALF_UP`。调用者不要自行重复 URL 编码或先经 `double` 构造金额。
- 日志：本地签名工具不再打印签名原文，三个应用精确 SDK 签名 logger 使用 INFO，保留 WARN/ERROR。应随制品更新实际加载的 `logback-spring.xml`；外部主动覆盖 logger 为 DEBUG/TRACE 不属于此配置保证。

## 内网通知与 Xxpay

商户可控的支付/退款/转账通知，以及 Xxpay 的四类出站请求默认只允许公网 HTTP(S)。每次请求固定经过校验的 DNS 快照，禁止自动重定向。合法内网端点由部署配置显式授权，两个列表互不授权：

```yaml
isys:
  outbound:
    notification-trusted-origins: []
    xxpay-trusted-origins: []
```

列表元素必须是精确 origin（协议、主机和端口，例如 `http://notify.internal:8080`），不含路径/query。它授权该 origin 的所有路径，不能由商户参数填充。固定运营 paytest 通知只允许配置的完整回调路径。

新的出站 transport 直连，不继承 JVM HTTP proxy；需要代理出站的部署应先核对网络路径。通知 URL 不接受 fragment。运营维护的 `paySiteUrl` 仍允许合法内网支付服务，未用商户 URL 的规则禁用它。

## 验证与回退

代码回归、CI 与 DEV 独立临时 MySQL 的实际执行范围见 [整合记录](20261116-upstream-reconciliation.md)。本轮未访问真实微信、PayPal 或 StarPOS 商户，没有正式实付/退款/清算验收，也未替换当前 DEV 支付体验服务。

如需回退，恢复前后端匹配的原制品和实际配置。新增参数定义是追加项，旧配置缺省行为保留；若要恢复数据库原貌，使用升级前备份，不删除实际商户参数来模拟回滚。依赖升级仍保留 ONS 内嵌 Commons Lang 3.5 的限制，详见处置清单 #122。
