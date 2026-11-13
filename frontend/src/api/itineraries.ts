import { apiClient } from './client'
import type { PageResult } from '@/types/api'
import type { DayInput,ItemInput,ItineraryDay,ItineraryDetail,ItineraryInput,ItineraryItem,ItineraryQuery,ItinerarySummary } from '@/types/itinerary'
const root=(id:string)=>`/itineraries/${encodeURIComponent(id)}`
const day=(id:string)=>`/itinerary-days/${encodeURIComponent(id)}`
const item=(id:string)=>`/itinerary-items/${encodeURIComponent(id)}`
export function fetchItineraries(params:ItineraryQuery={}):Promise<PageResult<ItinerarySummary>>{return apiClient.get<PageResult<ItinerarySummary>,PageResult<ItinerarySummary>>('/itineraries',{params})}
export function fetchItinerary(id:string):Promise<ItineraryDetail>{return apiClient.get<ItineraryDetail,ItineraryDetail>(root(id))}
export function createItinerary(body:ItineraryInput):Promise<ItineraryDetail>{return apiClient.post<ItineraryDetail,ItineraryDetail>('/itineraries',body)}
export function updateItinerary(id:string,body:ItineraryInput):Promise<ItineraryDetail>{return apiClient.put<ItineraryDetail,ItineraryDetail>(root(id),body)}
export function deleteItinerary(id:string):Promise<null>{return apiClient.delete<null,null>(root(id))}
export function createItineraryDay(id:string,body:DayInput):Promise<ItineraryDay>{return apiClient.post<ItineraryDay,ItineraryDay>(`${root(id)}/days`,body)}
export function updateItineraryDay(id:string,body:DayInput):Promise<ItineraryDay>{return apiClient.put<ItineraryDay,ItineraryDay>(day(id),body)}
export function deleteItineraryDay(id:string):Promise<null>{return apiClient.delete<null,null>(day(id))}
export function createItineraryItem(id:string,body:ItemInput):Promise<ItineraryItem>{return apiClient.post<ItineraryItem,ItineraryItem>(`${day(id)}/items`,body)}
export function updateItineraryItem(id:string,body:ItemInput):Promise<ItineraryItem>{return apiClient.put<ItineraryItem,ItineraryItem>(item(id),body)}
export function deleteItineraryItem(id:string):Promise<null>{return apiClient.delete<null,null>(item(id))}
export function reorderItineraryItems(id:string,itemIds:string[]):Promise<ItineraryDay>{return apiClient.put<ItineraryDay,ItineraryDay>(`${day(id)}/items/order`,{itemIds})}

export function importItinerary(body:import('@/types/itinerary').ItineraryImportInput):Promise<ItineraryDetail>{return apiClient.post<ItineraryDetail,ItineraryDetail>('/itineraries/import',body)}

export function importGuidePlaces(body: import('@/types/itinerary').GuideTripImportInput): Promise<import('@/types/itinerary').GuideTripImportResult> {
  return apiClient.post<import('@/types/itinerary').GuideTripImportResult, import('@/types/itinerary').GuideTripImportResult>('/itineraries/guide-imports', body)
}
