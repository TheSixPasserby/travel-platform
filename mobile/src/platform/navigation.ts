import { imageUrl } from './url.ts'

const detailPages = { guide: 'guides', destination: 'destinations', itinerary: 'itineraries' } as const
export function openDetail(kind: keyof typeof detailPages, id: string): void {
  if (!/^[0-9]+$/.test(id)) return
  uni.navigateTo({ url: `/pages/${detailPages[kind]}/detail?id=${encodeURIComponent(id)}` })
}
export function openLogin(): void { uni.navigateTo({ url: '/pages/login/index' }) }
export function openPrivatePage(kind: 'favorites' | 'itineraries'): void {
  uni.navigateTo({ url: `/pages/${kind}/index` })
}
export function previewImages(paths: string[], selected: string): void {
  const urls = paths.map(imageUrl).filter(Boolean)
  const current = imageUrl(selected)
  if (urls.length && current) uni.previewImage({ urls, current })
}
