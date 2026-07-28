package com.kangli.qms.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页结果（对齐前端 PageResult<T> 类型）。
 * <p>结构：{ list, total, page, size }</p>
 */
@Data
@AllArgsConstructor
@ApiModel(description = "分页结果")
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "数据列表")
    private List<T> list;

    @ApiModelProperty(value = "总记录数")
    private Long total;

    @ApiModelProperty(value = "当前页码")
    private Long page;

    @ApiModelProperty(value = "每页条数")
    private Long size;

    /**
     * 从 MyBatis-Plus IPage 转换。
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
