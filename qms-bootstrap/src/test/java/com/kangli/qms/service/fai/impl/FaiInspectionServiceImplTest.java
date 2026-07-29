package com.kangli.qms.service.fai.impl;

import com.kangli.qms.common.BusinessException;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.service.fai.dto.FaiSignatureRequest;
import com.kangli.qms.domain.fai.entity.FaiInspectionRecord;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.fai.mapper.FaiChangeTriggerMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionRecordMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardItemMapper;
import com.kangli.qms.domain.fai.mapper.FaiInspectionStandardMapper;
import com.kangli.qms.domain.fai.mapper.FaiSignatureMapper;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.service.exception.ExceptionService;
import com.kangli.qms.service.fai.FaiStandardService;
import com.kangli.qms.service.spc.SpcSubgroupService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FaiInspectionServiceImplTest {

    @Test
    void signature_shouldRejectIncorrectCurrentUserPassword() {
        FaiInspectionRecordMapper recordMapper = mock(FaiInspectionRecordMapper.class);
        FaiInspectionRecord record = new FaiInspectionRecord();
        record.setId(101L);
        record.setInspectionResult("合格");
        when(recordMapper.selectById(101L)).thenReturn(record);
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser signer = new SysUser();
        signer.setId(9L);
        signer.setRealName("测试员");
        signer.setStatus((short) 1);
        signer.setPasswordHash(new BCryptPasswordEncoder().encode("correct-password"));
        when(userMapper.selectById(9L)).thenReturn(signer);

        FaiInspectionServiceImpl service = new FaiInspectionServiceImpl(
                recordMapper,
                mock(FaiInspectionItemMapper.class),
                mock(FaiChangeTriggerMapper.class),
                mock(FaiInspectionStandardMapper.class),
                mock(FaiInspectionStandardItemMapper.class),
                mock(FaiSignatureMapper.class),
                mock(FaiStandardService.class),
                mock(SpcSubgroupService.class),
                userMapper);

        FaiSignatureRequest request = new FaiSignatureRequest();
        request.setFaiRecordId(101L);
        request.setSignerId("9");
        request.setSignType("检验签");
        request.setSignReason("检验完成");
        request.setPassword("1");

        LoginUser loginUser = LoginUser.builder().userId(9L).realName("测试员").build();

        assertThrows(BusinessException.class, () -> service.signature(request, loginUser));
    }
}
