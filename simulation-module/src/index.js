// AI情景演绎模块对外出口。并入主工程后从这里引用。

export { default as SimulationPage } from './components/SimulationPage.vue'
export { default as SimulationSetupPage } from './components/SimulationSetupPage.vue'
export { useSimulation } from './composables/useSimulation.js'
export { useSimulationStarter } from './composables/useSimulationStarter.js'
export { configureSimulationHttp, ApiError } from './api/http.js'
export { simulationApi } from './api/simulationApi.js'
export * from './core/constants.js'
