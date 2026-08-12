package com.kangli.qms.service.finishedgoods;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kangli.qms.common.LoginUser;
import com.kangli.qms.common.PageResult;
import com.kangli.qms.domain.finishedgoods.entity.FinishedGoodsInspection;
import com.kangli.qms.service.finishedgoods.dto.FinishedGoodsInspectionResponse;

public interface FinishedGoodsInspectionService extends IService<FinishedGoodsInspection> {

    FinishedGoodsInspectionResponse detail(Long id);

    FinishedGoodsInspectionResponse getByBarcode(String barcode);

    PageResult<FinishedGoodsInspectionResponse> page(Page<FinishedGoodsInspection> page,
                                                     LambdaQueryWrapper<FinishedGoodsInspection> wrapper);

    FinishedGoodsInspectionResponse create(FinishedGoodsInspection record, LoginUser loginUser);

    FinishedGoodsInspectionResponse update(Long id, FinishedGoodsInspection record, LoginUser loginUser);

    void delete(Long id);
}
