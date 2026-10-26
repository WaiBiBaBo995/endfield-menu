#version 150
uniform vec4 Terrain;
uniform vec2 Resolution;
uniform vec4 Scanner;
in vec4 vertexColor;
in vec3 worldPoint;
out vec4 fragColor;
void main(){
 float r=length((gl_FragCoord.xy/Resolution-.5)*2.0);
 if(r>.99)discard;
 vec2 relative = worldPoint.xz-Scanner.xy;
 float a=atan(relative.y,relative.x)-Scanner.z;
 float scan=max(0.0,1.0-abs(atan(sin(a),cos(a)))/.55);
 vec3 c=vertexColor.rgb;
 if(Terrain.z<.5)c+=vec3(.247,.188,-.031)*scan;
 float ring=abs(length(relative)-mod(Scanner.w*12.0,36.0)-10.0);
 if(Terrain.z<2.0)c+=vec3(.15,.12,0.0)*exp(-ring*ring/1.6)*.45;
 fragColor=vec4(c,vertexColor.a*(1.0-smoothstep(.975,.99,r)));
}
