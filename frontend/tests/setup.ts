import { afterEach } from 'vitest'
import { readFileSync } from 'node:fs'

// Production loads this synchronous engine from index.html before mounting Vue.
window.eval(readFileSync('public/theme.js', 'utf8'))

afterEach(() => {
  window.sessionStorage.clear()
  window.localStorage.clear()
})
