# 序章出生点：初始集镇

以水井广场为中心的 80×65 格设计，建筑沿用余烬驿的木梁、石基和面向街面的坡屋顶。玩家醒来点和床仍在原设计的自己的住宅内。方位是平地样板，实际生成会调整房基高度和街线。

| 街区 | 场所 |
|---|---|
| 镇中心与主街 | 水井广场、摊位、带钟的议事厅、餐厅与后厨、诊所、杂货店 |
| 居民巷 | 玩家住宅、两栋村民住宅 |
| 生产与出镇 | 灌溉农田、粮仓、工匠铺、玩家扩建空地、有顶镇口 |

这次交付建筑与内装，容器留空；没有增加专属治疗、交易、NPC 对话、任务奖励或生产配方。

## 建筑资产

- [镇中心与主街预览](../../assets/settlement/starter-town/starter-town-bearing-220.png) · [高视角布局](../../assets/settlement/starter-town/starter-town-bearing-145.png)
- [Minecraft Builder 导出的 1.21.1 schematic](../../assets/settlement/starter-town/starter-town.schem)、[建模命令](../../assets/settlement/starter-town/operations.json)、[布局和锚点](../../assets/settlement/starter-town/layout.json)、[导入回执](../../assets/settlement/starter-town/build-report.json)
- `generate_operations.py` 在同目录重建建模命令和布局。预览额外画了一层平地及连接路；导出的 schematic 只含需要导入的地块。游戏中由核心按地形铺路，不需要 WorldEdit。旧六地块资产保留在 `assets/settlement/emberstead/`。

## 接入与验收

先为 13 处地块各自选址，再从广场把入口连起来，并从镇口南侧接向野外。地势平缓的主街可以加宽；有高差的路段继续使用台阶。所有地块和通路确立后再写方块，农田仍在结构与街道之后种植。旧世界中已登记的聚落不重新生成。

核心已部署到「星庭远征-开发版」，见[部署回执](../../evidence/starter-town-deployment.json)。由玩家新建世界确认：在自宅醒来并持有成长剑；走广场、主街，确认餐厅、议事厅、诊所、商店等能进入；再看农田是否有作物、路能否穿过镇口。部署过程中未启动游戏或修改存档。
