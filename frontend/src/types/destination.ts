export type DestinationType = 'CITY' | 'SCENIC_SPOT' | 'OTHER'
export type DestinationStatus = 'ACTIVE' | 'INACTIVE'

export interface DestinationImage {
  id: string; url: string; altText: string; sortOrder: number
  sourceUrl: string; author: string; license: string; licenseUrl: string; retrievedAt: string
}
export type DestinationImageInput = Omit<DestinationImage, 'id' | 'sortOrder'>
export interface DestinationDetails {
  address: string | null
  longitude: string | null
  latitude: string | null
  recommendedDurationMinutes: number | null
  recommendedDays: number | null
  openingHours: string | null
  ticketInfo: string | null
  officialUrl: string | null
  sourceUrl: string | null
  verifiedAt: string | null
  images: DestinationImage[]
}

export interface Destination extends DestinationDetails {
  id: string
  name: string
  destinationType: DestinationType
  parentId: string | null
  country: string
  province: string | null
  city: string | null
  description: string | null
  coverImageUrl: string | null
  recommendedSeasons: string | null
  status: DestinationStatus
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type DestinationInput = Omit<Destination, 'id' | 'createdAt' | 'updatedAt' | keyof DestinationDetails>
  & Partial<Omit<DestinationDetails, 'images'>> & { images?: DestinationImageInput[] }
export type DestinationCreate = Pick<DestinationInput, 'name'> & Partial<Omit<DestinationInput, 'name'>>
export interface DestinationQuery {
  page?: number
  size?: number
  keyword?: string
  destinationType?: DestinationType
  parentId?: string
}
export interface AdminDestinationQuery extends DestinationQuery { status?: DestinationStatus }
export const destinationTypeLabels: Record<DestinationType, string> = {
  CITY: '城市', SCENIC_SPOT: '景点', OTHER: '其他目的地'
}
