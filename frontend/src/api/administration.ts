import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { AdminSummary } from '@/types/administration'
import type { AdminComment,AdminCommentQuery,AdminGuideQuery,AdminUser,AdminUserQuery,AdminUserStatus,CommentStatus,GuideSummary } from '@/types/administration'
const path=(resource:'users'|'guides'|'comments',id:string)=>`/admin/${resource}/${encodeURIComponent(id)}/status`
export function fetchAdminUsers(params:AdminUserQuery={}):Promise<PageResult<AdminUser>>{return apiClient.get<PageResult<AdminUser>,PageResult<AdminUser>>('/admin/users',{params})}
export function patchUserStatus(id:string,status:AdminUserStatus):Promise<AdminUser>{return apiClient.patch<AdminUser,AdminUser>(path('users',id),{status})}
export function fetchAdminGuides(params:AdminGuideQuery={}):Promise<PageResult<GuideSummary>>{return apiClient.get<PageResult<GuideSummary>,PageResult<GuideSummary>>('/admin/guides',{params})}
export function patchGuideStatus(id:string,status:'HIDDEN'|'PUBLISHED'):Promise<GuideSummary>{return apiClient.patch<GuideSummary,GuideSummary>(path('guides',id),{status})}
export function fetchAdminComments(params:AdminCommentQuery={}):Promise<PageResult<AdminComment>>{return apiClient.get<PageResult<AdminComment>,PageResult<AdminComment>>('/admin/comments',{params})}
export function patchCommentStatus(id:string,status:CommentStatus):Promise<AdminComment>{return apiClient.patch<AdminComment,AdminComment>(path('comments',id),{status})}
export function fetchAdminSummary():Promise<AdminSummary>{return apiClient.get<AdminSummary,AdminSummary>('/admin/dashboard/summary')}
