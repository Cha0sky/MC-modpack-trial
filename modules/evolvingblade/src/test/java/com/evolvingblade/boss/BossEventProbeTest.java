package com.evolvingblade.boss;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.evolvingblade.util.BoundedLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 反射探测的纯 JVM 测试：使用测试自带的标记类型与类继承结构，
 * 不需要真实的 Minecraft 实体，也能验证"继承链扫描 / 空值跳过 / 只缓存访问器 / 类名兜底"。
 */
class BossEventProbeTest {

    /** 模拟 ServerBossEvent 的标记类型。 */
    static class Marker {
    }

    /** Marker 的子类型：字段声明为子类型时，按父类型查询也必须命中。 */
    static final class SubMarker extends Marker {
    }

    /** 与 Marker 无关的类型。 */
    static final class OtherMarker {
    }

    static class HoldsSubMarker {
        @SuppressWarnings("unused")
        private final SubMarker marker = new SubMarker();
    }

    static class NoMarker {
        @SuppressWarnings("unused")
        private final Marker marker = null;
    }

    static class WithMarker {
        @SuppressWarnings("unused")
        private final Marker marker = new Marker();
    }

    static class InheritsMarker extends WithMarker {
    }

    static class InheritsNullMarker extends NoMarker {
        @SuppressWarnings("unused")
        private final String other = "x";
    }

    /** 类名含 Boss，模拟 WitherBoss 这类命名。 */
    static class FakeBossMob extends NoMarker {
    }

    @BeforeEach
    void reset() {
        BossEventProbe.clearCache();
    }

    @Test
    @DisplayName("非空字段判定：直接字段、继承字段、空字段")
    void fieldDetection() {
        BoundedLog log = new BoundedLog(16, message -> { });
        assertTrue(BossEventProbe.hasNonNullFieldOfType(new WithMarker(), Marker.class, 8, log));
        assertTrue(BossEventProbe.hasNonNullFieldOfType(new InheritsMarker(), Marker.class, 8, log),
                "必须能看到父类的字段");
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new NoMarker(), Marker.class, 8, log));
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new InheritsNullMarker(), Marker.class, 8, log));
        assertFalse(BossEventProbe.hasNonNullFieldOfType(null, Marker.class, 8, log));
        assertEquals(0, log.suppressedCount());
    }

    @Test
    @DisplayName("类型过滤：不相关字段不会被误判")
    void typeFiltering() {
        BoundedLog log = new BoundedLog(16, message -> { });
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new WithMarker(), String.class, 8, log));
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new InheritsNullMarker(), Marker.class, 8, log));
    }

    @Test
    @DisplayName("深度限制：maxDepth=0 时不扫描任何类")
    void depthLimit() {
        BoundedLog log = new BoundedLog(16, message -> { });
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new InheritsMarker(), Marker.class, 0, log));
    }

    @Test
    @DisplayName("只缓存访问器：同一 (类,字段类型,深度) 只解析一次")
    void accessorCache() {
        BoundedLog log = new BoundedLog(16, message -> { });
        List<Field> first = BossEventProbe.accessorsFor(WithMarker.class, Marker.class, 8, log);
        List<Field> second = BossEventProbe.accessorsFor(WithMarker.class, Marker.class, 8, log);
        assertEquals(first, second);
        assertEquals(first, BossEventProbe.accessorsFor(WithMarker.class, Marker.class, 8, log));
        assertTrue(BossEventProbe.cachedClassCount() >= 1);
    }

    @Test
    @DisplayName("缓存键包含深度：先浅后深不会命中不完整的结果")
    void cacheIsDepthSensitive() {
        BoundedLog log = new BoundedLog(16, message -> { });
        // 深度 0：什么都不扫
        List<Field> shallow = BossEventProbe.accessorsFor(InheritsMarker.class, Marker.class, 0, log);
        assertTrue(shallow.isEmpty(), "深度 0 不应发现任何字段");
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new InheritsMarker(), Marker.class, 0, log));

        // 深度 8：必须发现父类字段（若缓存键忽略深度，这里会错误地返回空）
        List<Field> deep = BossEventProbe.accessorsFor(InheritsMarker.class, Marker.class, 8, log);
        assertFalse(deep.isEmpty(), "深度 8 必须发现继承链上的字段");
        assertTrue(BossEventProbe.hasNonNullFieldOfType(new InheritsMarker(), Marker.class, 8, log));

        // 深度 1：只扫 InheritsMarker 自己（没有声明 Marker 字段）
        List<Field> one = BossEventProbe.accessorsFor(InheritsMarker.class, Marker.class, 1, log);
        assertTrue(one.isEmpty(), "深度 1 不应看到父类字段");

        // 反向顺序（先深后浅）也必须各自正确
        BossEventProbe.clearCache();
        assertFalse(BossEventProbe.accessorsFor(InheritsMarker.class, Marker.class, 8, log).isEmpty());
        assertTrue(BossEventProbe.accessorsFor(InheritsMarker.class, Marker.class, 1, log).isEmpty());
    }

    @Test
    @DisplayName("字段类型可赋值即可命中：声明为子类型的字段也能被父类型查询发现")
    void subclassFieldIsAssignable() {
        BoundedLog log = new BoundedLog(16, message -> { });
        assertTrue(BossEventProbe.hasNonNullFieldOfType(new HoldsSubMarker(), Marker.class, 8, log),
                "字段声明为 Marker 的子类型时必须命中");
        assertFalse(BossEventProbe.hasNonNullFieldOfType(new HoldsSubMarker(), OtherMarker.class, 8, log),
                "不相关的类型不得误判");
    }

    @Test
    @DisplayName("类名兜底：继承链上任意一层包含 Boss 即命中（大小写不敏感）")
    void classNameFallback() {
        assertTrue(BossEventProbe.classChainNameContains(FakeBossMob.class, "boss", 8));
        assertTrue(BossEventProbe.classChainNameContains(FakeBossMob.class, "BoSs", 8));
        assertFalse(BossEventProbe.classChainNameContains(WithMarker.class, "boss", 8));
        assertFalse(BossEventProbe.classChainNameContains(null, "boss", 8));
        assertFalse(BossEventProbe.classChainNameContains(WithMarker.class, "", 8));
    }

    @Test
    @DisplayName("有界日志：访问器解析失败时不会刷屏（这里验证上限行为本身）")
    void boundedWarnings() {
        List<String> messages = new ArrayList<>();
        BoundedLog log = new BoundedLog(1, messages::add);
        assertTrue(log.log("one"));
        assertFalse(log.log("two"));
        assertEquals(1, messages.size());
    }
}
