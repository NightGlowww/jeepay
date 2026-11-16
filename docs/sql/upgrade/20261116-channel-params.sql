-- 2026-11-16 支付宝用户标识类型 / 微信支付公钥参数定义补齐。
-- 适用 MySQL 5.7.9+ / 8.0+；JSON_ARRAY_APPEND 自 5.7.9 使用此名称。
-- 仅修改 t_pay_interface_define 中 alipay、wxpay 的 isv_params / normal_mch_params。
-- 不修改 t_pay_interface_config 的商户/服务商实际配置，也不修改 config_page_type。
-- 不重放历史 patch.sql；已有同名定义（包括自定义内容）保持原样。
-- 仅向有效 JSON 数组追加；非数组、无效 JSON 或追加后超过 VARCHAR(4096) 的定义跳过。
-- 支付宝未配置 userIdType 时由后端按 USER_ID 处理；这里不写入实际支付配置。
-- 独立执行：mysql --default-character-set=utf8mb4 -u USER -p DATABASE < docs/sql/upgrade/20261116-channel-params.sql

SET NAMES utf8mb4;
START TRANSACTION;

UPDATE t_pay_interface_define
SET isv_params = JSON_ARRAY_APPEND(isv_params, '$', JSON_OBJECT('name', 'userIdType', 'desc', '用户标识类型（缺省USER_ID）', 'type', 'radio', 'verify', '', 'values', 'USER_ID,OPEN_ID', 'titles', '旧版用户ID（USER_ID）,开放账号（OPEN_ID）'))
WHERE if_code = 'alipay'
  AND JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'userIdType')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'userIdType', 'desc', '用户标识类型（缺省USER_ID）', 'type', 'radio', 'verify', '', 'values', 'USER_ID,OPEN_ID', 'titles', '旧版用户ID（USER_ID）,开放账号（OPEN_ID）'))) <= 4096;

UPDATE t_pay_interface_define
SET normal_mch_params = JSON_ARRAY_APPEND(normal_mch_params, '$', JSON_OBJECT('name', 'userIdType', 'desc', '用户标识类型（缺省USER_ID）', 'type', 'radio', 'verify', '', 'values', 'USER_ID,OPEN_ID', 'titles', '旧版用户ID（USER_ID）,开放账号（OPEN_ID）'))
WHERE if_code = 'alipay'
  AND JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'userIdType')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'userIdType', 'desc', '用户标识类型（缺省USER_ID）', 'type', 'radio', 'verify', '', 'values', 'USER_ID,OPEN_ID', 'titles', '旧版用户ID（USER_ID）,开放账号（OPEN_ID）'))) <= 4096;

UPDATE t_pay_interface_define
SET isv_params = JSON_ARRAY_APPEND(isv_params, '$', JSON_OBJECT('name', 'wxpayPublicKeyId', 'desc', '微信支付公钥ID（公钥模式必填）', 'type', 'text', 'verify', ''))
WHERE if_code = 'wxpay'
  AND JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKeyId')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'wxpayPublicKeyId', 'desc', '微信支付公钥ID（公钥模式必填）', 'type', 'text', 'verify', ''))) <= 4096;

UPDATE t_pay_interface_define
SET normal_mch_params = JSON_ARRAY_APPEND(normal_mch_params, '$', JSON_OBJECT('name', 'wxpayPublicKeyId', 'desc', '微信支付公钥ID（公钥模式必填）', 'type', 'text', 'verify', ''))
WHERE if_code = 'wxpay'
  AND JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKeyId')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'wxpayPublicKeyId', 'desc', '微信支付公钥ID（公钥模式必填）', 'type', 'text', 'verify', ''))) <= 4096;

UPDATE t_pay_interface_define
SET isv_params = JSON_ARRAY_APPEND(isv_params, '$', JSON_OBJECT('name', 'wxpayPublicKey', 'desc', '微信支付公钥（pub_key.pem，公钥模式必填）', 'type', 'file', 'verify', ''))
WHERE if_code = 'wxpay'
  AND JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKey')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'wxpayPublicKey', 'desc', '微信支付公钥（pub_key.pem，公钥模式必填）', 'type', 'file', 'verify', ''))) <= 4096;

UPDATE t_pay_interface_define
SET normal_mch_params = JSON_ARRAY_APPEND(normal_mch_params, '$', JSON_OBJECT('name', 'wxpayPublicKey', 'desc', '微信支付公钥（pub_key.pem，公钥模式必填）', 'type', 'file', 'verify', ''))
WHERE if_code = 'wxpay'
  AND JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) = 'ARRAY'
  AND JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKey')) = 0
  AND CHAR_LENGTH(JSON_ARRAY_APPEND(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), '$', JSON_OBJECT('name', 'wxpayPublicKey', 'desc', '微信支付公钥（pub_key.pem，公钥模式必填）', 'type', 'file', 'verify', ''))) <= 4096;

COMMIT;

-- 以下结果应为空；若有输出，检查对应定义是否为有效数组或已达到 4096 字符上限。
SELECT if_code, 'isv_params' AS definition_column, 'userIdType' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'alipay'
  AND (JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'userIdType')) = 0)
UNION ALL
SELECT if_code, 'normal_mch_params' AS definition_column, 'userIdType' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'alipay'
  AND (JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'userIdType')) = 0)
UNION ALL
SELECT if_code, 'isv_params' AS definition_column, 'wxpayPublicKeyId' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'wxpay'
  AND (JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKeyId')) = 0)
UNION ALL
SELECT if_code, 'normal_mch_params' AS definition_column, 'wxpayPublicKeyId' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'wxpay'
  AND (JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKeyId')) = 0)
UNION ALL
SELECT if_code, 'isv_params' AS definition_column, 'wxpayPublicKey' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'wxpay'
  AND (JSON_TYPE(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(isv_params), isv_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKey')) = 0)
UNION ALL
SELECT if_code, 'normal_mch_params' AS definition_column, 'wxpayPublicKey' AS missing_field
FROM t_pay_interface_define
WHERE if_code = 'wxpay'
  AND (JSON_TYPE(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY())) <> 'ARRAY'
       OR JSON_CONTAINS(IF(JSON_VALID(normal_mch_params), normal_mch_params, JSON_ARRAY()), JSON_OBJECT('name', 'wxpayPublicKey')) = 0);
