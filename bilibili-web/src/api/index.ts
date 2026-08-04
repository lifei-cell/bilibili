import { apiRequest } from './http'
import type {
  CollectionFolder, CommentItem, CurrentUser, DanmuItem, LoginResult, SearchVideo,
  UploadCheck, UploadMerge, UserProfile, VideoDetail, VideoListItem, VideoPlay,
} from '@/types/api'

export const userApi = {
  sendCode: (phone: string) => apiRequest<void>({ method: 'POST', url: '/user/sendCode', data: { phone } }),
  login: (data: { username?: string; phone?: string; password?: string; code?: string; terminal: string }) =>
    apiRequest<LoginResult>({ method: 'POST', url: '/user/login', data }),
  register: (data: { phone: string; code: string; username: string; password: string; terminal: string }) =>
    apiRequest<LoginResult>({ method: 'POST', url: '/user/register', data }),
  logout: () => apiRequest<void>({ method: 'POST', url: '/user/logout' }),
  me: () => apiRequest<CurrentUser>({ method: 'GET', url: '/user/me' }),
  profile: (userId: number) => apiRequest<UserProfile>({ method: 'GET', url: `/user/profile/${userId}` }),
  updateProfile: (data: Partial<Pick<CurrentUser, 'nickname' | 'avatar' | 'gender' | 'birthday' | 'signature'>>) =>
    apiRequest<void>({ method: 'PUT', url: '/user/profile', data }),
}

export const videoApi = {
  list: (params: { page?: number; size?: number; categoryId?: number; sort?: string }) =>
    apiRequest<VideoListItem[]>({ method: 'GET', url: '/video/list', params }),
  userList: (userId: number, params: { page?: number; size?: number; sort?: string }) =>
    apiRequest<VideoListItem[]>({ method: 'GET', url: `/video/user/${userId}`, params }),
  detail: (videoId: number) => apiRequest<VideoDetail>({ method: 'GET', url: `/video/${videoId}` }),
  play: (videoId: number) => apiRequest<VideoPlay>({ method: 'GET', url: `/video/${videoId}/play` }),
  publish: (data: Record<string, unknown>) => apiRequest<{ videoId: number; status: number }>({ method: 'POST', url: '/video/publish', data }),
}

export const searchApi = {
  search: (params: { keyword: string; categoryId?: number; sort?: string; page?: number; size?: number }) =>
    apiRequest<SearchVideo[]>({ method: 'GET', url: '/search', params }),
  hot: (size = 10) => apiRequest<string[]>({ method: 'GET', url: '/search/hot', params: { size } }),
  suggest: (keyword: string, size = 8) => apiRequest<string[]>({ method: 'GET', url: '/search/suggest', params: { keyword, size } }),
}

export const socialApi = {
  likeStatus: (targetId: number, targetType = 1) => apiRequest<{ liked: boolean }>({ method: 'GET', url: '/like/status', params: { targetId, targetType } }),
  like: (targetId: number, liked: boolean, targetType = 1) => apiRequest<{ liked: boolean }>({ method: liked ? 'DELETE' : 'POST', url: '/like', data: { targetId, targetType } }),
  followStatus: (userId: number) => apiRequest<{ isFollowing: boolean }>({ method: 'GET', url: `/follow/status/${userId}` }),
  follow: (userId: number, following: boolean) => apiRequest<{ following: boolean }>({ method: following ? 'DELETE' : 'POST', url: `/follow/${userId}` }),
  comments: (videoId: number, params = { page: 1, size: 20, sort: 'hot' }) => apiRequest<CommentItem[]>({ method: 'GET', url: `/comment/list/${videoId}`, params }),
  comment: (data: { videoId: number; content: string; parentId?: number; replyToId?: number }) => apiRequest<{ commentId: number }>({ method: 'POST', url: '/comment', data }),
  collect: (videoId: number, collected: boolean, folderId = 0) => apiRequest<{ collected: boolean }>({ method: collected ? 'DELETE' : 'POST', url: '/collection', data: { videoId, folderId } }),
  folders: () => apiRequest<CollectionFolder[]>({ method: 'GET', url: '/collection/folders' }),
}

export const danmuApi = {
  list: (videoId: number) => apiRequest<DanmuItem[]>({ method: 'GET', url: `/danmu/list/${videoId}`, params: { page: 1, size: 500 } }),
  send: (data: { videoId: number; content: string; color: string; position: number; fontSize: number; videoTime: number; requestId: string }) =>
    apiRequest<{ danmuId: number }>({ method: 'POST', url: '/danmu/send', data }),
}

export const uploadApi = {
  check: (data: { fileMd5: string; fileName: string; fileSize: number; totalChunks: number }) => apiRequest<UploadCheck>({ method: 'POST', url: '/upload/check', data }),
  chunk: (data: FormData) => apiRequest<{ chunkIndex: number; uploaded: boolean }>({ method: 'POST', url: '/upload/chunk', data }),
  merge: (data: { uploadId: string; fileMd5: string; fileName: string; totalChunks: number }) => apiRequest<UploadMerge>({ method: 'POST', url: '/upload/merge', data }),
}
