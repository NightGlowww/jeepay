package com.jeequan.jeepay.mch.ctrl.sysuser;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jeequan.jeepay.core.constants.ApiCodeEnum;
import com.jeequan.jeepay.core.constants.CS;
import com.jeequan.jeepay.core.entity.SysUser;
import com.jeequan.jeepay.core.exception.BizException;
import com.jeequan.jeepay.core.model.security.JeeUserDetails;
import com.jeequan.jeepay.mch.ctrl.MerchantControllerTestSupport;
import com.jeequan.jeepay.mch.service.AuthService;
import com.jeequan.jeepay.service.impl.SysUserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 在租户校验前不得删除用户或刷新其令牌，不启动数据库或缓存。 */
class SysUserControllerAuthorizationTest extends MerchantControllerTestSupport {
    private static final long TARGET_ID = 1002L;
    private static final long DEFAULT_ADMIN_ID = 1003L;

    @Mock private SysUserService sysUserService;
    @Mock private AuthService authService;
    @InjectMocks private SysUserController controller;

    @BeforeAll
    static void initializeSysUserMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysUser.class);
    }

    @ParameterizedTest
    @CsvSource(value = {"MCH_OTHER,MCH", "MCH_OWN,MGR", "NULL,MCH", "MCH_OWN,NULL"}, nullValues = "NULL")
    void rejectsForeignTenantOrSystemBeforeAnyDeletion(String merchant, String system) {
        SysUser target = user(TARGET_ID, merchant);
        target.setSysType(system);
        when(sysUserService.getById(TARGET_ID)).thenReturn(target);
        // 旧实现可以继续走到删除；新实现应在查询默认管理员之前拒绝。
        lenient().when(sysUserService.getOne(any(Wrapper.class))).thenReturn(user(DEFAULT_ADMIN_ID, CURRENT_MCH_NO));

        BizException error = assertThrows(BizException.class, () -> controller.delete(TARGET_ID));

        assertEquals(ApiCodeEnum.SYS_PERMISSION_ERROR.getCode(), error.getApiRes().getCode());
        verify(sysUserService).getById(TARGET_ID);
        verifyNoMoreInteractions(sysUserService);
        verifyNoInteractions(authService);
    }

    @Test
    void deletesOwnOperatorAndRefreshesOnlyThatUser() {
        SysUser target = user(TARGET_ID, CURRENT_MCH_NO);
        when(sysUserService.getById(TARGET_ID)).thenReturn(target);
        when(sysUserService.getOne(any(Wrapper.class))).thenReturn(user(DEFAULT_ADMIN_ID, CURRENT_MCH_NO));

        assertEquals(ApiCodeEnum.SUCCESS.getCode(), controller.delete(TARGET_ID).getCode());

        var ordered = inOrder(sysUserService, authService);
        ordered.verify(sysUserService).getById(TARGET_ID);
        ordered.verify(sysUserService).getOne(any(Wrapper.class));
        ordered.verify(sysUserService).removeUser(target, CS.SYS_TYPE.MCH);
        ordered.verify(authService).refAuthentication(List.of(TARGET_ID));
        verifyNoMoreInteractions(sysUserService, authService);
    }

    @Test
    void stillRejectsDeletingCurrentUser() {
        when(sysUserService.getById(CURRENT_USER_ID)).thenReturn(user(CURRENT_USER_ID, CURRENT_MCH_NO));

        BizException error = assertThrows(BizException.class, () -> controller.delete(CURRENT_USER_ID));

        assertEquals("系统不允许删除当前登陆用户！", error.getMessage());
        verify(sysUserService).getById(CURRENT_USER_ID);
        verifyNoMoreInteractions(sysUserService);
        verifyNoInteractions(authService);
    }

    @Test
    void stillRejectsDeletingDefaultAdministrator() {
        SysUser defaultAdmin = user(DEFAULT_ADMIN_ID, CURRENT_MCH_NO);
        defaultAdmin.setIsAdmin(CS.YES);
        when(sysUserService.getById(DEFAULT_ADMIN_ID)).thenReturn(defaultAdmin);
        when(sysUserService.getOne(any(Wrapper.class))).thenReturn(defaultAdmin);

        BizException error = assertThrows(BizException.class, () -> controller.delete(DEFAULT_ADMIN_ID));

        assertEquals("系统不允许删除商户默认用户！", error.getMessage());
        verify(sysUserService).getById(DEFAULT_ADMIN_ID);
        verify(sysUserService).getOne(any(Wrapper.class));
        verifyNoMoreInteractions(sysUserService);
        verifyNoInteractions(authService);
    }

    @Test
    void missingUserDoesNotCauseAnyMutation() {
        BizException error = assertThrows(BizException.class, () -> controller.delete(TARGET_ID));

        assertEquals("该操作员不存在！", error.getMessage());
        verify(sysUserService).getById(TARGET_ID);
        verifyNoMoreInteractions(sysUserService);
        verifyNoInteractions(authService);
    }
    @Test
    void updatesOwnOperatorWithoutChangingTenantOrSystem() {
        operatorUpdateRequest();
        SysUser stored = user(TARGET_ID, CURRENT_MCH_NO);
        when(sysUserService.getOne(any(Wrapper.class))).thenReturn(stored);

        assertEquals(ApiCodeEnum.SUCCESS.getCode(), controller.update(TARGET_ID).getCode());

        ArgumentCaptor<SysUser> update = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateSysUser(update.capture());
        assertEquals(TARGET_ID, update.getValue().getSysUserId());
        assertEquals(CURRENT_MCH_NO, update.getValue().getBelongInfoId());
        assertEquals(CS.SYS_TYPE.MCH, update.getValue().getSysType());
        assertEquals("updated operator", update.getValue().getRealname());
        assertEquals(CS.NO, update.getValue().getIsAdmin());
        ArgumentCaptor<LambdaQueryWrapper<SysUser>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sysUserService).getOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("sys_user_id"));
        assertTrue(sql.contains("belong_info_id"));
        assertTrue(sql.contains("sys_type"));
        assertEquals(Set.of(TARGET_ID, CURRENT_MCH_NO, CS.SYS_TYPE.MCH),
                new HashSet<>(query.getValue().getParamNameValuePairs().values()));
        verifyNoInteractions(authService);
    }

    @Test
    void rejectsUnmatchedOperatorBeforeAnyUpdate() {
        operatorUpdateRequest();

        BizException error = assertThrows(BizException.class, () -> controller.update(TARGET_ID));

        assertEquals(ApiCodeEnum.SYS_OPERATION_FAIL_SELETE.getCode(), error.getApiRes().getCode());
        verify(sysUserService).getOne(any(Wrapper.class));
        verifyNoMoreInteractions(sysUserService);
        verifyNoInteractions(authService);
    }

    private void operatorUpdateRequest() {
        JeeUserDetails.getCurrentUserDetails().getSysUser().setIsAdmin(CS.NO);
        JSONObject body = new JSONObject();
        body.put("sysUserId", 999L);
        body.put("belongInfoId", FOREIGN_MCH_NO);
        body.put("sysType", CS.SYS_TYPE.MGR);
        body.put("realname", "updated operator");
        body.put("isAdmin", CS.YES);
        body.put("state", CS.YES);
        body.put("resetPass", false);
        when(requestKitBean.getReqParamJSON()).thenReturn(body);
    }

}
