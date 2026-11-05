// Usage : node tools/preview/shot.js <racine_depot> <sortie_prefixe> "<params1>" "<params2>"...
const {chromium}=require(process.env.PW||'playwright');
(async()=>{
 const http=require('http'),fs=require('fs'),path=require('path');
 const root=process.argv[2], out=process.argv[3], views=process.argv.slice(4);
 const srv=http.createServer((q,s)=>{const u=decodeURIComponent(q.url.split('?')[0]);const f=path.join(root,u);fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;}s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.json')?'application/json':'application/octet-stream'});s.end(d);});}).listen(0);
 const port=srv.address().port;
 const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 let i=0, ok=true;
 for(const v of views){
  const p=new URLSearchParams(v);const pg=await b.newPage({viewport:{width:+p.get('w')||1024,height:+p.get('h')||600}});
  await pg.goto(`http://localhost:${port}/tools/preview/preview.html?${v}`);
  await pg.waitForFunction(()=>document.title!=='Apercu 3D',null,{timeout:90000});
  const t=await pg.title(); console.log(v,'->',t); if(!t.startsWith('done')) ok=false;
  await pg.screenshot({path:`${out}_${i++}.png`}); await pg.close();
 }
 await b.close(); srv.close(); process.exit(ok?0:1);
})();
