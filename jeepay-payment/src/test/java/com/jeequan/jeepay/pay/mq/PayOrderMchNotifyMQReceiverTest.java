package com.jeequan.jeepay.pay.mq;

import com.jeequan.jeepay.components.mq.model.PayOrderMchNotifyMQ;
import com.jeequan.jeepay.components.mq.vender.IMQSender;
import com.jeequan.jeepay.core.entity.MchNotifyRecord;
import com.jeequan.jeepay.core.model.DBApplicationConfig;
import com.jeequan.jeepay.pay.util.OutboundHttpClient;
import com.jeequan.jeepay.service.impl.MchNotifyRecordService;
import com.jeequan.jeepay.service.impl.PayOrderService;
import com.jeequan.jeepay.service.impl.SysConfigService;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayOrderMchNotifyMQReceiverTest {

    @Test
    void blocksPersistedTransferPaymentAndRefundUrlsOnEveryRetry() throws Exception {
        for (byte orderType : new byte[]{MchNotifyRecord.TYPE_TRANSFER_ORDER, MchNotifyRecord.TYPE_PAY_ORDER, MchNotifyRecord.TYPE_REFUND_ORDER}) {
            PayOrderMchNotifyMQReceiver receiver = new PayOrderMchNotifyMQReceiver();
            MchNotifyRecordService records = mock(MchNotifyRecordService.class);
            IMQSender sender = mock(IMQSender.class);
            SysConfigService configs = mock(SysConfigService.class);
            PrivateDestinationClient http = new PrivateDestinationClient();
            when(configs.getDBApplicationConfig()).thenReturn(new DBApplicationConfig());
            MchNotifyRecord record = new MchNotifyRecord().setNotifyId(7L).setOrderId("order-test").setOrderType(orderType)
                    .setNotifyUrl("http://127.0.0.1/internal?sign=test").setState(MchNotifyRecord.STATE_ING).setNotifyCountLimit(6);
            when(records.getById(7L)).thenReturn(record);
            ReflectionTestUtils.setField(receiver, "mchNotifyRecordService", records);
            ReflectionTestUtils.setField(receiver, "payOrderService", mock(PayOrderService.class));
            ReflectionTestUtils.setField(receiver, "mqSender", sender);
            ReflectionTestUtils.setField(receiver, "sysConfigService", configs);
            ReflectionTestUtils.setField(receiver, "outboundHttpClient", http);
            record.setNotifyCount(0);
            receiver.receive(new PayOrderMchNotifyMQ.MsgPayload(7L));
            record.setNotifyCount(1);
            receiver.receive(new PayOrderMchNotifyMQ.MsgPayload(7L));
            verify(records, times(2)).updateNotifyResult(eq(7L), eq(MchNotifyRecord.STATE_ING), eq("通知连接异常"));
            verify(sender).send(any(PayOrderMchNotifyMQ.class), eq(30));
            verify(sender).send(any(PayOrderMchNotifyMQ.class), eq(60));
            assertEquals(2, http.lookups);
        }
    }

    static class PrivateDestinationClient extends OutboundHttpClient {
        int lookups;
        @Override protected InetAddress[] resolve(String host) throws UnknownHostException {
            lookups++;
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{127, 0, 0, 1})};
        }
        @Override protected CloseableHttpClient createClient(DnsResolver resolver, int timeout) {
            throw new AssertionError("内网 URL 不得到达 HTTP transport");
        }
    }
}
