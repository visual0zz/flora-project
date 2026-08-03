package com.flora.osmetes;

/**
 * 检查问题的严重级别。
 */
public enum Severity {

    /** 错误：必须修复，构建失败。 */
    ERROR,

    /** 警告：建议修复，默认不阻断构建，但会被报告。 */
    WARNING
}
