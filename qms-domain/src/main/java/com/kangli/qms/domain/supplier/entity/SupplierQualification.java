package com.kangli.qms.domain.supplier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应商资质证照实体 — 对应 qms.supplier_qualification 表。
 * 一张供应商可有多条资质（营业执照、生产许可证、体系认证等），支持附件与到期预警。
 */
@Data
@TableName(value = "supplier_qualification", schema = "qms")
public class SupplierQualification implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商ID */
    private Long supplierId;
    /** 供应商编码（冗余，便于查询与隔离） */
    private String supplierCode;
    /** 供应商名称（冗余） */
    private String supplierName;

    /** 资质类型：营业执照/生产许可证/ISO9001/ISO13485/医疗器械经营许可证/其他 */
    private String certType;
    /** 证照编号 */
    private String certNo;
    /** 发证机构 */
    private String issuer;
    /** 签发日期 */
    private LocalDate issueDate;
    /** 到期日期（NULL 表示长期有效，不预警） */
    private LocalDate expireDate;
    /** 是否长期有效：0=否 1=是 */
    private Short longTerm;
    /** 附件 URL（逗号分隔） */
    private String fileUrls;
    /** 备注 */
    private String remark;

    // ---- 系统扩展列 ----
    private String plantCode;
    private String plantName;
    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Short isDeleted;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
