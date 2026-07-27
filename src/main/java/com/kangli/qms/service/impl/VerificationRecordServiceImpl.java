package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.entity.VerificationRecord;
import com.kangli.qms.mapper.VerificationRecordMapper;
import com.kangli.qms.service.VerificationRecordService;
import org.springframework.stereotype.Service;

@Service
public class VerificationRecordServiceImpl extends ServiceImpl<VerificationRecordMapper, VerificationRecord> implements VerificationRecordService {
}
