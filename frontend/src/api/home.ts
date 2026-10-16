import { http } from './http'
import type { HomePayload, NewsPayload } from './types'

export const homeApi = {
  getHome: () => http.get<HomePayload>('/home'),
  getNews: () => http.get<NewsPayload>('/news'),
}
