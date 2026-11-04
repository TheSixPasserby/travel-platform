import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { GuideSummary } from '@/types/guide'
import type { CommentQuery,FavoriteQuery,GuideComment,GuideInteraction } from '@/types/interaction'
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
export function createComment(id:string,payload:{ content:string }):Promise<GuideComment>{return apiClient.post<GuideComment,GuideComment>(`${guidePath(id)}/comments`,payload)}
export function deleteComment(id:string):Promise<null>{return apiClient.delete<null,null>(`/comments/${encodeURIComponent(id)}`)}
export function fetchFavorites(params:FavoriteQuery={}):Promise<PageResult<GuideSummary>>{
  return apiClient.get<PageResult<GuideSummary>,PageResult<GuideSummary>>('/users/me/favorites',{ params })
}
