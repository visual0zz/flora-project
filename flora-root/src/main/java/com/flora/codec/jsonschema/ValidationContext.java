package com.flora.codec.jsonschema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 递归校验上下文。
 * <p>持有共享的可变错误列表与求值状态；instancePath/schemaPath 为当前校验位置
 * （JSON Pointer 形式）。组合关键字（anyOf/oneOf/if）通过错误计数快照回滚失败分支。</p>
 */
public final class ValidationContext {

    public final SchemaRegistry registry;
    public final String instancePath;
    public final String schemaPath;
    private final List<ValidationError> errors;
    public final EvaluationState evaluation;
    private final Set<String> activeRefs;

    public ValidationContext(SchemaRegistry registry) {
        this(registry, "", "#", new ArrayList<>(), new EvaluationState(), new HashSet<>());
    }

    private ValidationContext(SchemaRegistry registry,
                              String instancePath,
                              String schemaPath,
                              List<ValidationError> errors,
                              EvaluationState evaluation,
                              Set<String> activeRefs) {
        this.registry = registry;
        this.instancePath = instancePath;
        this.schemaPath = schemaPath;
        this.errors = errors;
        this.evaluation = evaluation;
        this.activeRefs = activeRefs;
    }

    public void addError(String keyword, String message) {
        errors.add(new ValidationError(instancePath, schemaPath, keyword, message));
    }

    /** 子上下文：追加对象属性段（实例侧）与 schema 段。 */
    public ValidationContext childProperty(String propertyName, String schemaSegment) {
        return new ValidationContext(registry,
                instancePath + "/" + escapePointer(propertyName),
                schemaPath + "/" + escapePointer(schemaSegment),
                errors, evaluation, activeRefs);
    }

    /** 子上下文：追加数组索引段。 */
    public ValidationContext childIndex(int index, String schemaSegment) {
        return new ValidationContext(registry,
                instancePath + "/" + index,
                schemaPath + "/" + escapePointer(schemaSegment),
                errors, evaluation, activeRefs);
    }

    /** 当前错误数（供 branch 尝试回滚）。 */
    public int errorCount() {
        return errors.size();
    }

    /** 分支尝试上下文：errors/activeRefs 共享，evaluation 独立（供 anyOf/oneOf/not/if）。 */
    public ValidationContext forBranch() {
        return new ValidationContext(registry, instancePath, schemaPath, errors, new EvaluationState(), activeRefs);
    }

    /** 回滚到指定错误数（丢弃失败分支的错误）。 */
    public void truncateErrors(int count) {
        while (errors.size() > count) {
            errors.remove(errors.size() - 1);
        }
    }

    public List<ValidationError> snapshotErrors() {
        return new ArrayList<>(errors);
    }

    // ── $ref 循环引用防护 ──

    public boolean enterRef(String ref) {
        return activeRefs.add(ref);
    }

    public void leaveRef(String ref) {
        activeRefs.remove(ref);
    }

    public boolean isRefActive(String ref) {
        return activeRefs.contains(ref);
    }

    private static String escapePointer(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
