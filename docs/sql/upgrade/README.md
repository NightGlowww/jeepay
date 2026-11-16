# 独立增量 SQL

存量数据库按所需变更单独执行本目录脚本；不要为补新字段重新执行 `init.sql` 或整份历史 `patch.sql`。

`20261116-channel-params.sql` 适用于 MySQL 5.7.9+ 和 8.0+，仅向 `alipay`、`wxpay` 的 `isv_params` / `normal_mch_params` 定义数组追加缺失项，可重复执行：

- 支付宝 `userIdType`：`USER_ID` / `OPEN_ID`，未配置时后端保持 `USER_ID`。
- 微信支付 `wxpayPublicKeyId`、`wxpayPublicKey`：微信支付公钥 ID 和 `.pem` 公钥文件。

```bash
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < docs/sql/upgrade/20261116-channel-params.sql
```

脚本不会修改商户或服务商的实际支付配置，也不会更改渠道的 `config_page_type`；JSON 渲染和自定义配置页面共用完整定义。已有同名字段及其自定义元数据保持原样。无效 JSON、非数组或追加后超过当前字段 `VARCHAR(4096)` 上限的定义跳过，末尾查询会列出仍缺字段的现有渠道定义供处理；脚本不创建缺失渠道。

MySQL 5.7 的函数兼容依据：[JSON 修改函数](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/json-modification-functions.html)、[JSON 查询函数](https://docs.oracle.com/cd/E17952_01/mysql-5.7-en/json-search-functions.html)。
