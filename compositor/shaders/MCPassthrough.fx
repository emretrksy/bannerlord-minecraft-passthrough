
uniform int uDepthMode <
	ui_type = "combo";
	ui_label = "Bannerlord depth buffer";
	ui_items = "Standard (near = 0)\0Reversed (near = 1)\0Reversed, infinite far\0";
	ui_tooltip = "How the game's depth buffer is encoded. Pick the one where Steve hides behind walls correctly.";
> = 1;

uniform float uBias <
	ui_type = "drag";
	ui_label = "Depth bias (metres)";
	ui_min = 0.0; ui_max = 1.0; ui_step = 0.01;
	ui_tooltip = "Lets Steve win close depth ties, e.g. his feet against the ground.";
> = 0.08;

uniform float uHudScale <
	ui_type = "drag";
	ui_label = "Minecraft HUD size";
	ui_min = 0.25; ui_max = 1.0; ui_step = 0.05;
	ui_tooltip = "Scales Minecraft's hotbar and hearts about the bottom centre of the screen. Keep at 1 for the inventory cursor to line up.";
> = 1.0;

uniform int uView <
	ui_type = "combo";
	ui_label = "View";
	ui_items = "Composite\0Minecraft only\0Bannerlord depth\0";
> = 0;

uniform float uEnabled < hidden = true; > = 0.0;
uniform float3 uBlRight < hidden = true; >;
uniform float3 uBlUp < hidden = true; >;
uniform float3 uBlFwd < hidden = true; >;
uniform float2 uBlTan < hidden = true; >;
uniform float2 uBlNearFar < hidden = true; >;
uniform float3 uMcOffset < hidden = true; >;
uniform float3 uMcRight < hidden = true; >;
uniform float3 uMcUp < hidden = true; >;
uniform float3 uMcFwd < hidden = true; >;
uniform float2 uMcTan < hidden = true; >;
uniform float3 uMcDepth < hidden = true; >;
uniform float2 uMcFlags < hidden = true; >;
uniform float2 uMcTexel < hidden = true; >;
uniform float uHasOverlay < hidden = true; > = 0.0;

texture BackBufferTex : COLOR;
texture DepthBufferTex : DEPTH;
texture MCColorTex : MCPT_COLOR;
texture MCDepthTex : MCPT_DEPTH;
texture MCOverlayTex : MCPT_OVERLAY;

sampler BackBuffer { Texture = BackBufferTex; };
sampler DepthBuffer { Texture = DepthBufferTex; MagFilter = POINT; MinFilter = POINT; MipFilter = POINT; };
sampler MCColor { Texture = MCColorTex; MagFilter = POINT; MinFilter = POINT; MipFilter = POINT; };
sampler MCDepth { Texture = MCDepthTex; MagFilter = POINT; MinFilter = POINT; MipFilter = POINT; };
sampler MCOverlay { Texture = MCOverlayTex; MagFilter = POINT; MinFilter = POINT; MipFilter = POINT; };

void FullscreenVS(in uint id : SV_VertexID, out float4 position : SV_Position, out float2 uv : TEXCOORD)
{
	uv.x = (id == 2) ? 2.0 : 0.0;
	uv.y = (id == 1) ? 2.0 : 0.0;
	position = float4(uv * float2(2.0, -2.0) + float2(-1.0, 1.0), 0.0, 1.0);
}

float BannerlordDistance(float raw)
{
	float n = uBlNearFar.x;
	float f = uBlNearFar.y;
	if (uDepthMode == 0)
		return n * f / (f - raw * (f - n));
	if (uDepthMode == 1)
		return n * f / (n + raw * (f - n));
	return n / max(raw, 1e-7);
}

float MinecraftDistance(float raw)
{
	float ndc = uMcFlags.y > 0.5 ? raw : raw * 2.0 - 1.0;
	return uMcDepth.y / (ndc + uMcDepth.x);
}

float4 Hud(float2 uv)
{
	float2 hudUv = (uv - float2(0.5, 1.0)) / uHudScale + float2(0.5, 1.0);
	if (uHasOverlay < 0.5 || any(hudUv < 0.0) || any(hudUv > 1.0))
		return 0.0;
	if (uMcFlags.x > 0.5)
		hudUv.y = 1.0 - hudUv.y;
	return tex2Dlod(MCOverlay, float4(hudUv, 0, 0));
}

float4 WorldPS(float2 uv)
{
	float4 scene = tex2D(BackBuffer, uv);
	float sceneDistance = BannerlordDistance(tex2Dlod(DepthBuffer, float4(uv, 0, 0)).x);
	if (uView == 2)
		return float4(saturate(sceneDistance / 50.0).xxx, 1.0);
	if (uEnabled < 0.5)
		return scene;

	float2 ndc = float2(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0);
	float3 ray = uBlRight * (ndc.x * uBlTan.x) + uBlUp * (ndc.y * uBlTan.y) + uBlFwd;

	float3 v = float3(dot(ray, uMcRight), dot(ray, uMcUp), dot(ray, uMcFwd));
	if (v.z <= 1e-4)
		return uView == 1 ? float4(0, 0, 0, 1) : scene;
	float2 mcNdc = v.xy / (v.z * uMcTan);
	if (any(abs(mcNdc) > 1.0))
		return uView == 1 ? float4(0, 0, 0, 1) : scene;
	float2 mcUv = float2(mcNdc.x * 0.5 + 0.5, uMcFlags.x > 0.5 ? mcNdc.y * 0.5 + 0.5 : 0.5 - mcNdc.y * 0.5);

	float mcRaw = tex2Dlod(MCDepth, float4(mcUv, 0, 0)).x;
	if (mcRaw == uMcDepth.z)
		return uView == 1 ? float4(0, 0, 0, 1) : scene;

	float steveDistance = dot(uMcOffset, uBlFwd) + MinecraftDistance(mcRaw) / v.z;
	float4 steve = float4(tex2Dlod(MCColor, float4(mcUv, 0, 0)).rgb, 1.0);
	if (uView == 1)
		return steve;
	return steveDistance <= sceneDistance + uBias ? steve : scene;
}

float4 CompositePS(float4 position : SV_Position, float2 uv : TEXCOORD) : SV_Target
{
	float4 world = WorldPS(uv);
	if (uEnabled < 0.5 || uView == 2)
		return world;
	float4 hud = Hud(uv);
	return float4(hud.rgb + world.rgb * (1.0 - hud.a), 1.0);
}

technique MCPassthrough <
	ui_label = "Minecraft Passthrough";
	ui_tooltip = "Draws Minecraft's Steve into the game. Needs the MCPassthrough add-on and the Minecraft mod.";
>
{
	pass
	{
		VertexShader = FullscreenVS;
		PixelShader = CompositePS;
	}
}
