# 需求、实现与验收追踪

按 [验收用例](09-test-and-acceptance.md) 映射实现与测试。TC-001–040 保留基础场景，TC-041–048 覆盖当前社区与规划增量。最近完整执行结果见 M17 测试报告。源码中的测试方法可用`python3 scripts/backend.py test --test Class#method`定向执行，需独立测试库。

下表简写对应真实测试类：

| 简写 | 源码 |
|---|---|
| A | [AuthApiTest](../backend/src/test/java/com/example/travelplatform/user/AuthApiTest.java) |
| D | [DestinationApiTest](../backend/src/test/java/com/example/travelplatform/destination/DestinationApiTest.java) |
| F | [ImageApiTest](../backend/src/test/java/com/example/travelplatform/file/ImageApiTest.java) |
| G | [GuideApiTest](../backend/src/test/java/com/example/travelplatform/guide/GuideApiTest.java) |
| I | [InteractionApiTest](../backend/src/test/java/com/example/travelplatform/interaction/InteractionApiTest.java) |
| C | [CommentApiTest](../backend/src/test/java/com/example/travelplatform/comment/CommentApiTest.java) |
| T | [ItineraryApiTest](../backend/src/test/java/com/example/travelplatform/itinerary/ItineraryApiTest.java) |
| M | [AdministrationApiTest](../backend/src/test/java/com/example/travelplatform/administration/AdministrationApiTest.java) |

## 业务场景映射

“已执行”引用实际里程碑结果，不代表此文档编写时又重跑一次所有测试；最新提交包的实测以阶段报告为准。

