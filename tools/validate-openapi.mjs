import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const parser = require('@apidevtools/swagger-parser')
const document = await parser.validate(fileURLToPath(new URL('../openapi.yaml', import.meta.url)))
for (const [path, method] of [
  ['/auth/csrf', 'get'], ['/auth/register', 'post'], ['/auth/login', 'post'],
  ['/auth/logout', 'post'], ['/auth/me', 'get'], ['/home', 'get'], ['/news', 'get'],
  ['/scenarios', 'get'], ['/scammer-profiles', 'get'], ['/scammer-profiles/{id}', 'get'],
  ['/victim-profiles', 'get'], ['/victim-profiles/{id}', 'get'], ['/avatars/{key}', 'get'],
  ['/admin/scenarios', 'get'], ['/admin/scenarios/{id}', 'put'],
  ['/admin/scammer-profiles', 'get'], ['/admin/scammer-profiles', 'post'],
  ['/admin/scammer-profiles/{id}', 'get'], ['/admin/scammer-profiles/{id}', 'put'],
  ['/admin/scammer-profiles/{id}/disable', 'post'], ['/admin/scammer-profiles/{id}/enable', 'post'],
  ['/admin/victim-profiles', 'get'], ['/admin/victim-profiles', 'post'],
  ['/admin/victim-profiles/{id}', 'get'], ['/admin/victim-profiles/{id}', 'put'],
  ['/admin/victim-profiles/{id}/disable', 'post'], ['/admin/victim-profiles/{id}/enable', 'post'],
  ['/admin/scammer-profiles/{id}', 'delete'], ['/admin/victim-profiles/{id}', 'delete'],
  ['/admin/avatars', 'post'], ['/admin/avatars/{key}', 'get'],
]) assert.ok(document.paths[path]?.[method], `${method} ${path} is missing`)
assert.equal(document.servers[0].url, '/anti.scam/v1')
assert.equal(document.components.schemas.Scenario.additionalProperties, false)
assert.equal(document.components.schemas.AvatarKey.pattern, '^[A-Za-z0-9_-]+$')
assert.equal(document.components.schemas.AdminScenario.additionalProperties, false)
for (const field of ['enabled', 'version', 'createdAt', 'updatedAt']) {
  assert.ok(document.components.schemas.AdminScammerProfile.required.includes(field))
}
console.log('OpenAPI valid; phase 1 (7) + phase 2 (6) + phase 3 (18) operations present.')
