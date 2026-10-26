#version 150
in vec3 Position;
in vec4 Color;
uniform vec4 View;
uniform vec4 Terrain;
out vec4 vertexColor;
out vec3 worldPoint;
void main(){
 float cs=cos(View.x),sn=sin(View.x),ct=cos(View.y),st=sin(View.y);
 float x=Position.x*cs-Position.z*sn,z=Position.x*sn+Position.z*cs;
 float h=(Position.y-Terrain.x)*Terrain.y;
 float y=z*st-h*ct;
 gl_Position=vec4(x/View.w*View.z,-y/View.w*View.z,clamp(-(z*ct+h*st)/2048.0,-.9,.9)-Terrain.z*.0003,1.0);
 vertexColor=Color;worldPoint=Position;
}
