<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { commandsCtx, editorViewCtx, parserCtx, serializerCtx } from '@milkdown/kit/core'
import type { Editor } from '@milkdown/kit/core'
import { TextSelection, NodeSelection } from '@milkdown/kit/prose/state'
import type { SelectionBookmark, Transaction } from '@milkdown/kit/prose/state'
import { replaceAll } from '@milkdown/kit/utils'
import { wrapInHeadingCommand, toggleStrongCommand, toggleEmphasisCommand, wrapInBulletListCommand, wrapInOrderedListCommand, wrapInBlockquoteCommand, insertHrCommand, toggleLinkCommand, insertImageCommand, updateImageCommand } from '@milkdown/kit/preset/commonmark'
import { undoCommand, redoCommand } from '@milkdown/kit/plugin/history'
import { createMarkdownEditor, editorDocumentIssue, roundTripIssue, sourceIssue } from '@/utils/guideEditor'
import { isManagedImageSource, isSafeLinkTarget } from '@/utils/markdown'
const props=defineProps<{modelValue:string;disabled?:boolean}>()
const emit=defineEmits<{'update:modelValue':[value:string]}>()
const root=ref<HTMLElement|null>(null),sourceArea=ref<HTMLTextAreaElement|null>(null)
const mode=ref<'visual'|'source'>('visual'),ready=ref(false),warning=ref(''),linkOpen=ref(false),linkUrl=ref(''),linkError=ref(''),imageAlt=ref(''),selectedImage=ref(false)
const source=ref(props.modelValue),count=computed(()=>[...source.value].length)
let editor:Editor|undefined,syncing=false,disposed=false,initialImport=true,visualSource='',bookmark:SelectionBookmark|null=null,sourceSelection={from:0,to:0},visualPosition=1
function changed(value:string){
  if(syncing||disposed)return
  source.value=value;visualSource=value;emit('update:modelValue',value)
}
function selection(transaction:Transaction){
  if(bookmark&&transaction.docChanged)bookmark=bookmark.map(transaction.mapping)
  if(!editor||!ready.value)return
  editor.action(ctx=>{const view=ctx.get(editorViewCtx);selectedImage.value=view.state.selection instanceof NodeSelection&&view.state.selection.node.type.name==='image';if(selectedImage.value)imageAlt.value=(view.state.selection as NodeSelection).node.attrs.alt??''})
}
function sourceChanged(event:Event){source.value=(event.target as HTMLTextAreaElement).value;emit('update:modelValue',source.value);rememberSource()}
function rememberSource(){const area=sourceArea.value;if(area)sourceSelection={from:area.selectionStart,to:area.selectionEnd}}
function rememberSelection(){
  if(mode.value==='source')rememberSource()
  else editor?.action(ctx=>{bookmark=ctx.get(editorViewCtx).state.selection.getBookmark()})
}
function importSource(value:string):boolean{
  if(!editor)return false
  let issue=sourceIssue(value)
  if(!issue)editor.action(ctx=>{
    const doc=ctx.get(parserCtx)(value)
    issue=doc?(editorDocumentIssue(doc)||roundTripIssue(value,ctx.get(serializerCtx)(doc))):'正文无法完整导入'
    if(!issue&&doc&&value!==visualSource){
      syncing=true
      try{editor!.action(replaceAll(value,initialImport))}finally{syncing=false}
      // 标题插件会生成仅用于编辑器的 id；以实际序列化语义确认写入，避免误判。
      if(roundTripIssue(value,ctx.get(serializerCtx)(ctx.get(editorViewCtx).state.doc)))issue='可视化安全校验未接受本次导入'
      else visualSource=value
    }
  })
  if(issue){warning.value=`${issue}；为避免丢失内容，已保留 Markdown 源码。可继续编辑和保存原文。`;mode.value='source';return false}
  warning.value=''
  initialImport=false
  return true
}
function switchMode(next:'visual'|'source'){
  if(!ready.value||props.disabled||next===mode.value)return
  if(next==='source'){
    editor?.action(ctx=>{visualPosition=ctx.get(editorViewCtx).state.selection.from})
    mode.value='source'
    // 靠近视觉选区的文字定位源码；视觉编辑器保留挂载以保留撤销历史。
    editor?.action(ctx=>{const view=ctx.get(editorViewCtx),near=view.state.doc.textBetween(Math.max(0,visualPosition-15),visualPosition,' ');const found=near?source.value.indexOf(near):-1;sourceSelection={from:found<0?sourceSelection.from:found+near.length,to:found<0?sourceSelection.to:found+near.length}})
    queueMicrotask(()=>{sourceArea.value?.focus();sourceArea.value?.setSelectionRange(sourceSelection.from,sourceSelection.to)})
  }else if(importSource(source.value)){
    mode.value='visual';editor?.action(ctx=>{const view=ctx.get(editorViewCtx);view.dispatch(view.state.tr.setSelection(TextSelection.near(view.state.doc.resolve(Math.min(visualPosition,view.state.doc.content.size)))));view.focus()})
  }
}
function command(name:'heading'|'bold'|'italic'|'bullet'|'ordered'|'quote'|'rule'|'undo'|'redo',level=2){
  if(props.disabled||!ready.value||mode.value!=='visual')return
  editor?.action(ctx=>{
    const commands=ctx.get(commandsCtx)
    if(name==='heading')commands.call(wrapInHeadingCommand.key,level)
    else commands.call(({bold:toggleStrongCommand,italic:toggleEmphasisCommand,bullet:wrapInBulletListCommand,ordered:wrapInOrderedListCommand,quote:wrapInBlockquoteCommand,rule:insertHrCommand,undo:undoCommand,redo:redoCommand}[name]).key)
    ctx.get(editorViewCtx).focus()
  })
}
function addLink(){
  if(!isSafeLinkTarget(linkUrl.value)){linkError.value='请输入完整的 http/https 地址或站内路径';return}
  editor?.action(ctx=>{const view=ctx.get(editorViewCtx);if(bookmark)view.dispatch(view.state.tr.setSelection(bookmark.resolve(view.state.doc)));ctx.get(commandsCtx).call(toggleLinkCommand.key,{href:linkUrl.value});view.focus()})
  bookmark=null;linkOpen.value=false;linkError.value=''
}
function insertImage(url:string,alt='旅行影像'):boolean{
  if(!isManagedImageSource(url)||props.disabled)return false
  if(mode.value==='source'){
    const escaped=alt.replace(/[\\\[\]]/g,'\\$&'),markdown=`![${escaped}](${url})`
    source.value=source.value.slice(0,sourceSelection.from)+markdown+source.value.slice(sourceSelection.to)
    sourceSelection={from:sourceSelection.from+markdown.length,to:sourceSelection.from+markdown.length}
    emit('update:modelValue',source.value)
    queueMicrotask(()=>{sourceArea.value?.focus();sourceArea.value?.setSelectionRange(sourceSelection.from,sourceSelection.to)})
  }else{
    if(!ready.value)return false
    editor?.action(ctx=>{const view=ctx.get(editorViewCtx);if(bookmark)view.dispatch(view.state.tr.setSelection(bookmark.resolve(view.state.doc)));ctx.get(commandsCtx).call(insertImageCommand.key,{src:url,alt});view.focus()})
  }
  bookmark=null;return true
}
function updateAlt(){if(props.disabled)return;editor?.action(ctx=>ctx.get(commandsCtx).call(updateImageCommand.key,{alt:imageAlt.value}))}
watch(()=>props.disabled,()=>{editor?.action(ctx=>ctx.get(editorViewCtx).setProps({editable:()=>!props.disabled}))})
watch(()=>props.modelValue,value=>{if(value===source.value)return;source.value=value;if(ready.value){if(importSource(value))mode.value='visual'}})
onMounted(async()=>{
  try{
    const instance=await createMarkdownEditor(root.value!,'',()=>Boolean(props.disabled),changed,selection)
    if(disposed){await instance.destroy();return}
    editor=instance;ready.value=true
    if(!importSource(source.value))mode.value='source'
  }catch{warning.value='可视化编辑器暂时无法打开；为避免丢失内容，已保留 Markdown 源码。';mode.value='source';ready.value=true}
})
onBeforeUnmount(()=>{disposed=true;void editor?.destroy()})
defineExpose({insertImage,rememberSelection})
</script>
<template>
  <section class="markdown-editor" aria-label="正文编辑器">
    <div class="editor-mode"><span class="field-label !mb-0">攻略正文</span><div class="flex flex-wrap gap-1"><button type="button" data-test="visual-mode" :aria-pressed="mode==='visual'" :disabled="disabled||!ready" @click="switchMode('visual')">可视化</button><button type="button" data-test="source-mode" :aria-pressed="mode==='source'" :disabled="disabled||!ready" @click="switchMode('source')">Markdown 源码</button></div></div>
    <p class="my-3 text-xs text-ink-soft">直接输入正文，选中文字即可排版。插图请使用下方上传按钮。</p>
    <p v-if="warning" role="status" class="mb-3 rounded-xl bg-ember/10 p-3 text-sm text-ember">{{ warning }}</p>
    <div v-show="mode==='visual'" class="visual-editor">
      <div class="editor-toolbar" role="toolbar" aria-label="正文排版" @mousedown.prevent>
        <button v-for="level in [1,2,3]" :key="level" type="button" :disabled="disabled||!ready" :aria-label="`${level}级标题`" @click="command('heading',level)">H{{ level }}</button>
        <button type="button" :disabled="disabled||!ready" aria-label="粗体" @click="command('bold')"><strong>粗体</strong></button><button type="button" :disabled="disabled||!ready" aria-label="斜体" @click="command('italic')"><em>斜体</em></button>
        <button type="button" :disabled="disabled||!ready" @click="command('bullet')">无序列表</button><button type="button" :disabled="disabled||!ready" @click="command('ordered')">有序列表</button><button type="button" :disabled="disabled||!ready" @click="command('quote')">引用</button><button type="button" :disabled="disabled||!ready" @click="command('rule')">分隔线</button>
        <button type="button" :disabled="disabled||!ready" @click="rememberSelection();linkOpen=!linkOpen">链接</button><button type="button" :disabled="disabled||!ready" @click="command('undo')">撤销</button><button type="button" :disabled="disabled||!ready" @click="command('redo')">重做</button>
      </div>
      <div v-if="linkOpen" class="editor-properties"><label for="editor-link" class="field-label">选中文字的链接地址</label><input id="editor-link" v-model="linkUrl" type="url" class="field-input" placeholder="https://" :disabled="disabled" @keydown.enter.prevent="addLink" /><p v-if="linkError" role="alert" class="text-sm text-ember">{{ linkError }}</p><button type="button" class="btn-ghost" :disabled="disabled" @click="addLink">应用链接</button><button type="button" class="btn-ghost" @click="linkOpen=false">取消</button></div>
      <div v-if="selectedImage" class="editor-properties"><label for="editor-image-alt" class="field-label">所选图片的说明</label><input id="editor-image-alt" v-model="imageAlt" class="field-input" :disabled="disabled" @change="updateAlt" /></div>
      <p v-if="!ready" role="status" class="p-4 text-sm text-ink-soft">正在准备编辑器…</p>
      <div ref="root" class="editor-document" :aria-busy="!ready" />
    </div>
    <textarea v-show="mode==='source'" id="editor-content" ref="sourceArea" :value="source" data-test="guide-content" aria-label="Markdown 源码" class="field-input min-h-[28rem] font-mono text-sm leading-relaxed" rows="20" :disabled="disabled" @input="sourceChanged" @select="rememberSource" @blur="rememberSource" />
    <p class="mt-2 text-right text-xs" :class="count>100000?'text-ember':'text-ink-soft'">{{ count }} / 100000</p>
  </section>
