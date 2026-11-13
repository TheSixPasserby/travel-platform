import type { DestinationStatus } from './destination'
export type ItineraryStatus='ACTIVE'|'ARCHIVED'
export interface ItineraryDestination { id:string;name:string;status:DestinationStatus }
export interface ItinerarySummary {
  id:string;title:string;destination:ItineraryDestination|null;startDate:string;endDate:string;status:ItineraryStatus
  dayCount:number;itemCount:number;createdAt:string;updatedAt:string
}
export interface ItineraryDetail extends ItinerarySummary { notes:string|null;days:ItineraryDay[] }
export interface ItineraryDay {
  id:string;itineraryId:string;dayNo:number;travelDate:string;title:string|null;notes:string|null
  createdAt:string;updatedAt:string;items:ItineraryItem[]
}
export interface ItineraryItem {
  id:string;itineraryDayId:string;destination:ItineraryDestination|null;placeName:string;addressText:string|null
  startTime:string|null;endTime:string|null;notes:string|null;sortOrder:number;createdAt:string;updatedAt:string
}
export interface ItineraryInput { title:string;destinationId:string|null;startDate:string;endDate:string;notes:string|null;status:ItineraryStatus }
export interface DayInput { travelDate:string;title:string|null;notes:string|null }
export interface ItemInput { placeName:string;destinationId:string|null;addressText:string|null;startTime:string|null;endTime:string|null;notes:string|null }
export interface ItineraryQuery { page?:number;size?:number;keyword?:string;status?:ItineraryStatus }
export const itineraryStatusLabels:Record<ItineraryStatus,string>={ACTIVE:'进行中的计划',ARCHIVED:'已归档'}

export interface ItineraryImportItem extends ItemInput { kind:'SCENIC_SPOT'|'FREE_TIME' }
export interface ItineraryImportDay extends DayInput { items:ItineraryImportItem[] }
export interface ItineraryImportInput { clientRequestId:string;title:string;destinationId:string;startDate:string;endDate:string;notes:string|null;days:ItineraryImportDay[] }

export interface GuideTripImportInput {
  clientRequestId: string
  guideId: string
  destinationIds: string[]
  itineraryId: string | null
  dayId: string | null
  newItinerary: { title: string; startDate: string; endDate: string; travelDate: string } | null
}
export interface GuideTripImportResult {
  itineraryId: string
  dayId: string
  added: string[]
  alreadyPresent: string[]
}
