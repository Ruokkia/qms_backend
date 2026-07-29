package com.kangli.qms.service.incoming.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.incoming.entity.CriticalMaterialBinding;
import com.kangli.qms.domain.incoming.mapper.CriticalMaterialBindingMapper;
import com.kangli.qms.service.incoming.CriticalMaterialBindingService;
import org.springframework.stereotype.Service;

@Service
public class CriticalMaterialBindingServiceImpl
        extends ServiceImpl<CriticalMaterialBindingMapper, CriticalMaterialBinding>
        implements CriticalMaterialBindingService {
}
