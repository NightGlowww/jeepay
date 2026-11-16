# 微信、支付宝与 PayPal 渠道接入

本页说明本仓库已有实现对应的配置路径和验收顺序，不代替渠道开户、产品签约与官方协议。配置字段以 `jeepay-core/.../model/params/` 与当前数据库接口定义为准；同一份配置必须匹配部署中的后端和 `jeepay-ui`。本文未进行真实支付联调。

## 公共步骤

1. 先完成部署与登录，保证 payment、数据库、Redis、MQ 正常；不要在 payment 启动失败时继续调渠道参数。
2. 在运营平台建立商户及商户应用，启用商户、应用、支付接口和目标支付方式，设置费率。保存平台的 `mchNo`、商户应用 `appId` 和该应用的签名密钥。平台应用 ID 与渠道 appId 是两个概念。
3. 在系统应用配置设置 `paySiteUrl` 为公网支付网关根地址，例如 `https://pay.example.com`，不带结尾 `/`。默认 `http://127.0.0.1:9216` 仅适合同机开发；容器里的 loopback 地址不能代表其他容器。
4. 普通商户在应用的渠道配置中填写自身参数；服务商模式先设置服务商渠道参数，再为其特约商户应用填写子商户参数。不要把两种模式的证书或商户号混用。
5. 先用商户端支付测试确认单一支付方式，再让自己的服务端调用 `/api/pay/unifiedOrder`。公共字段包括 `mchNo`、`appId`、`mchOrderNo`、`wayCode`、`amount`、`currency`、`subject`、`body` 和公共签名参数。金额是整数最小货币单位（人民币为分）；签名由与后端兼容的 SDK/协议生成，不把私钥或签名密钥交给浏览器。

下单请求的 `notifyUrl` 是 **Jeepay 通知商户业务系统** 的地址，`returnUrl` 是支付后回到商户页面的地址；它们不是渠道回调 Jeepay 的地址。渠道回调由 `paySiteUrl` 生成：

```text
/api/pay/notify/{ifCode}[/支付订单号]
/api/pay/return/{ifCode}[/支付订单号]
```

商户系统必须验签、校验商户订单号及金额并幂等处理通知；浏览器回跳不能单独作为发货依据。异步通知未到时可通过 `/api/pay/query` 查询并按实际渠道状态核对。

## 微信支付（`ifCode=wxpay`）

代码包含 `WX_NATIVE`、`WX_H5`、`WX_JSAPI`、`WX_LITE`、`WX_APP`、`WX_BAR`。能否使用取决于商户已开通的产品及相应 AppID 绑定，不能只在平台勾选支付方式。

| 参数 | 配置含义 |
|---|---|
| `mchId`、`appId` | 微信支付商户号和对应公众号/小程序/应用 AppID |
| `appSecret`、`oauth2Url` | 需要获取渠道用户身份时使用；`oauth2Url` 留空使用内置默认逻辑 |
| `apiVersion` | `V2` 或 `V3`，必须与本次证书/密钥配置匹配 |
| `key`、`cert` | API v2 密钥、`apiclient_cert.p12`；按支付/退款等产品要求配置 |
| `apiV3Key`、`serialNo`、`apiClientCert`、`apiClientKey` | API v3 密钥、商户证书序列号、`apiclient_cert.pem`、`apiclient_key.pem` |
| `subMchId`、`subMchAppId` | 服务商模式下的特约商户参数，放在子商户应用配置中 |

