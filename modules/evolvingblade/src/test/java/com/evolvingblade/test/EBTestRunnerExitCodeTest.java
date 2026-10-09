package com.evolvingblade.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 测试运行器退出码判定：确保"什么都没跑到"绝不等于"测试通过"。
 *
 * <p>这是审查明确要求的一条：不能只判断 {@code failureCount > 0}。
 */
class EBTestRunnerExitCodeTest {

    private static int code(long testsFound, long succeeded, long failed, long aborted, long skipped,
            long containersFound, long containersFailed, long containersAborted) {
        return EBTestRunner.exitCode(containersFound, testsFound, succeeded, failed, aborted, skipped,
                containersFailed, containersAborted);
    }

    @Test
    @DisplayName("全部通过：退出码 0")
    void allPass() {
        assertEquals(0, code(48, 48, 0, 0, 0, 8, 0, 0));
    }

    @Test
    @DisplayName("零发现测试：必须失败（空目录 / 选择器写错）")
    void zeroDiscovered() {
        assertEquals(1, code(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(1, code(1, 0, 0, 0, 0, 1, 0, 0));
    }

    @Test
    @DisplayName("有失败用例：退出码 1")
    void failures() {
        assertEquals(1, code(10, 9, 1, 0, 0, 3, 0, 0));
    }

    @Test
    @DisplayName("中止的用例或容器：退出码 1（即使没有 failure）")
    void aborted() {
        assertEquals(1, code(10, 9, 0, 1, 0, 3, 0, 0));
        assertEquals(1, code(10, 9, 0, 0, 0, 3, 0, 1));
        assertEquals(1, code(10, 9, 0, 0, 0, 3, 1, 0));
    }

    @Test
    @DisplayName("全部被跳过：等于没跑，退出码 1")
    void allSkipped() {
        assertEquals(1, code(10, 0, 0, 0, 10, 3, 0, 0));
    }

    @Test
    @DisplayName("没有容器却报告了用例：视为异常，退出码 1")
    void noContainers() {
        assertEquals(1, code(0, 5, 5, 0, 0, 0, 0, 0));
    }
}
