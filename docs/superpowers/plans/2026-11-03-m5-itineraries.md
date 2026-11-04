# M5 手动行程 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. 按任务保留失败与验证证据。

**Goal:** 用户可独立完成私人行程、每日地点时间备注和排序。

**Architecture:** 复用三表，根行程锁统一协调子写事务；读RR/写RC；细粒度REST接口和Vue时间线。Codex最终维护共享契约并验收DSH限定交付。

**Tech Stack:** 已锁定Java21/Spring Boot3.5/MyBatis-Plus/MySQL8.4与Vue3/TypeScript/Pinia；无新依赖。

**Spec:** [M5设计](../specs/2026-11-03-m5-itineraries-design.md)，API20。

## Global Constraints

- 无DDL、无自动commit或模型更换；DSH只buddy/deepseek-v4.1-flash/max，不读.env/.local或真实DB。
- 所有入口登录本人，ADMIN无例外；根软删、显式删日/项目物理删；字符串ID。
- 日期1000–9999、yyyy-MM-dd、包含首尾1–366天；HH:mm时间，结束不早于开始。
- 文本标题/日标题/地点160、地址300、notes2000码点，纯文本；每日100/根500项目。
- 状态ACTIVE/ARCHIVED均可编辑；日期缩短不删除日程，全部排序原子校验。

## Review Focus

- 根起始日期平移遇相邻dayNo唯一键碰撞：Task1/2测向前向后及回滚，双阶段暂存值。
- 子定位后等待期间根/日已删除：Task2根锁后重读，删除后全子入口404测试。
- 排序缺项/重复/跨天或01与1等价ID：Task2先转Long集合校验，再一次事务更新，全失败保原序。
- 日期更新并发添加日程：Task2真实持锁缩短并让加日等待，提交后400不写越界日。
- 页面离开后迟到401/响应或失败清表单：Task3挂真实RouterView与deferred请求覆盖。

## Task0 契约/前置审查

**Files:** 本设计/计划、docs/06-api-contract.md第20节、PROJECT_STATUS.md。
- [x] 固定请求/VO、根锁/日期重编号与上限规则，静态自审对齐DDL；hub50只读前置审查并由Codex裁定，目的地/用户无产品硬删路径，实施门已释放。

## Task1 根行程与纯日期策略

**Files:** backend/src/main/java/com/example/travelplatform/itinerary/{dto,entity,mapper,vo,service,controller,validation}/；tests/itinerary/{ItineraryDatesTest,ItineraryApiTest}.java；共用HttpTestSupport.java仅Codex改。

**Interfaces:** ItineraryDates.date(String):LocalDate、range(String,String):Range(startDate,endDate)、dayNo(LocalDate,LocalDate,LocalDate):int、time(String):LocalTime、times(String,String):Times(startTime,endTime)；所有非法输入抛ApiException40001。ItineraryService.list(page,size,keyword,status):PageResponse<ItinerarySummaryVO>、create(ItineraryCreateRequest):ItineraryDetailVO、get(String):ItineraryDetailVO、update(String,ItineraryUpdateRequest):ItineraryDetailVO、delete(String):void。
- [x] 写严格日期/闰年/上限/越界/时间纯测试，执行缺类RED；root HTTP认证/所有权/分页/JSON/归档/目的地/软删测试先入口RED。
- [x] Codex实现DTO/Entity/Mapper/VO与根Service/Controller；DSH可单独拥有ItineraryDates.java，不改其他文件。列表SQL参数化COUNT/LIMIT/OFFSET与LIKE转义，装配批量目的地。
- [x] 定向GREEN，根日期更新在Task2同时实现两阶段dayNo重编号；不将未覆盖子资源宣称完成。

## Task2 日程、项目与排序

**Files:** Task1同模块内新增Day/Item DTO/Entity/Mapper/VO、ItineraryDayController/ItineraryItemController；ItineraryService根协调；ItineraryApiTest.java。

**Interfaces:** Service.createDay(rootId,DayWriteRequest):DayVO、updateDay(dayId,DayWriteRequest):DayVO、deleteDay(dayId):void、createItem(dayId,ItemWriteRequest):ItemVO、updateItem(itemId,ItemWriteRequest):ItemVO、deleteItem(itemId):void、order(dayId,ItemOrderRequest):DayVO；字段详API20，全部ID字符串。
- [x] 扩展HTTP失败测试：两级越权/软删、连续日期平移重编号、缩短冲突40902原样、重复日期40901、项目完整排列与等价重复ID400、并发缩短后创建日拒绝、每日100/根500满额40902且不写。
- [x] 实现根→日/项目锁与等待后重读；目标目的地当前读；追加/删除稠密排序；先集合核对再原子写。日项目用两次批量查询装配完整详情，避免N+1。
- [x] 跑全部itinerary测试与完整package，DSH只读SQL/锁/权限审查，Codex核对实际源码并闭合。

## Task3 私人行程界面

**Files:** frontend/src/types/itinerary.ts、api/itineraries.ts、views/MyItinerariesView.vue、ItineraryDetailView.vue、components/ItineraryDay.vue、ItineraryForm.vue、ItineraryItemForm.vue；router/index.ts与ProfileView.vue由Codex整合；tests/itinerary-views.spec.ts等。

**Interfaces:** API与Task1/2保持同名；ItineraryDay props day:ItineraryDay、busy:boolean；emit edit-day(day)、delete-day(day)、add-item(dayId)、edit-item(item)、delete-item(item)、order(dayId,itemIds:string[])。ItineraryForm props initial:ItineraryDetail|null、busy:boolean，emit save(ItineraryInput)/cancel；ItemForm props initial:ItineraryItem|null、busy:boolean，emit save(ItemInput)/cancel。详情拥有mutation状态、调用API与刷新，组件不直接写共享状态。
- [x] 写真实分页/空错重试、新建/详情、标题单改保留notes/目的地、删除确认/归档筛选、表单失败保留、防重、完整ID排列、纯文本安全、401与迟到请求RED。
- [x] 按精确文件所有权给DSH小组件；Codex维护API/types/页面整合，表单用服务端真实状态，不假造成功。
- [x] 全部Vitest、vue-tsc/Vite通过，独立前端审查与定向回归闭合。

## Task4 浏览器与闭环

**Files:** docs/20-m5-test-report.md、docs/verification/2026-11-04-m5.json、PROJECT_STATUS.md、README.md。
- [x] 两临时账号验收两日日程/多项目上移下移/编辑/刷新、日期缩短冲突、B越权、390px手机布局；确认删除自己的日/项目/行程。
- [x] 精确身份和关联校验后清理自己的夹具；核对原种子/两个MySQL服务保留，归档证据与源码快照。
- [x] 全套测试/审查/浏览器通过才验收M5，继续M6基础后台前重新规划审查。
