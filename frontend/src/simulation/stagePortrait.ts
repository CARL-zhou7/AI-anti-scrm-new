import type { Profile } from '../api/types'
import chen from '../assets/stage-chen.webp'
import liang from '../assets/stage-liang.webp'
import lin from '../assets/stage-lin.webp'
import zhao from '../assets/stage-zhao.webp'
import zhou from '../assets/stage-zhou.webp'

// Match both original frame keys so an admin's replacement takes precedence, even with the same profile name.
const portraits = new Map([
  ['48d6c708ae546f4d0ca4d17d9f98eda89177ddcc480133070a89f7e42dc642ec', ['d32624d7261baaeb14726fdcc1332a1b1865b57341d6c0e6b1f940f7b2b849e5', chen]],
  ['0a4aa6f76d42c7c40c3646efab6a46af918b808596907e3853c4cd9ea90e0201', ['fb78d9424abf461f7163d9958fb9f4b9ce1587e6b6e823f8d5792149dc02163f', liang]],
  ['f2069504f87a663eabb24812791b5d6df296327342162c46d6fdd84f5557b9f9', ['be9986d6be1a73dfb392811fb0d75b9b020854e44e55b1a2efb4adf306678c07', lin]],
  ['54db4e6e556bf56bbc3b01d61d2de990afbbfec64739d51bd6e98e1f179add18', ['3085547af195e0e19fb69e7f9df96d5c1fe4ab796ef7e903bda568ef9fb9a63e', zhao]],
  ['39e82bce528e303444502835278ae3123fc15d204539dfd1e6ea011dda82e439', ['08b6ff4d5d6a9e5d5a72c5d777c4c6d1396e87679b0bc7f76701d833f3c24c01', zhou]],
])

export function stagePortrait({ avatar, speakingAvatar }: Pick<Profile, 'avatar' | 'speakingAvatar'>) {
  const portrait = portraits.get(avatar.key)
  return portrait?.[0] === speakingAvatar.key ? portrait[1] : undefined
}
