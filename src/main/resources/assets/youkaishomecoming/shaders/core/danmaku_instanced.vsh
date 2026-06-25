#version 150

// Per-vertex (from mesh VBO)
in vec3 Position;
in vec2 UV0;

// Per-instance (from instance VBO, divisor=1)
in vec4 InstancePosScale;  // xyz = view-space position, w = scale
in vec2 InstanceExtra;     // x = Z rotation angle (radians), y = UV V offset
in vec4 InstanceColor;     // rgba (normalized unsigned bytes)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float FrameHeight; // 1.0 for non-animated, 1.0/frameCount for animated

out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    // Apply Z rotation
    float angle = InstanceExtra.x;
    float c = cos(angle);
    float s = sin(angle);
    vec2 rotated = vec2(Position.x * c - Position.y * s,
                        Position.x * s + Position.y * c);

    // Scale and translate
    vec3 pos = vec3(rotated * InstancePosScale.w + InstancePosScale.xy,
                    InstancePosScale.z);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    // UV: apply animation offset + frame height
    texCoord0 = vec2(UV0.x, InstanceExtra.y + UV0.y * FrameHeight);

    vertexColor = InstanceColor;
}
