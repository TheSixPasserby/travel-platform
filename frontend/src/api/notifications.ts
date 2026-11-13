import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { AppNotification, NotificationQuery, NotificationUnreadCount } from '@/types/notification'

/** 当前用户的通知列表，服务端分页，可按未读筛选。 */
export function fetchNotifications(params: NotificationQuery = {}): Promise<PageResult<AppNotification>> {
  return apiClient.get<PageResult<AppNotification>, PageResult<AppNotification>>('/notifications', { params })
}

/** 当前用户的未读通知数量。 */
export function fetchNotificationUnreadCount(): Promise<NotificationUnreadCount> {
  return apiClient.get<NotificationUnreadCount, NotificationUnreadCount>('/notifications/unread-count')
}

/** 单条标记已读：仅本人可见，重复调用幂等，越权返回 404。 */
export function markNotificationRead(id: string): Promise<null> {
  return apiClient.put<null, null>('/notifications/' + encodeURIComponent(id) + '/read')
}

/** 全部标记已读。 */
export function markAllNotificationsRead(): Promise<null> {
  return apiClient.put<null, null>('/notifications/read-all')
}
