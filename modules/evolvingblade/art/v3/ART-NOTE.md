# 32像素星砂金属

绘图使用内置 image_gen，透明母版，剑尖朝右上。统一粗像素、短直护手、普通握柄、深色剑心；银青切刃，极少青紫星砂。没有王冠、镂空、宝石、复杂翅膀，也不复刻其他模组武器。阶段7母版 stage7.png 已放入本目录 masters；Codex正在分别生成早期阶段同轮廓材质变体。

母版1254px是工具的放大展示格式；最终游戏必须32px每帧，不允许把大图直接接入128px。DSH导出代码可读本母版、采样色块、硬透明边缘、固定少量颜色。后期原生Minecraft材质动画只动剑身内部少量颜色/星砂，切刃与握柄轮廓固定。前两阶段静态。

提示集：Minecraft vanilla 32x32 enlarged nearest-neighbor sword sprite, 2-3 logical pixels blade width, plain short crossguard, dark slate hilt, silver-teal edge, subdued indigo blade, three cyan/violet star-sand pixels, eight flat colors, crisp hard alpha, no ornament, no aura, no text.

母版绘制已完成，采用内置ImageGen；阶段0..6分别以阶段7母版为编辑目标，仅要求改变材质，保留简洁轮廓。

| 阶段 | 材质编辑提示概要 | 原始生成文件 |
|---|---|---|
| 0 | charcoal-gray core, silver edge, remove all three star pixels, quiet iron metal | exec-edcdeea0-32a6-48b5-bb60-b1606f39acda.png |
| 1 | slate-blue core, silver steel edge, only one dim teal speck near blade base | exec-fc0efc09-1e3c-4483-bb84-0df99791c107.png |
| 2 | blue-gray spirit steel, muted teal edge, two small teal specks | exec-252bd2bb-dba9-43ab-95fd-a04ee2787cd1.png |
| 3 | dark violet soul metal, lavender steel edge, sparse violet specks | exec-bec1df7b-4a48-4a81-80e8-6f92a28746dc.png |
| 4 | nearly black indigo rift core, cyan cutting edge, two teal and one violet speck | exec-282e7564-9680-4676-b696-7af17b6fc0c6.png |
| 5 | deep navy void metal, silver ice edge, sparse cool ivory/indigo stars, no gold ornament | exec-69cbb5b0-923a-4f47-804c-4363ac8b6636.png |
| 6 | black-blue stellar core, muted lavender-blue edge, three cyan/violet specks | exec-e31dc596-9304-47cc-888e-382cbf582c04.png |
| 7 | original 32 logical pixels, charcoal hilt, silver-teal edge, subdued indigo core, three cyan/violet stars | exec-9a75f8d8-f2f9-4cc0-9363-7fe4d3a1ab52.png |

最终动画由DSH编写的原生PNG/mcmeta导出管线生成，透明轮廓固定，仅内层材质动色；母版是绘图参考与生产输入，游戏不是1254px高精细贴图。资源位置为src/main/resources/assets/evolvingblade/textures/item。

不要使用已被用户否定的 art/v2 纹理。
