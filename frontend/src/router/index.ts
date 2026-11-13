import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { loginRole } from '../api/loginRole'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import RegistrationPracticeView from '../views/RegistrationPracticeView.vue'
import HomeView from '../views/HomeView.vue'
import HistoryView from '../views/HistoryView.vue'
import HistoryDetailView from '../views/HistoryDetailView.vue'
import ConfigurationView from '../views/ConfigurationView.vue'
import SimulationView from '../views/SimulationView.vue'
import ReportView from '../views/ReportView.vue'
import AdminHomeView from '../views/AdminHomeView.vue'
import AdminScenariosView from '../views/AdminScenariosView.vue'
import AdminScammersView from '../views/AdminScammersView.vue'
import AdminTrustPolicyView from '../views/AdminTrustPolicyView.vue'
import AdminVictimsView from '../views/AdminVictimsView.vue'
import AdminDemoView from '../views/AdminDemoView.vue'
import AdminInterventionsView from '../views/AdminInterventionsView.vue'
import AdminSceneRulesView from '../views/AdminSceneRulesView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginView, meta: { title: '登录', public: true } },
    { path: '/register', component: RegisterView, meta: { title: '注册', public: true, depth: 1 } },
    { path: '/register/practice', component: RegistrationPracticeView, meta: { title: '注册学习账号', public: true, depth: 1 } },
    { path: '/register/practice/result', component: RegistrationPracticeView, props: { revealed: true }, meta: { title: '反诈练习结果', public: true, depth: 2 } },
    { path: '/home', component: HomeView, meta: { title: '首页', role: 'USER' } },
    { path: '/configuration', component: ConfigurationView, meta: { title: '开始体验', role: 'USER' } },
    { path: '/simulation', component: SimulationView, meta: { title: '情景演绎', role: 'USER', fullscreen: true } },
    { path: '/simulations/:id/report', component: ReportView, meta: { title: '分析复盘', role: 'USER', depth: 2 } },
    { path: '/history', component: HistoryView, meta: { title: '个人历史', role: 'USER' } },
    { path: '/history/:id', component: HistoryDetailView, meta: { title: '历史详情', role: 'USER', depth: 1 } },
    { path: '/admin', component: AdminHomeView, meta: { title: '管理后台', role: 'ADMIN' } },
    { path: '/admin/scenarios', component: AdminScenariosView, meta: { title: '场景管理', role: 'ADMIN' } },
    { path: '/admin/scammers', component: AdminScammersView, meta: { title: '骗子画像管理', role: 'ADMIN' } },
    { path: '/admin/trust-policy', component: AdminTrustPolicyView, meta: { title: '信任分机制', role: 'ADMIN' } },
    { path: '/admin/victims', component: AdminVictimsView, meta: { title: '受害者画像管理', role: 'ADMIN' } },
    { path: '/admin/demo', component: AdminDemoView, meta: { title: '演示测试', role: 'ADMIN' } },
    { path: '/admin/demo/run', component: SimulationView, meta: { title: '演示测试', role: 'ADMIN', fullscreen: true } },
    { path: '/admin/demo/report/:id', component: ReportView, meta: { title: '分析复盘', role: 'ADMIN', depth: 1 } },
    { path: '/admin/interventions', component: AdminInterventionsView, meta: { title: '待入库记录', role: 'ADMIN' } },
    { path: '/admin/scene-rules', component: AdminSceneRulesView, meta: { title: '场景规则', role: 'ADMIN' } },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (to.path === '/login' && !loginRole.current()) return
  if (!auth.ready && to.meta.role) loginRole.set(to.meta.role as 'USER' | 'ADMIN')
  await auth.bootstrap()
  if (!to.meta.public && !auth.account) return '/login'
  if (auth.account && (to.meta.public || to.meta.role !== auth.account.role)) return auth.landing
})

window.addEventListener('session-expired', () => {
  const auth = useAuthStore()
  auth.reset()
  const current = router.currentRoute.value
  if (current.matched.length && !current.meta.public) void router.replace('/login')
})

export default router
