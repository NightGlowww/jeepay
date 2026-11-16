package com.jeequan.jeepay.pay.util;

import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.model.params.xxpay.XxpayNormalMchParams;
import com.jeequan.jeepay.pay.channel.xxpay.XxpayPaymentService;
import com.jeequan.jeepay.pay.rqrs.msg.ChannelRetMsg;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboundHttpClientTest {

    @Test
    void merchantXxpayOrderCannotSendToItsPersistedLoopbackGateway() throws Exception {
        XxpayPaymentService service = new XxpayPaymentService();
        FakeClient http = new FakeClient();
        http.addresses = new InetAddress[]{ip("127.0.0.1")};
        ReflectionTestUtils.setField(service, "outboundHttpClient", http);
        XxpayNormalMchParams params = new XxpayNormalMchParams();
        params.setPayUrl("http://127.0.0.1:28081/poc");
        params.setKey("synthetic-test-key");
        ChannelRetMsg result = new ChannelRetMsg();
        assertNull(ReflectionTestUtils.invokeMethod(service, "doPay", new PayOrder(), params, new TreeMap<String, Object>(), result));
        assertEquals(ChannelRetMsg.ChannelState.CONFIRM_FAIL, result.getChannelState());
        assertEquals(1, http.lookups);
        assertEquals(0, http.requests);
    }

    @Test
    void blocksPrivateSpecialAndEncodedAddressesBeforeTransport() throws Exception {
        String[] hosts = {"127.0.0.1", "127.1", "2130706433", "0.0.0.0", "10.0.0.2", "172.16.0.1", "192.168.1.1",
                "169.254.169.254", "100.100.100.200", "198.18.0.1", "192.0.2.1", "224.0.0.1", "240.0.0.1", "168.63.129.16",
                "[::1]", "[::ffff:127.0.0.1]", "[fc00::1]", "[fe80::1]", "[2002:7f00:1::1]", "[2001:db8::1]"};
        for (String host : hosts) {
            FakeClient client = new FakeClient();
            client.addresses = new InetAddress[]{InetAddress.getByName(host)};
            assertThrows(IOException.class, () -> client.postNotification("http://" + host + "/notify?sign=test", null), host);
            assertThrows(IOException.class, () -> client.postXxpay("http://" + host + "/api/pay/create_order"), host);
            assertEquals(0, client.requests, host);
        }
    }

    @Test
    void rejectsAmbiguousUrlFormsAndMixedDnsAnswers() throws Exception {
        FakeClient client = new FakeClient();
        for (String url : List.of("file:///tmp/test", "http://user:pass@notify.example/", "http://notify.example/#fragment",
                "http://127.0.0.1\\@notify.example/", "http://notify.example:0/", "http://notify.example:65536/", "http://notify.example:/")) {
            assertThrows(IOException.class, () -> client.postXxpay(url), url);
        }
        client.addresses = new InetAddress[]{ip("8.8.8.8"), ip("127.0.0.1")};
        assertThrows(IOException.class, () -> client.postNotification("https://notify.example/hook", null));
        assertEquals(0, client.requests);
    }

    @Test
    void preservesNotificationFormAndXxpayQueryWhilePinningDnsPerAttempt() throws Exception {
        FakeClient client = new FakeClient();
        client.addresses = new InetAddress[]{ip("8.8.8.8")};
        assertEquals("SUCCESS", client.postNotification("https://notify.example/hook?token=first&token=a%2Bb&sign=c%26d", null));
        assertEquals("https://notify.example/hook", client.lastPost.getUri().toString());
        assertEquals(Map.of("token", "a+b", "sign", "c&d"), URLEncodedUtils.parse(
                EntityUtils.toString(client.lastPost.getEntity(), StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .stream().collect(Collectors.toMap(pair -> pair.getName(), pair -> pair.getValue())));
        assertEquals(20000, client.timeout);
        client.addresses = new InetAddress[]{ip("127.0.0.1")};
        assertArrayEquals(new InetAddress[]{ip("8.8.8.8")}, client.pinned.resolve("notify.example"));
        assertThrows(UnknownHostException.class, () -> client.pinned.resolve("another.example"));
        assertEquals(1, client.lookups);
        assertThrows(IOException.class, () -> client.postNotification("https://notify.example/hook", null));
        assertEquals(1, client.requests);

        client.addresses = new InetAddress[]{ip("2606:4700:4700::1111")};
        assertEquals("SUCCESS", client.postXxpay("https://pay.example/api/pay/create_order?sign=a%2Bb"));
        assertEquals("https://pay.example/api/pay/create_order?sign=a%2Bb", client.lastPost.getUri().toString());
        assertNull(client.lastPost.getEntity());
        assertEquals(60000, client.timeout);
    }

    @Test
    void refusesRedirectResponsesWithoutFollowingTheirTargets() throws Exception {
        FakeClient client = new FakeClient();
        client.status = 302;
        assertThrows(IOException.class, () -> client.postXxpay("https://pay.example/api/pay/create_order"));
        assertEquals(1, client.requests);
        assertEquals(1, client.lookups);
    }

    @Test
    void onlyOperatorConfiguredOriginsAndExactPaytestCallbackCanUsePrivateAddresses() throws Exception {
        FakeClient client = new FakeClient();
        client.addresses = new InetAddress[]{ip("127.0.0.1")};
        String callback = "http://127.0.0.1:9218/api/anon/paytestNotify/payOrder";
        assertEquals("SUCCESS", client.postNotification(callback + "?sign=test", callback));
        assertThrows(IOException.class, () -> client.postNotification("http://127.0.0.1:9218/api/sysUsers?sign=test", callback));
        assertThrows(IOException.class, () -> client.postNotification(callback + "/../secret?sign=test", callback));
        assertThrows(IOException.class, () -> client.postXxpay("http://127.0.0.1:9218/api/pay/create_order"));
        client.setXxpayTrustedOrigins(List.of("http://gateway.internal:8080"));
        assertEquals("SUCCESS", client.postXxpay("http://gateway.internal:8080/api/pay/create_order"));
        assertThrows(IOException.class, () -> client.postXxpay("http://gateway.internal:8081/api/pay/create_order"));
        assertThrows(IOException.class, () -> client.postNotification("http://gateway.internal:8080/hook", null));
        client.setNotificationTrustedOrigins(List.of("https://notify.internal"));
        assertEquals("SUCCESS", client.postNotification("https://notify.internal:443/hook?sign=test", null));
        assertThrows(IOException.class, () -> client.postXxpay("https://notify.internal/api/pay/create_order"));
    }

    private static InetAddress ip(String literal) throws UnknownHostException {
        return InetAddress.getByName(literal);
    }

    static class FakeClient extends OutboundHttpClient {
        InetAddress[] addresses;
        DnsResolver pinned;
        HttpPost lastPost;
        int requests;
        int lookups;
        int timeout;
        int status = 200;

        FakeClient() throws UnknownHostException {
            addresses = new InetAddress[]{ip("8.8.8.8")};
        }

        @Override protected InetAddress[] resolve(String host) {
            lookups++;
            return addresses;
        }

        @Override protected CloseableHttpClient createClient(DnsResolver resolver, int timeout) {
            pinned = resolver;
            this.timeout = timeout;
            CloseableHttpClient http = mock(CloseableHttpClient.class);
            try {
                doAnswer(invocation -> {
                    requests++;
                    lastPost = invocation.getArgument(0);
                    BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
                    response.setHeader("Location", "http://127.0.0.1/internal");
                    response.setEntity(new StringEntity("SUCCESS"));
                    HttpClientResponseHandler<?> handler = invocation.getArgument(1);
                    return handler.handleResponse(response);
                }).when(http).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return http;
        }
    }
}
