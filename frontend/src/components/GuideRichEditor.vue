<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import icons from 'vditor/dist/js/icons/ant.js?raw'
import { uploadImage } from '@/api/files'
import { ApiError } from '@/api/client'
import { readToken } from '@/api/token'
import { isManagedImageSource, renderMarkdown, sanitizeEditorHtml } from '@/utils/markdown'
import { roundTripIssue, sourceIssue, validateEditorImages } from '@/utils/richEditor'
import ImageLightbox from './ImageLightbox.vue'
import { useImageLightbox } from '@/composables/useImageLightbox'
type Mode='wysiwyg'|'ir'|'sv'
const props=withDefaults(defineProps<{modelValue:string;disabled?:boolean;galleryCount?:number}>(),{disabled:false,galleryCount:0})
const emit=defineEmits<{'update:modelValue':[value:string];uploaded:[image:{url:string;altText:string}];busy:[value:boolean];'session-expired':[]}>()
const root=ref<HTMLElement|null>(null),fallbackArea=ref<HTMLTextAreaElement|null>(null)
const source=ref(props.modelValue),mode=ref<Mode>('wysiwyg'),ready=ref(false),fallback=ref(false),warning=ref(''),uploading=ref(false),uploadError=ref('')
const copyNotice=ref('')
async function copySource(){try{await navigator.clipboard.writeText(getContent());copyNotice.value='Markdown 源码已复制'}catch{void useSource('请在下方选中并复制正文')}}
const count=computed(()=>[...source.value].length),blocked=computed(()=>props.disabled||uploading.value)
const {preview,open,close}=useImageLightbox()
function previewImages(event:MouseEvent){const images=Array.from(root.value?.querySelectorAll<HTMLImageElement>(`.vditor-${mode.value} img`)??[]).filter(img=>isManagedImageSource(img.getAttribute('src')??''));const first=images[0];if(first)open(images.map(img=>({src:img.getAttribute('src')!,alt:img.alt})),first.getAttribute('src')!,event.currentTarget)}
let editor:Vditor|undefined,disposed=false,generation=0,syncing=false,userEdited=false,selection:Range|null=null,sourceSelection={from:0,to:0},resourceTimer:ReturnType<typeof setTimeout>|undefined
function update(value:string){if(disposed||syncing||props.disabled)return;source.value=value;emit('update:modelValue',value)}
/** Read the current DOM before save, even when Vditor's input callback is debounced or during IME. */
function getContent():string {
 if(!disposed&&editor&&ready.value&&!fallback.value&&userEdited){const value=editor.getValue();source.value=value;emit('update:modelValue',value);userEdited=false}
 return source.value
}
function rememberSelection(){
 const area=fallback.value?fallbackArea.value:root.value?.querySelector<HTMLTextAreaElement>('.vditor-sv')
 if(mode.value==='sv'&&area)sourceSelection={from:area.selectionStart,to:area.selectionEnd}
 else{const current=window.getSelection();if(current?.rangeCount&&root.value?.contains(current.anchorNode))selection=current.getRangeAt(0).cloneRange()}
}
function teardown(){clearTimeout(resourceTimer);if(editor?.vditor?.wysiwyg)editor.destroy();editor=undefined;ready.value=false;selection=null}
async function useSource(reason:string){
 teardown();fallback.value=true;mode.value='sv';warning.value=`${reason}；为避免丢失内容，已保留 Markdown 源码，可继续编辑和保存。`;ready.value=true
 await nextTick();fallbackArea.value?.focus()
}
async function create(modeToUse:Mode){
 const value=getContent(),run=++generation;teardown();fallback.value=false;mode.value=modeToUse;warning.value='';userEdited=false
 const issue=sourceIssue(value)
 if(issue){await useSource(issue);return}
 await nextTick();if(disposed||run!==generation||!root.value)return
 try{
  // The official icon loader uses synchronous XHR. Bundle the exact package icon
  // resource so mode changes never need a blocking network request.
  if(!document.getElementById('vditorIconScript')){const script=document.createElement('script');script.id='vditorIconScript';script.textContent=icons;document.head.append(script)}
  const mountPoint=document.createElement('div');root.value.replaceChildren(mountPoint)
  let instance:Vditor
  instance=new Vditor(mountPoint,{
   cdn:'/vendor/vditor/4.0.1',lang:'zh_CN',mode:modeToUse,value,cache:{enable:false},height:520,minHeight:360,
   theme:document.documentElement.dataset.theme==='dark'?'dark':'classic',placeholder:'记录你的路线、风景和旅行建议…',
   toolbar:['headings','bold','italic','strike','|','list','ordered-list','quote','line','code','inline-code','link','table','upload','|','undo','redo'],
   toolbarConfig:{pin:false},hint:{parse:false,emoji:{}},link:{isOpen:false},image:{isPreview:false},
   preview:{mode:'editor',actions:[],hljs:{enable:false},markdown:{sanitize:true,autoSpace:false,fixTermTypo:false,toc:false,footnotes:false,callout:false,inlineMath:false,codeBlockPreview:false,mathBlockPreview:false,mark:false,gfmAutoLink:false,sub:false,sup:false},render:{media:{enable:false}},transform:()=>renderMarkdown(editor?.getValue()??source.value)},
   upload:{accept:'image/jpeg,image/png,image/webp',max:5*1024*1024,multiple:true,url:'',linkToImgUrl:'',handler:async files=>{await upload(files);return null}},
   customWysiwygToolbar(type,element){
    element.querySelectorAll('button').forEach(button=>button.type='button')
    // Image addresses always come from authenticated managed uploads. Keep alt/title editing.
    if(type==='image'){const input=element.querySelector('input');if(input){input.readOnly=true;input.setAttribute('aria-label','受管图片地址（只读）')}}
   },
   input(value){if(!disposed&&!syncing&&ready.value&&run===generation){userEdited=false;source.value=value;emit('update:modelValue',value)}},
   after(){
    if(disposed||run!==generation){instance.destroy();return}
    clearTimeout(resourceTimer)
    const problem=roundTripIssue(value,instance.getValue())
    if(problem){void useSource(problem);return}
    instance.clearStack();ready.value=true;if(blocked.value)instance.disabled()
    root.value?.querySelectorAll('button').forEach(button=>button.type='button')
    root.value?.querySelectorAll<HTMLElement>('[contenteditable],.vditor-sv').forEach(el=>{el.setAttribute('aria-label','攻略正文');el.setAttribute('aria-multiline','true')})
   }
  })
  editor=instance
  resourceTimer=setTimeout(()=>{if(!disposed&&run===generation&&!ready.value)void useSource('编辑器资源暂时无法加载')},12000)
 }catch{await useSource('可视化编辑器暂时无法打开')}
}
function switchMode(next:Mode){if(blocked.value||!ready.value||next===mode.value&&!fallback.value)return;void create(next)}
function insertImage(url:string,alt='旅行影像'):boolean {
 if(blocked.value||!ready.value||!isManagedImageSource(url))return false
 const markdown=`![${alt.replace(/[\\[\]]/g,'\\$&')}](${url})`
 if(fallback.value){
  const value=source.value.slice(0,sourceSelection.from)+markdown+source.value.slice(sourceSelection.to);update(value);sourceSelection={from:sourceSelection.from+markdown.length,to:sourceSelection.from+markdown.length}
 }else if(editor){
  if(mode.value==='sv'){
   const area=root.value?.querySelector<HTMLTextAreaElement>('.vditor-sv')
   area?.focus();area?.setSelectionRange(sourceSelection.from,sourceSelection.to)
  }else if(selection&&root.value?.contains(selection.commonAncestorContainer)){const current=window.getSelection();current?.removeAllRanges();current?.addRange(selection)}
  editor.insertValue(markdown);userEdited=true;getContent()
 }else return false
 return true
}
async function upload(files:File[]){
 if(blocked.value||!ready.value)return
 const issue=validateEditorImages(files,props.galleryCount);uploadError.value=issue;if(issue)return
 rememberSelection();const run=generation,token=readToken();uploading.value=true;emit('busy',true)
 try{
  for(const file of files){
   const url=await uploadImage(file)
   if(disposed||run!==generation||token!==readToken()||props.disabled)return
   if(!isManagedImageSource(url))throw new Error('上传结果不是有效的受管图片')
   // Temporarily release the local busy gate only for insertion; parent remains busy.
   uploading.value=false;const inserted=insertImage(url,file.name.replace(/\.[^.]+$/,'')||'旅行影像');uploading.value=true
   if(!inserted)throw new Error('图片未能插入，正文已保留')
   emit('uploaded',{url,altText:file.name.replace(/\.[^.]+$/,'')||'旅行影像'})
  }
 }catch(e){if(!disposed&&run===generation){uploadError.value=e instanceof Error?e.message:'上传未能完成，请重试';if(e instanceof ApiError&&e.status===401)emit('session-expired')}}
 finally{if(!disposed){uploading.value=false;emit('busy',false)}}
}
function paste(event:ClipboardEvent){
 if(blocked.value){event.preventDefault();event.stopImmediatePropagation();return}
 const data=event.clipboardData;if(!data)return
 event.preventDefault();event.stopImmediatePropagation();rememberSelection()
 if(data.files.length){void upload([...data.files]);return}
 const html=data.getData('text/html'),text=data.getData('text/plain')
 let value=html&&editor?editor.html2md(sanitizeEditorHtml(html)):text
 if(mode.value!=='sv'&&sourceIssue(value))value=value.replace(/[\\`*_[\]<>!]/g,'\\$&')
 if(fallback.value){update(source.value.slice(0,sourceSelection.from)+value+source.value.slice(sourceSelection.to))}
 else if(editor){editor.insertValue(value);userEdited=true;getContent()}
}
function drop(event:DragEvent){
 if(!event.dataTransfer)return
 event.preventDefault();event.stopImmediatePropagation();if(blocked.value)return
 if(event.dataTransfer.files.length)void upload([...event.dataTransfer.files])
 else uploadError.value='拖入图片文件即可上传；文字可使用粘贴'
}
function keydown(event:KeyboardEvent){
 // All mode changes pass through the semantic guard, including Vditor's mode shortcuts.
 if((event.ctrlKey||event.metaKey)&&event.altKey&&['7','8','9'].includes(event.key)){event.preventDefault();event.stopImmediatePropagation();switchMode(({7:'wysiwyg',8:'ir',9:'sv'} as const)[event.key as '7'|'8'|'9'])}
}
watch(()=>props.disabled,()=>{if(!ready.value||fallback.value)return;if(blocked.value)editor?.disabled();else editor?.enable()})
watch(()=>props.modelValue,value=>{if(value===source.value)return;source.value=value;userEdited=false;void create(mode.value)})
const themeObserver=new MutationObserver(()=>{if(ready.value&&!fallback.value)editor?.setTheme(document.documentElement.dataset.theme==='dark'?'dark':'classic')})
onMounted(()=>{themeObserver.observe(document.documentElement,{attributes:true,attributeFilter:['data-theme']});void create('wysiwyg')})
onBeforeUnmount(()=>{disposed=true;generation++;themeObserver.disconnect();teardown();close()})
defineExpose({insertImage,rememberSelection,getContent})
</script>
<template>
 <section data-test="rich-editor" class="rich-editor" :data-mode="mode" aria-label="正文编辑器">
  <div class="editor-modes" role="group" aria-label="正文编辑模式"><span class="field-label !mb-0">攻略正文</span><div class="flex flex-wrap gap-1"><button v-for="option in [{value:'wysiwyg',label:'所见即所得'},{value:'ir',label:'即时渲染'},{value:'sv',label:'Markdown 源码'}]" :key="option.value" type="button" :data-test="`mode-${option.value}`" :aria-pressed="mode===option.value" :disabled="blocked||!ready" @click="switchMode(option.value as Mode)">{{ option.label }}</button></div></div>
  <button type="button" class="text-sm text-pine underline mb-3" @click="copySource">复制 Markdown 源码</button><p v-if="copyNotice" role="status" class="text-sm text-pine">{{ copyNotice }}</p>
  <p v-if="warning" role="status" class="my-3 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ warning }}</p>
  <p v-if="!ready" role="status" class="my-3 text-sm text-ink-soft">正在准备编辑器…</p>
  <div v-show="!fallback" ref="root" class="vditor-host" @input.capture="userEdited=true" @paste.capture="paste" @drop.capture="drop" @dragover.prevent @keydown.capture="keydown" />
  <textarea v-if="fallback" ref="fallbackArea" :value="source" data-test="guide-content" aria-label="Markdown 源码" class="field-input min-h-[28rem] font-mono text-sm" :readonly="blocked" @input="update(($event.target as HTMLTextAreaElement).value)" @select="rememberSelection" @blur="rememberSelection" />
  <p v-if="uploading" role="status" class="mt-2 text-sm text-pine">正在上传图片，请稍候…</p><p v-if="uploadError" role="alert" class="mt-2 text-sm text-ember">{{ uploadError }}</p>
  <button v-if="!fallback" type="button" class="btn-ghost mt-3" @click="previewImages">预览正文插图</button>
  <div class="mt-3 flex flex-wrap items-center justify-between gap-3 text-xs text-ink-soft"><span>图片可上传、粘贴或拖入；JPG / PNG / WebP，单张最大 5MB。</span><span :class="{'text-ember':count>100000}">{{ count }} / 100000</span></div>
  <ImageLightbox v-if="preview" :preview="preview" @close="close" />
 </section>
</template>
<style scoped>
.rich-editor{min-width:0}.editor-modes{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:.75rem;margin-bottom:.75rem}.editor-modes button{min-height:44px;padding:.5rem .7rem;border-radius:.5rem;font-size:.8rem;color:var(--color-pine-deep);cursor:pointer}.editor-modes button[aria-pressed=true]{background:var(--color-pine-soft)}.editor-modes button:disabled{opacity:.5;cursor:default}
.vditor-host {
  border-radius: var(--ui-radius-md);
  max-width: 100%;
  overflow: hidden;
  color: var(--ui-text-primary);
}
.vditor-host :deep(.vditor),
.vditor-host :deep(.vditor--dark) {
  --border-color: var(--ui-border-subtle);
  --toolbar-background-color: var(--ui-bg-muted);
  --panel-background-color: var(--ui-bg-elevated);
  --panel-shadow: var(--ui-shadow-lg);
  --textarea-background-color: var(--ui-bg-surface);
  --textarea-text-color: var(--ui-text-primary);
  --second-color: var(--ui-text-secondary);
  --toolbar-icon-color: var(--ui-text-secondary);
  --toolbar-icon-hover-color: var(--ui-accent);
  --toolbar-height: 44px;
  --heading-border-color: var(--ui-border-subtle);
  --blockquote-color: var(--ui-text-secondary);
  --blockquote-text-color: var(--ui-text-secondary);
  --blockquote-border-color: var(--ui-accent);
  --ir-heading-color: var(--ui-accent);
  --ir-title-color: var(--ui-text-secondary);
  --ir-bi-color: var(--ui-text-primary);
  --ir-link-color: var(--ui-link);
  --ir-bracket-color: var(--ui-text-secondary);
  --ir-paren-color: var(--ui-text-secondary);
  border-color: var(--ui-border-subtle);
  background: var(--ui-bg-surface);
}
.vditor-host :deep(.vditor-reset a){color:var(--ui-link)}
.vditor-host :deep(.vditor-toolbar){padding:8px!important;flex-wrap:wrap}.vditor-host :deep(.vditor-toolbar__item){padding:0}.vditor-host :deep(.vditor-toolbar__item>button),.vditor-host :deep(.vditor-toolbar__item>span){width:36px;height:40px}.vditor-host :deep(.vditor-reset){font-family:inherit;font-size:16px;line-height:1.9;color:var(--color-ink);padding:20px!important;overflow-wrap:anywhere}.vditor-host :deep(.vditor-sv){padding:20px!important;font-family:monospace;line-height:1.8;color:var(--color-ink);background:var(--color-surface);min-width:0;width:100%}.vditor-host :deep(img){max-width:100%;height:auto;border-radius:.5rem}.vditor-host :deep(pre){max-width:100%;overflow:auto}.vditor-host :deep(.vditor-content){min-width:0}.vditor-host :deep(table){display:block;max-width:100%;overflow:auto}.vditor-host :deep(.vditor-reset h1),.vditor-host :deep(.vditor-reset h2),.vditor-host :deep(.vditor-reset h3){font-family:var(--font-serif);color:var(--color-pine-deep)}
.vditor-host :deep(.vditor-reset blockquote){color:var(--color-ink-soft);background:var(--color-pine-soft);border-left:3px solid var(--color-pine);padding:.6rem 1rem}.vditor-host :deep(.vditor-reset pre){background:var(--color-code);color:var(--color-on-code)}
@media(max-width:430px){.vditor-host :deep(.vditor-reset),.vditor-host :deep(.vditor-sv){padding:12px!important}.editor-modes button{font-size:.75rem;padding:.5rem}}
</style>
