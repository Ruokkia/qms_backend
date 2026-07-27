package com.kangli.qms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.entity.Supplier;
import com.kangli.qms.mapper.SupplierMapper;
import com.kangli.qms.service.SupplierService;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {
}
