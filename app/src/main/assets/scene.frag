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

float hash(vec3 p) { return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453); }
float noise(vec3 p) {
    vec3 i=floor(p), f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p) { float v=0.0, a=.5; for(int i=0;i<5;i++){v+=noise(p)*a;p=p*2.03+vec3(7.1,4.3,1.7);a*=.5;}return v; }
mat2 rotate(float a) { return mat2(cos(a),-sin(a),sin(a),cos(a)); }
vec3 sky(vec2 p) {
    vec2 cell=floor(p*650.0);float n=hash(vec3(cell,19.0));
    float star=pow(n,180.0)*.65;
    return vec3(.002,.004,.008)+vec3(star*.7,star*.8,star);
}
vec3 terrain(vec2 p, int k) {
    vec3 color=k==5?vec3(.06,.10,.16):(k==2?vec3(.13,.045,.024):(k==4?vec3(.045,.055,.075):vec3(.035,.075,.075)));
    float daylight=k==5 ? .68+.32*sin(time*.035) : 1.0;
    vec3 outColor=sky(p)*.5;
    if(k==5)outColor+=vec3(.025,.04,.075)*daylight*max(0.0,1.0-abs(p.y+.1));
    float sun=exp(-length(p-vec2(.17,-.22))*16.0);
    outColor+=sun*(k==2?vec3(.9,.3,.1):vec3(.18,.3,.55));
    for(int i=0;i<5;i++) {
        float z=float(i);float h=.12+z*.15-fbm(vec3(p.x*(3.0+z)+motion.x*.4,z*4.0,1.0))*(.48-z*.035);
        if(p.y>h) {
            float ridge=fbm(vec3(p.x*35.0,p.y*18.0,z*6.0));
            outColor=color*(1.4-z*.22)*(ridge*.5+.65)*daylight;
            if(k==5 && p.y<h+.045 && i<3) outColor=mix(vec3(.55,.66,.8)*daylight,outColor,smoothstep(h,h+.045,p.y));
        }
    }
    return outColor;
}
vec3 planets(vec2 p) {
    bool hasTerrain=kind==1||kind==2||kind==4;
    // Once the transition reaches land, orbital shading no longer contributes.
    if(hasTerrain && progress>=1.0) return terrain(p,kind);
    vec3 col=sky(p);
    float close=progress;
    float scale=kind==3 ? .86 : mix(.78,1.85,close);
    vec2 center=vec2(motion.x*.09,-.08+motion.y*.05+close*.3);
    vec2 q=(p-center)/scale;
    vec3 ro=vec3(0,0,3.0),rd=normalize(vec3(q*2.0,-2.6));
    float b=dot(ro,rd),c=dot(ro,ro)-1.0,disc=b*b-c;
    float sphereT=100.0;
    if(disc>0.0) {
        sphereT=-b-sqrt(disc);vec3 n=normalize(ro+rd*sphereT);vec3 sampleN=n;
        sampleN.xz=rotate(time*.012)*sampleN.xz;
        float f=fbm(sampleN*4.8);vec3 mat;
        if(kind==1) {
            mat=mix(vec3(.012,.12,.31),vec3(.07,.19,.10),smoothstep(.47,.51,f));
            mat=mix(mat,vec3(.44,.36,.20),smoothstep(.60,.73,f));
            mat=mix(mat,vec3(.84,.9,.94),smoothstep(.78,.94,abs(sampleN.y)));
            float clouds=smoothstep(.60,.77,fbm(sampleN*9.0+vec3(time*.005,0,0)));
            mat=mix(mat,vec3(.86,.91,.96),clouds*.85);
        } else if(kind==2) {
            mat=mix(vec3(.20,.055,.025),vec3(.70,.29,.12),f);
            mat*=.7+.5*noise(sampleN*70.0);
        } else if(kind==3) {
            float bands=sin(sampleN.y*58.0+f*4.0)*.07;
            mat=vec3(.72,.55,.32)*(f*.7+.65+bands);
        } else if(kind==7) {
            float fire=fbm(sampleN*12.0+vec3(time*.055,0,0));
            mat=mix(vec3(.8,.065,.001),vec3(1.0,.85,.30),fire);
        } else {
            float crater=noise(sampleN*34.0); mat=vec3(.53,.55,.59)*(.5+f*.6+crater*.3);
        }
        float light=kind==7 ? .85 : max(.025,dot(n,normalize(vec3(-.8,.65,1.0))));
        col=mat*light;
        if(kind==1) col+=vec3(.02,.13,.36)*pow(1.0-max(0.0,n.z),3.0)*light;
    }
    if(kind==3) {
        vec3 normal=normalize(vec3(.22,.87,.44));float den=dot(rd,normal);
        float t=-dot(ro,normal)/den;
        vec3 hit=ro+rd*t;float radius=length(hit);
        if(t>0.0 && t<sphereT && radius>1.22 && radius<2.05) {
            float rings=.55+.35*sin(radius*160.0)+.1*sin(radius*440.0);
            col=mix(col,vec3(.65,.48,.28)*(.55+rings*.45),.88);
            if(radius>1.61 && radius<1.69) col*=.25;
        }
    }
    if(kind==7) {
        float limb=length(q);float corona=exp(-abs(limb-.78)*15.0);
        col+=vec3(.95,.23,.01)*corona*.2;
    }
    // Avoid evaluating five terrain layers while their blend weight is zero.
    if(hasTerrain && progress>.88) col=mix(col,terrain(p,kind),smoothstep(.88,1.0,progress));
    return col;
}
vec3 geometry(vec2 p) {
    vec3 col=vec3(.008,.004,.014);p+=motion*.035;p=rotate(time*.025)*p;
    for(int i=0;i<10;i++) {
        float f=float(i);vec2 q=rotate(f*.085+sin(time*.12)*.1)*p;
        float d=max(abs(q.x)*.866025+abs(q.y)*.5,abs(q.y));
        float line=1.0-smoothstep(.001,.006,abs(d-(.12+f*.044)));
        col+=mix(vec3(.97,.27,.07),vec3(.35,.23,.82),f/9.0)*line*(.4+f*.06);
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
        vec3 color=kind==5?terrain(p,5):(kind==6?geometry(p):planets(p));
        gl_FragColor=vec4(color*(1.0-clock.a)+clock.rgb,1.0);
    }
}
