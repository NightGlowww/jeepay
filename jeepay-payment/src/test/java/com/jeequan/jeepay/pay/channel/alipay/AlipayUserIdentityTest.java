package com.jeequan.jeepay.pay.channel.alipay;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.domain.AlipayTradeCreateModel;
import com.alipay.api.request.AlipaySystemOauthTokenRequest;
import com.alipay.api.request.AlipayTradeCreateRequest;
import com.alipay.api.response.AlipaySystemOauthTokenResponse;
import com.alipay.api.response.AlipayTradeCreateResponse;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.PayOrder;
import com.jeequan.jeepay.core.entity.MchInfo;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.params.alipay.AlipayNormalMchParams;
import com.jeequan.jeepay.core.model.params.alipay.AlipayIsvParams;
import com.jeequan.jeepay.pay.channel.alipay.payway.AliJsapi;
import com.jeequan.jeepay.pay.exception.ChannelException;
import com.jeequan.jeepay.pay.model.AlipayClientWrapper;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.rqrs.payorder.payway.AliJsapiOrderRQ;
import com.jeequan.jeepay.pay.service.ConfigContextQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlipayUserIdentityTest {
    private final ConfigContextQueryService configs = mock(ConfigContextQueryService.class);
    private final AlipayClientWrapper client = mock(AlipayClientWrapper.class);
    private final MchAppConfigContext context = mock(MchAppConfigContext.class);
    private final AlipayNormalMchParams params = new AlipayNormalMchParams();
    private final AlipayChannelUserService users = new AlipayChannelUserService();

    @BeforeEach
    void setUp() {
        when(context.getMchNo()).thenReturn("synthetic-mch");
        when(context.getAppId()).thenReturn("synthetic-app");
        when(configs.queryNormalMchParams("synthetic-mch", "synthetic-app", CS.IF_CODE.ALIPAY)).thenReturn(params);
        when(configs.getAlipayClientWrapper(context)).thenReturn(client);
        ReflectionTestUtils.setField(users, "configContextQueryService", configs);
    }

    @ParameterizedTest
    @CsvSource({"USER_ID,legacy-user-id", "OPEN_ID,2088-is-an-explicit-open-id"})
    void oauthAndJsapiUseTheConfiguredIdentitySpaceRegardlessOfPrefix(String type, String expected) throws Exception {
        params.setUserIdType(type);
        AlipaySystemOauthTokenResponse oauth = new AlipaySystemOauthTokenResponse();
        oauth.setUserId("legacy-user-id");
        oauth.setOpenId("2088-is-an-explicit-open-id");
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(oauth);
        String id = users.getChannelUserId(authParams(), context);
        assertEquals(expected, id);
        AliJsapiOrderRQ rq = new AliJsapiOrderRQ();
        rq.setBuyerUserId(id);
        AliJsapi jsapi = new AliJsapi() {
            @Override protected String getNotifyUrl() { return "https://synthetic.example/notify"; }
        };
        ReflectionTestUtils.setField(jsapi, "configContextQueryService", configs);
        PayOrder order = new PayOrder();
        order.setAmount(100L);
        order.setExpiredTime(new Date());
        when(client.execute(any(AlipayTradeCreateRequest.class))).thenReturn(new AlipayTradeCreateResponse());
        jsapi.pay(rq, order, context);
        ArgumentCaptor<AlipayTradeCreateRequest> request = ArgumentCaptor.forClass(AlipayTradeCreateRequest.class);
        verify(client).execute(request.capture());
        AlipayTradeCreateModel model = (AlipayTradeCreateModel) request.getValue().getBizModel();
        assertEquals("USER_ID".equals(type) ? id : null, model.getBuyerId());
        assertEquals("OPEN_ID".equals(type) ? id : null, model.getBuyerOpenId());
    }

    @Test
    void existingConfigurationDefaultsToUserIdAndDoesNotSilentlyReturnOpenId() {
        AlipaySystemOauthTokenResponse oauth = new AlipaySystemOauthTokenResponse();
        oauth.setUserId("legacy-user-id");
        oauth.setOpenId("synthetic-open-id");
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(oauth);
        assertEquals("legacy-user-id", users.getChannelUserId(authParams(), context));
        oauth.setUserId(null);
        assertTrue(assertThrows(BizException.class, () -> users.getChannelUserId(authParams(), context))
                .getMessage().contains("userIdType"));
    }

    @Test
    void unknownConfigurationAndFailedOauthStopBeforeOrderCreation() {
        params.setUserIdType("AUTO_GUESS");
        assertThrows(BizException.class, () -> users.getChannelUserId(authParams(), context));
        verifyNoInteractions(client);
        params.setUserIdType("OPEN_ID");
        AlipaySystemOauthTokenResponse oauth = new AlipaySystemOauthTokenResponse();
        oauth.setSubCode("invalid-auth-code");
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenReturn(oauth);
        assertThrows(BizException.class, () -> users.getChannelUserId(authParams(), context));
        when(client.execute(any(AlipaySystemOauthTokenRequest.class))).thenThrow(ChannelException.sysError("synthetic failure"));
        assertThrows(BizException.class, () -> users.getChannelUserId(authParams(), context));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "undefined", "null"})
    void missingOrJavascriptPlaceholderIdentityIsRejectedBeforePay(String value) {
        AliJsapiOrderRQ rq = new AliJsapiOrderRQ();
        rq.setBuyerUserId(value);
        assertThrows(BizException.class, () -> new AliJsapi().preCheck(rq, new PayOrder()));
        JSONObject request = new JSONObject();
        request.put("auth_code", value);
        assertThrows(BizException.class, () -> users.getChannelUserId(request, context));
        verifyNoInteractions(client);
    }

    @Test
    void serviceProviderModeResolvesIdentityFromTheCallingIsvApplication() {
        when(context.isIsvsubMch()).thenReturn(true);
        MchInfo merchant = new MchInfo();
        merchant.setIsvNo("synthetic-isv");
        when(context.getMchInfo()).thenReturn(merchant);
        AlipayIsvParams isv = new AlipayIsvParams();
        isv.setUserIdType("OPEN_ID");
        when(configs.queryIsvParams("synthetic-isv", CS.IF_CODE.ALIPAY)).thenReturn(isv);
        assertEquals(AlipayUserIdType.OPEN_ID, AlipayUserIdType.resolve(configs, context));
        verify(configs, never()).queryNormalMchParams(any(), any(), any());
    }

    private JSONObject authParams() {
        JSONObject request = new JSONObject();
        request.put("auth_code", "synthetic-code");
        return request;
    }
}