| TC | 实现/入口 | 已执行测试或证据 | 状态 |
|---|---|---|---|
| 001 注册 | AuthController/UserService | A.registrationCreatesSafeUserAndBcryptHash；M1浏览器 | 已执行 |
| 002 重名冲突 | 规范化用户名+唯一键 | A.registrationRejectsSequentialDuplicateUsername / registrationRejectsConcurrentDuplicateUsername | 已执行 |
| 003 错误密码 | BCrypt及统一登录错误 | A.loginFailuresAreGeneric | 已执行 |
| 004 匿名发布 | Service当前登录身份 | G.publicReadsAndAnonymousWritesHaveSeparateBoundaries | 已执行 |
| 005 改他人攻略 | 作者所有权 | G.draftsArePrivateAndAllAuthorOperationsRejectOtherUsersIncludingAdmin | 已执行 |
| 006 发布公开可见 | 攻略PUBLISHED | G.firstPublicationTimeSurvivesEditingWithdrawalAndRepeatedActions；M3浏览器 | 已执行 |
| 007 游客读草稿 | 私有/公开入口分开 | G.draftsArePrivateAndAllAuthorOperationsRejectOtherUsersIncludingAdmin | 已执行 |
| 008 重复点赞 | 唯一键及幂等PUT | I.duplicatePutAndDeleteAreIdempotentWithoutResettingRelationshipTime / concurrentRepeatedLikeAndFavoriteStillCreateOnlyOnePairEach | 已执行 |
| 009 取消点赞 | 幂等DELETE | I.duplicatePutAndDeleteAreIdempotentWithoutResettingRelationshipTime | 已执行 |
| 010 重复收藏 | 唯一键及幂等PUT | I.concurrentRepeatedLikeAndFavoriteStillCreateOnlyOnePairEach | 已执行 |
| 011 删他人评论 | 评论作者身份 | C.onlyCommentAuthorCanDeleteEvenWhenOtherUserOwnsGuideOrIsAdmin | 已执行 |
| 012 目的地停用 | 状态/历史引用 | D.publicPaginationFiltersVisibilityAndKeepsInactiveDetails；M2浏览器 | 已执行 |
| 013 空搜索 | 数据库WHERE/分页 | G.literalSearchFiltersCountsAndStableDatabasePaginationAgree | 已执行 |
| 014 page/size非法 | 严格HTTP参数 | G.invalidQueryValuesAreRejectedWithoutSilentlyClampingPages；D.requiredFieldsTypesEnumsAndPublicQueryBoundariesAreStrict | 已执行 |
| 015 非图片 | 上传白名单与解码 | F.unsupportedOrMismatchedImagesAreRejectedAsUnsupportedMedia | 已执行 |
| 016 超大图片 | 字节/像素上限 | F.exactFiveMebibyteImageIsAcceptedButOversizedRequestsAreRejected / oversizedPixelDimensionsAreRejectedFromHeaderOnly | 已执行 |
| 017 行程起止倒置 | ItineraryDates/Service | T.malformedInputsNeverCreateRowsOrApplyPartialChanges；纯日期策略测试 | 已执行 |
| 018 日程日期越界 | 根事务锁及范围校验 | T.daysCannotEscapeDateRangeOrDuplicateAndDoNotAutofill / aDayWaitingOnTheRootLockSeesCommittedDateShrink | 已执行 |
| 019 他人行程 | 全父子链所有权 | T.foreignUsersAndAdminsCannotReadOrMutateAnyLevel；M5隔离浏览器 | 已执行 |
| 020 AI不可用 | AI capabilities与手动入口 | AiItineraryApiTest及M8真实浏览器错误后手动保存 | 已执行，本地stub |
| 021 上传失败业务原子性 | 图片与受管路径验证 | F.failedAndSuccessfulUploadsKeepExistingFilesAndBusinessRowsIntact；G.publishingRevalidatesStoredImagePathsAndRollsBackOnMissingFiles | 已执行 |
| 022 组合搜索翻页 | 稳定数据库分页 | G.literalSearchFiltersCountsAndStableDatabasePaginationAgree | 已执行 |
| 023 退出复用旧token | 当前token失效 | A.multipleLoginsIssueIndependentTokensAndLogoutOnlyInvalidatesCurrent | 已执行 |
| 024 安全用户响应 | CurrentUserVO不含密码 | A.registrationCreatesSafeUserAndBcryptHash / loginReturnsSatokenTokenAndMeProjectionMatches | 已执行 |
| 025 普通用户管理目的地 | 当前ADMIN检查 | D.administrationRejectsAnonymousAndOrdinaryUsers | 已执行 |
| 026 夹带role/userId | 严格DTO/当前身份 | A.profilePatchRejectsPrivilegedFieldsAndExistenceMarkers | 已执行 |
| 027 篡改子ID/跨行程排序 | 根与子归属重核 | T.foreignUsersAndAdminsCannotReadOrMutateAnyLevel / orderRequiresTheEntireSameDayPermutationAndFailuresLeaveItUnchanged | 已执行 |
| 028 自己草稿私有读取 | /users/me/guides/:id | G.draftsArePrivateAndAllAuthorOperationsRejectOtherUsersIncludingAdmin | 已执行 |
| 029 作者解除隐藏 | HIDDEN写冲突 | G.hiddenContentCannotBeRestoredByTheAuthorButCanBePrivatelyReadAndDeleted；M6浏览器 | 已执行 |
| 030 非公开父资源屏蔽互动 | Guide父闸门 | I.hiddenWithdrawnAndDeletedGuidesDoNotExposeOrAcceptInteractionsButKeepRows；C.nonpublicGuidesRejectNewCommentsAndRetainPriorCommentsForRestoration | 已执行 |
| 031 禁用旧会话 | 每次当前用户状态校验 | A.disabledOrDeletedUserLosesExistingToken；M.usersCannotDisableThemselvesAndRepeatedStatusDoesNotRewriteTime；M6浏览器 | 已执行 |
| 032 大ID字符串 | Jackson长整型字符串/前端string | A.largeUserIdAndOffsetTimestampAreSerializedAsStrings；T.projectionsKeepIdsStringsTimesExplicitAndInternalFieldsPrivate | 已执行（用户大ID、其他模块字符串投影） |
| 033 历史停用目的地 | 保留未变引用/拒绝新引用 | G.inactiveDestinationsRetainHistoryButCannotBeNewReferences；T.inactiveDestinationReferencesAreHistoricalAndNewAssociationsAreRejected | 已执行 |
| 034 缩短行程排除日程 | 事务范围校验 | T.rootStartChangesRenumberAdjacentDaysWithoutMovingDatesAndShrinkIsAtomic | 已执行 |
| 035 排序重复/遗漏/跨天 | 完整同日排列 | T.orderRequiresTheEntireSameDayPermutationAndFailuresLeaveItUnchanged | 已执行 |
| 036 首次发布时间 | 状态变更不重置 | G.firstPublicationTimeSurvivesEditingWithdrawalAndRepeatedActions；M.guideHidingAndRestorationPreservePublicationAndAllRelationships | 已执行 |
| 037 标签字典与非法ID | tags表/关系校验 | G.literalSearchFiltersCountsAndStableDatabasePaginationAgree / relationReplacementIsAtomicAndNullableFieldsReallyClear | 已执行 |
| 038 外链/穿越/不存在图片 | ManagedImagePaths及解码重验 | G.publishedPutAndJsonFieldTypesCannotBypassValidation / publishingRevalidatesStoredImagePathsAndRollsBackOnMissingFiles；F.managedImageReadRejectsDisguisesMissingFilesAndEscapingLinks | 已执行 |
| 039 自禁/交叉禁用 | ADMIN集合锁与重核 | M.usersCannotDisableThemselvesAndRepeatedStatusDoesNotRewriteTime / concurrentCrossDisablingRetainsAUsableAdministrator / administratorWaitingOnTheSetLockMustRecheckItsOwnCurrentStatus | 已执行 |
| 040 关联更新失败原子性 | Guide事务及完整关系替换 | G.relationReplacementIsAtomicAndNullableFieldsReallyClear；M3记录 | 已执行 |

