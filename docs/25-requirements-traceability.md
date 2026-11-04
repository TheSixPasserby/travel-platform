# 需求、实现与验收追踪

按[TC-001–040定义](09-test-and-acceptance.md)逐项映射。M6实际完整后端165项、前端227项；后续交付脚本检查单独计数，不混为业务测试。源码中的测试方法可用`python3 scripts/backend.py test --test Class#method`定向执行，需独立测试库。

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
| 020 AI不可用 | P2未接入，无AI接口/外部依赖 | 当前核心在无AI配置下运行；未执行AI错误降级测试 | 未执行（选做） |
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

性能负载、异地数据库恢复、正式公网HTTPS及学校最终验收未执行。AI不纳入P0/P1通过率；不把未执行项从原始清单中删除或伪装成已通过。
