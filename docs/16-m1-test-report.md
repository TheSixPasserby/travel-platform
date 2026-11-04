# M1 验证记录（通过）

日期：2026-11-02。构建、浏览器闭环和定向独立审查均通过；不代表整个毕业设计完成。

| 范围 | 实际命令 / 证据 | 结果 |
|---|---|---|
| 统一异常/响应基础 | `python3 scripts/backend.py test --test GlobalExceptionHandlerTest` | 13/13 通过；Java 21；含内部字段拒绝与405/406用例 |
| 本地启动器 | `python3 -m unittest discover -s scripts/tests -v` | 23/23 通过；合成临时配置，不访问真实凭据 |
| Vue 状态和页面 | `npm --prefix frontend test` | 69/69 通过，9 文件；网络边界 mock；新增 7 项状态回归 |
| Vue 类型与生产构建 | `npm --prefix frontend run build` | vue-tsc、Vite 通过 |
| 输入策略 | `python3 scripts/backend.py test --test UserInputPolicyTest` | 14/14 通过；Unicode码点/UTF-8字节边界 |
| 后端完整构建 | `python3 scripts/backend.py package` | 55/55 通过：HTTP24、策略14、文件路径4、统一异常13；可运行JAR生成 |
| 浏览器初查 | 1440×1000 首页、390×844 登录截图；匿名 `/me` | 页面正常；跳转 `/login?redirect=/me` |
| 真实 MySQL HTTP 认证 | `python3 scripts/backend.py test --test AuthApiTest#registrationCreatesSafeUserAndBcryptHash` | 已确认红灯：预期 201、实际 404；真实独立测试库；随后才派生产实现 |
| 浏览器完整闭环 | 注册→登录→改资料→刷新→退出 | 已通过；昵称/简介持久化，会话恢复；退出后旧token401，匿名受保护路由跳登录 |
| OpenAPI | GET /api/v1/openapi | 200，5路径/6操作；字符串用户ID、header apiKey、注册公开且201 |
| 独立实现审查 | 基础hub24、前端hub21、认证服务hub27/路径hub28 | 全部通过，无剩余must-fix；原大范围hub25接近输出上限后由两个已完成小审查替代 |

DSH 原工程任务在报告写入处长时间无进展，主代理取消上下文并保留代码；失败原因未确认。主代理重新执行上述验证，实际日志存于被忽略的 `.superpowers/sdd/2026-11-02-m1-auth/`。原先红灯日志缺失，不声称已恢复这些历史证据；后续认证 HTTP、路径和统一异常的新增红灯均实际执行并保留。认证运行时执行器在154258输出token（145699推理）时仍未写文件，主代理取消并接管小范围Service/Controller/拦截器整合，保留DSH已交付类型和测试，再由DSH独立终审。两侧模型未改变，没有自动 commit。

代码范围：backend 工程/统一异常/config，frontend 认证状态/页面/测试，scripts/backend.py 与隔离测试；数据库 DDL 不变。

主代理新增内部 JSON 字段注入测试，实际红灯为预期 HTTP 400、实际 200；补齐 fail-on-ignored-properties 后全部 11 项通过。原恢复任务的历史红灯缺失与这条新证据分别记录。

首轮整合的真实失败与修复：缺失登录字段返回401改为400；测试查询TINYINT(1)由驱动Boolean导致Number转换失败，改为SQL显式CAST，保留原值等于1的断言；统一异常Web切片限定探针Controller并排除真实认证配置，真实HTTP测试仍加载完整权限链。修复后定点15项、最终全套55项均通过。

浏览器截图保留在本地忽略目录 output/playwright/：首页桌面、登录手机、资料桌面/手机。浏览器内随机生成口令，不输出口令/token；临时QA账号已精确清理（原1、删除1、剩余0）。[机器可读证据](verification/2026-11-02-m1.json)。后续攻略、目的地、上传、行程和管理员业务尚未实现，其验收仍未执行。
