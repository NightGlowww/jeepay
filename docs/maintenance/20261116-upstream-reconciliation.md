# 2026-11-16 上游开放事项整合

本记录覆盖 `jeequan/jeepay` 在 2026-11-16 的 47 个开放 issue、17 个开放 PR。基线为 `upstream/dev` `3395690d9fbcc46a53a40140689987b56944666a`。这里记录的是本 fork 的代码和处置结论；未修改、关闭或替上游批准任何原 issue/PR。重复方案不重复合并，功能建议和缺少复现的信息不计为已修复。

后端：[NightGlowww/jeepay](https://github.com/NightGlowww/jeepay)。配套前端：[NightGlowww/jeepay-ui](https://github.com/NightGlowww/jeepay-ui)。保留 LGPL、原作者和上游提交来源。此次不创建正式 tag/Release，不部署或替换现有亿科豆支付服务。

## 上游 PR（17/17）

| PR | 处置 | 结果 |
| --- | --- | --- |
| [#161](https://github.com/jeequan/jeepay/pull/161) | incorporated | 移除本地签名诊断，并补齐两版真实 SDK 在 DEBUG 下的同类密钥日志；三个应用保留安全 WARN。 |
| [#160](https://github.com/jeequan/jeepay/pull/160) | incorporated | 完整编码参数值，保留 hash 路由参数位置。 |
| [#159](https://github.com/jeequan/jeepay/pull/159) | incorporated | 畸形括号参数名称作为普通 key 处理。 |
| [#158](https://github.com/jeequan/jeepay/pull/158) | incorporated | 基于 dev 启用两端方法授权并验证真实 HTTP 权限。 |
| [#157](https://github.com/jeequan/jeepay/pull/157) | superseded-by-160 | 采用 #160；不移动既有 fragment 路由参数位置。 |
| [#155](https://github.com/jeequan/jeepay/pull/155) | incorporated | 空数组安全处理，保持重复标量末值契约。 |
| [#154](https://github.com/jeequan/jeepay/pull/154) | superseded-by-149 | 保留精确十进制和 HALF_UP，不采用 double/HALF_EVEN 替代方案。 |
| [#153](https://github.com/jeequan/jeepay/pull/153) | incorporated | core 编码与 #148 同源，payment 编码/签名回归也已纳入渠道分支，无重复 cherry-pick。 |
| [#152](https://github.com/jeequan/jeepay/pull/152) | incorporated | 同步跳转真实 MVC 验证。 |
| [#151](https://github.com/jeequan/jeepay/pull/151) | incorporated | 三个入参元转分入口 HALF_UP，避免向零截断。 |
| [#150](https://github.com/jeequan/jeepay/pull/150) | superseded-by-155 | 不将所有重复标量逗号拼接；采用保留现有契约的 #155。 |
| [#149](https://github.com/jeequan/jeepay/pull/149) | incorporated | BigDecimal 精确金额换算与 Locale 独立性，修正大额期望值为字面量。 |
| [#148](https://github.com/jeequan/jeepay/pull/148) | incorporated | 统一 UTF-8 完整编码渠道参数值。 |
| [#146](https://github.com/jeequan/jeepay/pull/146) | incorporated | 角色列表排序字段实际拼接边界白名单。 |
| [#145](https://github.com/jeequan/jeepay/pull/145) | incorporated | 显式 redirect 视图替代空视图返回。 |
| [#138](https://github.com/jeequan/jeepay/pull/138) | already-fixed-in-dev / boundary-completed | dev 已有当前用户改密/应用读取归属修复；不重放旧实现，本轮进一步补齐相关写入边界。 |
| [#117](https://github.com/jeequan/jeepay/pull/117) | split-StarPOS / EPAY-deferred | 仅合入 StarPOS 普通商户 QR 部分，按官方协议修正签名、状态与身份绑定。用户明确暂不接 EPAY，缺少具体提供方/版本，保留待办。 |

## 上游 issue（47/47）

| Issue | 处置 | 结果与限制 |
| --- | --- | --- |
| [#147](https://github.com/jeequan/jeepay/issues/147) 🔔 有 2 个 fork PR 的 CI 等待批准 | requires-upstream-maintainer-permission | 自动维护的上游fork PR CI审批提醒，快照正文/评论列出9项等待批准。放行上游action_required运行需要上游维护者权限；本fork已启用CI由主Agent确认，但fork CI不能替代上游Approve and run，也不能据此关闭上游提醒。 |
| [#144](https://github.com/jeequan/jeepay/issues/144) Security vulnerability in jeequan/jeepay: please review the report and fix | fixed-confirmed-path | 已修复源码确认的跨商户操作员删除路径并做正反回归。原外链报告不可读取，未声称已核验外链全部内容。 |
| [#127](https://github.com/jeequan/jeepay/issues/127) [Vulnerability] Cross-Merchant Payment Channel Information Disclosure | already-safe / fixed-write-path | dev 已限制配置读取归属；本次进一步固定配置写入应用归属、数据库主键与租户边界。 |
| [#126](https://github.com/jeequan/jeepay/issues/126) [Vulnerability] Unauthorized Merchants Data Access | already-safe / fixed-write-path | dev 已限制应用读取；本次补齐应用/操作员更新不可转移租户及所属系统的边界。 |
| [#125](https://github.com/jeequan/jeepay/issues/125) 支付同步跳转完成后出现 NoResourceFoundException（response committed already） | fixed | 同步跳转返回显式 redirect 视图，包含 ONLYJUMP；PR #145/#152 真实 MVC 回归。 |
| [#124](https://github.com/jeequan/jeepay/issues/124) 希望增加 1panel | fixed | 提供 docs/deploy/1panel.md 通用源码 Compose 导入说明，覆盖工作目录、版本固定、专用数据库账户/必填密码、端口收口、OpenResty网络、paySiteUrl、数据备份/升级回退。未制作或宣称官方应用商店包、未操作真实1Panel。 |
| [#123](https://github.com/jeequan/jeepay/issues/123) [Bug] 新版支付宝应用(OpenID机制)下聚合码/JSAPI支付报错ACQ.BUYER_NOT_EXIST | fixed-with-configuration | 支付宝 OAuth 与 JSAPI 增加 USER_ID/OPEN_ID 显式模式，旧配置缺省 USER_ID；同步 SQL 定义、升级脚本及三处前端控件。新应用需正确选 OPEN_ID；未代替真实渠道联调。 |
| [#122](https://github.com/jeequan/jeepay/issues/122) Potential Vulnerabilities Due To Hidden Dependencies | partially-fixed | Tomcat 10.1.62、gRPC BOM 1.82.4、ONS 1.9.6.Final 已验证兼容；ONS 内嵌 Commons Lang 3.5 仍存在，默认应用不打包 provided ONS。未证明所有报告依赖均已消失或可利用。 |
| [#121](https://github.com/jeequan/jeepay/issues/121) SSRF in Jeepay Merchant Pay-Test `paySiteUrl` | no-change | paySiteUrl 是运营权限维护的部署配置，合法场景包含内网支付服务；不能把可信配置按商户可控 URL 禁用。方法权限由本轮授权修复启用。 |
| [#120](https://github.com/jeequan/jeepay/issues/120) SSRF in Jeepay Xxpay Merchant `payUrl` | fixed | Xxpay 下单、查询、退款与退款查询共用出站边界，固定校验后的 DNS、禁止重定向与默认私网访问；内网实例由部署者精确 origin 授权。 |
| [#119](https://github.com/jeequan/jeepay/issues/119) SSRF in Jeepay Transfer Notification `notifyUrl` | fixed | 支付/退款/转账通知及重试共用出站边界；每次解析验证，实际连接使用同份 DNS 快照。运营 paytest 只授权固定回调路径。 |
| [#118](https://github.com/jeequan/jeepay/issues/118) SQL Injection in Jeepay Manager Role-List `sortField` | fixed | 角色排序白名单在 getOrderStr 实际 SQL 拼接边界执行，不将客户端 sortField 直接拼接。 |
| [#114](https://github.com/jeequan/jeepay/issues/114) 建议完善一下文档 | fixed | 补 docs/integrate/payment-channels.md：按实际模型/初始接口定义说明微信/支付宝/PayPal普通与服务商范围、字段、回调角色、测试顺序；明确PayPal仅PP_PC普通商户，Webhook格式/验签、Capture、币种精度和支付闭环需单独联调，不宣称未实现功能。 |
| [#113](https://github.com/jeequan/jeepay/issues/113) v3.2.9存在权限控制缺陷可导致垂直越权访问 | fixed | 两应用启用方法级授权，带真实过滤器/方法代理 HTTP 正反回归。 |
| [#105](https://github.com/jeequan/jeepay/issues/105) MQ 配置问题 | already-fixed | conf/payment、manager、merchant、devCommons/config 四份模板的 spring.activemq 已注释，isys.mq.vender=rocketMQ；无需重复修改。 |
| [#78](https://github.com/jeequan/jeepay/issues/78) Spring Security 配置项没有启用方法级别的安全性 | fixed | 与 #113 同根因；两应用 @EnableMethodSecurity 生效，管理员/普通权限场景保持正常。 |
| [#75](https://github.com/jeequan/jeepay/issues/75) 定义”根据接口配置定义描述渲染页面"，在商户端无法编辑参数，显示"该渠道无法进行商户参数配置" | already-fixed-in-ui-dev | 商户前端 dev 已使用后端统一 record.mchParams，实际组件回归通过；此次将配套 UI dev 修复合入 fork 默认分支。 |
| [#74](https://github.com/jeequan/jeepay/issues/74) 2025-03-07 00:08:28.955 WARN  [ActiveMQ Task-1] [o.a.a.t.f.FailoverTransport] - Failed to connect to [tcp://127.0.0.1:61616?wireFormat.maxInactivityDuration=0] after: 60 attempt(s) with Connection refused, continuing to retry. | config-question | 旧 Shell 安装的 ActiveMQ 连接指向 loopback；缺少当时真实挂载与运行配置，不能确认为当前业务 bug。补文档说明容器服务名、所选 MQ 和实际镜像/挂载需一致。 |
| [#73](https://github.com/jeequan/jeepay/issues/73) 无可用的平台证书 | verified-existing-public-key-mode | dev 已有微信平台公钥模型与三处 UI 控件；本次补齐初始化/升级定义，并新增真实 RSA 验签、AES-GCM 解密、篡改拒绝及旧证书回归。真实账号仍需上传匹配公钥与 ID。 |
| [#72](https://github.com/jeequan/jeepay/issues/72) 有可用的支付方式吗 | answered-existing-implementation | 源码已有微信、支付宝、云闪付、PayPal、xxpay、plspay适配器；此条是宽泛可用性询问，没有失败步骤。可说明现有实现及配置入口，不能把存在源码等同于当前账号/渠道已完成实付验收。 |
| [#71](https://github.com/jeequan/jeepay/issues/71) 配置了镜像加速器地址, 有些模块一直拉不下来 | config-question | Docker Hub 拉取超时属于镜像仓库连通性。当前安装与 Compose 默认已使用 SWR且允许覆盖；未验证任何第三方加速器，不宣称网络问题已修复。 |
| [#70](https://github.com/jeequan/jeepay/issues/70) 请问一下 docs/Dockerfile 在哪去了？试着操作了下安装，根本没有这个东西 | fixed | 旧 docker/build-docker-starter.sh backend deps 仍访问已删除 docs/Dockerfile，且其后端使用不存在的根 Dockerfile。改为三个模块独立构建上下文/Dockerfile，先检查 JAR；name 模式不再创建或删除 Docker builder。离线 mock 原版失败、修复后通过。 |
| [#67](https://github.com/jeequan/jeepay/issues/67) BigDecimal计算费率存在问题 | not-reproduced-for-reported-method | 原截图标出new BigDecimal(amount)，amount实际为Long，Java选BigDecimal(long)构造器，没有double中转。现有calPercentageFee是精确十进制乘法再显式HALF_UP；未给出失败输入。此前新增行为测试证实9007199254740993L保持精确，正负半分按HALF_UP。#149修的是另一个convertDollar2Cent方法，不能据此声称已复现修好本issue。 |
| [#66](https://github.com/jeequan/jeepay/issues/66) 有计划加入订阅功能吗 | feature-needs-contract | 原文只询问订阅计划；没有可执行的业务范围。现有源码命中的订阅主要是MQ/订单状态与支付宝消息订阅，未发现周期扣款/订阅计划领域实现，不能将消息订阅当作该功能完成。 |
| [#58](https://github.com/jeequan/jeepay/issues/58) 。 | needs-reproduction-information | 正文为空、无评论；标题没有可判定的业务问题，不能构造修复目标。 |
| [#57](https://github.com/jeequan/jeepay/issues/57) 支持小微商户么 | needs-channel-and-eligibility-contract | 已有普通商户与特约商户模式、微信subMchId下单路径；这不能证明任意小微主体可开户、签约或使用所有支付产品。未发现小微进件流程，原文缺少目标渠道和能力范围。 |
| [#56](https://github.com/jeequan/jeepay/issues/56) 支持微企付吗 | feature-needs-contract | 标题明确询问微企付，正文为空；渠道常量和适配器目录没有微企付实现。新增渠道必须先指定产品及接口契约，不能伪报已经修复。 |
| [#55](https://github.com/jeequan/jeepay/issues/55) WeChat Pay 无效的openid | needs-runtime-reproduction | OAuth 错误不再静默，但原报告缺少实际应用/商户模式与脱敏授权错误；不能据此断言任意无效 openid 已修复。 |
| [#54](https://github.com/jeequan/jeepay/issues/54) 自动获取openid失效 | needs-runtime-reproduction | 增强 OAuth 空值与渠道错误处理；原报告缺少可复现的当前环境和 AppID/授权契约，仍需运行证据。 |
| [#53](https://github.com/jeequan/jeepay/issues/53) PayPal | already-implemented-not-channel-accepted | PayPal已有PP_PC、初始化SQL、真实SDK下单、查询/退款及通知实现；因此新增集成的需求在源码层已存在。本次没有真实PayPal联调或实付，不能标为渠道验收通过。 |
| [#51](https://github.com/jeequan/jeepay/issues/51) jeepay-manager 服务异常 | config-question | 完整旧日志的根异常是 Netty availableProcessors : 0 (expected: > 0)，运行于 v2.2.0/Java8，不是由验证码现象即可推断的数据库故障。当前 Dockerfile为JRE17，没有当前复现证据，未硬改业务/Netty。另独立修复 Compose 新库重放旧 patch.sql 的可确认缺陷。 |
| [#50](https://github.com/jeequan/jeepay/issues/50) Payment启动报错 | already-fixed | 当前 payment 使用 SwaggerConfig + OpenAPI/Springdoc，旧 SwaggerConfiguration/OpenApiExtensionResolver 构造依赖已不存在。 |
| [#49](https://github.com/jeequan/jeepay/issues/49) 渠道回调方法每次都设置自动获取微信平台证书，是有特别的场景考虑吗？ | fixed | 三类微信回调复用配置中的 verifier，不在每次回调重新注册自动证书验证器；保留 SDK 验签。 |
| [#48](https://github.com/jeequan/jeepay/issues/48) 微信支付通道存在线程安全问题 | fixed | 不再修改共享 WxPayConfig.subMchId，通过请求 URL 传递子商户号；并发两商户回归通过。 |
| [#47](https://github.com/jeequan/jeepay/issues/47) 可否对接易宝支付和联通支付？ | feature-needs-contract | 易宝支付与联通支付适配器未实现；owner于2023-05-12已回复可参考对接手册自行对接。当前属于渠道开发需求，不能通过已有云闪付或xxpay替代宣称完成。 |
| [#46](https://github.com/jeequan/jeepay/issues/46) 服务器部署时数据库连不上 | config-question | 当前 conf 使用 JDBC URL 自动发现驱动；原 issue 的 com.jc.mysql.jdbc.Driver 拼写不正确。缺乏具体连接异常/环境，补连接地址、账号/数据库及现代 driver 类名说明，不把建新库当修复。 |
| [#45](https://github.com/jeequan/jeepay/issues/45) O | needs-reproduction-information | 正文为空、无评论；标题没有可判定的业务问题，不能构造修复目标。 |
| [#42](https://github.com/jeequan/jeepay/issues/42) 有没有k8s helm安装支持计划 | feature | 仓库没有 Helm chart；维护者原回复暂无计划。本轮不虚构 Kubernetes/Helm 支持。 |
| [#40](https://github.com/jeequan/jeepay/issues/40) 升级 1.15.0 之后构建镜像出现问题 | already-fixed | 当前 jeepay-payment/pom.xml 已改用 com.jeequan:jeepay-sdk-java:pls-1.3.0，与维护者修复回复一致。未改依赖版本。 |
| [#39](https://github.com/jeequan/jeepay/issues/39) control层 接口提交的地方在哪里  没找到呢 | answered-source-location | 属于Controller入口定位问题。开放接口统一下单在payment模块POST /api/pay/unifiedOrder；商户支付测试在merchant模块POST /api/paytest/payOrders。若所指是支付接口定义配置，则manager模块POST /api/payIfDefines。无需虚构代码缺陷。 |
| [#35](https://github.com/jeequan/jeepay/issues/35) 阿里云rocketMQ相关问题 | already-fixed | AliYunRocketMQFactory.genGroupId 已按 groupIdPrefix + 系统名 + _QUEUE/_BROADCAST 隔离各系统及模式，避免旧固定 group 导致不同订阅竞争。补同组订阅一致及云端资源创建说明，未访问真实 MQ。 |
| [#34](https://github.com/jeequan/jeepay/issues/34) Add SECURITY.md | open | 2026-11-16 gh api repos/jeequan/jeepay/private-vulnerability-reporting 返回 enabled=false。现有 CONTRIBUTING §9 也说明无经确认私密入口；旧 issue 中研究者自行建文件并非维护者公布联系方式。本轮不新增虚构 SECURITY.md、不启用 PVR 权限。 |
| [#33](https://github.com/jeequan/jeepay/issues/33) 这个项目是凉了吗 | answered-not-a-code-defect | 项目维护状态询问；owner于2022-03-10已回复一直维护并指向Gitee。当前所审upstream/dev包含2026-10-01提交，可作为仓库快照活动证据；这不证明未来维护承诺或远端实时状态。 |
| [#32](https://github.com/jeequan/jeepay/issues/32) 请问可以增加docker部署方案吗 | already-fixed | 当前已有根 docker-compose.yml、三个模块 Dockerfile 和 docs/deploy/compose.md。 |
| [#30](https://github.com/jeequan/jeepay/issues/30) 请求Jeepay(http://127.0.0.1:9216/api/pay/unifiedOrder)异常,请检查网络或重试.异常信息:Connection refused (Connection refused) | config-question | payment 未就绪和容器内 127.0.0.1 地址均会导致 Connection refused；原评论包含 payment 启动后退出的确认。补持续存活、调用者视角和系统 paySiteUrl 配置步骤，不改订单逻辑。 |
| [#29](https://github.com/jeequan/jeepay/issues/29) 没有docker部署方法吗? | already-fixed | 同 #32，Docker Compose 部署实现与说明已存在。 |
| [#25](https://github.com/jeequan/jeepay/issues/25) Could not create connection to database server. Attempted reconnect 3 times. Giving up. | config-question | 原 issue 自述已找到原因且维护者指向环境配置；无当前代码复现。补数据库连接、配置加载与禁止重建已有库的排障说明。 |

## 仍需外部信息或后续产品决定

- EPAY：用户决定暂不接；需要具体提供方、API/SDK 与版本后另行开发。
- #66、#56、#47、#42：订阅、微企付、易宝/联通、Helm 属于未实现的功能需求，本轮没有伪造产品或协议。
- #54、#55、#67：缺少当前失败输入、版本和运行证据；现有专项已验证的范围如表所列。#58/#45 没有可行动正文。
- #122：ONS 内嵌旧 Commons Lang 仍为限制；不把兼容依赖升级等同于消除所有隐藏依赖问题。
- #34、#147：原仓库私密报告入口和 CI 批准取决于上游维护者；fork 无法代行原仓库权限。

## 验证范围

最终整合构建、CI 和 DEV 临时数据库结果见下方完成记录；离线 fixture 与单元回归不代表真实微信、PayPal、StarPOS 渠道的实付、退款或清算验收。前序亿科豆支付宝 0.01 元实付/退款属于另一个已部署版本，不作为此次 fork 的渠道验收证据。

- 后端整合源码 `46f9145`（StarPOS 候选合并全部已完成修复），JDK 17.0.21，`mvn -B -ntp clean package`：10 个 reactor 模块成功，240 项测试，0 failure / 0 error / 0 skip。
- 三个实际 fat JAR 均包含 Tomcat 10.1.62、gRPC 1.82.4；默认 fat JAR 不含 provided ONS。manager/merchant 使用 SDK 1.6.1，payment 保留 pls-1.3.0，未声称 SDK 版本已统一。
- 后端 [核心 #2](https://github.com/NightGlowww/jeepay/pull/2)、[部署 #3](https://github.com/NightGlowww/jeepay/pull/3)、[依赖 #4](https://github.com/NightGlowww/jeepay/pull/4)、[权限 #5](https://github.com/NightGlowww/jeepay/pull/5)、[出站 #6](https://github.com/NightGlowww/jeepay/pull/6)、[日志 #7](https://github.com/NightGlowww/jeepay/pull/7)、[渠道 #8](https://github.com/NightGlowww/jeepay/pull/8) 均通过 Shell / Maven CI；[StarPOS #10](https://github.com/NightGlowww/jeepay/pull/10) 也已通过 Shell / Maven CI 并合入 dev；后续合并提交检查见分支 Actions。
- 配套前端 `00d32ef` 及治理冲突合并：三端安装与生产构建通过，商户配置 8/8，OAuth 16/16。三项 GitHub 检查 `callback`、`manager build`、`merchant build` 已通过。没有安装或执行 Playwright；原浏览器套件已移除，保留 jsdom 和手工浏览器入口。
- 签名日志：本地工具及真实 SDK 回归 5 项通过；三个实际 Logback 配置 × 两版 SDK，通过 LoggingSystem 加载验证 6/6。全局 TRACE 正对照能检出旧敏感原文，修复配置无密钥/签名原文输出且保留安全 WARN。
- DEV 验证只使用新建的 `jeepay-fork-sql-20261116` 一次性 MySQL 8.4.13 容器（network=none、无 host port、临时 tmpfs），不连接现有支付数据库。初始及最终整合 `init.sql` 均成功创建 23 表；StarPOS 种子重复执行各仅一条，自定义说明/参数不被覆盖。
- 参数升级：6 个字段只追加一次；原字段顺序、元数据、非目标渠道、子商户定义和模拟实际配置均保持；重复执行无变化。无效 JSON、NULL、非数组、超长数组均跳过并列出 6 项诊断；已有同名自定义元数据和自定义页面模式保持。修前无效 JSON 触发 ERROR 3141，补保护条件后这些正反场景全部通过。MySQL 5.7 只核对函数兼容文档，没有执行版本实测。
- 最终订单表仍为 `channel_extra VARCHAR(512) NULL`、`ext_param VARCHAR(2048) NULL`、`notify_url VARCHAR(128) NOT NULL DEFAULT ''`、`return_url VARCHAR(128) NULL DEFAULT ''`。临时 SQL 容器已删除。
- EPAY 已记录为本 fork [待办 #9](https://github.com/NightGlowww/jeepay/issues/9)。新增截图所报订单表格跨列问题另行同步到 fork UI 与当前 DEV 体验前端，其验收不作为本轮新后端的实付证明。
