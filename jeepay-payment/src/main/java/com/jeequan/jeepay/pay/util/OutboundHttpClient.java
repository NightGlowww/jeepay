package com.jeequan.jeepay.pay.util;

import cn.hutool.http.HttpUtil;
import lombok.Getter;
import lombok.Setter;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 商户可控 URL 的出站边界。内网例外只能由部署配置授权，不能来自商户支付参数。
 * isys.outbound.notification-trusted-origins / xxpay-trusted-origins 的元素是精确 HTTP(S) origin，
 * 例如 http://notify.internal:8080；授权该 origin 的所有路径。默认只允许公网，两个列表互不授权。
 */
@Component
@ConfigurationProperties(prefix = "isys.outbound")
public class OutboundHttpClient {

    @Getter @Setter
    private List<String> notificationTrustedOrigins = new ArrayList<>();
    @Getter @Setter
    private List<String> xxpayTrustedOrigins = new ArrayList<>();

    public String postNotification(String url, String paytestEndpoint) throws IOException {
        URI uri = parse(url);
        HttpPost post = new HttpPost(withoutQuery(uri));
        // 保持旧通知接口的解码及同名参数覆盖语义，只替换实际发出请求的 transport。
        List<BasicNameValuePair> form = new ArrayList<>();
        HttpUtil.decodeParamMap(url, StandardCharsets.UTF_8).forEach((key, value) -> form.add(new BasicNameValuePair(key, value)));
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        return execute(post, uri, notificationTrustedOrigins, matchesEndpoint(uri, paytestEndpoint), 20000);
    }

    public String postXxpay(String url) throws IOException {
        URI uri = parse(url);
        return execute(new HttpPost(uri), uri, xxpayTrustedOrigins, false, 60000);
    }

    private String execute(HttpPost post, URI uri, List<String> trustedOrigins, boolean trustedEndpoint, int timeout) throws IOException {
        String host = host(uri);
        InetAddress[] addresses = resolve(host);
        if (addresses == null || addresses.length == 0) throw new UnknownHostException("目标地址无法解析");
        if (!trustedEndpoint && !matchesOrigin(uri, trustedOrigins)) {
            for (InetAddress address : addresses) {
                if (!isPublicAddress(address)) throw new IOException("出站地址不是允许的公网地址");
            }
        }
        // 校验与连接使用同一份 DNS 快照；HttpClient 不再查询系统 DNS，重试则重新校验。
        InetAddress[] pinned = addresses.clone();
        DnsResolver resolver = new DnsResolver() {
            @Override public InetAddress[] resolve(String requestedHost) throws UnknownHostException {
                if (!host.equalsIgnoreCase(unbracket(requestedHost))) throw new UnknownHostException("不允许更换目标主机");
                return pinned.clone();
            }
            @Override public String resolveCanonicalHostname(String requestedHost) throws UnknownHostException {
                resolve(requestedHost);
                return host;
            }
        };
        try (CloseableHttpClient client = createClient(resolver, timeout)) {
            return client.execute(post, response -> {
                if (response.getCode() >= 300 && response.getCode() < 400) throw new IOException("出站请求不允许重定向");
                return response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            });
        }
    }

    protected InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    protected CloseableHttpClient createClient(DnsResolver resolver, int timeout) {
        Timeout limit = Timeout.ofMilliseconds(timeout);
        return HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
                        .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(limit).setSocketTimeout(limit).build()).build())
                .setDefaultRequestConfig(RequestConfig.custom().setConnectionRequestTimeout(limit).setResponseTimeout(limit).build())
                .disableRedirectHandling().disableAutomaticRetries().disableCookieManagement().build();
    }

    private static URI parse(String url) throws IOException {
        try {
            URI uri = new URI(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":")) {
                throw new IOException("出站地址必须是无凭据和 fragment 的 HTTP(S) URL");
            }
            return uri;
        } catch (URISyntaxException | NullPointerException e) {
            throw new IOException("出站地址格式错误", e);
        }
    }

    private static URI withoutQuery(URI uri) throws IOException {
        String raw = uri.toASCIIString();
        int query = raw.indexOf('?');
        return parse(query < 0 ? raw : raw.substring(0, query));
    }

    private static boolean matchesOrigin(URI uri, List<String> origins) throws IOException {
        if (origins == null) return false;
        for (String origin : origins) {
            URI trusted = parse(origin);
            if (trusted.getRawQuery() != null || !(trusted.getRawPath().isEmpty() || "/".equals(trusted.getRawPath()))) {
                throw new IOException("受信 origin 不能包含路径或查询参数");
            }
            if (sameOrigin(uri, trusted)) return true;
        }
        return false;
    }

    private static boolean matchesEndpoint(URI uri, String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) return false;
        try {
            URI trusted = parse(endpoint);
            return trusted.getRawQuery() == null && sameOrigin(uri, trusted) && uri.getRawPath().equals(trusted.getRawPath());
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme()) && host(left).equalsIgnoreCase(host(right)) && port(left) == port(right);
    }

    private static int port(URI uri) {
        return uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }

    private static String host(URI uri) {
        return unbracket(uri.getHost()).toLowerCase(Locale.ROOT);
    }

    private static String unbracket(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        if (bytes.length == 4) {
            int third = bytes[2] & 255;
            int fourth = bytes[3] & 255;
            return !(first == 0 || first == 10 || first == 127 || first >= 224
                    || first == 100 && second >= 64 && second <= 127
                    || first == 169 && second == 254 || first == 172 && second >= 16 && second <= 31
                    || first == 192 && (second == 168 || second == 0 && (third == 0 || third == 2) || second == 88 && third == 99)
                    || first == 198 && (second == 18 || second == 19 || second == 51 && third == 100)
                    || first == 203 && second == 0 && third == 113
                    || first == 168 && second == 63 && third == 129 && fourth == 16);
        }
        // 仅全球单播；排除隧道、文档和协议保留地址，避免其嵌入内网 IPv4。
        return (first & 0xe0) == 0x20 && !(first == 0x20 && second == 0x02)
                && !(first == 0x20 && second == 0x01 && ((bytes[2] & 255) < 2 || ((bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8)))
                && !(first == 0x3f && second == 0xff && (bytes[2] & 0xf0) == 0);
    }
}
