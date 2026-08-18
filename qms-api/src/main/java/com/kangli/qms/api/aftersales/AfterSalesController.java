package com.kangli.qms.api.aftersales;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangli.qms.common.*;
import com.kangli.qms.domain.aftersales.entity.*;
import com.kangli.qms.domain.aftersales.mapper.*;
import com.kangli.qms.domain.auth.entity.SysUser;
import com.kangli.qms.domain.auth.mapper.SysUserMapper;
import com.kangli.qms.service.notification.NotificationService;
import com.kangli.qms.service.notification.dto.NotificationCreateDTO;
import com.kangli.qms.domain.exception.entity.ExceptionOrder;
import com.kangli.qms.service.exception.ExceptionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/v1/after-sales")
@Api(tags = "售后工单与客户满意度")
public class AfterSalesController {
    private final AfterSalesWorkOrderMapper orderMapper;
    private final AfterSalesWorkOrderLogMapper logMapper;
    private final CustomerSatisfactionMapper satisfactionMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final ExceptionService exceptionService;
    public AfterSalesController(AfterSalesWorkOrderMapper orderMapper, AfterSalesWorkOrderLogMapper logMapper, CustomerSatisfactionMapper satisfactionMapper,
                                SysUserMapper userMapper, NotificationService notificationService, ExceptionService exceptionService) {
        this.orderMapper = orderMapper; this.logMapper = logMapper; this.satisfactionMapper = satisfactionMapper;
        this.userMapper = userMapper; this.notificationService = notificationService; this.exceptionService = exceptionService;
    }

