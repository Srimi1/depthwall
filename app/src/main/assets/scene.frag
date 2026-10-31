precision highp float;
varying vec2 uv;
uniform vec2 resolution;
uniform vec2 motion;
uniform vec2 cropOffset;
uniform float zoom;
uniform float imageAspect;
uniform float time;
uniform float progress;
uniform int kind;
uniform sampler2D backgroundTex;
uniform sampler2D foregroundTex;
uniform sampler2D clockTex;

const vec3 PAPAYA=vec3(1.0,.42,.12);
float hash(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }
float hash1(float x) { return fract(sin(x*127.1)*43758.5453); }
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float noise1(float x) { float i=floor(x), f=fract(x); return mix(hash1(i),hash1(i+1.0),f*f*(3.0-2.0*f)); }
float fbm(vec3 p) { float v=0.0, a=.5; for(int i=0;i<5;i++){v+=noise(p)*a;p=p*2.03+vec3(7.1,4.3,1.7);a*=.5;}return v; }
mat2 rotate(float a) { return mat2(cos(a),-sin(a),sin(a),cos(a)); }
vec3 sky(vec2 p) {
    vec2 cell=floor(p*650.0);float n=hash(vec3(cell,19.0));
    float star=pow(n,180.0)*.65*(.75+.25*sin(time*1.7+n*400.0));
    return vec3(star*.7,star*.8,star);
}
// One crater per cell keeps the cost at four hashes per scale; x shades the bowl and rim, y darkens floors.
vec2 craters(vec3 p, vec3 light) {
    vec3 cell=floor(p);float h=hash(cell);
    if(h<.45) return vec2(0.0,1.0);
    vec3 o=(vec3(hash(cell+3.1),hash(cell+7.3),hash(cell+1.7))-.5)*.3;
    vec3 d=fract(p)-.5-o;float r=.08+.12*fract(h*7.0),dist=length(d)/r;
    float bowl=1.0-smoothstep(.75,1.0,dist),rim=exp(-(dist-1.05)*(dist-1.05)*28.0);
    float side=dot(d/max(length(d),1e-4),light);
    return vec2(-side*bowl*.45+rim*max(0.0,side)*.55,1.0-bowl*.18);
}
// Spin eases back to a fixed longitude while unlocking so the settled surface view is static.
float settle(float speed) { float a=time*speed;a-=6.2832*floor(a/6.2832+.5);return a*(1.0-progress); }
vec3 globe(vec2 p) {
    float e=progress;bool sun=kind==7;
    vec3 col=sky(p+motion*.012)*(1.0-e*.6);
    float radius=sun?mix(.56,1.25,e):.56*exp(e*1.85);
    float top=sun?mix(-.12,-.48,e):mix(-.16,-.6,e);
    vec2 center=vec2(motion.x*.07*(1.0-e*.5),top+radius+motion.y*.05);
    vec2 q=(p-center)/radius;float r2=dot(q,q),r=sqrt(r2);
    vec3 light=normalize(mix(kind==2?vec3(-.7,.55,.45):vec3(-.75,.45,.5),vec3(-.55,.8,.22),e));
    vec2 rimDir=q/max(r,1e-4);float rimLight=smoothstep(-.45,.7,dot(vec3(rimDir.x,-rimDir.y,0.0),light));
    if(r<1.0) {
        vec3 n=vec3(q.x,-q.y,sqrt(1.0-r2)),s=n;
        s.yz=rotate(-.8)*s.yz;s.xz=rotate(settle(.022)+(kind==1?4.8:.8))*s.xz;
        float ndl=dot(n,light),day=smoothstep(-.12,.22,ndl),diffuse=max(ndl,0.0);
        float fresnel=pow(1.0-n.z,3.0),detail=noise(s*52.0)+noise(s*140.0)*.5-.75,haze=exp(-(p.y-top)*mix(30.0,9.0,e));
        if(kind==1) {
            float f=fbm(s*2.4+vec3(1.3,0,0))+detail*.08*e;
            float land=smoothstep(.5,.54,f);
            vec3 ocean=mix(vec3(.005,.03,.1),vec3(.02,.16,.27),smoothstep(.38,.505,f));
            vec3 ground=mix(vec3(.05,.15,.04),vec3(.34,.25,.12),smoothstep(.56,.68,f+abs(s.y)*.1-.04+detail*.1));
            ground=mix(ground,vec3(.42,.36,.25),smoothstep(.68,.78,f));
            vec3 mat=mix(ocean,ground,land);
            mat=mix(mat,vec3(.8,.86,.92),smoothstep(.95,.97,abs(s.y)+(f-.5)*.3));
            vec3 drift=vec3(settle(.009)*2.0,0,0);
            float clouds=smoothstep(.55,.8,fbm(s*vec3(4.5,7.0,4.5)+drift+detail*.05));
            vec3 h=normalize(light+vec3(0,0,1));
            float glint=pow(max(dot(n,h),0.0),160.0)*(1.0-land)*(1.0-clouds)*.7;
            col=mat*(diffuse*1.35+.006)+glint*vec3(1.0,.88,.7)*day;
            col=mix(col,vec3(.9,.93,.98)*(diffuse*1.05+.004),clouds*.88);
            float cities=land*(1.0-day)*(1.0-clouds*.8)*smoothstep(.42,.8,noise(s*11.0));
            col+=vec3(1.0,.6,.25)*cities*smoothstep(.55,.95,noise(s*mix(170.0,60.0,e)))*1.3;
            col+=vec3(.12,.32,.85)*max(fresnel,haze*.6)*(.3+diffuse)*rimLight;
        } else if(kind==2) {
            float f=fbm(s*2.8)+detail*.1*e;
            vec3 mat=mix(vec3(.11,.04,.022),vec3(.58,.22,.08),smoothstep(.36,.6,f));
            mat=mix(mat,vec3(.78,.44,.24),smoothstep(.6,.8,f));
            vec2 c1=craters(s*5.0,light),c2=craters(s*13.0+3.7,light),c3=craters(s*31.0+8.2,light);
            float shade=1.0+c1.x+c2.x*.7+c3.x*.5*e;mat*=c1.y*c2.y;
            mat=mix(mat,vec3(.86,.8,.74),smoothstep(.975,.99,abs(s.y)+f*.04));
            col=mat*(diffuse*1.5*shade+.004);
            col=mix(col,vec3(.75,.38,.18)*(diffuse+.02),max(pow(1.0-n.z,3.0)*(1.0-e),haze)*.5);
        } else if(kind==4) {
            float f=fbm(s*2.2)+detail*.1*e;
            vec3 mat=vec3(.52,.53,.55)*mix(.5,1.0,smoothstep(.36,.46,f));
            vec2 c1=craters(s*4.0,light),c2=craters(s*11.0+5.1,light),c3=craters(s*27.0+1.3,light);
            mat*=c1.y*c2.y*(1.0+(noise(s*90.0)-.5)*.15);
            col=mat*(diffuse*mix(1.6,1.15,e)*(1.0+c1.x+c2.x*.8+c3.x*.6)+.003);
        } else {
            float fire=fbm(s*7.0+vec3(time*.05,0,time*.02)),cells=noise(s*40.0+time*.1);
            col=mix(vec3(.78,.1,.004),vec3(1.0,.78,.32),fire*.85+cells*.2)*(.5+.5*pow(n.z,.5));
        }
    }
    float height=(r-1.0)*radius;
    if(kind==1) col+=vec3(.16,.42,1.0)*exp(-max(height,0.0)*mix(30.0,9.0,e))*(.04+.75*rimLight)*smoothstep(-.02,0.0,height)*.9;
    if(kind==2) col+=vec3(.9,.42,.2)*exp(-max(height,0.0)*mix(42.0,14.0,e))*(.02+.45*rimLight)*smoothstep(-.015,0.0,height);
    if(sun) col+=vec3(1.0,.32,.03)*(exp(-max(height,0.0)*10.0)*.45+exp(-max(height,0.0)*4.0)*.05)*step(1.0,r);
    return col;
}
// Ray traced Saturn: oblate banded body, banded rings, ring shadow on the planet and planet shadow on the rings.
vec3 saturn(vec2 p) {
    float e=progress;
    vec3 col=sky(p+motion*.02);
    vec3 ro=vec3(0,0,mix(7.4,5.4,e)),rd=normalize(vec3(p.x+motion.x*.04,-(p.y-mix(.12,.2,e)),-2.3));
    mat2 yaw=rotate(motion.x*.35+sin(time*.05)*.08),pitch=rotate(motion.y*.2);
    ro.xz=yaw*ro.xz;rd.xz=yaw*rd.xz;ro.yz=pitch*ro.yz;rd.yz=pitch*rd.yz;
    vec3 axis=normalize(vec3(.62,.62,.42)),light=normalize(vec3(-.35,.55,.75));
    float b=dot(ro,rd),c=dot(ro,ro)-1.0,disc=b*b-c,sphereT=1e4;
    if(disc>0.0) {
        sphereT=-b-sqrt(disc);vec3 hit=ro+rd*sphereT,n=hit;
        float lat=dot(n,axis),band=lat*9.0+(noise(vec3(lat*20.0,n.x*1.5+time*.01,n.z*1.5))-.5)*.9;
        vec3 mat=mix(vec3(.84,.71,.49),vec3(.66,.50,.30),(.5+.5*sin(band*3.1))*.7);
        mat=mix(mat,vec3(.95,.88,.70),smoothstep(.65,.9,sin(band*1.3+1.0))*.45);
        mat=mix(mat,vec3(.52,.55,.56),smoothstep(.84,.97,abs(lat))*.7);
        float ndl=max(dot(n,light),0.0);
        float rt=-dot(hit,axis)/dot(light,axis),rr=length(hit+light*rt);
        float shadow=rt>0.0?smoothstep(1.2,1.3,rr)*(1.0-smoothstep(2.25,2.3,rr))*(.75-.45*step(1.95,rr)*step(rr,2.03)):0.0;
        col=mat*(ndl*1.45*(1.0-shadow)+.01)+vec3(.9,.7,.45)*pow(1.0-n.z,4.0)*ndl*.15;
    }
    float den=dot(rd,axis),t=-dot(ro,axis)/den;
    if(t>0.0 && t<sphereT) {
        vec3 hit=ro+rd*t;float rr=length(hit);
        float dens=smoothstep(1.22,1.26,rr)*(1.0-smoothstep(2.26,2.3,rr));
        dens*=mix(.25,1.0,smoothstep(1.5,1.55,rr))*(1.0-.92*step(1.95,rr)*step(rr,2.03))*(1.0-.8*step(2.2,rr)*step(rr,2.215));
        dens*=.7+.15*sin(rr*95.0)+.15*sin(rr*370.0);
        float pb=dot(hit,light),pc=dot(hit,hit)-1.0;
        float lit=(pb<0.0 && pb*pb-pc>0.0)?.06:1.0;
        vec3 ring=mix(vec3(.5,.46,.4),vec3(.9,.85,.74),smoothstep(1.5,2.0,rr))*(.2+1.1*abs(dot(light,axis)))*lit;
        col=mix(col,ring,clamp(dens,0.0,1.0)*.85);
    }
    return col;
}
float ridge(float x, float z) {
    float v=0.0,a=.55;
    for(int i=0;i<5;i++){v+=(1.0-abs(noise1(x+z*13.7)*2.0-1.0))*a;x=x*2.1+1.7;a*=.45;}
    return v;
}
float peak(float x, float z) { return .02+z*.13-ridge(x*(1.1+z*.35),z)*(.42-z*.05); }
vec3 snow(vec2 p) {
    float e=progress,glow=exp(-abs(p.y-.08)*5.0);
    vec3 col=mix(vec3(0),vec3(.012,.03,.075),smoothstep(-1.0,.15,p.y))+vec3(.16,.055,.02)*glow*.55+sky(p)*smoothstep(.2,-.6,p.y);
    vec2 moon=vec2(.2-motion.x*.01,-.55+e*.06);float md=length(p-moon);
    col+=vec3(.85,.9,1.0)*(smoothstep(.052,.048,md)*.9+exp(-md*9.0)*.12);
    float layer=-1.0,top=0.0,lx=0.0;
    for(int i=0;i<5;i++) {
        float z=float(i),scale=1.0+e*(.04+z*.07);
        float x=p.x/scale+motion.x*(.015+z*.03)+z*3.1;
        float h=(peak(x,z)+e*(.02+z*.05))*scale-.02;
        if(p.y>h){layer=z;top=h;lx=x;}
    }
    if(layer>=0.0) {
        float z=layer,scale=1.0+e*(.04+z*.07),depth=p.y-top;
        float slope=(peak(lx+.01,z)-peak(lx,z))*scale/.01;
        // Spurs fall diagonally from the crest; their slope decides which facet catches the moonlight.
        float bx=lx*(1.1+z*.35)+z*13.7,side=clamp((abs(noise1(bx+.01)*2.0-1.0)-abs(noise1(bx)*2.0-1.0))*120.0,-1.0,1.0);
        float sx=lx*(3.0+z*.4)-depth*(2.4-z*.2)*side,sp=ridge(sx,z+.5),spur=(sp-ridge(sx+.02,z+.5))/.02;
        float tex=noise(vec3(lx*(30.0-z*3.0),depth*(12.0-z),z*7.0));
        float face=mix(-slope*.6,-slope*.25+spur*.35+(tex-.5)*.3,smoothstep(.0,.08,depth));
        float lit=smoothstep(-.25,.35,face);
        float rock=smoothstep(.1,-.4,face)*smoothstep(.02,.15,depth)*smoothstep(.45,.75,tex);
        vec3 m=mix(vec3(.06,.1,.2),vec3(.82,.88,1.0),lit)*(1.0-rock*.7);
        m+=vec3(1.0,.45,.18)*smoothstep(.025,0.0,depth)*lit*.35;
        m*=mix(1.0,.25,smoothstep(.02,.55,depth));
        float fog=pow(1.0-z/4.0,1.2)*.8;
        m=mix(m,vec3(.02,.04,.1)+vec3(.12,.045,.015)*glow*.4,fog);
        col=m*(.55+.12*z);
    }
    for(int i=0;i<3;i++) {
        float z=float(i),size=26.0-z*7.0;
        vec2 sp=p*size+vec2(time*(.25+z*.15)+motion.x*(2.0+z*3.0),-time*(1.1+z*.5));
        sp.x+=sin(sp.y*.4+z)*.4;
        vec2 cell=floor(sp);float h=hash(vec3(cell,z+3.0));
        vec2 f=fract(sp)-vec2(hash(vec3(cell,z+7.0)),h)*.7-.15;
        col+=vec3(.82,.88,1.0)*step(.72,h)*smoothstep(.05+z*.03,0.0,length(f))*(.2+z*.2);
    }
    return col;
}
// Distance from p to the screen projection of a circle with basis u, v (orthographic), and that point's depth.
vec2 orbit(vec2 p, vec3 u, vec3 v) {
    float det=u.x*v.y-v.x*u.y;det=sign(det)*max(abs(det),1e-3);
    vec2 c=vec2(v.y*p.x-v.x*p.y,u.x*p.y-u.y*p.x)/det;float l=max(length(c),1e-4);
    vec2 g=vec2(v.y*c.x-u.y*c.y,u.x*c.y-v.x*c.x)/(det*l);
    vec2 cs=c/l;
    return vec2(abs(l-1.0)/max(length(g),1e-3),cs.x*u.z+cs.y*v.z);
}
vec3 geometry(vec2 p) {
    float e=progress,px=2.0/resolution.y;
    vec3 col=vec3(0);p+=motion*.05;p.y+=.02;
    float size=mix(.36,.42,e),open=mix(.35,1.0,e);
    for(int i=0;i<7;i++) {
        float f=float(i);
        vec3 u=vec3(1,0,0),v=vec3(0,1,0);
        float tilt=(f*.45+.3)*open+sin(time*.11+f)*.12,spin=time*(.05+f*.013)+f*.9;
        u.yz=rotate(tilt)*u.yz;v.yz=rotate(tilt)*v.yz;
        u.xz=rotate(spin)*u.xz;v.xz=rotate(spin)*v.xz;
        mat2 view=rotate(motion.x*.6);u.xz=view*u.xz;v.xz=view*v.xz;
        float rad=size*(1.0+f*.07*open);
        vec2 o=orbit(p/rad,u,v);float d=o.x*rad,depth=o.y*.5+.5;
        vec3 tint=i==2?PAPAYA:mix(vec3(.55,.62,.85),vec3(.92,.93,.97),f/6.0);
        col+=tint*(smoothstep(px*1.6,0.0,d)*.85+exp(-d*90.0)*.12)*mix(.18,1.0,depth);
        float ang=time*(.3+f*.07)+f*2.1;vec3 bead=(u*cos(ang)+v*sin(ang))*rad;
        float bd=length(p-bead.xy),br=px*(2.5+2.5*(bead.z/rad*.5+.5));
        col+=(i==2?PAPAYA:vec3(.95))*(smoothstep(br,br*.4,bd)+exp(-bd*60.0)*.25)*mix(.3,1.0,bead.z/rad*.5+.5);
    }
    // Wireframe cube at the core, rotating on two axes.
    mat2 ry=rotate(time*.17+motion.x*.6),rx=rotate(time*.11+.6);float cs=size*mix(.28,.36,e);
    for(int i=0;i<12;i++) {
        float axis=floor(float(i)/4.0),k=mod(float(i),4.0);
        vec2 sgn=vec2(mod(k,2.0)*2.0-1.0,floor(k/2.0)*2.0-1.0);
        vec3 a=axis<.5?vec3(-1,sgn):(axis<1.5?vec3(sgn.x,-1,sgn.y):vec3(sgn,-1));
        vec3 b=axis<.5?vec3(1,sgn):(axis<1.5?vec3(sgn.x,1,sgn.y):vec3(sgn,1));
        a.yz=rx*a.yz;b.yz=rx*b.yz;a.xz=ry*a.xz;b.xz=ry*b.xz;a*=cs;b*=cs;
        vec2 pa=p-a.xy,ba=b.xy-a.xy;float h=clamp(dot(pa,ba)/max(dot(ba,ba),1e-6),0.0,1.0);
        float d=length(pa-ba*h),z=mix(a.z,b.z,h)/cs*.5+.5;
        col+=vec3(.95,.55,.3)*(smoothstep(px*1.4,0.0,d)*.7+exp(-d*120.0)*.1)*mix(.2,1.0,z);
    }
    return col;
}
void main() {
    vec2 p=(uv-.5)*vec2(resolution.x/resolution.y,1.0)*2.0;
    vec4 clock=texture2D(clockTex,uv);
    if(kind==0) {
        float viewAspect=resolution.x/resolution.y;
        vec2 crop=vec2(min(1.0,viewAspect/imageAspect),min(1.0,imageAspect/viewAspect))/zoom;
        vec2 t=uv*crop+(1.0-crop)*cropOffset;
        vec4 bg=texture2D(backgroundTex,clamp(t+motion*.012,0.0,1.0));
        vec4 fg=texture2D(foregroundTex,clamp((t-.5)/1.04+.5-motion*.018,0.0,1.0));
        vec3 color=bg.rgb*(1.0-clock.a)+clock.rgb;
        gl_FragColor=vec4(color*(1.0-fg.a)+fg.rgb,1.0);
    } else {
        vec3 color=kind==5?snow(p):(kind==6?geometry(p):(kind==3?saturn(p):globe(p)));
        gl_FragColor=vec4(color*(1.0-clock.a)+clock.rgb,1.0);
    }
}
