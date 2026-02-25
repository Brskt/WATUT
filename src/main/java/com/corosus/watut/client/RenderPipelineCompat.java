package com.corosus.watut.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Cross-version bridge for RenderPipeline.Builder between 1.21.11 (NeoForge path)
 * and 26.1 snapshots (Fabric path).
 */
public final class RenderPipelineCompat {
    private static boolean initialized;
    private static Method withColorTargetStateMethod;
    private static Method withDepthStencilStateMethod;
    private static Constructor<?> colorTargetStateCtor;
    private static Constructor<?> depthStencilStateCtor;
    private static Object compareAlwaysPass;
    private static Object compareLequal;

    private static Method withBlendMethod;
    private static Method withDepthTestFunctionMethod;
    private static Method withDepthWriteMethod;
    private static Object depthNoDepthTest;
    private static Object depthLequalTest;

    private RenderPipelineCompat() {
    }

    public static RenderPipeline.Builder withTranslucentColorTarget(RenderPipeline.Builder builder) {
        init();
        if (withColorTargetStateMethod != null && colorTargetStateCtor != null) {
            try {
                Object colorTargetState = colorTargetStateCtor.newInstance(BlendFunction.TRANSLUCENT);
                withColorTargetStateMethod.invoke(builder, colorTargetState);
                return builder;
            } catch (Exception ignored) {
            }
        }
        if (withBlendMethod != null) {
            try {
                withBlendMethod.invoke(builder, BlendFunction.TRANSLUCENT);
                return builder;
            } catch (Exception ignored) {
            }
        }
        throw new RuntimeException("Unsupported RenderPipeline.Builder color target/blend API");
    }

    public static RenderPipeline.Builder withAlwaysPassNoDepthWrite(RenderPipeline.Builder builder) {
        init();
        if (withDepthStencilStateMethod != null && depthStencilStateCtor != null && compareAlwaysPass != null) {
            try {
                Object depthStencilState = depthStencilStateCtor.newInstance(compareAlwaysPass, false);
                withDepthStencilStateMethod.invoke(builder, depthStencilState);
                return builder;
            } catch (Exception ignored) {
            }
        }
        if (withDepthTestFunctionMethod != null && withDepthWriteMethod != null && depthNoDepthTest != null) {
            try {
                withDepthTestFunctionMethod.invoke(builder, depthNoDepthTest);
                withDepthWriteMethod.invoke(builder, false);
                return builder;
            } catch (Exception ignored) {
            }
        }
        throw new RuntimeException("Unsupported RenderPipeline.Builder depth API (always-pass/no-write)");
    }

    public static RenderPipeline.Builder withLequalNoDepthWrite(RenderPipeline.Builder builder) {
        init();
        if (withDepthStencilStateMethod != null && depthStencilStateCtor != null && compareLequal != null) {
            try {
                Object depthStencilState = depthStencilStateCtor.newInstance(compareLequal, false);
                withDepthStencilStateMethod.invoke(builder, depthStencilState);
                return builder;
            } catch (Exception ignored) {
            }
        }
        if (withDepthTestFunctionMethod != null && withDepthWriteMethod != null && depthLequalTest != null) {
            try {
                withDepthTestFunctionMethod.invoke(builder, depthLequalTest);
                withDepthWriteMethod.invoke(builder, false);
                return builder;
            } catch (Exception ignored) {
            }
        }
        throw new RuntimeException("Unsupported RenderPipeline.Builder depth API (lequal/no-write)");
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        Class<?> builderClass = RenderPipeline.Builder.class;

        // 26.1+ API: ColorTargetState / DepthStencilState / CompareOp.
        try {
            Class<?> colorTargetStateClass = Class.forName("com.mojang.blaze3d.pipeline.ColorTargetState");
            Class<?> depthStencilStateClass = Class.forName("com.mojang.blaze3d.pipeline.DepthStencilState");
            Class<?> compareOpClass = Class.forName("com.mojang.blaze3d.platform.CompareOp");
            withColorTargetStateMethod = builderClass.getMethod("withColorTargetState", colorTargetStateClass);
            withDepthStencilStateMethod = builderClass.getMethod("withDepthStencilState", depthStencilStateClass);
            colorTargetStateCtor = colorTargetStateClass.getConstructor(BlendFunction.class);
            depthStencilStateCtor = depthStencilStateClass.getConstructor(compareOpClass, boolean.class);
            compareAlwaysPass = compareOpClass.getField("ALWAYS_PASS").get(null);
            compareLequal = compareOpClass.getField("LESS_THAN_OR_EQUAL").get(null);
        } catch (Exception ignored) {
        }

        // 1.21.11 API: withBlend / withDepthTestFunction / withDepthWrite.
        try {
            withBlendMethod = builderClass.getMethod("withBlend", BlendFunction.class);
        } catch (Exception ignored) {
        }
        try {
            Class<?> depthTestFunctionClass = Class.forName("com.mojang.blaze3d.platform.DepthTestFunction");
            withDepthTestFunctionMethod = builderClass.getMethod("withDepthTestFunction", depthTestFunctionClass);
            withDepthWriteMethod = builderClass.getMethod("withDepthWrite", boolean.class);
            depthNoDepthTest = depthTestFunctionClass.getField("NO_DEPTH_TEST").get(null);
            depthLequalTest = getFirstPresentField(depthTestFunctionClass, "LEQUAL_DEPTH_TEST", "LESS_DEPTH_TEST");
        } catch (Exception ignored) {
        }
    }

    private static Object getFirstPresentField(Class<?> type, String... fieldNames) throws IllegalAccessException {
        for (String fieldName : fieldNames) {
            try {
                Field field = type.getField(fieldName);
                return field.get(null);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
