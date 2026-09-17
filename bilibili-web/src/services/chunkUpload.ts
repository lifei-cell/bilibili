import SparkMD5 from 'spark-md5'
import { uploadApi } from '@/api'
import type { UploadMerge } from '@/types/api'

const CHUNK_SIZE = 5 * 1024 * 1024

export async function uploadSmallVideo(file: File, fileMd5: string, onProgress: (value: number) => void): Promise<UploadMerge | { sourceUrl: string; instant: true }> {
  const totalChunks = Math.ceil(file.size / CHUNK_SIZE)
  const checked = (await uploadApi.check({ fileMd5, fileName: file.name, fileSize: file.size, totalChunks })).data
  if (checked.instant && checked.sourceUrl) {
    onProgress(100)
    return { sourceUrl: checked.sourceUrl, instant: true }
  }

  const uploaded = new Set(checked.uploadedChunks || [])
  for (let index = 0; index < totalChunks; index++) {
    if (!uploaded.has(index)) {
      const blob = file.slice(index * CHUNK_SIZE, Math.min((index + 1) * CHUNK_SIZE, file.size))
      const chunkMd5 = SparkMD5.ArrayBuffer.hash(await blob.arrayBuffer())
      const data = new FormData()
      data.append('uploadId', checked.uploadId)
      data.append('fileMd5', fileMd5)
      data.append('chunkMd5', chunkMd5)
      data.append('chunkIndex', String(index))
      data.append('chunkSize', String(blob.size))
      data.append('totalChunks', String(totalChunks))
      data.append('file', blob, `${file.name}.part${index}`)
      await uploadApi.chunk(data)
    }
    onProgress(15 + Math.round(((index + 1) / totalChunks) * 80))
  }
  return (await uploadApi.merge({ uploadId: checked.uploadId, fileMd5, fileName: file.name, totalChunks })).data
}
