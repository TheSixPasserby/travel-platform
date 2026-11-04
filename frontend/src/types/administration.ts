import type { GuideStatus,GuideSummary } from './guide'
import type { GuideComment } from './interaction'
export type AdminUserStatus='ACTIVE'|'DISABLED'
export type AdminRole='USER'|'ADMIN'
export type CommentStatus='NORMAL'|'HIDDEN'
export interface AdminUser {
  id:string;username:string;nickname:string;avatarUrl:string|null;role:AdminRole;status:AdminUserStatus;createdAt:string;updatedAt:string
}
export interface AdminComment extends GuideComment {guideTitle:string;status:CommentStatus}
export interface AdminUserQuery {page?:number;size?:number;keyword?:string;status?:AdminUserStatus;role?:AdminRole}
export interface AdminGuideQuery {page?:number;size?:number;keyword?:string;status?:GuideStatus}
export interface AdminCommentQuery {page?:number;size?:number;keyword?:string;status?:CommentStatus}
export type { GuideSummary }
export interface AdminSummary {userCount:number;guideCount:number;destinationCount:number;commentCount:number}