    @GetMapping @ApiOperation("分页查询售后工单")
    public R<PageResult<AfterSalesWorkOrder>> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String keyword, @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String serviceType) {
        LoginUser user = user();
        LambdaQueryWrapper<AfterSalesWorkOrder> q = new LambdaQueryWrapper<AfterSalesWorkOrder>()
                .eq(AfterSalesWorkOrder::getPlantCode, user.getPlantCode().name())
                .eq(has(status), AfterSalesWorkOrder::getStatus, status)
                .eq(has(serviceType), AfterSalesWorkOrder::getServiceType, serviceType)
                .and(has(keyword), w -> w.like(AfterSalesWorkOrder::getOrderNo, keyword).or().like(AfterSalesWorkOrder::getCustomerName, keyword)
                        .or().like(AfterSalesWorkOrder::getProductName, keyword)).orderByDesc(AfterSalesWorkOrder::getCreatedAt);
        return R.ok(PageResult.of(orderMapper.selectPage(new Page<>(page, size), q)));
    }

    @PostMapping @ApiOperation("创建售后工单")
    public R<AfterSalesWorkOrder> create(@RequestBody AfterSalesWorkOrder order) {
        if (!has(order.getCustomerName()) || !has(order.getServiceType())) throw new BusinessException(ResultCode.BAD_REQUEST, "请填写客户名称和服务类型");
        if (!Arrays.asList("安装", "维修", "咨询", "投诉").contains(order.getServiceType())) throw new BusinessException(ResultCode.BAD_REQUEST, "服务类型不正确");
        LoginUser u = user();
        order.setId(null); order.setOrderNo("AS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
        order.setStatus("新建"); order.setPlantCode(u.getPlantCode().name()); order.setPlantName(u.getPlantCode().getChineseName()); order.setCreatedBy(u.getRealName()); order.setUpdatedBy(u.getRealName());
        orderMapper.insert(order); log(order, null, "新建", "创建工单", u); return R.ok(order, "售后工单已创建");
    }

    @PutMapping("/{id}") @ApiOperation("更新工单基础信息")
    public R<AfterSalesWorkOrder> update(@PathVariable Long id, @RequestBody AfterSalesWorkOrder input) {
        AfterSalesWorkOrder order = require(id); if ("已结案".equals(order.getStatus()) || "取消".equals(order.getStatus())) throw new BusinessException(ResultCode.BAD_REQUEST, "已结束工单不可修改");
        input.setId(id); input.setOrderNo(order.getOrderNo()); input.setPlantCode(order.getPlantCode()); input.setPlantName(order.getPlantName()); input.setStatus(order.getStatus()); input.setUpdatedBy(user().getRealName()); orderMapper.updateById(input);
        return R.ok(orderMapper.selectById(id), "工单已更新");
    }

    @PostMapping("/{id}/transition") @ApiOperation("按规则流转售后工单")
    public R<AfterSalesWorkOrder> transition(@PathVariable Long id, @RequestParam String targetStatus, @RequestParam(required = false) String remark) {
        AfterSalesWorkOrder order = require(id); LoginUser u = user(); String current = order.getStatus();
        if (!allowed(current, targetStatus)) throw new BusinessException(ResultCode.BAD_REQUEST, "工单必须按 新建→分配→处理中→待回访→已结案 顺序流转");
        if ("已结案".equals(targetStatus) && !has(order.getVisitRecord())) throw new BusinessException(ResultCode.BAD_REQUEST, "请先填写回访记录后再结案");
        order.setStatus(targetStatus); order.setUpdatedBy(u.getRealName());
        if ("已结案".equals(targetStatus)) { order.setCloseSignature(u.getRealName()); order.setCloseTime(LocalDateTime.now()); }
        orderMapper.updateById(order); log(order, current, targetStatus, remark, u); return R.ok(orderMapper.selectById(id), "工单状态已更新");
    }

    @GetMapping("/{id}/logs") @ApiOperation("查询工单流转日志")
    public R<List<AfterSalesWorkOrderLog>> logs(@PathVariable Long id) {
        return R.ok(logMapper.selectList(new LambdaQueryWrapper<AfterSalesWorkOrderLog>().eq(AfterSalesWorkOrderLog::getWorkOrderId, id).orderByAsc(AfterSalesWorkOrderLog::getCreatedAt)));
    }

    @PostMapping("/{id}/satisfaction") @ApiOperation("登记客户满意度及低分跟进")
    public R<CustomerSatisfaction> saveSatisfaction(@PathVariable Long id, @RequestBody CustomerSatisfaction satisfaction) {
        AfterSalesWorkOrder order = require(id); if (!"已结案".equals(order.getStatus())) throw new BusinessException(ResultCode.BAD_REQUEST, "仅已结案工单可录入满意度");
        if (satisfaction.getScore() == null || satisfaction.getScore() < 1 || satisfaction.getScore() > 5) throw new BusinessException(ResultCode.BAD_REQUEST, "满意度评分须为1至5分");
        if (satisfactionMapper.selectCount(new LambdaQueryWrapper<CustomerSatisfaction>().eq(CustomerSatisfaction::getWorkOrderId, id)) > 0) throw new BusinessException(ResultCode.BAD_REQUEST, "满意度已提交，不可修改");
        LoginUser u = user(); satisfaction.setId(null); satisfaction.setWorkOrderId(id); satisfaction.setPlantCode(order.getPlantCode()); satisfaction.setPlantName(order.getPlantName()); satisfaction.setCreatedBy(u.getRealName()); satisfaction.setUpdatedBy(u.getRealName());
        if (satisfaction.getScore() <= 3) { if (!has(satisfaction.getResponsibleName())) satisfaction.setResponsibleName(u.getRealName()); if (satisfaction.getFollowUpTime() == null) satisfaction.setFollowUpTime(LocalDateTime.now()); }
        satisfactionMapper.insert(satisfaction);
        if (satisfaction.getScore() <= 3) notifyLowScore(order, satisfaction);
        return R.ok(satisfaction, satisfaction.getScore() <= 3 ? "低满意度已登记并转交负责人跟进" : "满意度已提交");
    }

    @GetMapping("/{id}/satisfaction") @ApiOperation("查询工单满意度")
    public R<CustomerSatisfaction> satisfaction(@PathVariable Long id) { return R.ok(satisfactionMapper.selectOne(new LambdaQueryWrapper<CustomerSatisfaction>().eq(CustomerSatisfaction::getWorkOrderId, id))); }

    @PutMapping("/{id}/satisfaction/follow-up") @ApiOperation("更新低满意度跟进与CAPA关联")
    public R<CustomerSatisfaction> followUp(@PathVariable Long id, @RequestBody CustomerSatisfaction input) {
        require(id); CustomerSatisfaction value = satisfactionMapper.selectOne(new LambdaQueryWrapper<CustomerSatisfaction>().eq(CustomerSatisfaction::getWorkOrderId, id));
        if (value == null || value.getScore() > 3) throw new BusinessException(ResultCode.BAD_REQUEST, "该工单不存在低满意度待跟进记录");
        if (!has(input.getFollowUpResult())) throw new BusinessException(ResultCode.BAD_REQUEST, "请填写跟进结果");
        value.setFollowUpResult(input.getFollowUpResult()); value.setResponsibleName(input.getResponsibleName()); value.setCapaNo(input.getCapaNo()); value.setFollowUpTime(LocalDateTime.now()); value.setUpdatedBy(user().getRealName());
        satisfactionMapper.updateById(value); return R.ok(value, "低满意度跟进已更新");
    }

    @PostMapping("/{id}/capa") @ApiOperation("低满意度工单一键发起CAPA并回写编号")
    public R<Map<String,Object>> createCapa(@PathVariable Long id) {
        AfterSalesWorkOrder workOrder=require(id); CustomerSatisfaction satisfaction=satisfactionMapper.selectOne(new LambdaQueryWrapper<CustomerSatisfaction>().eq(CustomerSatisfaction::getWorkOrderId,id));
        if(satisfaction==null||satisfaction.getScore()>3)throw new BusinessException(ResultCode.BAD_REQUEST,"该工单没有低满意度记录");if(has(satisfaction.getCapaNo()))throw new BusinessException(ResultCode.BAD_REQUEST,"该工单已关联CAPA");
        ExceptionOrder input=new ExceptionOrder();input.setSourceType("客诉");input.setSourceId(id);input.setWorkOrderId(id);input.setCustomerName(workOrder.getCustomerName());input.setComplaintNo(workOrder.getOrderNo());input.setDefectDesc("低满意度工单（"+satisfaction.getScore()+"分）："+(has(satisfaction.getReasonDimension())?satisfaction.getReasonDimension():workOrder.getFaultDescription()));input.setSeverity(satisfaction.getScore()<=2?"严重":"一般");input.setProcessType("CAPA");input.setCapaStatus("待发起");ExceptionOrder created=exceptionService.create(input);satisfaction.setCapaNo(created.getExceptionNo());satisfaction.setUpdatedBy(user().getRealName());satisfactionMapper.updateById(satisfaction);Map<String,Object> result=new LinkedHashMap<>();result.put("exceptionId",created.getId());result.put("capaNo",created.getExceptionNo());return R.ok(result,"CAPA单已创建并回写编号");
    }

    @GetMapping("/stats") @ApiOperation("售后工单与满意度统计")
    public R<Map<String, Object>> stats() {
        String plant = user().getPlantCode().name(); List<AfterSalesWorkOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<AfterSalesWorkOrder>().eq(AfterSalesWorkOrder::getPlantCode, plant));
        List<CustomerSatisfaction> scores = satisfactionMapper.selectList(new LambdaQueryWrapper<CustomerSatisfaction>().eq(CustomerSatisfaction::getPlantCode, plant));
        Map<String, Long> byType = new LinkedHashMap<>(), monthlyTrend = new TreeMap<>(), lowScoreReasons = new LinkedHashMap<>();
        for (AfterSalesWorkOrder order : orders) { byType.put(order.getServiceType(), byType.getOrDefault(order.getServiceType(), 0L) + 1); if(order.getCreatedAt()!=null){String month=order.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));monthlyTrend.put(month,monthlyTrend.getOrDefault(month,0L)+1);} }
        for(CustomerSatisfaction score:scores) if(score.getScore()<=3){String reason=has(score.getReasonDimension())?score.getReasonDimension():"其他";lowScoreReasons.put(reason,lowScoreReasons.getOrDefault(reason,0L)+1);}
        Map<String, Object> result = new LinkedHashMap<>(); result.put("total", orders.size()); result.put("byServiceType", byType); result.put("monthlyTrend",monthlyTrend);result.put("lowScoreReasons",lowScoreReasons); result.put("closed", orders.stream().filter(o -> "已结案".equals(o.getStatus())).count()); result.put("averageScore", scores.isEmpty() ? null : scores.stream().mapToInt(CustomerSatisfaction::getScore).average().orElse(0)); result.put("lowScoreCount", scores.stream().filter(s -> s.getScore() <= 3).count()); return R.ok(result);
    }

    private boolean allowed(String from, String to) { if ("取消".equals(to)) return !"已结案".equals(from); return ("新建".equals(from) && "分配".equals(to)) || ("分配".equals(from) && "处理中".equals(to)) || ("处理中".equals(from) && "待回访".equals(to)) || ("待回访".equals(from) && "已结案".equals(to)); }
    private void log(AfterSalesWorkOrder order, String from, String to, String remark, LoginUser u) { AfterSalesWorkOrderLog entry = new AfterSalesWorkOrderLog(); entry.setWorkOrderId(order.getId()); entry.setBeforeStatus(from); entry.setAfterStatus(to); entry.setRemark(remark); entry.setOperatorName(u.getRealName()); entry.setPlantCode(order.getPlantCode()); logMapper.insert(entry); }
    private void notifyLowScore(AfterSalesWorkOrder order, CustomerSatisfaction satisfaction) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPlantCode, order.getPlantCode()).eq(SysUser::getStatus, (short) 1)
                .eq(has(satisfaction.getResponsibleName()), SysUser::getRealName, satisfaction.getResponsibleName()));
        for (SysUser receiver : users) {
            NotificationCreateDTO dto = new NotificationCreateDTO(); dto.setUserId(receiver.getId()); dto.setType("低满意度预警"); dto.setTitle("客户满意度低分跟进");
            dto.setContent("售后工单 " + order.getOrderNo() + " 的客户满意度为 " + satisfaction.getScore() + " 分，请调查原因并完成跟进。");
            dto.setLevel("严重"); dto.setBusinessType("AFTER_SALES_SATISFACTION"); dto.setBusinessId(order.getId()); dto.setPlantCode(order.getPlantCode()); dto.setCreatedBy(user().getRealName());
            notificationService.createNotification(dto);
        }
    }
    private AfterSalesWorkOrder require(Long id) { AfterSalesWorkOrder value = orderMapper.selectById(id); if (value == null || !value.getPlantCode().equals(user().getPlantCode().name())) throw new BusinessException(ResultCode.NOT_FOUND, "售后工单不存在"); return value; }
    private LoginUser user() { LoginUser u = LoginUserHolder.get(); if (u == null || u.getPlantCode() == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "未获取到登录用户信息"); return u; }
    private boolean has(String value) { return value != null && !value.trim().isEmpty(); }
}
