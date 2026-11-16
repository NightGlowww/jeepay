package com.jeequan.jeepay.pay.channel.wxpay;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.model.WxServiceWrapper;
import com.jeequan.jeepay.pay.service.ConfigContextQueryService;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.common.service.WxOAuth2Service;
import me.chanjar.weixin.mp.api.WxMpService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WxpayChannelUserServiceTest {
    @Test
    void oauthFailuresAndEmptyIdentityAreReportedInsteadOfReturningNull() throws Exception {
        ConfigContextQueryService configs = mock(ConfigContextQueryService.class);
        MchAppConfigContext context = new MchAppConfigContext();
        WxMpService mp = mock(WxMpService.class);
        WxOAuth2Service oauth = mock(WxOAuth2Service.class);
        when(mp.getOAuth2Service()).thenReturn(oauth);
        when(configs.getWxServiceWrapper(context)).thenReturn(new WxServiceWrapper("V3", null, mp));
        WxpayChannelUserService users = new WxpayChannelUserService();
        ReflectionTestUtils.setField(users, "configContextQueryService", configs);
        JSONObject request = new JSONObject();
        request.put("code", "synthetic-code");
        when(oauth.getAccessToken("synthetic-code")).thenThrow(new WxErrorException("synthetic failure"));
        assertThrows(BizException.class, () -> users.getChannelUserId(request, context));
        doReturn(new WxOAuth2AccessToken()).when(oauth).getAccessToken("synthetic-code");
        assertThrows(BizException.class, () -> users.getChannelUserId(request, context));
        WxOAuth2AccessToken token = new WxOAuth2AccessToken();
        token.setOpenId("synthetic-open-id");
        doReturn(token).when(oauth).getAccessToken("synthetic-code");
        assertEquals("synthetic-open-id", users.getChannelUserId(request, context));
    }
}
