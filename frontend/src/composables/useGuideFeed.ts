import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchGuides } from '@/api/guides'
import { ApiError } from '@/api/client'
import type { GuideSummary } from '@/types/guide'
import type { PageResult } from '@/types/api'
export type FeedOrder='recommended'|'latest'|'popular'
/** Shared URL-backed discovery/search pagination. Draft inputs never change an in-flight query. */
export function useGuideFeed(discovery=false){
 const route=useRoute(),router=useRouter()
 const keyword=ref(''),destinationId=ref<string|null>(null),tagId=ref(''),order=ref<FeedOrder>(discovery?'recommended':'latest'),page=ref(1)
 const result=ref<PageResult<GuideSummary>>({records:[],total:0,current:1,size:12,pages:0}),loading=ref(false),error=ref('')
 let version=0
 const query=computed(()=>{
  const text=(key:string)=>typeof route.query[key]==='string'?route.query[key] as string:''
  const requested=Number(text('page')),sort=text('sort')
  return {keyword:text('keyword'),destinationId:text('destinationId')||null,tagId:text('tagId'),page:Number.isSafeInteger(requested)&&requested>0?requested:1,order:(sort==='popular'||sort==='latest'?sort:discovery?'recommended':'latest') as FeedOrder}
 })
 async function load(){
  const request=++version,q=query.value;loading.value=true;error.value=''
  try{const data=await fetchGuides({page:q.page,size:12,keyword:q.keyword.trim()||undefined,destinationId:q.destinationId||undefined,tagId:q.tagId||undefined,sort:q.order==='latest'?'latest':'popular'});if(request===version)result.value=data}
  catch(e){if(request===version)error.value=e instanceof ApiError?e.message:'攻略暂时无法加载，请稍后重试'}finally{if(request===version)loading.value=false}
 }
 async function search(reset=true){
  const target={path:route.path,query:{page:String(reset?1:page.value),keyword:keyword.value.trim()||undefined,destinationId:destinationId.value||undefined,tagId:tagId.value||undefined,sort:order.value===(discovery?'recommended':'latest')?undefined:order.value}}
  if(router.resolve(target).fullPath===route.fullPath)await load();else await router.push(target)
 }
 function turn(value:number){page.value=value;void search(false)}
 function selectOrder(value:FeedOrder){order.value=value;void search()}
 function selectTag(value:string){tagId.value=tagId.value===value?'':value;void search()}
 function selectCity(value:string){destinationId.value=destinationId.value===value?null:value;void search()}
 watch(()=>route.fullPath,()=>{const q=query.value;keyword.value=q.keyword;destinationId.value=q.destinationId;tagId.value=q.tagId;page.value=q.page;order.value=q.order;void load()},{immediate:true})
 onBeforeUnmount(()=>version++)
 return {keyword,destinationId,tagId,order,page,result,loading,error,load,search,turn,selectOrder,selectTag,selectCity}
}
