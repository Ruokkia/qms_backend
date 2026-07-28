package com.kangli.qms.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 面向用户公开展示的来料异常规则目录。
 */
@Data
public class QualityRuleCatalogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String version;
    private String dataSource;
    private String repeatKey;
    private List<RuleItem> severityRules = new ArrayList<>();
    private List<RuleItem> notificationRules = new ArrayList<>();
    private List<RuleItem> escalationRules = new ArrayList<>();
    private List<String> handlingMethods = new ArrayList<>();
    private List<String> generalMeasures = new ArrayList<>();
    private List<String> severeMeasures = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuleItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String title;
        private String condition;
        private String result;
        private String systemAction;
    }
}
