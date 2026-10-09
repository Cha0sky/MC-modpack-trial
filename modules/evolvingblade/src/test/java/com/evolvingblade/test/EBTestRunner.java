package com.evolvingblade.test;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClasspathRoots;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * JUnit 5 运行器，供 Gradle 的 {@code ebTest} 任务使用。
 *
 * <p><b>为什么需要它：</b>Gradle 的 {@code test} 任务通过 worker 进程执行，worker 会把 classpath
 * 写进一个 UTF-8 的 {@code @argfile}；Windows 上的 JVM 却按<b>平台 ANSI 代码页</b>解析该 argfile。
 * 项目路径一旦包含非 ASCII 字符（本机为 {@code D:\PCL 正式版 2.10.6 (1)\...}），classpath 就会被
 * 解码坏，直接报
 * {@code ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain}。
 *
 * <p>{@code JavaExec} 不使用 argfile（classpath 直接内联在命令行里），因此本运行器
 * 在任意项目路径下都能跑通同一套 JUnit 测试。
 *
 * <p><b>退出码语义（严格）：</b>只要出现以下任一情况即以非零码退出——
 * 一个测试都没发现、全部用例都被跳过/中止、有用例失败、有容器失败或中止。
 * "任务跑完了"本身不构成成功。
 *
 * <p>用法：{@code java ... EBTestRunner <测试类目录> <报告目录>}
 */
public final class EBTestRunner {

    private EBTestRunner() {}

    /** 记录每个用例的最终状态，用于生成可人工核对的报告。 */
    private static final class RecordingListener implements TestExecutionListener {
        private final List<String> lines = new ArrayList<>();

        @Override
        public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
            if (!identifier.isTest()) {
                return;
            }
            String status = switch (result.getStatus()) {
                case SUCCESSFUL -> "PASS";
                case ABORTED -> "ABORTED";
                case FAILED -> "FAIL";
            };
            lines.add(status + " " + identifier.getUniqueId());
            if (result.getThrowable().isPresent()) {
                Throwable throwable = result.getThrowable().get();
                lines.add("      " + throwable.getClass().getName() + ": " + throwable.getMessage());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path testClasses = Path.of(args.length > 0 ? args[0] : "build/classes/java/test");
        Path reportDir = Path.of(args.length > 1 ? args[1] : "build/test-results/ebTest");
        Files.createDirectories(reportDir);

        LauncherDiscoveryRequest discovery = request()
                .selectors(selectClasspathRoots(Set.of(testClasses.toAbsolutePath())))
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        RecordingListener recordingListener = new RecordingListener();
        launcher.execute(discovery, summaryListener, recordingListener);
        TestExecutionSummary summary = summaryListener.getSummary();

        try (BufferedWriter writer = Files.newBufferedWriter(reportDir.resolve("ebTest-summary.txt"),
                StandardCharsets.UTF_8);
                PrintWriter out = new PrintWriter(writer)) {
            summary.printTo(out);
            summary.printFailuresTo(out, 60);
            recordingListener.lines.forEach(out::println);
        }
        writeXml(reportDir.resolve("ebTest-results.xml"), summary, recordingListener.lines);

        PrintWriter console = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        summary.printTo(console);
        summary.printFailuresTo(console, 60);
        int exitCode = exitCode(summary);
        console.println("EBTEST tests=" + summary.getTestsFoundCount()
                + " succeeded=" + summary.getTestsSucceededCount()
                + " failed=" + summary.getTestsFailedCount()
                + " aborted=" + summary.getTestsAbortedCount()
                + " skipped=" + summary.getTestsSkippedCount()
                + " containersFailed=" + summary.getContainersFailedCount()
                + " containersAborted=" + summary.getContainersAbortedCount()
                + " -> exitCode=" + exitCode);
        console.flush();

        System.exit(exitCode);
    }

    /** 从 JUnit 汇总结果计算退出码。 */
    public static int exitCode(TestExecutionSummary summary) {
        return exitCode(
                summary.getContainersFoundCount(),
                summary.getTestsFoundCount(),
                summary.getTestsSucceededCount(),
                summary.getTestsFailedCount(),
                summary.getTestsAbortedCount(),
                summary.getTestsSkippedCount(),
                summary.getContainersFailedCount(),
                summary.getContainersAbortedCount());
    }

    /**
     * 纯函数形式的退出码判定，便于单测覆盖"零发现 / 全部中止 / 全部跳过 / 有失败"等边界。
     *
     * @return 0 表示这套测试可以被视为成功；否则为 1
     */
    public static int exitCode(long containersFound, long testsFound, long testsSucceeded, long testsFailed,
            long testsAborted, long testsSkipped, long containersFailed, long containersAborted) {
        if (testsFound <= 0L) {
            return 1; // 一个测试都没发现：绝不能算成功
        }
        if (containersFound <= 0L) {
            return 1;
        }
        if (testsSucceeded <= 0L) {
            return 1; // 全部被跳过/中止，等于没跑
        }
        if (testsFailed > 0L || testsAborted > 0L) {
            return 1;
        }
        if (containersFailed > 0L || containersAborted > 0L) {
            return 1;
        }
        return 0;
    }

    private static void writeXml(Path file, TestExecutionSummary summary, List<String> recordedLines) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"evolvingblade\" tests=\"").append(summary.getTestsFoundCount())
                .append("\" failures=\"").append(summary.getTestsFailedCount())
                .append("\" skipped=\"").append(summary.getTestsSkippedCount())
                .append("\" aborted=\"").append(summary.getTestsAbortedCount()).append("\">\n");
        for (String line : recordedLines) {
            if (line.startsWith("PASS ")) {
                xml.append("  <testcase name=\"").append(escape(line.substring(5))).append("\"/>\n");
            } else if (line.startsWith("FAIL ") || line.startsWith("ABORTED ")) {
                int space = line.indexOf(' ');
                xml.append("  <testcase name=\"").append(escape(line.substring(space + 1)))
                        .append("\">\n    <failure/>\n  </testcase>\n");
            }
        }
        xml.append("</testsuite>\n");
        Files.writeString(file, xml.toString(), StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
