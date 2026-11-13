import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { GuideSummary } from '@/types/guide'
import type { CommentCreatePayload,CommentLocation,CommentQuery,FavoriteQuery,GuideComment,GuideInteraction,MentionQuery } from '@/types/interaction'
const guidePath=(id:string)=>`/guides/${encodeURIComponent(id)}`
export function fetchInteraction(id:string):Promise<GuideInteraction>{return apiClient.get<GuideInteraction,GuideInteraction>(`${guidePath(id)}/interaction`)}
export function setGuideLike(id:string,enabled:boolean):Promise<GuideInteraction>{
  const path=`${guidePath(id)}/like`;return enabled?apiClient.put<GuideInteraction,GuideInteraction>(path):apiClient.delete<GuideInteraction,GuideInteraction>(path)
}
export function setGuideFavorite(id:string,enabled:boolean):Promise<GuideInteraction>{
  const path=`${guidePath(id)}/favorite`;return enabled?apiClient.put<GuideInteraction,GuideInteraction>(path):apiClient.delete<GuideInteraction,GuideInteraction>(path)
}
export function fetchComments(id:string,params:CommentQuery={}):Promise<PageResult<GuideComment>>{
  return apiClient.get<PageResult<GuideComment>,PageResult<GuideComment>>(`${guidePath(id)}/comments`,{ params })
}
/** 只发送调用方显式提供的键：保持旧请求体 {content} 不变。 */
export function createComment(id:string,payload:CommentCreatePayload):Promise<GuideComment>{
  const body:CommentCreatePayload={ content:payload.content }
  if(payload.replyToId)body.replyToId=payload.replyToId
  if(payload.mentionUserIds&&payload.mentionUserIds.length)body.mentionUserIds=[...payload.mentionUserIds]
  return apiClient.post<GuideComment,GuideComment>(`${guidePath(id)}/comments`,body)
}
export function deleteComment(id:string):Promise<null>{return apiClient.delete<null,null>(`/comments/${encodeURIComponent(id)}`)}
/** 根评论下的公开回复，服务端分页、按创建时间正序。 */
export function fetchReplies(rootId:string,params:CommentQuery={}):Promise<PageResult<GuideComment>>{
  return apiClient.get<PageResult<GuideComment>,PageResult<GuideComment>>(`/comments/${encodeURIComponent(rootId)}/replies`,{ params })
}
/** 通知跳转所需的评论位置；目标隐藏或删除时抛 404。 */
export function fetchCommentLocation(id:string):Promise<CommentLocation>{
  return apiClient.get<CommentLocation,CommentLocation>(`/comments/${encodeURIComponent(id)}/location`)
}
/** @ 提及候选人，按用户名或昵称匹配，最多 8 个。 */
export function fetchMentionSuggestions(keyword:string):Promise<GuideComment['author'][]>{
  return apiClient.get<GuideComment['author'][],GuideComment['author'][]>('/users/mention-suggestions',{ params:{ keyword } as MentionQuery })
}
export function fetchFavorites(params:FavoriteQuery={}):Promise<PageResult<GuideSummary>>{
  return apiClient.get<PageResult<GuideSummary>,PageResult<GuideSummary>>('/users/me/favorites',{ params })
}
