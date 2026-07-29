package com.kangli.qms.service.auth;

import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.service.auth.impl.LoginDirectoryServiceImpl;
import com.kangli.qms.domain.auth.vo.LoginDirectoryUserVO;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class LoginDirectoryServiceTest {
    @Test
    void exposesOnlyEnabledUsersAndNeverPasswordHashes() {
        SysUser enabled = new SysUser(); enabled.setAccount("sz_qe01"); enabled.setRealName("赵六"); enabled.setRoleCode("R04"); enabled.setPlantCode("SZ"); enabled.setPlantName("深圳"); enabled.setStatus((short) 1); enabled.setPasswordHash("secret");
        SysUser disabled = new SysUser(); disabled.setAccount("former_user"); disabled.setStatus((short) 0);
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(enabled, disabled));

        LoginDirectoryUserVO user = new LoginDirectoryServiceImpl(mapper).listEnabledUsers().get(0);

        assertEquals("sz_qe01", user.getAccount());
        assertEquals("R04", user.getRoleCode());
        assertEquals(1, new LoginDirectoryServiceImpl(mapper).listEnabledUsers().size());
    }
}
