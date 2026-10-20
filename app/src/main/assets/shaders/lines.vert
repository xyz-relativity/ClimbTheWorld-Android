uniform mat4 u_ViewProjection;

attribute vec4 a_Position;
attribute vec4 a_Color;

varying vec4 v_Color;

void main() {
    gl_Position = u_ViewProjection * a_Position;
    v_Color = a_Color;
}
