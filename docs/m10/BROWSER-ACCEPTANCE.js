async (page) => {
  const base='http://127.0.0.1:15173', checks=[], errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const ensure=(condition,message)=>{if(!condition)throw new Error(message)};
  async function api(method,path,data,token) {
    const response=await page.request.fetch(base+'/api/v1'+path,{method,data,headers:token?{satoken:token}:{}});
    ensure(response.ok(),'API failed: '+method+' '+path+' '+response.status());
    const body=await response.json();ensure(body.code===0,'API business failure');return body.data;
  }
  async function screenshot(name) { await page.waitForTimeout(260); await page.screenshot({animations:'disabled',path:'output/playwright/m10-'+name+'.png',fullPage:false}); }
  async function closed() { await page.locator('[role="dialog"]').waitFor({state:'detached'}); }
  async function layout(state,theme,width) {
    await page.setViewportSize({width,height:900});
    await page.goto(base+'/');await page.locator('main h1').waitFor();
    if(state==='user')await page.locator('[data-test="account-toggle"]').waitFor();
    const measured=await page.evaluate(()=>{
      const row=document.querySelector('[data-test="header-row"]'),r=row.getBoundingClientRect();
      const targets=[...row.querySelectorAll('button,.login-button,.brand-link')].map(el=>el.getBoundingClientRect()).filter(r=>r.width>0);
      return {theme:document.documentElement.dataset.theme,width:innerWidth,scrollWidth:document.documentElement.scrollWidth,rowHeight:r.height,oneRow:targets.every(e=>e.top>=r.top&&e.bottom<=r.bottom),targetsInViewport:targets.every(e=>e.left>=0&&e.right<=innerWidth)};
    });
    ensure(measured.theme===theme&&measured.scrollWidth<=width&&measured.rowHeight===64&&measured.oneRow&&measured.targetsInViewport,'Layout failure '+JSON.stringify({state,theme,width,...measured}));
    checks.push({state,theme,width,...measured});
  }
  await page.emulateMedia({reducedMotion:'no-preference'});await page.goto(base);await page.evaluate(()=>{localStorage.removeItem('travel-notes.theme');sessionStorage.clear()});
  for(const theme of ['light','dark']){
    await page.emulateMedia({colorScheme:theme});
    for(const width of [360,390,430,768,1024,1440])await layout('anonymous',theme,width);
    if(theme==='dark')await screenshot('desktop-dark-1440');
  }
  await page.emulateMedia({colorScheme:'light'});await page.setViewportSize({width:390,height:844});await page.goto(base);await screenshot('anonymous-light-390');
  const username=('m10_'+Date.now().toString(36)+'_long_username_for_identity').slice(0,49), password='M10BrowserSynthetic42!';
  const user=await api('POST','/auth/register',{username,password,nickname:'林间旅人'.repeat(10)});
  await page.goto(base+'/login');await page.getByLabel('账号',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill(password);await page.locator('[data-test="login-submit"]').click();
  await page.locator('[data-test="account-toggle"]').waitFor();
  const token=await page.evaluate(()=>sessionStorage.getItem('travel-notes.token'));ensure(token,'Login did not persist session');
  for(const theme of ['light','dark']){
    await page.emulateMedia({colorScheme:theme});
    for(const width of [360,390,430,768,1024,1440])await layout('user',theme,width);
  }
  await page.setViewportSize({width:390,height:844});await page.goto(base+'/guides');await page.locator('main h1').waitFor();
  const menu=page.locator('[data-test="mobile-menu-toggle"]'),settings=page.locator('[data-test="settings-toggle"]'),account=page.locator('[data-test="account-toggle"]');
  await menu.click();await page.locator('#site-navigation').waitFor();
  ensure(await page.evaluate(()=>document.body.style.overflow==='hidden'&&document.querySelector('main').inert),'Drawer did not lock background');
  ensure(await page.locator('#site-navigation a[aria-current="page"]').textContent()==='攻略','Active route missing');
  await screenshot('drawer-dark-390');
  await page.keyboard.press('Escape');await closed();ensure(await menu.evaluate(el=>el===document.activeElement),'Drawer focus did not return');
  await menu.click();await page.locator('#site-navigation a[href="/destinations"]').click();await page.waitForURL('**/destinations');await closed();
  ensure(await page.evaluate(()=>!document.querySelector('main').inert&&document.body.style.overflow===''),'Drawer did not release background');
  await settings.click();await page.locator('#site-settings').waitFor();await page.locator('[data-theme-option="dark"]').click();await screenshot('settings-dark-390');
  await page.reload();ensure(await page.locator('html').getAttribute('data-theme')==='dark','Theme persistence lost');
  await settings.click();await page.locator('[data-theme-option="system"]').click();await page.emulateMedia({colorScheme:'light'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='light');
  await page.emulateMedia({colorScheme:'dark'});await page.waitForFunction(()=>document.documentElement.dataset.theme==='dark');
  await page.locator('#site-settings').focus();await page.keyboard.press('Shift+Tab');ensure(await page.locator('[data-theme-option="system"]').evaluate(el=>el===document.activeElement),'Focus escaped panel');
  await account.click();ensure(await page.locator('#site-settings').count()===0,'Panels not mutually exclusive');
  await page.locator('#site-account').waitFor();ensure((await page.locator('#site-account').textContent()).includes('@'+username),'Public username missing');await screenshot('account-dark-390');
  const accountBox=await page.locator('#site-account').boundingBox();ensure(accountBox.x>=0&&accountBox.x+accountBox.width<=390,'Account overflow');
  await page.mouse.click(3,700);await closed();
  await account.click();await page.route('**/api/v1/auth/logout',route=>route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:50001,message:'测试网络故障',data:null})}));
  await page.locator('[data-test="logout"]').click();await page.locator('[data-test="logout-retry"]').waitFor();ensure(await account.isVisible(),'Logout failure cleared session');
  await page.unroute('**/api/v1/auth/logout');await page.locator('[data-test="logout-retry"]').click();await closed();await page.locator('header .login-button').waitFor();
  await page.goto(base+'/login');await page.getByLabel('账号',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill(password);await page.locator('[data-test="login-submit"]').click();await account.waitFor();
  const nextToken=await page.evaluate(()=>sessionStorage.getItem('travel-notes.token'));
  const guide=await api('POST','/guides',{title:'M10 浏览器验收：成都两日慢行',content:'独立测试副本中的旅行建议。\n\n所有 IP 属地来自真实服务端安全降级，不伪造省份。',summary:'评论与作者身份的浏览器验收记录',status:'PUBLISHED'},nextToken);
  ensure(guide.author.username===username&&guide.ipRegion===null,'New API contract mismatch');
  await api('POST','/guides/'+guide.id+'/comments',{content:'茶馆与街巷之间，可以留一点休息时间。'},nextToken);
  const otherName='m10_other_'+Date.now().toString(36);await api('POST','/auth/register',{username:otherName,password,nickname:'云边旅人'});const other=await api('POST','/auth/login',{username:otherName,password});
  await api('POST','/guides/'+guide.id+'/comments',{content:'<img src=x onerror=alert(1)>\n长文本与换行安全展示。'},other.tokenValue);
  await page.goto(base+'/guides/'+guide.id);await page.locator('[data-test="comment-row"]').first().waitFor();
  ensure(await page.locator('[data-test="guide-author-badge"]').count()===1,'Author badge does not use IDs');
  ensure((await page.locator('main').textContent()).includes('IP 属地：未知'),'Unknown region missing');ensure(await page.locator('[data-test="comment-row"] img').count()===0,'Comment XSS rendered as image');
  await page.locator('[aria-label="攻略评论"]').scrollIntoViewIfNeeded();await screenshot('comments-dark-390');
  const draft='发送失败也要保留的旅行建议';await page.locator('textarea').fill(draft);
  await page.route('**/api/v1/guides/'+guide.id+'/comments',async route=>{if(route.request().method()==='POST')await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({code:50001,message:'测试评论失败',data:null})});else await route.continue()});
  await page.getByRole('button',{name:'发表评论',exact:true}).click();await page.getByRole('alert').waitFor();ensure(await page.locator('textarea').inputValue()===draft,'Failed comment draft lost');
  await page.unroute('**/api/v1/guides/'+guide.id+'/comments');await page.getByRole('button',{name:'发表评论',exact:true}).click();await page.getByRole('status').filter({hasText:'评论已发布'}).waitFor();
  const ownDelete=page.locator('[data-test^="delete-comment-"]').first();await ownDelete.click();await page.locator('[data-test^="confirm-delete-comment-"]').click();await page.getByRole('status').filter({hasText:'评论已删除'}).waitFor();
  for(let i=0;i<10;i++)await api('POST','/guides/'+guide.id+'/comments',{content:'服务端分页验收留言 '+i},other.tokenValue);
  await page.reload();await page.locator('[data-test="comments-next"]').waitFor();await page.locator('[data-test="comments-next"]').click();await page.getByRole('navigation',{name:'评论分页'}).getByText('2 / 2').waitFor();
  // An ephemeral response fixture tests admin navigation visibility; real backend still rejects this USER.
  await page.route('**/api/v1/auth/me',async route=>{const response=await route.fetch();const body=await response.json();body.data.role='ADMIN';await route.fulfill({response,json:body})});
  await page.goto(base);await account.waitFor();await menu.click();await page.locator('#site-navigation a[href="/admin/users"]').waitFor();
  const denied=await page.request.get(base+'/api/v1/admin/users',{headers:{satoken:nextToken}});ensure(denied.status()===403,'Backend admin boundary regressed');
  await page.unroute('**/api/v1/auth/me');await page.keyboard.press('Escape');await closed();
  await page.emulateMedia({reducedMotion:'reduce'});await settings.click();ensure(await page.locator('#site-settings').evaluate(el=>parseFloat(getComputedStyle(el).transitionDuration))<0.001,'Reduced motion ignored');await page.keyboard.press('Escape');await closed();
  ensure(errors.length===0,'Uncaught browser errors: '+JSON.stringify(errors));
  const evidence={checkedLayouts:checks.length,checks,themePersistence:true,systemThemeChanges:true,drawerFocusScrollInert:true,overlayMutualExclusion:true,backdropEscape:true,longIdentity:true,realLoginLogout:true,logoutFailurePreservesSession:true,publicUsername:true,unknownRegion:true,commentAuthorBadge:true,commentXss:true,commentFailureDraft:true,commentCreateDeletePagination:true,adminVisibilityFixture:true,realAdminForbidden:true,reducedMotion:true,uncaughtErrors:errors.length,isolatedDatabase:true};
  await page.evaluate(value=>window.__m10Evidence=value,evidence);return evidence;
}
