import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, join } from 'node:path'
import type { Plugin } from 'vite'
export const VDITOR_BASE='/vendor/vditor/4.0.1'
const packageDir=dirname(createRequire(import.meta.url).resolve('vditor/package.json'))
const files=['dist/js/lute/lute.min.js','dist/js/i18n/zh_CN.js','dist/js/icons/ant.js','dist/css/content-theme/light.css','dist/css/content-theme/dark.css','dist/js/highlight.js/styles/github.min.css','LICENSE']
/** Same package assets in dev and production; fixed allowlist prevents filesystem traversal. */
export function vditorAssets():Plugin {
 return {
  name:'vditor-local-assets',
  configureServer(server){server.middlewares.use((request,response,next)=>{
   const url=request.url?.split('?')[0]??''
   if(!url.startsWith(`${VDITOR_BASE}/`))return next()
   const file=url.slice(VDITOR_BASE.length+1)
   if(!files.includes(file)){response.statusCode=404;response.end();return}
   response.setHeader('Content-Type',file.endsWith('.js')?'text/javascript; charset=utf-8':file.endsWith('.css')?'text/css; charset=utf-8':'text/plain; charset=utf-8')
   response.end(readFileSync(join(packageDir,file)))
  })},
  generateBundle(){for(const file of files)this.emitFile({type:'asset',fileName:`${VDITOR_BASE.slice(1)}/${file}`,source:readFileSync(join(packageDir,file))})}
 }
}
