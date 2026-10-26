#version 150
uniform sampler2D Sampler0;
uniform vec4 Arc;
uniform vec4 Effects;
uniform mat4 ClipInverse;
uniform float ClipEnabled;
in vec2 texCoord;
in vec2 localPoint;
in vec2 stagePoint;
in vec4 vertexColor;
out vec4 fragColor;
void main(){
    vec4 color=texture(Sampler0,texCoord)*vertexColor;
    color.rgb*=Effects.w;
    if(Arc.x>=0.0){float angle=mod(atan(localPoint.y-Arc.y,localPoint.x-Arc.x)-Arc.z+6.2831853,6.2831853);float fraction=angle/max(Arc.w,.001);float dash=Effects.x;if(dash>0.001&&fraction>1.0-dash)discard;if(dash<-.001&&fraction<-dash)discard;}
    if(Effects.y>=0.0){float dist=length(localPoint-vec2(407.9,218.9));color.a*=1.0-smoothstep(max(0.0,Effects.y-192.0),max(.01,Effects.y),dist);}
    if(Effects.z>0.0)color.a*=1.0-smoothstep(488.0-226.5*Effects.z,558.0-44.0*Effects.z,length(localPoint-vec2(481.9,394.7)));
    if(ClipEnabled>.5){vec4 c=ClipInverse*vec4(stagePoint,0.0,1.0);vec2 q=c.xy/c.w;if(q.x<0.0||q.x>512.0||q.y<0.0||q.y>805.0)discard;color.a*=smoothstep(58.0,78.0,q.y)*(1.0-smoothstep(723.0,767.0,q.y));}
    if(color.a<.003)discard;fragColor=color;
}
