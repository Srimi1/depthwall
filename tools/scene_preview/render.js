// Renders app/src/main/assets/scene.frag through WebGL1 the way GlPainter does, one PNG per kind/progress.
// Usage: node tools/scene_preview/render.js [outDir] [kinds=1,2,3,4,5,6,7] [progress=0,1] [time=10] [motionX,motionY]
const fs=require('fs'),path=require('path'),{chromium}=require('playwright');
const root=path.resolve(__dirname,'../..');
const out=path.resolve(process.argv[2]||path.join(root,'build/scene-preview'));
const kinds=(process.argv[3]||'1,2,3,4,5,6,7').split(',').map(Number);
const progresses=(process.argv[4]||'0,1').split(',').map(Number);
const time=Number(process.argv[5]||10);
const motion=(process.argv[6]||'0,0').split(',').map(Number);
const names=['depth','earth','mars','saturn','moon','snow','geometry','sun'];
const frag=fs.readFileSync(process.env.FRAG||path.join(root,'app/src/main/assets/scene.frag'),'utf8');
(async()=>{
  fs.mkdirSync(out,{recursive:true});
  const browser=await chromium.launch();const page=await browser.newPage();
  await page.setContent('<canvas id="c" width="360" height="780"></canvas>');
  const shots=await page.evaluate(({frag,kinds,progresses,time,motion})=>{
    const gl=document.getElementById('c').getContext('webgl',{preserveDrawingBuffer:true});
    const vs='attribute vec2 position;varying vec2 uv;void main(){uv=vec2((position.x+1.0)*.5,(1.0-position.y)*.5);gl_Position=vec4(position,0.0,1.0);}';
    function sh(t,s){const x=gl.createShader(t);gl.shaderSource(x,s);gl.compileShader(x);if(!gl.getShaderParameter(x,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(x));return x;}
    const p=gl.createProgram();gl.attachShader(p,sh(gl.VERTEX_SHADER,vs));gl.attachShader(p,sh(gl.FRAGMENT_SHADER,frag));gl.linkProgram(p);
    if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw new Error(gl.getProgramInfoLog(p));
    gl.useProgram(p);
    const b=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,b);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
    const loc=gl.getAttribLocation(p,'position');gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,2,gl.FLOAT,false,0,0);
    ['backgroundTex','foregroundTex','clockTex'].forEach((n,i)=>{const t=gl.createTexture();gl.activeTexture(gl.TEXTURE0+i);gl.bindTexture(gl.TEXTURE_2D,t);
      gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,1,1,0,gl.RGBA,gl.UNSIGNED_BYTE,new Uint8Array([0,0,0,0]));gl.uniform1i(gl.getUniformLocation(p,n),i);});
    const u=n=>gl.getUniformLocation(p,n);
    gl.uniform2f(u('resolution'),360,780);gl.uniform2f(u('motion'),motion[0],motion[1]);gl.uniform2f(u('cropOffset'),.5,.5);
    gl.uniform1f(u('zoom'),1);gl.uniform1f(u('imageAspect'),1440/3120);gl.uniform1f(u('time'),time);
    const res=[];
    for(const k of kinds)for(const pr of progresses){gl.uniform1i(u('kind'),k);gl.uniform1f(u('progress'),pr);
      gl.viewport(0,0,360,780);gl.drawArrays(gl.TRIANGLE_STRIP,0,4);res.push({k,pr,url:gl.canvas.toDataURL('image/png')});}
    return res;
  },{frag,kinds,progresses,time,motion});
  for(const s of shots){const f=path.join(out,`${names[s.k]}-p${s.pr}.png`);fs.writeFileSync(f,Buffer.from(s.url.split(',')[1],'base64'));console.log(f);}
  await browser.close();
})().catch(e=>{console.error(e.message);process.exit(1);});
