package com.flora.ai.access;

/** 访问策略声明。 */
public record AccessPolicy(String pathPattern, AccessLevel level, String reason) {}
