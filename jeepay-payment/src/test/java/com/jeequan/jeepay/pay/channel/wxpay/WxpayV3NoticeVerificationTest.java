package com.jeequan.jeepay.pay.channel.wxpay;

import com.alibaba.fastjson.JSONObject;
import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.github.binarywang.wxpay.service.impl.WxPayServiceImpl;
import com.github.binarywang.wxpay.v3.auth.PublicCertificateVerifier;
import com.github.binarywang.wxpay.v3.auth.Verifier;
import com.jeequan.jeepay.core.beans.RequestKitBean;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.model.WxServiceWrapper;
import com.jeequan.jeepay.pay.service.ConfigContextQueryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用临时生成的测试密钥验签与解密，不连接微信或读取真实商户证书。 */
class WxpayV3NoticeVerificationTest {
    private static final String API_KEY = "synthetic-api-v3-key-32-bytes-xx";
    private static final String PUBLIC_KEY_ID = "PUB_KEY_ID_SYNTHETIC";
    private static final String NONCE = "synthetic123";

    @ParameterizedTest
    @ValueSource(strings = {"payment", "refund", "transfer"})
    void publicKeyModeVerifiesAndDecryptsWithoutDownloadingPlatformCertificates(String type) throws Exception {
        KeyPair pair = keyPair();
        WxPayConfig config = new WxPayConfig();
        config.setMchId("synthetic-mch");
        config.setCertSerialNo("synthetic-serial");
        config.setApiV3Key(API_KEY);
        config.setPrivateKeyContent(pem("PRIVATE KEY", pair.getPrivate().getEncoded()).getBytes(StandardCharsets.UTF_8));
        config.setPublicKeyContent(pem("PUBLIC KEY", pair.getPublic().getEncoded()).getBytes(StandardCharsets.UTF_8));
        config.setPublicKeyId(PUBLIC_KEY_ID);
        WxPayService service = new WxPayServiceImpl();
        service.setConfig(config);
        Object notice = notice(type, service);
        String body = encryptedBody();
        MockHttpServletRequest request = request(body, pair);
        setBody(notice, body);
        assertNotNull(parse(notice, type, request));
        Verifier verifier = config.getVerifier();
        assertInstanceOf(PublicCertificateVerifier.class, verifier);
        request.removeHeader("Wechatpay-Signature");
        request.addHeader("Wechatpay-Signature", sign(body + "tampered", pair));
        assertThrows(RuntimeException.class, () -> parse(notice, type, request));
        assertSame(verifier, config.getVerifier());
        config.getApiV3HttpClient().close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "refund", "transfer"})
    void certificateModeReusesVerifierAndStillChecksEverySignature(String type) throws Exception {
        WxPayConfig config = new WxPayConfig();
        config.setApiV3Key(API_KEY);
        Verifier verifier = mock(Verifier.class);
        when(verifier.verify(anyString(), any(), anyString())).thenReturn(true, false);
        config.setVerifier(verifier);
        WxPayService service = new WxPayServiceImpl();
        service.setConfig(config);
        Object notice = notice(type, service);
        String body = encryptedBody();
        setBody(notice, body);
        MockHttpServletRequest request = request(body, keyPair());
        assertNotNull(parse(notice, type, request));
        assertThrows(RuntimeException.class, () -> parse(notice, type, request));
        assertSame(verifier, config.getVerifier());
        verify(verifier, times(2)).verify(eq(PUBLIC_KEY_ID), any(), anyString());
    }

    private Object notice(String type, WxPayService service) {
        Object notice = switch (type) {
            case "refund" -> new WxpayChannelRefundNoticeService();
            case "transfer" -> new WxpayTransferNoticeService();
            default -> new WxpayChannelNoticeService();
        };
        ConfigContextQueryService configs = mock(ConfigContextQueryService.class);
        when(configs.getWxServiceWrapper(any())).thenReturn(new WxServiceWrapper("V3", service, null));
        ReflectionTestUtils.setField(notice, "configContextQueryService", configs);
        return notice;
    }

    private void setBody(Object notice, String body) {
        RequestKitBean requests = mock(RequestKitBean.class);
        when(requests.getReqParamFromBody()).thenReturn(body);
        ReflectionTestUtils.setField(notice, "requestKitBean", requests);
    }

    private Object parse(Object notice, String type, MockHttpServletRequest request) {
        String method = "transfer".equals(type) ? "parseTransferBillsNotifyV3Result" : "parseOrderNotifyV3Result";
        return ReflectionTestUtils.invokeMethod(notice, method, request, new MchAppConfigContext());
    }

    private MockHttpServletRequest request(String body, KeyPair pair) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Wechatpay-Timestamp", "1700000000");
        request.addHeader("Wechatpay-Nonce", "synthetic-header-nonce");
        request.addHeader("Wechatpay-Serial", PUBLIC_KEY_ID);
        request.addHeader("Wechatpay-Signature", sign(body, pair));
        return request;
    }

    private String sign(String body, KeyPair pair) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(("1700000000\nsynthetic-header-nonce\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private String encryptedBody() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new GCMParameterSpec(128, NONCE.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD("synthetic".getBytes(StandardCharsets.UTF_8));
        String ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal("{}".getBytes(StandardCharsets.UTF_8)));
        JSONObject resource = new JSONObject();
        resource.put("ciphertext", ciphertext);
        resource.put("nonce", NONCE);
        resource.put("associated_data", "synthetic");
        JSONObject body = new JSONObject();
        body.put("resource", resource);
        return body.toJSONString();
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String pem(String kind, byte[] content) {
        return "-----BEGIN " + kind + "-----\n" + Base64.getEncoder().encodeToString(content)
                + "\n-----END " + kind + "-----";
    }
}
