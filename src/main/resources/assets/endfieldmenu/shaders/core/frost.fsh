#version 150
uniform sampler2D Sampler0;
uniform vec2 Direction;
uniform vec4 Finish;
in vec2 texCoord;
out vec4 fragColor;
void main(){
 vec3 c=vec3(0);float weight=0;
 for(int i=-12;i<=12;i++){float x=float(i)/12.0,w=exp(-x*x*4.5);c+=texture(Sampler0,texCoord+Direction*x*3.0).rgb*w;weight+=w;}
 c/=weight;
 float noise=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453)-.5;
 c=mix(c,vec3(.11,.12,.13),Finish.x)+noise*Finish.y;
 fragColor=vec4(c,1.0);
}
