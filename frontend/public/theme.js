// Synchronous head script sets the palette before the stylesheet and Vue render.
(() => {
  let preference = 'system'
  try {
    const saved = localStorage.getItem('travel-notes.theme')
    if (saved === 'light' || saved === 'dark') preference = saved
  } catch {
    // Storage can be unavailable in private browsing. System appearance still works.
  }
  const systemDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  const theme = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', theme === 'dark' ? '#141e1a' : '#f9f7f2')
})()
