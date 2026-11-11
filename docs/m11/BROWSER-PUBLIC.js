async (page) => {
  const base='http://127.0.0.1:15173', checks=[], errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const ensure=(condition,message)=>{if(!condition)throw new Error(message)};
  const dialog=()=>page.getByRole('dialog',{name:'图片预览'});
  async function shot(name){await page.screenshot({path:'docs/m11/screenshots/'+name+'.png',animations:'disabled'});}
  async function closed(){await dialog().waitFor({state:'detached'});ensure(await page.evaluate(()=>document.body.style.overflow===''),'scroll lock leaked');}
  await page.goto(base+'/guides/4');await page.locator('.safe-markdown img').nth(4).waitFor({state:'attached'});
  ensure(await page.locator('.safe-markdown img').count()===5,'real article five images missing');
  const trigger=page.locator('.safe-markdown img').nth(2);await trigger.click();await dialog().waitFor();
  ensure((await dialog().textContent()).includes('3 / 5'),'wrong initial index');
  ensure(await page.evaluate(()=>document.body.style.overflow==='hidden'),'background not locked');
  await page.keyboard.press('ArrowRight');ensure((await dialog().textContent()).includes('4 / 5'),'right arrow');
  await page.keyboard.press('ArrowLeft');ensure((await dialog().textContent()).includes('3 / 5'),'left arrow');
  for(let i=0;i<8;i++)await dialog().getByRole('button',{name:'下一张'}).click();
  ensure((await dialog().textContent()).includes('1 / 5'),'fast cycling');
  for(let i=0;i<6;i++){await page.keyboard.press('Tab');ensure(await page.evaluate(()=>document.querySelector('dialog').contains(document.activeElement)),'focus escaped');}
  await shot('lightbox-desktop-light');await page.keyboard.press('Escape');await closed();
  ensure(await trigger.evaluate(el=>el===document.activeElement),'focus not restored');checks.push('five-image group/keys/rapid cycling/focus/scroll');
  await page.getByRole('button',{name:'预览图片：成都两日慢行：茶馆与街巷',exact:true}).click();await dialog().waitFor();ensure(await dialog().getByRole('button',{name:'下一张'}).count()===0,'single cover paging');await dialog().getByRole('button',{name:'关闭图片预览'}).click();await closed();
  const contentImages=await page.locator('main .content-image').count();ensure(contentImages>=7,'cover/gallery trigger missing');
  await page.locator('main .content-image').nth(2).click();await dialog().waitFor();ensure((await dialog().textContent()).includes('/ 6'),'guide gallery group');await page.mouse.click(3,100);await closed();checks.push('guide cover/gallery/backdrop');
  const dest=await (await page.request.get(base+'/api/v1/destinations?size=30')).json();const city=dest.data.records.find(x=>x.destinationType==='CITY'&&x.coverImageUrl);ensure(city,'city fixture');
  await page.goto(base+'/destinations/'+city.id);await page.locator('[data-test="destination-gallery"]').waitFor();
  ensure((await page.locator('[data-test="destination-gallery"]').textContent()).includes('作者：'),'attribution');
  await page.locator('[data-test="gallery-image"] .content-image').first().click();await dialog().waitFor();await dialog().getByRole('button',{name:'下一张'}).click();await dialog().getByRole('button',{name:'关闭图片预览'}).click();await closed();checks.push('city gallery/attribution');
  const spots=await (await page.request.get(base+'/api/v1/destinations?size=30&destinationType=SCENIC_SPOT')).json();const spot=spots.data.records.find(x=>x.coverImageUrl);ensure(spot,'spot fixture');
  await page.goto(base+'/destinations/'+spot.id);await page.locator('main .content-image').first().click();await dialog().waitFor();await page.keyboard.press('Escape');await closed();checks.push('scenic cover');
  for(const theme of ['light','dark']){
    await page.emulateMedia({colorScheme:theme,reducedMotion:'reduce'});
    for(const width of [360,390,430,1440]){
      await page.setViewportSize({width,height:900});await page.goto(base+'/guides/4');await page.locator('.safe-markdown img').nth(4).waitFor({state:'attached'});
      ensure(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'article overflow '+theme+width);
      await page.locator('.safe-markdown img').nth(1).click();await dialog().waitFor();await dialog().locator('img').evaluate(img=>img.decode());
      const bounds=await dialog().locator('img').boundingBox();ensure(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=width+1&&bounds.y+bounds.height<=900,'lightbox image clipped '+JSON.stringify(bounds));
      ensure(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'lightbox overflow');
      if(width===390||width===1440)await shot('lightbox-'+theme+'-'+width);
      await page.keyboard.press('Escape');await closed();checks.push({theme,width,article:true,lightbox:true});
    }
  }
  await page.setViewportSize({width:390,height:844});await page.goto(base+'/guides/4');await page.locator('.safe-markdown img').first().click();await dialog().waitFor();
  const client=await page.context().newCDPSession(page);await client.send('Emulation.setTouchEmulationEnabled',{enabled:true});
  await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:300,y:400}]});await client.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:80,y:400}]});await client.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  ensure((await dialog().textContent()).includes('2 / 5'),'touch swipe failed');const closeBox=await dialog().getByRole('button',{name:'关闭图片预览'}).boundingBox();await client.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:closeBox.x+closeBox.width/2,y:closeBox.y+closeBox.height/2}]});await client.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await closed();await client.send('Emulation.setTouchEmulationEnabled',{enabled:false});await client.detach();checks.push('real CDP touch swipe/close');
  await page.goto(base+'/guides/4');const bad='/uploads/2026/11/2bb3103a54bc451d89a3d53e1686e002.jpg';
  await page.route('**'+bad,route=>route.abort());await page.locator('.safe-markdown img').nth(1).click();await dialog().waitFor();await page.keyboard.press('ArrowLeft');await dialog().getByRole('alert').waitFor();await shot('lightbox-failure-390');await page.keyboard.press('Escape');await closed();await page.unroute('**'+bad);checks.push('failed-image close');
  await page.locator('.safe-markdown img').first().click();await dialog().waitFor();await page.goto(base+'/destinations');await closed();checks.push('page navigation cleanup');
  ensure(errors.length===0,'browser errors '+JSON.stringify(errors));return {checks,errors};
}
