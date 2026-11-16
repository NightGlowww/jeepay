package com.jeequan.jeepay.pay.channel.starpos;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用 JVM 默认 TLS 信任链和主机名校验的星驿付 HTTP 客户端。
 */
public final class StarposHttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StarposHttpClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String CONTENT_TYPE = "application/json; charset=UTF-8";
    private static final Set<String> FIXED_HOSTS = Set.of(
            "xyf-server-test.postar.cn",
            "xyzscxm.postar.cn",
            "yyfsvxm.postar.cn"
    );

    private final URI baseUri;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    /**
     * 使用固定星驿付环境地址创建客户端。
     */
    public StarposHttpClient(String baseUrl) {
        this(validateConfiguredBaseUri(baseUrl), newHttpClient(), REQUEST_TIMEOUT);
    }

    StarposHttpClient(URI baseUri, HttpClient httpClient, Duration requestTimeout) {
        this.baseUri = validateClientBaseUri(baseUri);
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = requirePositive(requestTimeout, "requestTimeout");
    }

    static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * POST UTF-8 JSON，并返回不超过 1 MiB 的 JSON 对象响应。
     */
    public JSONObject post(String path, JSONObject requestBody, String orderNo)
            throws IOException, InterruptedException {
        URI requestUri = resolvePath(path);
        HttpRequest request = HttpRequest.newBuilder(requestUri)
                .timeout(requestTimeout)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        Objects.requireNonNull(requestBody, "requestBody").toJSONString(),
                        StandardCharsets.UTF_8
                ))
                .build();

        long startedAt = System.nanoTime();
        String businessCode = "-";
        try {
            HttpResponse<byte[]> response = receiveResponse(request, startedAt);
            JSONObject json = parseJsonObject(response.body());
            businessCode = Objects.toString(json.getString("code"), "-");
            return json;
        } finally {
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            LOGGER.info(
                    "星驿付HTTP请求 path={}, elapsedMs={}, businessCode={}, orderNo={}",
                    requestUri.getPath(),
                    elapsedMillis,
                    businessCode,
                    maskOrderNo(orderNo)
            );
        }
    }

    private HttpResponse<byte[]> receiveResponse(HttpRequest request, long startedAt)
            throws IOException, InterruptedException {
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber();
        CompletableFuture<HttpResponse<byte[]>> response = httpClient.sendAsync(request, info -> {
            if (info.statusCode() < 200 || info.statusCode() >= 300) {
                subscriber.abort(new IOException("星驿付响应状态异常"));
            }
            return subscriber;
        });
        try {
            // ofInputStream 在响应头到达时便完成；这里等待 body 完整结束，deadline 覆盖整个请求。
            long remaining = requestTimeout.toNanos() - (System.nanoTime() - startedAt);
            return response.get(Math.max(1L, remaining), TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            HttpTimeoutException timeout = new HttpTimeoutException("星驿付响应超过整体请求时限");
            subscriber.abort(timeout);
            response.cancel(true);
            throw timeout;
        } catch (InterruptedException e) {
            subscriber.abort(e);
            response.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("星驿付请求失败", e.getCause());
        }
    }

    /** 直接订阅网络 body，不引入阻塞 reader 线程；取消订阅会终止底层响应读取。 */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription next) {
            if (subscription != null || body.isDone()) {
                next.cancel();
                return;
            }
            subscription = next;
            subscription.request(1);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> items) {
            if (body.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                if (item.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    abort(new IOException("星驿付响应超过 1 MiB"));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public synchronized void onError(Throwable failure) {
            body.completeExceptionally(failure);
        }

        @Override
        public synchronized void onComplete() {
            body.complete(bytes.toByteArray());
        }

        private synchronized void abort(Throwable failure) {
            if (body.completeExceptionally(failure) && subscription != null) {
                subscription.cancel();
            }
        }
    }

    private static JSONObject parseJsonObject(byte[] bytes) throws IOException {
        try {
            Object parsed = JSON.parse(new String(bytes, StandardCharsets.UTF_8));
            if (parsed instanceof JSONObject) {
                return (JSONObject) parsed;
            }
        } catch (JSONException ignored) {
            // 统一转换为不含响应内容的异常，避免敏感响应进入上层日志。
        }
        throw new IOException("星驿付响应不是合法 JSON 对象");
    }

    private URI resolvePath(String path) {
        Objects.requireNonNull(path, "path");
        URI relative = URI.create(path);
        if (!path.startsWith("/") || relative.isAbsolute() || relative.getRawAuthority() != null) {
            throw new IllegalArgumentException("星驿付请求路径必须为绝对路径");
        }
        return baseUri.resolve(relative);
    }

    private static URI validateConfiguredBaseUri(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        return validateClientBaseUri(URI.create(baseUrl));
    }

    private static URI validateClientBaseUri(URI uri) {
        Objects.requireNonNull(uri, "baseUri");
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("星驿付服务地址必须使用 HTTPS");
        }
        validateRootUri(uri);

        String host = normalizedHost(uri);
        if (!FIXED_HOSTS.contains(host)) {
            throw new IllegalArgumentException("不允许的星驿付服务主机: " + host);
        }
        return uri;
    }

    private static void validateRootUri(URI uri) {
        String path = uri.getPath();
        if (uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))) {
            throw new IllegalArgumentException("星驿付服务地址必须为固定根地址");
        }
    }

    private static String normalizedHost(URI uri) {
        String host = uri.getHost();
        return host == null ? "" : host.toLowerCase(Locale.ROOT);
    }

    private static Duration requirePositive(Duration duration, String field) {
        Objects.requireNonNull(duration, field);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " 必须大于零");
        }
        return duration;
    }

    private static String maskOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return "-";
        }
        if (orderNo.length() <= 4) {
            return "*".repeat(orderNo.length());
        }
        if (orderNo.length() <= 8) {
            return orderNo.substring(0, 2)
                    + "*".repeat(orderNo.length() - 4)
                    + orderNo.substring(orderNo.length() - 2);
        }
        return orderNo.substring(0, 4)
                + "*".repeat(orderNo.length() - 8)
                + orderNo.substring(orderNo.length() - 4);
    }
}
