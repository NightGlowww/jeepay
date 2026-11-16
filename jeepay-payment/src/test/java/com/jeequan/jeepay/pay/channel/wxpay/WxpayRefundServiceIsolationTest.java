package com.jeequan.jeepay.pay.channel.wxpay;

import com.github.binarywang.wxpay.config.WxPayConfig;
import com.github.binarywang.wxpay.service.WxPayService;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.RefundOrder;
import com.jeequan.jeepay.core.model.params.wxpay.WxpayIsvsubMchParams;
import com.jeequan.jeepay.pay.model.MchAppConfigContext;
import com.jeequan.jeepay.pay.model.WxServiceWrapper;
import com.jeequan.jeepay.pay.service.ConfigContextQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WxpayRefundServiceIsolationTest {
    @Test
    void concurrentSubMerchantQueriesKeepRequestIdentityOutOfSharedConfig() throws Exception {
        ConfigContextQueryService configs = mock(ConfigContextQueryService.class);
        WxPayService wx = mock(WxPayService.class);
        WxPayConfig sharedConfig = new WxPayConfig();
        sharedConfig.setSubMchId("original-config");
        when(wx.getConfig()).thenReturn(sharedConfig);
        when(configs.getWxServiceWrapper(any())).thenReturn(new WxServiceWrapper(CS.PAY_IF_VERSION.WX_V3, wx, null));
        for (String id : new String[]{"a", "b"}) {
            WxpayIsvsubMchParams params = new WxpayIsvsubMchParams();
            params.setSubMchId("sub-" + id);
            when(configs.queryIsvsubMchParams("mch-" + id, "app", CS.IF_CODE.WXPAY)).thenReturn(params);
        }
        Set<String> urls = ConcurrentHashMap.newKeySet();
        CyclicBarrier barrier = new CyclicBarrier(2);
        when(wx.getV3(anyString())).thenAnswer(invocation -> {
            urls.add(invocation.getArgument(0));
            barrier.await(5, TimeUnit.SECONDS);
            return "{\"status\":\"PROCESSING\"}";
        });
        WxpayRefundService refunds = new WxpayRefundService();
        ReflectionTestUtils.setField(refunds, "configContextQueryService", configs);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> query(refunds, "a"));
            Future<?> second = executor.submit(() -> query(refunds, "b"));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(Set.of("https://api.mch.weixin.qq.com/v3/refund/domestic/refunds/refund-a?sub_mchid=sub-a",
                "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds/refund-b?sub_mchid=sub-b"), urls);
        assertEquals("original-config", sharedConfig.getSubMchId());
    }

    private void query(WxpayRefundService refunds, String id) {
        RefundOrder order = new RefundOrder();
        order.setRefundOrderId("refund-" + id);
        MchAppConfigContext context = mock(MchAppConfigContext.class);
        when(context.isIsvsubMch()).thenReturn(true);
        when(context.getMchNo()).thenReturn("mch-" + id);
        when(context.getAppId()).thenReturn("app");
        try {
            refunds.query(order, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
