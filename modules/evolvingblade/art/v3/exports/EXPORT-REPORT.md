# v3 像素材质导出报告（tools/ExportSwordArtworkV3.java）

| 参数 | 值 |
|---|---|
| 逻辑分辨率 | 32×32 |
| alpha 硬阈值 | 128（输出仅 0/255） |
| 颜色上限 | 12 |
| 帧数 | stage0/1=1，stage2..4=6，stage5..7=8 |
| frametime | 5 tick，interpolate=false |
| 动区起点 u | 0.42（沿护手→剑尖对角轴，腐蚀 1px 后再取该段） |

| 阶段 | 帧数 | 调色板色数 | 动区像素 | 静态像素 | 输出文件 | 输出字节 | alpha 掩码 SHA-256 |
|---|---|---|---|---|---|---|---|
| 0 | 1 | 7 | 65 | 959 | `star_eater_stage0.png` | 264 | `b4365b31777b242505c7aa496866c2ccdeffd3dcb3477aa771c119291a19bbf6` |
| 1 | 1 | 10 | 65 | 959 | `star_eater_stage1.png` | 276 | `b4365b31777b242505c7aa496866c2ccdeffd3dcb3477aa771c119291a19bbf6` |
| 2 | 6 | 12 | 65 | 959 | `star_eater_stage2.png` + `.mcmeta | 693 | `b4365b31777b242505c7aa496866c2ccdeffd3dcb3477aa771c119291a19bbf6` |
| 3 | 6 | 10 | 65 | 959 | `star_eater_stage3.png` + `.mcmeta | 613 | `f1364d5b29f26abf55e90724f0df58677382bd7621078c9decdad5d1f13c4f80` |
| 4 | 6 | 12 | 66 | 958 | `star_eater_stage4.png` + `.mcmeta | 670 | `c7a506ddba95ec83e8401136a6506304e8f7cf5d21852dfd9a21d7b1a6743a1f` |
| 5 | 8 | 12 | 66 | 958 | `star_eater_stage5.png` + `.mcmeta | 825 | `50e3cffc3487ba14fae24ec17b47e367d4c2cfd96d71565498728e11e2f59320` |
| 6 | 8 | 12 | 64 | 960 | `star_eater_stage6.png` + `.mcmeta | 812 | `620acba7de01c42fde47d16db913baf79a3042ff915bcabd7e544283bd4b5095` |
| 7 | 8 | 11 | 65 | 959 | `star_eater_stage7.png` + `.mcmeta | 745 | `b4365b31777b242505c7aa496866c2ccdeffd3dcb3477aa771c119291a19bbf6` |

## 预览文件

- `art/v3/exports/contact-sheet.png`：八阶段<b>单帧横排</b>，每张 4×，中灰棋盘底。
- `art/v3/exports/animation-frames.png`：<b>可查看的帧序列</b>，行=阶段、列=帧，每张 2×；静态阶段只有一列。
- `art/v3/exports/blade-mask.png`：动区可视化（青=会流动的剑身内部，灰=逐帧固定的边缘/护手/握柄）。
- `art/v3/exports/star_eater_stage0..7.png`：与工程贴图同内容副本（动画阶段是纵向帧条）。

> 每阶段 alpha 掩码在所有帧中完全一致；动区之外像素逐帧相同。
