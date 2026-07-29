package com.kangli.qms.service.supplier.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kangli.qms.domain.supplier.entity.Supplier;
import com.kangli.qms.domain.supplier.mapper.SupplierMapper;
import com.kangli.qms.service.supplier.SupplierService;
import org.springframework.stereotype.Service;

@Service
public class SupplierServiceImpl extends ServiceImpl<SupplierMapper, Supplier> implements SupplierService {
}
