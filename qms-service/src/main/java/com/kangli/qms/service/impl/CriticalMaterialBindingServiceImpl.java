package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.entity.CriticalMaterialBinding;
import com.kangli.qms.mapper.CriticalMaterialBindingMapper;
import com.kangli.qms.service.CriticalMaterialBindingService;
import org.springframework.stereotype.Service;

@Service
public class CriticalMaterialBindingServiceImpl
        extends ServiceImpl<CriticalMaterialBindingMapper, CriticalMaterialBinding>
        implements CriticalMaterialBindingService {
}
