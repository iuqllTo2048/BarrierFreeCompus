# Stage 3 — A* 无障碍寻路核心

这是项目技术核心。

先输出详细算法计划、成本模型草案和测试场景，等待确认。

## A*
实现可测试、可解释 A*。

行动模式：
WHEELCHAIR、CRUTCH、TEMPORARY_INJURY、CART_LUGGAGE、WALKING。

Profile：
SHORTEST、ACCESSIBLE、BALANCED。

## 成本
至少：distance、slope、stairs、width、surface、lighting/day-night、active barrier、data uncertainty、user preferences。

成本规则集中管理，不散落 magic numbers。

轮椅 + stairs：不可通行。
UNKNOWN slope：可配置中等风险惩罚。
BLOCKED：不可通行。

## 三路线
同一请求分别按三个 Profile 运行同一 A*。
结果重复时去重并说明。

## 自定义偏好
支持避楼梯、距离、坡度、道路宽、休息点、无障碍卫生间；限制权重范围，防止极端值破坏算法。

## RouteResult
返回：
- polyline/GeoJSON
- distance
- estimated time（明确估算）
- risk summary
- stairs
- slope summary
- facilities
- barriers
- confidence
- profile
- cost breakdown
- constraints
- warnings
- algorithm metrics

## 无完整无障碍路线
返回风险最低可达路线 + 明确警告。

## 动态封路
管理员改为 BLOCKED 后，下一次规划无需重启立即绕开。

## 测试
正常、轮椅避楼梯、坡度冲突、封路、无路、同点、UNKNOWN、单向边、多 Profile、自定义偏好、重复候选。

## 前端
实现基本地图规划交互与三路线结果骨架，遵守已确认设计系统；重点算法，不做最终精修。

完成后停止。
