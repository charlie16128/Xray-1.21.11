package com.ctugm.xray.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

// 透過 Mixin 存取 Minecraft 內部的 RenderPipeline 設定。
@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    // 取得半透明填色使用的 pipeline snippet。
    @Accessor("POSITION_COLOR_SNIPPET")
    static RenderPipeline.Snippet xray$getPositionColorSnippet() {
        throw new AssertionError();
    }

    // 取得外框線條使用的 pipeline snippet。
    @Accessor("RENDERTYPE_LINES_SNIPPET")
    static RenderPipeline.Snippet xray$getLinesSnippet() {
        throw new AssertionError();
    }

    // 註冊 Xray 自訂 pipeline。
    @Invoker("register")
    static RenderPipeline xray$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
