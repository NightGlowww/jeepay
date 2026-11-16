package com.jeequan.jeepay.mch.ctrl.merchant;

import com.alibaba.fastjson.JSONObject;
import com.jeequan.jeepay.components.mq.model.ResetIsvMchAppInfoConfigMQ;
import com.jeequan.jeepay.components.mq.vender.IMQSender;
import com.jeequan.jeepay.core.constants.ApiCodeEnum;
import com.jeequan.jeepay.core.entity.MchApp;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.mch.ctrl.MerchantControllerTestSupport;
import com.jeequan.jeepay.service.impl.MchAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MchAppControllerAuthorizationTest extends MerchantControllerTestSupport {
    @Mock private MchAppService mchAppService;
    @Mock private IMQSender mqSender;
    @InjectMocks private MchAppController controller;

    @ParameterizedTest
    @ValueSource(strings = {FOREIGN_APP_ID, MISSING_APP_ID})
    void rejectsForeignOrMissingApplicationBeforeUpdate(String appId) {
        applicationUpdateRequest();
        if (FOREIGN_APP_ID.equals(appId)) {
            when(mchAppService.getById(appId)).thenReturn(application(appId, FOREIGN_MCH_NO));
        }

        assertThrows(BizException.class, () -> controller.update(appId));

        verify(mchAppService).getById(appId);
        verifyNoMoreInteractions(mchAppService);
        verifyNoInteractions(mqSender);
    }

    @Test
    void updatesOwnApplicationAndMessageWithoutTransferringMerchant() {
        applicationUpdateRequest();
        when(mchAppService.getById(OWN_APP_ID)).thenReturn(application(OWN_APP_ID, CURRENT_MCH_NO));
        when(mchAppService.updateById(any(MchApp.class))).thenReturn(true);

        assertEquals(ApiCodeEnum.SUCCESS.getCode(), controller.update(OWN_APP_ID).getCode());

        ArgumentCaptor<MchApp> update = ArgumentCaptor.forClass(MchApp.class);
        verify(mchAppService).updateById(update.capture());
        assertEquals(OWN_APP_ID, update.getValue().getAppId());
        assertEquals(CURRENT_MCH_NO, update.getValue().getMchNo());
        assertEquals("renamed application", update.getValue().getAppName());
        assertEquals("replacement-secret", update.getValue().getAppSecret());
        ArgumentCaptor<ResetIsvMchAppInfoConfigMQ> message = ArgumentCaptor.forClass(ResetIsvMchAppInfoConfigMQ.class);
        verify(mqSender).send(message.capture());
        assertEquals(CURRENT_MCH_NO, message.getValue().getPayload().getMchNo());
        assertEquals(OWN_APP_ID, message.getValue().getPayload().getAppId());
    }

    private void applicationUpdateRequest() {
        JSONObject body = new JSONObject();
        body.put("appId", FOREIGN_APP_ID);
        body.put("mchNo", FOREIGN_MCH_NO);
        body.put("appName", "renamed application");
        body.put("appSecret", "replacement-secret");
        when(requestKitBean.getReqParamJSON()).thenReturn(body);
    }
}
