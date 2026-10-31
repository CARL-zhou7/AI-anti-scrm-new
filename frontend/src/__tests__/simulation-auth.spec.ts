import { afterEach, expect, it, vi } from 'vitest'
import { csrf } from '../api/csrf'
import { loginRole } from '../api/loginRole'
import { simulationApi } from '../api/simulation'
import { reportApi } from '../api/report'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); csrf.clear(); loginRole.clear() })

it.each(['ADMIN', 'USER'] as const)('keeps %s simulation requests, streams and audio in the same login scope', async role => {
  loginRole.set(role)
  vi.spyOn(csrf, 'ensure').mockResolvedValue('scoped-token')
  const fetcher = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 'OK', data: {} }))))
  vi.stubGlobal('fetch', fetcher)
  const source = { addEventListener: vi.fn(), close: vi.fn(), onerror: null }
  const events = vi.fn(function () { return source })
  vi.stubGlobal('EventSource', events)
  const prefix = role === 'ADMIN' ? '/anti.scam/v1/admin/simulations' : '/anti.scam/v1/simulations'
  await simulationApi.start({ startRequestId: 'request', scenarioId: '1', scammerProfileId: '1', victimProfileId: '1' })
  await simulationApi.state('12')
  await simulationApi.presentOpening('12', { status: 'COMPLETED' })
  await simulationApi.adoptDraft('12', { draftId: '3', expectedRevisionNo: 1, continue: true })
  await reportApi.view('12')
  await reportApi.retry('12')
  simulationApi.exitKeepalive('12', 'CLOSE')
  expect(fetcher.mock.calls.map(call => call[0])).toEqual([
    prefix, `${prefix}/12`, `${prefix}/12/opening/presented`, `${prefix}/12/intervention/adopt`,
    `${prefix}/12/report`, `${prefix}/12/report/retry`, `${prefix}/12/exit`,
  ])
  expect(fetcher.mock.calls[0]![1].headers['X-CSRF-TOKEN']).toBe('scoped-token')
  const close = simulationApi.openStream('12', vi.fn(), vi.fn())
  expect(events).toHaveBeenCalledWith(`${prefix}/12/events`)
  expect(simulationApi.audioUrl('/anti.scam/v1/simulations/12/messages/3/audio')).toBe(`${prefix}/12/messages/3/audio`)
  close()
  expect(source.close).toHaveBeenCalledOnce()
})
