package com.flora.mock.jsonschema;

/**
 * 生成上下文：随机源、配置、当前深度。
 */
final class GenerationContext {

    private final RandomSupport random;
    private final GenerationConfig config;
    private final int depth;

    GenerationContext(RandomSupport random, GenerationConfig config) {
        this(random, config, 0);
    }

    private GenerationContext(RandomSupport random, GenerationConfig config, int depth) {
        this.random = random;
        this.config = config;
        this.depth = depth;
    }

    GenerationContext deeper() {
        return new GenerationContext(random, config, depth + 1);
    }

    RandomSupport random() {
        return random;
    }

    GenerationConfig config() {
        return config;
    }

    int depth() {
        return depth;
    }
}
