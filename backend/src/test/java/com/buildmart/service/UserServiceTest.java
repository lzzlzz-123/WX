package com.buildmart.service;

import com.buildmart.common.AuthException;
import com.buildmart.entity.SysUser;
import com.buildmart.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {
    @Mock
    private SysUserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(userMapper);
    }

    @Test
    void createsUserWhenOpenidDoesNotExist() {
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            SysUser user = invocation.getArgument(0);
            user.setId(42L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        SysUser user = userService.login(" openid-42 ", " union-user ", "张三", "13800000000");

        assertEquals(42L, user.getId());
        assertEquals("openid-42", user.getOpenid());
        assertEquals("union-user", user.getUnionid());
        assertEquals("张三", user.getNickname());
        assertEquals(1, user.getStatus());
    }

    @Test
    void readsWinnerWhenConcurrentInsertHitsUniqueKey() {
        SysUser winner = new SysUser();
        winner.setId(43L);
        winner.setOpenid("openid-43");
        winner.setNickname("已存在");
        winner.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(null, winner);
        doThrow(new DuplicateKeyException("duplicate openid"))
                .when(userMapper).insert(any(SysUser.class));

        SysUser user = userService.login("openid-43", "微信用户", null);

        assertEquals(43L, user.getId());
        verify(userMapper, times(2)).selectOne(any());
    }

    @Test
    void updatesOnlyProfileFieldsProvidedByClient() {
        SysUser existing = new SysUser();
        existing.setId(44L);
        existing.setOpenid("openid-44");
        existing.setNickname("旧昵称");
        existing.setPhone("13800000000");
        existing.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(existing);

        userService.login("openid-44", null, "新昵称", null);

        assertEquals("新昵称", existing.getNickname());
        assertEquals("13800000000", existing.getPhone());
        verify(userMapper).updateById(existing);
    }

    @Test
    void rejectsDisabledUser() {
        SysUser disabled = new SysUser();
        disabled.setId(45L);
        disabled.setOpenid("openid-45");
        disabled.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        AuthException error = assertThrows(AuthException.class,
                () -> userService.login("openid-45", "微信用户", null));

        assertEquals(AuthException.Reason.USER_DISABLED, error.reason());
    }
}
