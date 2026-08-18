# PROJECT_SPEC.md — BarrierFreeCampus 冻结规格

项目：无碍智行——基于多源数据与智能体的校园无障碍出行及治理平台  
代码名：BarrierFreeCampus  
默认展示校园：云麓校园  
角色：USER、ADMIN  
登录：用户名 + 密码  
比赛演示：固定 USER/ADMIN 演示账号。

## v1.0 用户端
首页、地图路线规划、智能体助手、路线结果/对比、设施详情、障碍上报、我的上报、收藏路线、历史路线、个人中心、登录。

## v1.0 管理端
数据总览、建筑、入口、道路节点、道路边、设施、障碍审核、数据核验、数据集、用户、智能体调用日志、审计日志、系统设置。

## v1.0 只做室外路线
不做 GPS 实时跟随、偏离提醒、实时天气、室内导航、跨楼层、RAG/pgvector、图片/OSS、多模态、微信小程序。

## 设施
ACCESSIBLE_ENTRANCE  
RAMP  
ELEVATOR  
ACCESSIBLE_TOILET  
REST_AREA  
ACCESSIBLE_PARKING  
DROP_OFF_POINT  
TRANSIT_BOARDING_POINT

无障碍卫生间至少：建筑、楼层、开放状态、最近更新时间、数据来源、可信等级、photo_url(nullable)。

## 障碍
STAIRS、CONSTRUCTION、TEMPORARY_CLOSURE、DAMAGED_SURFACE、NARROW_PATH、VEHICLE_BLOCKING、STEEP_SLOPE、ELEVATOR_OUTAGE、ENTRANCE_CLOSED、WATERLOGGING。

## 坡度
FLAT、GENTLE、MODERATE、STEEP、UNKNOWN。UNKNOWN 有适度风险惩罚。

## 数据来源
DEMO_GENERATED、PUBLIC_SOURCE、MANUAL_ESTIMATE、USER_REPORT、FIELD_VERIFIED、UNVERIFIED。

## 可信等级
HIGH：实地核验  
MEDIUM：管理员确认的可信来源/多源核实  
LOW：单个用户上报  
UNKNOWN：Demo 或未核验

两个不同用户在相近时间、相近坐标、相同障碍类型上报可进入待核验中等级；管理员审核后才生效；HIGH 仍只给实地核验。

## 行动模式
WHEELCHAIR、CRUTCH、TEMPORARY_INJURY、CART_LUGGAGE、WALKING。

## Route Profile
SHORTEST、ACCESSIBLE、BALANCED。

支持偏好：避开楼梯、距离、坡度、道路宽、休息点、无障碍卫生间等。

## 无完整无障碍路线
返回风险最低可达路线并强警告，不虚假标记为完全无障碍。

## Demo
初始 5 建筑、约 30 道路、约 15 设施；固定随机种子坡度；五类确定性演示场景；可启停/重置；Formal 不受 Demo 重置影响。

## 图片
v1.0 `photo_url` 可为空，不上传，不接 OSS。

## UI
具体设计在 Design Gate 确认。原则：地图优先、简洁公共服务感、避免 AI 模板、Element Plus 二次设计、PC+移动、深色模式。
