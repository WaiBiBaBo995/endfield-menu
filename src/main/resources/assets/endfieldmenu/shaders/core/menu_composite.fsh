#version 150
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform float KeyedBackground;
uniform vec4 AvatarX;
uniform vec4 AvatarY;
uniform float MapReady;
uniform float PageOpacity;
uniform mat4 ScreenToMap;
uniform vec2 Raster;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
void main() {
    float packedHeight=Raster.y+64.0;
    vec4 page=texture(Sampler0,vec2(texCoord.x,texCoord.y*Raster.y/packedHeight));
    float alpha=page.a;
    if(alpha<.003)discard;
    if(KeyedBackground>.5){
      bool positive=true,negative=true;
      for(int i=0;i<4;i++){
        int j=(i+1)%4;
        vec2 edge=vec2(AvatarX[j]-AvatarX[i],AvatarY[j]-AvatarY[i]);
        vec2 delta=texCoord-vec2(AvatarX[i],AvatarY[i]);
        float cross=edge.x*delta.y-edge.y*delta.x;
        positive=positive&&cross>0.0;negative=negative&&cross<0.0;
      }
      float matte=(positive||negative)?0.0:clamp(page.g-max(page.r,page.b),0.0,1.0);
      vec3 world=texture(Sampler2,vec2(texCoord.x,1.0-texCoord.y)).rgb;
      page.rgb=max(vec3(0),page.rgb-vec3(0,matte,0))+world*matte;
    }
    vec4 projected=ScreenToMap*vec4(texCoord,0.0,1.0);
    vec2 uv=projected.xy/projected.w;
    float coverage=max(0.0,min(page.r,page.b)-page.g);
    if(coverage>.012 && all(greaterThanEqual(uv,vec2(-.01))) && all(lessThanEqual(uv,vec2(1.01)))){
        vec3 map=MapReady>.5?texture(Sampler1,vec2(uv.x,1.0-uv.y)).rgb:vec3(.035,.039,.047);
        page.rgb=max(vec3(0),page.rgb-coverage*vec3(1,0,1))+map*coverage;
    }
    // PNG supplies true alpha; JPEG uses the protected matte path above.
    fragColor=vec4(clamp(page.rgb/max(alpha,.003),0.0,1.0),alpha*PageOpacity)*vertexColor;
}
