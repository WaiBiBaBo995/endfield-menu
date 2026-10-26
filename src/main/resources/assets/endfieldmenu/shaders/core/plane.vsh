#version 150
in vec3 Position;
in vec2 UV0;
in vec4 Color;
uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform mat4 PlaneMatrix;
uniform mat4 UiMatrix;
out vec2 texCoord;
out vec2 localPoint;
out vec2 stagePoint;
out vec4 vertexColor;
void main(){
    vec4 p=PlaneMatrix*vec4(Position,1.0);
    vec2 q=p.xy/p.w;
    gl_Position=ProjMat*ModelViewMat*UiMatrix*vec4(q*p.w,0.0,p.w);
    texCoord=UV0;localPoint=Position.xy;stagePoint=q;vertexColor=Color;
}
