package com.ctugm.xray.client.render;

import com.ctugm.xray.client.XrayClient;
import com.ctugm.xray.client.mixin.RenderPipelinesAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

public final class OreRenderer {
    private static final int INITIAL_BUFFER_SIZE = 256 * 1024;
    private static final float FILL_RED = 0.0f;
    private static final float FILL_GREEN = 0.85f;
    private static final float FILL_BLUE = 1.0f;
    private static final float FILL_ALPHA = 0.18f;
    private static final int OUTLINE_COLOR = 0xFF4DEBFF;
    private static final float OUTLINE_WIDTH = 2.0f;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final BufferAllocator ALLOCATOR = new BufferAllocator(INITIAL_BUFFER_SIZE);

    private static RenderPipeline fillPipeline;
    private static RenderPipeline outlinePipeline;
    private static MappableRingBuffer fillVertexBuffer;
    private static MappableRingBuffer outlineVertexBuffer;
    private static boolean initialized;
    private static boolean closed;

    private OreRenderer() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        fillPipeline = RenderPipelinesAccessor.xray$register(RenderPipeline.builder(
                        RenderPipelinesAccessor.xray$getPositionColorSnippet()
                )
                .withLocation(Identifier.of("xray", "pipeline/ore_fill_through_walls"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build());

        outlinePipeline = RenderPipelinesAccessor.xray$register(RenderPipeline.builder(
                        RenderPipelinesAccessor.xray$getLinesSnippet()
                )
                .withLocation(Identifier.of("xray", "pipeline/ore_outline_through_walls"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .build());

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(OreRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        initialized = true;
    }

    private static void render(WorldRenderContext context) {
        Set<BlockPos> orePositions = XrayClient.getDetectedOrePositions();
        if (closed || !XrayClient.isXrayEnabled() || orePositions.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Vec3d cameraPosition = context.worldState().cameraRenderState.pos;
        MinecraftClient client = MinecraftClient.getInstance();

        fillVertexBuffer = renderFill(client, matrices, cameraPosition, orePositions);
        outlineVertexBuffer = renderOutlines(client, matrices, cameraPosition, orePositions);
    }

    private static MappableRingBuffer renderFill(
            MinecraftClient client,
            MatrixStack matrices,
            Vec3d cameraPosition,
            Set<BlockPos> orePositions
    ) {
        BufferBuilder builder = new BufferBuilder(
                ALLOCATOR,
                fillPipeline.getVertexFormatMode(),
                fillPipeline.getVertexFormat()
        );

        matrices.push();
        matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        Matrix4fc positionMatrix = matrices.peek().getPositionMatrix();

        for (BlockPos position : orePositions) {
            renderFilledBox(
                    positionMatrix,
                    builder,
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    position.getX() + 1.0f,
                    position.getY() + 1.0f,
                    position.getZ() + 1.0f,
                    FILL_RED,
                    FILL_GREEN,
                    FILL_BLUE,
                    FILL_ALPHA
            );
        }

        matrices.pop();
        return uploadAndDraw(client, fillPipeline, builder.end(), fillVertexBuffer, "ore fill");
    }

    private static MappableRingBuffer renderOutlines(
            MinecraftClient client,
            MatrixStack matrices,
            Vec3d cameraPosition,
            Set<BlockPos> orePositions
    ) {
        BufferBuilder builder = new BufferBuilder(
                ALLOCATOR,
                outlinePipeline.getVertexFormatMode(),
                outlinePipeline.getVertexFormat()
        );

        matrices.push();
        matrices.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);

        for (BlockPos position : orePositions) {
            VertexRendering.drawOutline(
                    matrices,
                    builder,
                    VoxelShapes.fullCube(),
                    position.getX(),
                    position.getY(),
                    position.getZ(),
                    OUTLINE_COLOR,
                    OUTLINE_WIDTH
            );
        }

        matrices.pop();
        return uploadAndDraw(client, outlinePipeline, builder.end(), outlineVertexBuffer, "ore outline");
    }

    private static MappableRingBuffer uploadAndDraw(
            MinecraftClient client,
            RenderPipeline pipeline,
            BuiltBuffer builtBuffer,
            MappableRingBuffer vertexBuffer,
            String label
    ) {
        try (builtBuffer) {
            BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
            VertexFormat format = drawParameters.format();
            int requiredSize = drawParameters.vertexCount() * format.getVertexSize();

            if (vertexBuffer == null || vertexBuffer.size() < requiredSize) {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }

                vertexBuffer = new MappableRingBuffer(
                        () -> "Xray " + label + " vertex buffer",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                        requiredSize
                );
            }

            ByteBuffer vertices = builtBuffer.getBuffer();
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(
                    vertexBuffer.getBlocking().slice(0, vertices.remaining()),
                    false,
                    true
            )) {
                MemoryUtil.memCopy(vertices, mappedView.data());
            }

            GpuBuffer indexBuffer;
            VertexFormat.IndexType indexType;
            if (pipeline.getVertexFormatMode() == VertexFormat.DrawMode.QUADS) {
                builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().getVertexSorter());
                indexBuffer = format.uploadImmediateIndexBuffer(builtBuffer.getSortedBuffer());
                indexType = builtBuffer.getDrawParameters().indexType();
            } else {
                RenderSystem.ShapeIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(
                        pipeline.getVertexFormatMode()
                );
                indexBuffer = sequentialBuffer.getIndexBuffer(drawParameters.indexCount());
                indexType = sequentialBuffer.getIndexType();
            }

            GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().write(
                    RenderSystem.getModelViewMatrix(),
                    COLOR_MODULATOR,
                    MODEL_OFFSET,
                    TEXTURE_MATRIX
            );

            try (RenderPass renderPass = RenderSystem.getDevice()
                    .createCommandEncoder()
                    .createRenderPass(
                            () -> "Xray " + label + " render pass",
                            client.getFramebuffer().getColorAttachmentView(),
                            OptionalInt.empty(),
                            client.getFramebuffer().getDepthAttachmentView(),
                            OptionalDouble.empty()
                    )) {
                renderPass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(renderPass);
                renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                renderPass.setVertexBuffer(0, vertexBuffer.getBlocking());
                renderPass.setIndexBuffer(indexBuffer, indexType);
                renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
            }

            vertexBuffer.rotate();
            return vertexBuffer;
        }
    }

    private static void renderFilledBox(
            Matrix4fc positionMatrix,
            BufferBuilder buffer,
            float minX,
            float minY,
            float minZ,
            float maxX,
            float maxY,
            float maxZ,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha);

        buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha);

        buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha);

        buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha);

        buffer.vertex(positionMatrix, minX, maxY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, maxY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, maxY, minZ).color(red, green, blue, alpha);

        buffer.vertex(positionMatrix, minX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, minY, minZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, maxX, minY, maxZ).color(red, green, blue, alpha);
        buffer.vertex(positionMatrix, minX, minY, maxZ).color(red, green, blue, alpha);
    }

    public static void close() {
        if (closed) {
            return;
        }

        closed = true;
        ALLOCATOR.close();

        if (fillVertexBuffer != null) {
            fillVertexBuffer.close();
            fillVertexBuffer = null;
        }

        if (outlineVertexBuffer != null) {
            outlineVertexBuffer.close();
            outlineVertexBuffer = null;
        }
    }
}
