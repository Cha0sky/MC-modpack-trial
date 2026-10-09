package com.evolvingblade.boss;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.evolvingblade.util.BoundedLog;

/**
 * 反射探测：在实体的继承链里寻找"持有某类型字段且值非空"的证据（用于 ServerBossEvent 兜底判定），
 * 以及"继承链里是否有类名包含某关键字"的判定。
 *
 * <p>本类<b>只缓存反射访问器（Field 列表）</b>，不缓存实体生命值或任何每次调用都会变化的分类结果，
 * 因此不会把一次判定结果错误地粘到另一个实体上。
 *
 * <p>设计成对字段类型开放（{@code Class<?> fieldType}），所以纯 JVM 单元测试可以用自定义类覆盖，
 * 不需要真的构造 Minecraft 实体。
 */
public final class BossEventProbe {

    /** 默认最多向上遍历 8 层父类，足够覆盖原版与绝大多数模组 Boss。 */
    public static final int DEFAULT_MAX_DEPTH = 8;

    private static final Map<CacheKey, List<Field>> ACCESSOR_CACHE = new ConcurrentHashMap<>();

    private BossEventProbe() {}

    private record CacheKey(Class<?> owner, Class<?> fieldType, int maxDepth) {}

    /**
     * 目标对象（或其继承链）中是否存在该类型的非空字段。
     *
     * <p>访问失败的字段会被安全跳过，并通过 {@code log} 做有界记录。
     */
    public static boolean hasNonNullFieldOfType(Object target, Class<?> fieldType, int maxDepth, BoundedLog log) {
        if (target == null || fieldType == null) {
            return false;
        }
        for (Field field : accessorsFor(target.getClass(), fieldType, maxDepth, log)) {
            try {
                Object value = field.get(target);
                if (value != null) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                // 只吞掉"反射访问失败"这一类异常；VM 级致命错误（Error）必须继续向上传播。
                if (log != null) {
                    log.log("Skipping inaccessible field " + field.getDeclaringClass().getName() + "#" + field.getName()
                            + ": " + exception.getClass().getSimpleName());
                }
            }
        }
        return false;
    }

    /**
     * 取得（并缓存）目标类继承链上所有该类型的可读字段。
     *
     * <p>缓存键包含 {@code maxDepth}：先用浅深度、后用深深度调用时，不会命中不完整的缓存结果。
     */
    public static List<Field> accessorsFor(Class<?> owner, Class<?> fieldType, int maxDepth, BoundedLog log) {
        if (owner == null || fieldType == null) {
            return List.of();
        }
        CacheKey key = new CacheKey(owner, fieldType, maxDepth);
        List<Field> cached = ACCESSOR_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<Field> discovered = new ArrayList<>();
        Class<?> current = owner;
        int depth = 0;
        while (current != null && current != Object.class && depth < maxDepth) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (!fieldType.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    discovered.add(field);
                } catch (RuntimeException exception) {
                    // InaccessibleObjectException / SecurityException 等"访问失败"：跳过并记录。
                    if (log != null) {
                        log.log("Cannot access field " + current.getName() + "#" + field.getName() + ": "
                                + exception.getClass().getSimpleName());
                    }
                }
            }
            current = current.getSuperclass();
            depth++;
        }
        List<Field> result = List.copyOf(discovered);
        ACCESSOR_CACHE.put(key, result);
        return result;
    }

    /**
     * 继承链上是否有类名（小写）包含 {@code needle}。用于 SPEC 的"类名包含 Boss"兜底。
     */
    public static boolean classChainNameContains(Class<?> owner, String needle, int maxDepth) {
        if (owner == null || needle == null || needle.isEmpty()) {
            return false;
        }
        String normalized = needle.toLowerCase(Locale.ROOT);
        Class<?> current = owner;
        int depth = 0;
        while (current != null && current != Object.class && depth < maxDepth) {
            if (current.getSimpleName().toLowerCase(Locale.ROOT).contains(normalized)) {
                return true;
            }
            current = current.getSuperclass();
            depth++;
        }
        return false;
    }

    /** 仅供测试：清空访问器缓存。 */
    public static void clearCache() {
        ACCESSOR_CACHE.clear();
    }

    /** 仅供诊断：当前缓存的类数量。 */
    public static int cachedClassCount() {
        return ACCESSOR_CACHE.size();
    }
}
