package com.kangli.qms.service.auth;

import com.kangli.qms.domain.auth.vo.LoginDirectoryUserVO;
import java.util.List;

public interface LoginDirectoryService {
    List<LoginDirectoryUserVO> listEnabledUsers();
}
