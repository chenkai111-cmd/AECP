import '@testing-library/jest-dom/vitest'

Object.assign(globalThis, {
  AbortController: window.AbortController,
  AbortSignal: window.AbortSignal,
  Headers: window.Headers,
  Request: window.Request,
  Response: window.Response
})
