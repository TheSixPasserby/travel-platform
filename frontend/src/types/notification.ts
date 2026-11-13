import type { PublicProfile } from '@/types/guide'

/** 通知来源类型，与后端 NotificationType 枚举保持一致。 */
export type NotificationType = 'COMMENT' | 'REPLY' | 'MENTION'

/** 一条通知，字段与 GET /notifications 的列表项契约一致。 */
export interface AppNotification {
  id: string
  type: NotificationType
  actor: PublicProfile
  guideId: string
  commentId: string
  content: string
  createdAt: string
  readAt: string | null
  href: string
}

/** 通知列表查询参数，服务端分页。 */
export interface NotificationQuery {
  page?: number
  size?: number
  unreadOnly?: boolean
}

/** 未读角标数据。 */
export interface NotificationUnreadCount {
  count: number
}
