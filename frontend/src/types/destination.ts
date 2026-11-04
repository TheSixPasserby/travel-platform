export type DestinationType = 'CITY' | 'SCENIC_SPOT' | 'OTHER'
export type DestinationStatus = 'ACTIVE' | 'INACTIVE'

export interface Destination {
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

export type DestinationInput = Omit<Destination, 'id' | 'createdAt' | 'updatedAt'>
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
