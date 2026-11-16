package com.jeequan.jeepay.core.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AmountUtilPercentageFeeTest {

    @Test
    void longAmountStaysExactAndDefaultFeeRoundsHalfUp() {
        // #67 标出的构造调用走 BigDecimal(long)，金额超过 double 的精确整数范围也不能丢失一分。
        assertEquals(9007199254740993L,
                AmountUtil.calPercentageFee(9007199254740993L, BigDecimal.ONE));
        assertEquals(1L, AmountUtil.calPercentageFee(100L, new BigDecimal("0.005")));
        assertEquals(-1L, AmountUtil.calPercentageFee(-100L, new BigDecimal("0.005")));
    }
}
