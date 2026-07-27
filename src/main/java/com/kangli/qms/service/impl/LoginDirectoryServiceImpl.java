package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangli.qms.entity.SysUser;
import com.kangli.qms.mapper.SysUserMapper;
import com.kangli.qms.service.LoginDirectoryService;
import com.kangli.qms.vo.LoginDirectoryUserVO;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoginDirectoryServiceImpl implements LoginDirectoryService {
    private final SysUserMapper userMapper;
    public LoginDirectoryServiceImpl(SysUserMapper userMapper) { this.userMapper = userMapper; }
    @Override public List<LoginDirectoryUserVO> listEnabledUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().eq(SysUser::getStatus, 1).orderByAsc(SysUser::getPlantCode).orderByAsc(SysUser::getAccount))
                .stream().filter(user -> user.getStatus() != null && user.getStatus() == 1).map(this::toVO).collect(Collectors.toList());
    }
    private LoginDirectoryUserVO toVO(SysUser user) {
        LoginDirectoryUserVO vo = new LoginDirectoryUserVO(); vo.setAccount(user.getAccount()); vo.setRealName(user.getRealName()); vo.setRoleCode(user.getRoleCode()); vo.setPlantCode(user.getPlantCode()); vo.setPlantName(user.getPlantName()); return vo;
    }
}
