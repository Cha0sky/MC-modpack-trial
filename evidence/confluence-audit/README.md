# 调查证据说明

对象是当前开发实例的 Confluence 1.2.4 JAR 与其中的 TerraEntity 1.2.1、Confluence Magic Lib 0.0.8。作者项目来源：`https://github.com/Magic-team-jvav/confluence`；JAR 元数据声明 GNU LGPL 3.0。

- `findings.json`：当前对象、原始 SHA-256、关键静态事实、证据文件校验及建议方案；建议尚未实现或作为最终设计批准。
- `class-references.json`：在主 JAR 类常量中查到的相关类型引用，用于定位，不代表完整运行时调用图。
- `bytecode/*.txt`：用本机 JDK 21 的 javap 生成的关键类/方法证据；统一 UTF-8/LF、清理行尾空白。原始反汇编哈希与保存证据哈希分别记录，裁剪的文件仅保留相关方法。
- 配置 TOML：当前已读取文件的快照；没有读取存档实际阶段或世界游戏规则，不能把这些文件当作所有世界运行配置的保证。
- `upstream-commit.json` / `upstream-sources.json`：发布前标记为 1.2.4 的上游提交及 9 个已取得源码的 URL/哈希。未重编上游 JAR 验证二进制等价，调查优先依照本机 JAR。
- `progression-tags.json`：原生 hardmode、召唤物与祭坛工具等标签，存在标签不等于所有来源已经被阶段锁覆盖。

厂商源码与内嵌 JAR 的临时检查副本只在忽略的 `work/confluence-audit`，不作为本项目的新实现。没有启动游戏或改写实例、存档和模组。

报告与新增资料的差异检查通过。原始 `terra_entity-server.toml` 快照第 15 行已有一处注释行尾空白；为了保留快照哈希，本次不格式化该文件。全文差异检查的这项警告及其他校验结果记录于 `validation.json`。
