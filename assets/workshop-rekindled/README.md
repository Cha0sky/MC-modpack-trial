# 废弃工坊：第一章现场修复遗迹

24×17×20 格的木石铜建筑，包含主厅、东侧动力翼、二层档案室与屋顶信号露台。主入口在北侧，西侧有破损侧入口，东侧有完整室外阶梯；主厅还有通往二层的木阶梯。普通建筑可拆改，设备交互由世界进度核心负责。

## 资产链

1. `python assets/workshop-rekindled/generate_operations.py` 生成 Minecraft Builder 操作源与布局。
2. Minecraft Builder 以 Java 1.21.1 导出 `abandoned-workshop.schem`。渲染图保存同目录，预览中有色羊毛代表设备占位；导入时替换为本包设备。
3. `python tools/build_workshop_structure.py` 生成标准 `structure/abandoned_workshop.nbt` 及单模板 jigsaw 世界生成资源。运行时不需要 WorldEdit。

结构、结构集与模板池 ID 均为 `world_progression_core:abandoned_workshop`。结构只在未生成区块自然出现，选择平原、向日葵平原、森林、繁花森林和白桦林等主世界陆地群系。随机间距24区块、最小分离10区块，避开自然村庄结构集4区块范围。单片刚性模板使用 `beard_thin` 地形衔接，不运行自定义世界放置脚本。

## 运行时接口

| 类型 | 本地坐标 | facing | 接轴侧的相邻坐标 |
|---|---|---|---|
| power | 19,2,10 | south | 19,2,11 |
| direction | 12,2,14 | west | 11,2,14 |
| signal | 19,6,7 | north | 19,6,6 |
| console | 10,2,8 | south | — |

三个 `workshop_relay` 均初始 `calibrated=false`，`workshop_console` 初始 `active=false`；这四个设备不写方块实体 NBT。坐标随结构旋转由原生模板系统转换，运行逻辑应依据结构范围与方块状态寻找设备。

正门入口为8,1,3，侧入口为2,1,11。楼梯顶端留出6,6,16的二层着陆点，露台门洞在14,6,8至14,8,10。设备接口留空，标牌写明接轴方向、转速和校准消耗。

主线奖励由启动事件发放，不放入静态宝箱。二层有一个使用原版工具匠战利品表的补给桶；入口附近有一个可拆除的低频骷髅刷怪笼（每次1只、附近上限2、间隔1200–2400刻、激活距离8）。原版照明和刷怪规则照常生效。

## 验证范围

已通过 Minecraft Builder 的1.21.1方块校验并查看建筑渲染；离线转换核对尺寸、4个设备、空接轴位和生成资源。未运行游戏、服务器或GameTest，实际世界生成与现场交互由用户实机验收。
