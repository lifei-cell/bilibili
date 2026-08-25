export interface ApiResult<T> {
  success: boolean
  errorMsg: string | null
  data: T
  total: number | null
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
  role: string
}

export interface CurrentUser extends UserInfo {
  phone: string
  gender: number
  birthday: string
  signature: string
}

export interface UserProfile {
  id: number
  username: string
  nickname: string
  avatar: string
  signature: string
  videoCount: number
  followerCount: number
  followingCount: number
  isFollowing: boolean
}

export interface LoginResult { token: string; user: UserInfo }

export interface VideoCategory {
  id: number
  parentId: number
  name: string
  sort: number
}

export interface VideoListItem {
  id: number
  title: string
  coverUrl: string
  duration: number
  authorName: string
  viewCount: number
  danmuCount: number
  createTime: string
}

export interface VideoAuthor { id: number; nickname: string; avatar: string }
export interface VideoStats {
  viewCount: number
  likeCount: number
  collectCount: number
  danmuCount: number
  commentCount: number
}

export interface VideoDetail {
  id: number
  title: string
  description: string
  coverUrl: string
  duration: number
  categoryId: number
  tags: string[]
  author: VideoAuthor
  stats: VideoStats
}

export interface VideoPlay {
  videoId: number
  defaultQuality: string
  qualities: { quality: string; url: string }[]
  playToken: string
}

export interface SearchVideo {
  id: number
  title: string
  description: string
  tags: string[]
  categoryId: number
  userId: number
  viewCount: number
  likeCount: number
  createTime: string
}

export interface CommentReply {
  id: number
  userId: number
  nickname: string
  avatar: string
  replyToId: number
  replyToNickname: string
  content: string
  likeCount: number
  createTime: string
}

export interface CommentItem {
  id: number
  userId: number
  nickname: string
  avatar: string
  content: string
  likeCount: number
  createTime: string
  replies: CommentReply[]
}

export interface DanmuItem {
  id: number
  userId: number
  content: string
  color: string
  position: number
  fontSize: number
  videoTime: number // seconds
  sendTime: string
}

export interface CollectionFolder {
  id: number
  name: string
  isPublic: boolean
  description: string
  coverUrl: string
  videoCount: number
}

export interface UploadCheck {
  instant: boolean
  videoId: number | null
  sourceUrl: string | null
  uploadId: string
  uploadedChunks: number[]
}

export interface UploadMerge {
  sourceUrl: string
  fileMd5: string
  transcodeTaskId: string
  transcodeStatus: 'waiting' | 'processing' | 'completed' | 'failed'
}

export interface UploadTranscodeTask {
  taskId: string
  status: 'waiting' | 'processing' | 'completed' | 'failed'
  retryCount: number
  errorMessage: string | null
  outputUrl: string | null
  nextRetryTime: string | null
}
