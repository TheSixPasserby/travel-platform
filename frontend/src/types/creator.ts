import type { PublicProfile } from './guide'
export interface CreatorProfile extends PublicProfile {
  username: string
  publishedGuideCount: number
}
