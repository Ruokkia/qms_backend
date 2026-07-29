package com.kangli.qms.service.exception.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.exception.entity.VerificationRecord;
import com.kangli.qms.domain.exception.mapper.VerificationRecordMapper;
import com.kangli.qms.service.exception.VerificationRecordService;
import org.springframework.stereotype.Service;

@Service
public class VerificationRecordServiceImpl extends ServiceImpl<VerificationRecordMapper, VerificationRecord> implements VerificationRecordService {
}
