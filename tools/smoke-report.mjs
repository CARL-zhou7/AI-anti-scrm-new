// Reuse the existing isolated MySQL + HTTP/SSE harness; ports 13311/18084, disposable data only.
// Usage: node tools/smoke-report.mjs [--java ".../bin/java.exe"] [--mysql ".../MySQL Server 8.0/bin"]
process.argv.push('--report')
await import('./smoke-simulation.mjs')
