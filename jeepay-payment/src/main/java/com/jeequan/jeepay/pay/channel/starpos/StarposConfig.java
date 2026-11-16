package com.jeequan.jeepay.pay.channel.starpos;

import com.jeequan.jeepay.core.model.params.starpos.StarposNormalMchParams;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * 星驿付固定环境配置。
 */
public final class StarposConfig {

    private static final DateTimeFormatter REQUEST_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Shanghai"));

    static String requestTimestamp() {
        return REQUEST_TIMESTAMP.format(Instant.now());
    }

    private static final Map<String, String> BASE_URLS = Map.of(
            "test", "https://xyf-server-test.postar.cn",
            "uat", "https://xyzscxm.postar.cn",
            "prod", "https://yyfsvxm.postar.cn"
    );

    // 官方取码示例及官方网页环境映射；只接受固定收银路径，不接受 API 地址或任意 postar 子域。
    // https://xingyifu.apifox.cn/341551818e0
    // https://xingyifu.apifox.cn/411284327e0
    private static final Map<String, String> CASHIER_HOSTS = Map.of(
            "test", "yyfwebcs.postar.cn",
            "uat", "deccapayuat.postar.cn",
            "prod", "deccacloud.postar.cn"
    );
    private static final String CASHIER_PATH = "/postalpay/p/generateCode/standard";

    private StarposConfig() {
    }

    /**
     * 校验商户参数并返回对应环境的服务地址。
     */
    public static String resolveBaseUrl(StarposNormalMchParams params) {
        if (params == null) {
            throw new IllegalArgumentException("星驿付商户参数不能为空");
        }

        requireNotBlank(params.getEnvironment(), "environment");
        requireNotBlank(params.getAgetId(), "agetId");
        requireNotBlank(params.getCustId(), "custId");
        requireNotBlank(params.getPublicKey(), "publicKey");
        requireNotBlank(params.getVersion(), "version");

        String baseUrl = BASE_URLS.get(params.getEnvironment());
        if (baseUrl == null) {
            throw new IllegalArgumentException("不支持的星驿付环境: " + params.getEnvironment());
        }
        return baseUrl;
    }

    /** 校验平台生成的收银链接，可用于协议适配层的跳转。 */
    public static boolean isKnownCashierUrl(String cashierUrl) {
        return parseCashierUrl(cashierUrl) != null;
    }

    /** 取码响应还需绑定当前环境、三方订单与机构，避免复用另一个有效链接。 */
    static boolean isCashierUrlForOrder(String cashierUrl, StarposNormalMchParams params,
                                         String payOrderId) {
        CashierUrl parsed = parseCashierUrl(cashierUrl);
        return parsed != null && params != null && params.getEnvironment() != null
                && parsed.uri().getHost().equalsIgnoreCase(CASHIER_HOSTS.get(params.getEnvironment()))
                && StringUtils.equals(payOrderId, parsed.query().get("threeOrderNo"))
                && StringUtils.equals(params.getAgetId(), parsed.query().get("agetId"));
    }

    private static CashierUrl parseCashierUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null
                    || !CASHIER_HOSTS.containsValue(uri.getHost().toLowerCase(Locale.ROOT))
                    || !CASHIER_PATH.equals(uri.getRawPath()) || uri.getRawQuery() == null) {
                return null;
            }
            Map<String, String> query = new LinkedHashMap<>();
            for (String item : uri.getRawQuery().split("&", -1)) {
                String[] pair = item.split("=", 2);
                if (pair.length != 2) {
                    return null;
                }
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String content = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (key.isEmpty() || query.putIfAbsent(key, content) != null
                        || content.chars().anyMatch(Character::isISOControl)) {
                    return null;
                }
            }
            if (StringUtils.isBlank(query.get("threeOrderNo")) || StringUtils.isBlank(query.get("agetId"))
                    || !StringUtils.defaultString(query.get("orderDate")).matches("[0-9]{8}")) {
                return null;
            }
            LocalDate.parse(query.get("orderDate"), DateTimeFormatter.BASIC_ISO_DATE);
            return new CashierUrl(uri, query);
        } catch (IllegalArgumentException | DateTimeException e) {
            return null;
        }
    }

    private record CashierUrl(URI uri, Map<String, String> query) {
    }

    private static void requireNotBlank(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }
}
