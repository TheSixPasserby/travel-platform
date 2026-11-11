async(page)=>{
 const base='http://127.0.0.1:15173',checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message));
 for(const type of ['CITY','SCENIC_SPOT']){
  const response=await page.request.get(base+'/api/v1/destinations?size=30&destinationType='+type),data=await response.json(),item=data.data.records.find(i=>i.coverImageUrl);
  for(const theme of ['light','dark'])for(const width of [360,390,430,1440]){
   await page.emulateMedia({colorScheme:theme,reducedMotion:'reduce'});await page.setViewportSize({width,height:844});await page.goto(base+'/destinations/'+item.id);await page.locator('[data-test="destination-gallery"]').waitFor();
   if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth))throw Error('destination overflow '+type+theme+width);
   if(width===390){await page.locator('[data-test="destination-gallery"] h2').scrollIntoViewIfNeeded();await page.waitForTimeout(180);await page.screenshot({path:'docs/m11/screenshots/destination-'+type.toLowerCase()+'-'+theme+'-390.png',animations:'disabled'});}
   if(!(await page.locator('[data-test="gallery-image"]').first().textContent()).includes('作者：'))throw Error('attribution lost');checks.push({type,theme,width});
  }
 }
 if(errors.length)throw Error(JSON.stringify(errors));return {checks,errors};
}
