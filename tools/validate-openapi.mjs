import { createRequire } from 'node:module'
import assert from 'node:assert/strict'
import { fileURLToPath } from 'node:url'

const require = createRequire(new URL('../frontend/package.json', import.meta.url))
const parser = require('@apidevtools/swagger-parser')
const document = await parser.validate(fileURLToPath(new URL('../openapi.yaml', import.meta.url)))
for (const [path, method] of [
  ['/auth/csrf', 'get'], ['/auth/register', 'post'], ['/auth/login', 'post'],
  ['/auth/logout', 'post'], ['/auth/me', 'get'], ['/home', 'get'], ['/news', 'get'],
]) assert.ok(document.paths[path]?.[method], `${method} ${path} is missing`)
assert.equal(document.servers[0].url, '/anti.scam/v1')
console.log('OpenAPI valid; phase 1 seven operations present.')
