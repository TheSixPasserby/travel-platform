import { apiClient } from './client'

interface UploadedImage { url: string }

export async function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const image = await apiClient.post<UploadedImage, UploadedImage>('/files/images', form, {
    headers: { 'Content-Type': undefined }
  })
  return image.url
}
