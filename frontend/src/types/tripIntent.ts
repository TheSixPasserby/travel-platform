export interface TripInspiration {
  ownerId: string
  guideId: string
  guideTitle: string
  cityId: string
  cityName: string
  places: { id: string; name: string }[]
}