## 前端与阶段证据

前端tests目录包含登录/注册/资料、目的地/上传、攻略编辑/详情/Markdown DOM、互动/评论/收藏、私人行程、后台用户/治理/概览及路由与HTTP401边界测试。组件网络mock不代替真实MySQL和浏览器；浏览器按随机夹具或已标识演示集执行，记录清理或保留范围。

| 阶段 | 报告 | 范围 |
|---|---|---|
| M0 | [基线](13-baseline-review.md) | 12表/种子/约束、模型与需求审查 |
| M1 | [认证](16-m1-test-report.md) | 注册登录资料、真实HTTP/浏览器 |
| M2 | [目的地与图片](17-m2-test-report.md) | 上传边界、父关系并发、历史引用 |
| M3 | [攻略](18-m3-test-report.md) | 状态/关联/分页/Markdown |
| M4 | [互动](19-m4-test-report.md) | 幂等、收藏、评论与公开闸门 |
| M5 | [行程](20-m5-test-report.md) | 全链归属、日期/排序/锁等待缓存回归 |
| M6 | [后台](21-m6-test-report.md) | ADMIN并发保护、治理、安全投影与统计 |

性能负载、异地数据库恢复、正式公网HTTPS及学校最终验收未执行。M8用户明确将AI纳入本轮范围，本地stub集成已经执行，真实收费提供商验收单列未执行。

## M8扩展追踪

| 范围 | 代码与实际证据 |
|---|---|
| 原攻略图文与数据保护 | scripts/m8_content.py、test_m8_content.py、Phase1报告、三篇实际详情与草稿浏览器 |
| 增量迁移与恢复 | 三个V008迁移、test_m8_migrate.py、Phase2副本首次17步/重复0步/旧JAR八项 |
| 六城24处景点与素材许可 | docs/m8/data/、scripts/m8_data.py、七项副本故障/重复实测、最终全14表/44文件指纹 |
| 层级、图库、字段和来源 | DestinationM8ApiTest、DestinationDetailsPolicyTest、m8-destination-pages与m8-admin-destinations前端测试 |
| 攻略多景点及公开关联 | GuideDestinationsApiTest、m8-guide-scenic-spots.spec.ts、真实城市/景点/草稿浏览器 |
| AI候选与结果边界 | AiItineraryValidationTest、AiPreviewApiTest，真实MySQL和本地模型替身，预览/错误零写入 |
| AI外部HTTP协议与失败 | AiModelClientTest，本地HTTP服务器验证超时/429/5xx/非法JSON/Key缺失/禁止重试 |
| AI单体限流 | AiGenerationGateTest、29项独立Java断言，滚动窗口、并发与容量 |
| 确认保存与所有权 | ItineraryImportApiTest，同key并发/重复/二日真实失败回滚/366天500项/403 |
| AI预览交互与竞态 | ai-itinerary.spec.ts，换账号、迟到单日、其他天编辑、保存重试、备注/费用与容量 |
| 真实浏览器 | 城市/景点/图库/草稿/管理员/390px，QA副本AI保存刷新、外来用户403、502后编辑保留及手动保存 |

完整命令、实际数量、限制与十项验收见[最终报告](33-m8-final-report.md)及[机器证据](verification/2026-11-06-m8-final.json)。

## M13–M17 增量映射

| TC / 范围 | 实现与测试 |
|---|---|
| 瀑布流、连续加载、主题和灯箱 | 前端 m13/m14/m15 测试及对应阶段浏览器证据 |
| TC-041 | SearchService、M16SearchApiTest |
| TC-042 | 攻略行程导入服务、GuideTripImportApiTest |
| TC-043–046 | CommentService、NotificationService、M17CommunityApiTest、m17-comments / m17-notifications 组件测试 |
| TC-047–048 | CreatorDestinationsController、ShareController、M17SharingApiTest、m17-creator-destinations 组件测试 |
| 图片受控衍生尺寸 | 缩略图接口测试与 M16 图片配对样本 |
| AI 当前浏览器 | 本地 HTTP 合成供应商下预览、编辑、确认保存及刷新 |

测试文件位于 [后端测试](../backend/src/test/java/com/example/travelplatform) 和 [前端测试](../frontend/tests)。当前结果及限制见 [M17 报告](../output/m17/项目P1完成情况与真实测试报告.md)，历史执行范围见 [阶段索引](history/README.md)。
