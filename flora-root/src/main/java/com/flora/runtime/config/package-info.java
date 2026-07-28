/**
 * 日志与运行时配置加载系统。
 * <p>
 * 本包承载从外部配置文件（如 properties）加载并应用日志路由规则的通用加载器，
 * 计划提供与格式无关的中性配置模型，以及可插拔的解析源（Source）。当前为占位包，
 * 具体实现待补充；实现后请在 {@code module-info.java} 中导出本包
 * （{@code exports com.flora.runtime.config;}）。
 */
package com.flora.runtime.config;
