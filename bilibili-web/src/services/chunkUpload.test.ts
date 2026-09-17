import { beforeEach, describe, expect, it, vi } from 'vitest'
import { uploadApi } from '@/api'
import { uploadSmallVideo } from './chunkUpload'

vi.mock('@/api', () => ({ uploadApi: { check: vi.fn(), chunk: vi.fn(), merge: vi.fn() } }))

const check = vi.mocked(uploadApi.check)
const chunk = vi.mocked(uploadApi.chunk)
const merge = vi.mocked(uploadApi.merge)

function smallVideo() {
  return new File([new Uint8Array(5 * 1024 * 1024 + 10)], 'retry.mp4', { type: 'video/mp4' })
}

describe('resumable chunk upload', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.spyOn(Blob.prototype, 'arrayBuffer').mockImplementation(async function (this: Blob) {
      return new ArrayBuffer(this.size)
    })
  })

  it('skips chunks already accepted by the server and merges the same upload', async () => {
    check.mockResolvedValue({ data: { instant: false, videoId: null, sourceUrl: null, uploadId: 'upload-1', uploadedChunks: [0] } } as Awaited<ReturnType<typeof uploadApi.check>>)
    chunk.mockResolvedValue({ data: { chunkIndex: 1, uploaded: true } } as Awaited<ReturnType<typeof uploadApi.chunk>>)
    merge.mockResolvedValue({ data: { sourceUrl: 'source.mp4', fileMd5: 'md5', transcodeTaskId: 'task-1', transcodeStatus: 'waiting' } } as Awaited<ReturnType<typeof uploadApi.merge>>)
    const progress = vi.fn()

    const result = await uploadSmallVideo(smallVideo(), 'md5', progress)

    expect(check).toHaveBeenCalledWith(expect.objectContaining({ totalChunks: 2 }))
    expect(chunk).toHaveBeenCalledTimes(1)
    expect(chunk.mock.calls[0][0].get('chunkIndex')).toBe('1')
    expect(merge).toHaveBeenCalledWith(expect.objectContaining({ uploadId: 'upload-1', totalChunks: 2 }))
    expect(result.sourceUrl).toBe('source.mp4')
    expect(progress).toHaveBeenLastCalledWith(95)
  })

  it('recovers from a failed chunk by rechecking server progress on retry', async () => {
    check.mockResolvedValueOnce({ success: true, errorMsg: null, total: null, data: { instant: false, videoId: null, sourceUrl: null, uploadId: 'upload-1', uploadedChunks: [] } })
      .mockResolvedValueOnce({ success: true, errorMsg: null, total: null, data: { instant: false, videoId: null, sourceUrl: null, uploadId: 'upload-1', uploadedChunks: [0] } })
    chunk.mockResolvedValueOnce({ data: { chunkIndex: 0, uploaded: true } } as Awaited<ReturnType<typeof uploadApi.chunk>>)
      .mockRejectedValueOnce(new Error('network interrupted'))
      .mockResolvedValueOnce({ data: { chunkIndex: 1, uploaded: true } } as Awaited<ReturnType<typeof uploadApi.chunk>>)
    merge.mockResolvedValue({ data: { sourceUrl: 'source.mp4', transcodeTaskId: 'task-1' } } as Awaited<ReturnType<typeof uploadApi.merge>>)
    const file = smallVideo()

    await expect(uploadSmallVideo(file, 'md5', vi.fn())).rejects.toThrow('network interrupted')
    await expect(uploadSmallVideo(file, 'md5', vi.fn())).resolves.toEqual(expect.objectContaining({ sourceUrl: 'source.mp4' }))

    expect(chunk.mock.calls.map(([data]) => data.get('chunkIndex'))).toEqual(['0', '1', '1'])
    expect(merge).toHaveBeenCalledTimes(1)
  })

  it('returns an existing source without writing chunks', async () => {
    check.mockResolvedValue({ success: true, errorMsg: null, total: null, data: { instant: true, videoId: 1, sourceUrl: 'already.mp4', uploadId: 'upload-1', uploadedChunks: [] } })
    await expect(uploadSmallVideo(smallVideo(), 'md5', vi.fn())).resolves.toEqual({ sourceUrl: 'already.mp4', instant: true })
    expect(chunk).not.toHaveBeenCalled()
    expect(merge).not.toHaveBeenCalled()
  })
})