</template>
<style scoped>
.markdown-editor{min-width:0}
.editor-mode{display:flex;align-items:center;justify-content:space-between;gap:.75rem;flex-wrap:wrap}
.editor-mode button,.editor-toolbar button{border-radius:.5rem;padding:.5rem .65rem;min-height:40px;color:var(--color-pine-deep);font-size:.8rem;cursor:pointer}
.editor-mode button[aria-pressed=true]{background:var(--color-pine-soft)}
.visual-editor{border:1px solid color-mix(in srgb,var(--color-pine) 25%,transparent);border-radius:1rem;overflow:hidden}
.editor-toolbar{display:flex;flex-wrap:wrap;gap:.15rem;padding:.5rem;background:var(--color-cream-deep);border-bottom:1px solid color-mix(in srgb,var(--color-pine) 20%,transparent)}
.editor-toolbar button:hover{background:var(--color-pine-soft)}
button:disabled{opacity:.5;cursor:default}
.editor-properties{padding:1rem;border-bottom:1px solid var(--color-pine-soft)}
.editor-document{padding:1.25rem;min-height:28rem;color:var(--color-ink);overflow-wrap:anywhere}
.editor-document :deep(.ProseMirror){min-height:25rem;outline:none;white-space:pre-wrap;word-break:break-word;line-height:1.9}
.editor-document :deep(p){margin:.7em 0}
.editor-document :deep(h1){font-size:1.8rem}.editor-document :deep(h2){font-size:1.5rem}.editor-document :deep(h3){font-size:1.2rem}
.editor-document :deep(h1),.editor-document :deep(h2),.editor-document :deep(h3){font-family:var(--font-serif);font-weight:600;color:var(--color-pine-deep);margin:1em 0 .5em;line-height:1.4}
.editor-document :deep(ul){list-style:disc;padding-left:1.5rem}.editor-document :deep(ol){list-style:decimal;padding-left:1.5rem}
.editor-document :deep(blockquote){border-left:3px solid var(--color-pine);padding:.5rem 1rem;background:var(--color-pine-soft)}
.editor-document :deep(a){color:var(--color-pine);text-decoration:underline}
.editor-document :deep(img){display:inline-block;max-width:100%;height:auto;border-radius:.75rem}
.editor-document :deep(pre){overflow:auto;max-width:100%;padding:1rem;background:var(--color-code);color:var(--color-on-code);border-radius:.75rem}
.editor-document :deep(table){display:block;max-width:100%;overflow:auto;border-collapse:collapse}
.editor-document :deep(td),.editor-document :deep(th){border:1px solid var(--color-pine);padding:.5rem}
.editor-document :deep(.ProseMirror-selectednode){outline:3px solid var(--color-pine);outline-offset:2px}
@media(max-width:430px){.editor-document{padding:.8rem}.editor-toolbar button{padding:.5rem;font-size:.75rem}}
</style>
