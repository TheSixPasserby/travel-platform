const MANAGED_IMAGE_PATTERN = /^\/uploads\/[0-9]{4}\/(0[1-9]|1[0-2])\/[0-9a-f]{32}\.(jpg|png|webp)$/

export function isManagedImageSource(value: string): boolean {
  return MANAGED_IMAGE_PATTERN.test(value)
}
