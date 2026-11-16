# StarPOS 二维码通道

本实现对应星驿支付 `/yyfsevr/order/getCodeUrl`、`/yyfsevr/order/orderQuery` 与交易通知。仅支持普通商户的 `STARPOS_QR`，请求 `channelExtra` 使用 `{"payDataType":"payUrl"}`。EPAY 不属于这次 StarPOS 交付；原 PR 的 EPAY V1/V2 目标协议和 SDK 版本未明确，需单独评审。

## 协议与状态

- HTTP 限定固定 HTTPS 主机和 JVM 默认 TLS 验证，最多读取 1 MiB；整体请求 deadline 覆盖响应头及完整 body。超时或中断取消响应订阅和请求，不使用额外的阻塞 reader 线程。
- 请求按 ASCII key 顺序拼接 `key=value&...`，排除 null 与 sign，保留空字符串；JSON 值按原始顺序序列化。参数串做 SHA-256 小写 hex，再使用渠道公钥 RSA/PKCS1 加密。`timeStamp` 使用上海时区 `yyyyMMddHHmmss`，`txamt` 为字符串分值。
- 取码成功表示等待用户付款，返回收银链接并进入 WAITING；通过后续查单或已验签通知确认支付结果。
- 查单由固定 HTTPS 主机返回；官方响应 schema 不要求 sign，若响应携带 sign 则必须验证。成功结果绑定三方订单号、机构号与金额；无业务数据的明确失败 code/msg envelope 按失败处理，含业务数据或绑定字段仍交叉校验；未找到、两个未知码、空值、矛盾或配置异常继续保留可查单状态。
- 入站异步通知始终验证渠道签名，并交叉核对三方订单、机构、收款商户及金额。普通商户应用凭据不具备修改支付状态的权限。

## 环境与收银链接

| 环境 | API 主机 | 允许的网页主机 |
| --- | --- | --- |
| test | xyf-server-test.postar.cn | yyfwebcs.postar.cn |
| uat | xyzscxm.postar.cn | deccapayuat.postar.cn |
| prod | yyfsvxm.postar.cn | deccacloud.postar.cn |

收银链接只允许 HTTPS、上表精确网页主机和 `/postalpay/p/generateCode/standard` 路径；不能包含用户信息、非默认端口或 fragment。query 必须包含唯一的 `threeOrderNo`、`agetId` 与有效的 `orderDate=yyyyMMdd`。前两项绑定本次请求的订单和机构；不使用任意 HTTPS 主机或 `*.postar.cn` 后缀放行。返回其他链接时保留可查单状态，不认定支付失败。

取码页面直接给出了 test 收银链接；uat/prod 网页域名来自同一官方文档中心的网页环境映射。后两者尚未通过真实商户通道联调。现有本地证据只覆盖协议解析、固定域名约束、合成渠道响应与状态流转。

## 数据库

新库使用 `docs/sql/init.sql`。现有库只执行 `docs/sql/patch.sql` 中 `20260726 => STARPOS` 段的两条注册 INSERT，不重跑历史补丁。

StarPOS 仅注册 `STARPOS_QR` 和 `starpos`。注册使用不存在才插入及重复键无操作，保留现有渠道配置；不改变业务表、字段类型、默认值、商户记录或凭据。支付订单的 `channel_extra VARCHAR(512) DEFAULT NULL`、`notify_url VARCHAR(128) NOT NULL DEFAULT ''`、`return_url VARCHAR(128) DEFAULT ''`、`ext_param VARCHAR(2048) DEFAULT NULL` 保持基线。

在受控 DEV 的独立临时 schema，待维护者执行的范围仅为：创建基线 `t_pay_way` 与 `t_pay_interface_define` 两张空表，执行上述注册两次，核对每项唯一；修改临时种子的说明再执行注册，确认不覆盖已有值。此次未连接服务器或启动本地数据库。

## 验证与来源

使用 JDK17：

```sh
mvn -B -pl jeepay-payment -am '-Dtest=Starpos*' -Dsurefire.failIfNoSpecifiedTests=false test
```

StarPOS 测试通过本地合成 HTTP fixture 工作，不访问真实渠道。官方取码链接结构、签名参数串、待支付状态、查单订单/金额绑定、通知验签与商户绑定都有定向覆盖；这些证据不包含真实支付、退款或清算验收。

- [安全规范与请求签名](https://www.postar.cn/xyf/doc/7306848m0)
- [取码接口与收银 URL 示例](https://www.postar.cn/xyf/doc/341551818e0)
- [订单查询与响应 schema](https://www.postar.cn/xyf/doc/341551811e0)
- [交易通知与验签](https://www.postar.cn/xyf/doc/341551808e0)
- [官方网页环境映射](https://xingyifu.apifox.cn/411284327e0)
