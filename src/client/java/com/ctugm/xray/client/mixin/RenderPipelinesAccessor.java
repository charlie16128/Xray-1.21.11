package com.ctugm.xray.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gl.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("POSITION_COLOR_SNIPPET")
    static RenderPipeline.Snippet xray$getPositionColorSnippet() {
        throw new AssertionError();
    }

    @Accessor("RENDERTYPE_LINES_SNIPPET")
    static RenderPipeline.Snippet xray$getLinesSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline xray$register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
