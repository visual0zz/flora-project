package com.flora.ramet.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖解析产物 {@link Template} 的构造与访问分支：
 * <ul>
 *   <li>正常解析：持有节点列表与（可选）元数据</li>
 *   <li>非法参数：{@code null} 节点列表被构造器拒绝</li>
 * </ul>
 */
class TemplateTest {

    @Test
    void holdsNodesAfterParse() {
        Template t = Template.parse("hello");
        assertNotNull(t.nodes());
    }

    @Test
    void nullNodesRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Template("", null, null));
    }
}
