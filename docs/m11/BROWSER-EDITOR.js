async (page) => {
  const base='http://127.0.0.1:15173', checks=[], errors=[];
  const ensure=(condition,message)=>{if(!condition)throw new Error(message)};
  page.on('pageerror',e=>errors.push(e.message));
  const credentials={username:'m11_'+Date.now().toString(36),password:'M11SyntheticOnly42!',nickname:'M11验收旅人'};
  const api=async(method,path,data,token)=>{const res=await page.request.fetch(base+'/api/v1'+path,{method,data,headers:token?{satoken:token}:{}});const body=await res.json();ensure(res.ok()&&body.code===0,'API '+method+' '+path+' '+res.status());return body.data;};
  const shot=async(name)=>page.screenshot({path:'docs/m11/screenshots/'+name+'.png',animations:'disabled'});
  await page.goto(base);await page.evaluate(()=>sessionStorage.clear());await page.reload();
  await api('POST','/auth/register',credentials);
  await page.goto(base+'/login');await page.getByLabel('账号',{exact:true}).fill(credentials.username);await page.getByLabel('密码',{exact:true}).fill(credentials.password);await page.locator('[data-test="login-submit"]').click();await page.locator('[data-test="account-toggle"]').waitFor();
  const token=await page.evaluate(()=>sessionStorage.getItem('travel-notes.token'));ensure(token,'missing session');
  const old=await api('GET','/guides/4');
  const seeded=await api('POST','/guides',{title:'M11旧攻略兼容性副本',content:old.content,summary:old.summary,destinationId:old.destination?.id??null,coverImageUrl:old.coverImageUrl,travelDays:old.travelDays,budgetPerPerson:old.budgetPerPerson,season:old.season,tagIds:old.tags.map(t=>t.id),images:old.images.map(i=>({url:i.url,altText:i.altText})),scenicSpotIds:old.scenicSpots.map(s=>s.id),status:'DRAFT'},token);
  await page.goto(base+'/guides/'+seeded.id+'/edit');const visual=page.locator('[data-test="visual-content"]');await visual.waitFor();
  ensure(await visual.isVisible(),'old article did not load visually');await page.getByRole('button',{name:'撤销',exact:true}).click();ensure((await visual.textContent()).includes('成都适合'),'initial undo deleted old content');
  await page.locator('[data-test="save-guide"]').click();await page.getByRole('status').filter({hasText:'修改已保存'}).waitFor();
  let saved=await api('GET','/users/me/guides/'+seeded.id,undefined,token);ensure(saved.content===old.content,'no-op save changed raw markdown');ensure(saved.status==='DRAFT'&&saved.publishedAt===null&&saved.author.id===seeded.author.id,'metadata drift');checks.push('old real article no-op save exact Markdown/author/status');
  await visual.click();await page.keyboard.press('ControlOrMeta+End');await page.keyboard.press('Enter');await page.keyboard.type('M11视觉新增段落');
  await page.locator('[data-test="source-mode"]').click();const source=page.locator('[data-test="guide-content"]');ensure((await source.inputValue()).includes('M11视觉新增段落'),'visual sync missing');
  await page.locator('[data-test="visual-mode"]').click();await page.locator('[data-test="save-guide"]').click();await page.getByRole('status').filter({hasText:'修改已保存'}).waitFor();await page.reload();await visual.waitFor();ensure((await visual.textContent()).includes('M11视觉新增段落'),'save/refresh missing');checks.push('visual edit/source/visual/save/refresh');
  for(const theme of ['light','dark']){
    await page.emulateMedia({colorScheme:theme,reducedMotion:'reduce'});
    for(const width of [360,390,430,1440]){
      await page.setViewportSize({width,height:900});await page.locator('.editor-mode').evaluate(el=>el.scrollIntoView({block:'start'}));await page.mouse.wheel(0,-85);
      ensure(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'editor overflow '+theme+width);
      const toolbar=await page.locator('[role="toolbar"]').boundingBox();ensure(toolbar.x>=0&&toolbar.x+toolbar.width<=width,'toolbar outside viewport');
      if(width===390||width===1440)await shot('editor-'+theme+'-'+width);checks.push({theme,width,editor:true});
    }
  }
  await page.setViewportSize({width:390,height:844});
  await page.locator('[data-test="source-mode"]').click();await source.fill('前文\n\n后文');await source.focus();for(let n=0;n<4;n++)await source.press('ArrowLeft');ensure(await source.evaluate(el=>el.selectionStart===2),'source selection staging');
  await page.getByLabel('下一张插图的说明').fill('光标处的试验图片');
  const input=page.locator('[data-test="image-upload-input"]').nth(1);await input.setInputFiles('backend/src/test/resources/fixtures/images/green-8x8.webp');await page.getByRole('status').filter({hasText:'上传成功'}).waitFor();
  const inserted=await source.inputValue();ensure(inserted.startsWith('前文![')&&inserted.endsWith('\n\n后文'),'image was appended instead of inserted '+inserted.slice(0,100));ensure(inserted.includes('光标处的试验图片'),'alt lost');
  await page.locator('[data-test="visual-mode"]').click();await visual.locator('img[src]').last().click();await page.getByLabel('所选图片的说明').fill('编辑后的图片说明');await page.getByLabel('所选图片的说明').press('Tab');
  await page.locator('[data-test="source-mode"]').click();ensure((await source.inputValue()).includes('编辑后的图片说明'),'image alt editing');await page.locator('[data-test="save-guide"]').click();await page.getByRole('status').filter({hasText:'修改已保存'}).waitFor();checks.push('real managed upload/current cursor/alt editing/gallery record');
  await page.locator('[data-test="visual-mode"]').click();await visual.fill('可视前文可视后文');for(let n=0;n<4;n++)await visual.press('ArrowLeft');const visualPrefix=await visual.evaluate(el=>{const selection=getSelection(),range=document.createRange();range.setStart(el,0);range.setEnd(selection.anchorNode,selection.anchorOffset);return range.toString()});await input.setInputFiles('backend/src/test/resources/fixtures/images/green-8x8.webp');await page.locator('[data-test="image-upload-status"]').nth(1).filter({hasText:'上传成功'}).waitFor();await page.locator('[data-test="source-mode"]').click();const visualInsert=await source.inputValue();ensure(visualInsert.startsWith(visualPrefix+'![')&&visualInsert.trim().endsWith('可视前文可视后文'.slice(visualPrefix.length)),'visual cursor upload wrong '+JSON.stringify({visualPrefix,visualInsert}));checks.push('default visual cursor upload');await page.locator('[data-test="visual-mode"]').click();await visual.click();await page.keyboard.type('不可丢失的未保存文字');
  await page.route('**/api/v1/files/images',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:50001,message:'合成上传失败',data:null})}));await input.setInputFiles('backend/src/test/resources/fixtures/images/green-8x8.webp');await page.locator('[data-test="image-upload-status"]').nth(1).filter({hasText:'请重试'}).waitFor();ensure((await visual.textContent()).includes('不可丢失的未保存文字'),'upload failure lost text');await page.unroute('**/api/v1/files/images');checks.push('503 upload preserves draft');
  await page.context().grantPermissions(['clipboard-read','clipboard-write']);
  await page.evaluate(async()=>{window.__m11xss=0;await navigator.clipboard.write([new ClipboardItem({'text/html':new Blob(['<p>安全粘贴<script>window.__m11xss=1</script><img src="https://evil.invalid/x.jpg" onerror="window.__m11xss=2"><a href="javascript:alert(1)">危险链接</a></p>'],{type:'text/html'}),'text/plain':new Blob(['安全粘贴'],{type:'text/plain'})})])});
  await visual.click();await page.keyboard.press('ControlOrMeta+End');await page.keyboard.press('ControlOrMeta+V');ensure(await page.evaluate(()=>window.__m11xss===0&&!document.querySelector('.editor-document script')&&!document.querySelector('.editor-document img[src^="https:"]')),'unsafe clipboard execution');checks.push('real clipboard HTML paste safety');
  await page.locator('[data-test="source-mode"]').click();const unusual='<div>必须完整保留</div>\n\n[^1]: 原样脚注';await source.fill(unusual);await page.locator('[data-test="visual-mode"]').click();ensure(await source.isVisible()&&(await source.inputValue())===unusual,'unsupported data loss');await shot('source-fallback-390');checks.push('unsupported syntax remains complete source');
  await page.route('**/api/v1/files/images',route=>route.fulfill({status:401,contentType:'application/json',body:JSON.stringify({code:40101,message:'合成会话过期',data:null})}));await input.setInputFiles('backend/src/test/resources/fixtures/images/green-8x8.webp');await page.getByRole('alert').filter({hasText:'未保存内容已保留'}).waitFor();ensure(page.url().includes('/edit')&&(await source.inputValue())===unusual,'401 lost draft');ensure(await page.evaluate(()=>!sessionStorage.getItem('travel-notes.token')),'stale token retained');await page.unroute('**/api/v1/files/images');checks.push('401 clears token and preserves complete draft');
  const anonymous=await page.request.post(base+'/api/v1/guides',{data:{title:'不允许',content:'x'}});ensure(anonymous.status()===401,'anonymous write');const forbidden=await page.request.put(base+'/api/v1/guides/4',{headers:{satoken:token},data:{title:'不允许',content:'x'}});ensure([403,404].includes(forbidden.status()),'non-owner update');checks.push('anonymous/non-owner write denied');
  ensure(errors.length===0,'browser script errors '+JSON.stringify(errors));return {checks,errors,fixtureGuideId:seeded.id,isolatedDatabase:true};
}
