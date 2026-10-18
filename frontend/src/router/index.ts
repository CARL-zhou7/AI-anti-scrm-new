import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import HomeView from '../views/HomeView.vue'
import PlaceholderView from '../views/PlaceholderView.vue'
import ConfigurationView from '../views/ConfigurationView.vue'
import SimulationView from '../views/SimulationView.vue'
import ReportEntryView from '../views/ReportEntryView.vue'
import AdminHomeView from '../views/AdminHomeView.vue'
import AdminScenariosView from '../views/AdminScenariosView.vue'
import AdminScammersView from '../views/AdminScammersView.vue'
import AdminVictimsView from '../views/AdminVictimsView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginView, meta: { title: '登录', public: true } },
    { path: '/register', component: RegisterView, meta: { title: '注册', public: true } },
    { path: '/home', component: HomeView, meta: { title: '首页', role: 'USER' } },
    { path: '/configuration', component: ConfigurationView, meta: { title: '开始体验', role: 'USER' } },
    { path: '/simulation', component: SimulationView, meta: { title: '情景演绎', role: 'USER', fullscreen: true } },
    { path: '/simulations/:id/report', component: ReportEntryView, meta: { title: '演绎结果', role: 'USER' } },
    { path: '/history', component: PlaceholderView, props: { title: '个人历史' }, meta: { title: '个人历史', role: 'USER' } },
    { path: '/admin', component: AdminHomeView, meta: { title: '管理后台', role: 'ADMIN' } },
    { path: '/admin/scenarios', component: AdminScenariosView, meta: { title: '场景管理', role: 'ADMIN' } },
    { path: '/admin/scammers', component: AdminScammersView, meta: { title: '骗子画像管理', role: 'ADMIN' } },
    { path: '/admin/victims', component: AdminVictimsView, meta: { title: '受害者画像管理', role: 'ADMIN' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  await auth.bootstrap()
  if (!to.meta.public && !auth.account) return '/login'
  if (auth.account && (to.meta.public || to.meta.role !== auth.account.role)) return auth.landing
})

window.addEventListener('session-expired', () => {
  const auth = useAuthStore()
  auth.reset()
  if (!router.currentRoute.value.meta.public) void router.replace('/login')
})

export default router
