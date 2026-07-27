package com.kangli.qms.service;

import com.kangli.qms.vo.LoginDirectoryUserVO;
import java.util.List;

public interface LoginDirectoryService {
    List<LoginDirectoryUserVO> listEnabledUsers();
}
