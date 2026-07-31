package com.flora.codec.jsonschema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 校验结果：是否通过 + 错误列表。
 */
public final class ValidationResult {

    private static final ValidationResult VALID = new ValidationResult(List.of());

    private final List<ValidationError> errors;

    private ValidationResult(List<ValidationError> errors) {
        this.errors = errors;
    }

    /** @return 通过的结果（无错误） */
    public static ValidationResult valid() {
        return VALID;
    }

    /** @return 含错误的结果 */
    public static ValidationResult invalid(List<ValidationError> errors) {
        return new ValidationResult(List.copyOf(errors));
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /** @return 不可变错误列表 */
    public List<ValidationError> errors() {
        return errors;
    }

    /** 追加一个错误，返回新结果。 */
    ValidationResult with(ValidationError error) {
        List<ValidationError> merged = new ArrayList<>(errors);
        merged.add(error);
        return new ValidationResult(Collections.unmodifiableList(merged));
    }
}