建议先用 `WX_NATIVE` 验证二维码下单、支付通知与主动查询。`WX_JSAPI` / `WX_LITE` 还要求与渠道 AppID 匹配的 `openid`，通过 `channelExtra` 传递；`WX_BAR` 使用用户出示付款码的 `authCode`，不是生成支付二维码。H5、JSAPI、小程序和 APP 还需在微信侧配置各产品要求的域名、目录、应用绑定或授权，参照 [微信支付商户文档](https://pay.weixin.qq.com/doc/v3/merchant/) 当前说明。

## 支付宝（`ifCode=alipay`）

数据库初始接口定义包含 `ALI_PC`、`ALI_WAP`、`ALI_APP`、`ALI_QR`、`ALI_JSAPI`、`ALI_BAR`。普通商户填写应用自身的参数；服务商填写服务商应用参数，并通过授权流程取得子商户 `appAuthToken`，不是填写另一个商户的私钥。

| 参数 | 配置含义 |
|---|---|
| `sandbox` | `1` 为沙箱、`0` 为生产；同一套 AppID、密钥和授权必须属于同一环境 |
| `appId`、`privateKey` | 支付宝开放平台应用 ID、应用私钥 |
| `signType` | 推荐使用当前实现支持的 `RSA2` |
| `useCert=0` | 公钥方式，填写 `alipayPublicKey`（支付宝公钥） |
| `useCert=1` | 证书方式，上传应用公钥证书 `appPublicCert`、支付宝公钥证书 `alipayPublicCert`、支付宝根证书 `alipayRootCert`，并配套 RSA2 私钥 |
| `appAuthToken` | 服务商子商户授权结果；不是普通商户模式必填参数 |

先选已签约的 `ALI_QR` 或 `ALI_PC` 验证下单与通知；条码支付 `ALI_BAR` 的 `authCode` 属于付款码，不能用静态商户收款码替代。沙箱和产品签约见 [支付宝开放平台文档](https://open.alipay.com/docs)。切换环境后重新核对应用、证书、授权 token 和域名。

## PayPal（`ifCode=pppay`）

本仓库接口定义仅声明**普通商户模式**及 `PP_PC`。不要据此宣称支持 PayPal 服务商模式、订阅、自动续费或所有币种精度。

| 参数 | 配置含义 |
|---|---|
| `sandbox` | `1` 为 Sandbox，`0` 为 Live |
| `clientId`、`secret` | 同一环境的 REST App 凭据 |
| `notifyWebhook` | 支付通知 Webhook ID |
| `refundWebhook` | 退款通知 Webhook ID |

在 [PayPal 开发者控制台](https://developer.paypal.com/dashboard/) 建立测试应用，使用 Sandbox 商户/买家账户，分别配置回调 URL 与 Webhook ID：

```text
支付：https://pay.example.com/api/pay/notify/pppay
退款：https://pay.example.com/api/refund/notify/pppay
```

代码的支付通知解析器读取 `resource.purchase_units[0].invoice_id` 和 `resource.id`，对应订单形状；它不能被当成适配所有 Capture/Webhook 事件的通用解析器。Webhook 事件类型、验签以及订单/退款状态变化须按部署版本和 [PayPal Webhooks 官方文档](https://developer.paypal.com/api/rest/webhooks/) 单独联调。填写 ID 本身不证明验签或事件闭环已经完成，不要通过订阅所有事件绕过数据格式差异。

使用 `PP_PC` 下单后跳到返回的 PayPal approval URL。当前代码包含后续 Capture（扣款捕获）、查询和退款逻辑；验收必须覆盖批准后实际完成 Capture、回跳中断后仍能核对状态，以及退款通知与主动查询。当前金额转换采用两位小数，首次接入请使用该规则适用且账户已支持的币种，其他币种须另行确认精度；不要将人民币示例原样换一个币种就上线。

## 验收与排错

每个渠道分别记录：后端/UI commit、环境、商户模式、支付方式、去敏订单号，以及下单、付款、渠道通知、商户通知、主动查询、退款的实际结果。只配置成功或返回支付链接不等于支付闭环通过。

- `Connection refused`：先确认 payment 服务持续存活，再从发起请求的容器核对 `paySiteUrl` 与路由。
- 下单成功但状态不变：分别看渠道到 Jeepay 的通知、MQ 和 Jeepay 到业务系统的通知，不要用页面回跳代替它们。
- 签名/证书失败：核对沙箱/生产、普通商户/服务商、AppID/商户号与密钥归属；不要在公开日志中打印完整密钥、token 或证书私钥。

源码依据：`docs/sql/init.sql` 的接口定义，`UnifiedOrderRQ` 的支付方式分派，`AbstractPaymentService` 的回调地址，`channel/wxpay/`、`channel/alipay/`、`channel/pppay/` 和 `PaypalWrapper`。后续代码变化时需同步核对本文。
