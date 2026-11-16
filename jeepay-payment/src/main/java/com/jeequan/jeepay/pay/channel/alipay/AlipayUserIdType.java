package com.jeequan.jeepay.pay.channel.alipay;

import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.params.alipay.AlipayIsvParams;
import com.jeequan.jeepay.core.model.params.alipay.AlipayNormalMchParams;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.service.ConfigContextQueryService;
import org.apache.commons.lang3.StringUtils;

/** 身份空间由支付宝应用配置明确指定，OAuth 和下单必须使用同一类型。 */
public enum AlipayUserIdType {
    USER_ID, OPEN_ID;

    public static AlipayUserIdType resolve(ConfigContextQueryService configs, MchAppConfigContext context) {
        String value;
        if (context.isIsvsubMch()) {
            AlipayIsvParams params = (AlipayIsvParams) configs.queryIsvParams(context.getMchInfo().getIsvNo(), CS.IF_CODE.ALIPAY);
            if (params == null) {
                throw new BizException("服务商支付宝接口没有配置！");
            }
            value = params.getUserIdType();
        } else {
            AlipayNormalMchParams params = (AlipayNormalMchParams) configs.queryNormalMchParams(context.getMchNo(), context.getAppId(), CS.IF_CODE.ALIPAY);
            if (params == null) {
                throw new BizException("商户支付宝接口没有配置！");
            }
            value = params.getUserIdType();
        }
        if (StringUtils.isBlank(value)) {
            return USER_ID;
        }
        for (AlipayUserIdType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new BizException("支付宝 userIdType 仅支持 USER_ID 或 OPEN_ID");
    }

    public static boolean isMissing(String value) {
        return StringUtils.isBlank(value) || "undefined".equalsIgnoreCase(value.trim()) || "null".equalsIgnoreCase(value.trim());
    }
}
