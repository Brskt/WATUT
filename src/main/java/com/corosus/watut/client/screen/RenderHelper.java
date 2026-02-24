package com.corosus.watut.client.screen;

import com.corosus.coroutil.util.CULog;
import com.corosus.watut.PlayerStatus;
import com.corosus.watut.ShaderRegistry;
import com.corosus.watut.ShaderReloader;
import com.corosus.watut.WatutMod;
import com.corosus.watut.config.ConfigClient;
import com.corosus.watut.config.ConfigServerControlledSyncedToClient;
import com.corosus.watut.mixin.client.NativeImageAccessor;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import org.slf4j.Logger;

public class RenderHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedGuiCapturePrepareFailure = false;

    public static boolean performingOwnRender = false;
    public static boolean pendingCapture = false;
    public static boolean pendingGuiOnlyCapturePrepared = false;
    private static GuiRenderState pendingScreenOnlyCaptureRenderState = null;
    public static ResourceLocation cursor = ResourceLocation.fromNamespaceAndPath(WatutMod.MODID, "textures/misc/mouse.png");

    // Xaero World Map detection (used to disable blur while the map GUI is open).
    public static Class guiMap;
    public static boolean xaeroGuiMapCaptureActive = false;

    //shaders enable check support
    public static Class irisConfig;
    public static Class iris;
    public static Method getIrisConfig;
    public static Method areShadersEnabled;

    //i dont want to depend build against an api, so i do this instead like the silly bitch that i am
    static {
        try {
            guiMap = Class.forName("xaero.map.gui.GuiMap");
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
        }
        try {
            iris = Class.forName("net.irisshaders.iris.Iris");
            irisConfig = Class.forName("net.irisshaders.iris.config.IrisConfig");
            getIrisConfig = iris.getDeclaredMethod("getIrisConfig");
            areShadersEnabled = irisConfig.getDeclaredMethod("areShadersEnabled");
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
            try {
                iris = Class.forName("net.coderbot.iris.Iris");
                irisConfig = Class.forName("net.coderbot.iris.config.IrisConfig");
                getIrisConfig = iris.getDeclaredMethod("getIrisConfig");
                areShadersEnabled = irisConfig.getDeclaredMethod("areShadersEnabled");
            } catch (ClassNotFoundException ex) {
                CULog.log("watut: oculus not installed or mod structure changed");
                //throw new RuntimeException(ex);
            } catch (NoSuchMethodException ex) {
                //throw new RuntimeException(ex);
                CULog.log("watut: oculus not installed or mod structure changed");
            }
        } catch (NoSuchMethodException e) {
            CULog.log("watut: oculus not installed or mod structure changed");
        }
    }

    public static boolean isShadersEnabled() {
        if (areShadersEnabled == null) return false;
        try {
            return (boolean) areShadersEnabled.invoke(getIrisConfig.invoke(null));
        } catch (IllegalAccessException e) {
            return false;
        } catch (InvocationTargetException e) {
            return false;
        }
    }

    public static boolean isXaeroGuiMap(Screen screen) {
        if (guiMap == null) return false;
        return guiMap.isInstance(screen);
    }

    public static ByteBufferProcessor processor = new ByteBufferProcessor(buffer -> {
        ByteBuffer processed = compress(buffer);

        //TODO: testing
        /*byte[] newData = new byte[buffer.remaining()];
        buffer.get(newData);
        if (WatutMod.getPlayerStatusManagerClient().getStatusLocal().getScreenData().getTexturePixelDataDiff() != null) {
            byte[] prevData = WatutMod.getPlayerStatusManagerClient().getStatusLocal().getScreenData().getTexturePixelDataDiff();

            List<PixelDifference> diff = calculateDifferences(prevData, newData);
            ByteBuffer diffBuffer = ByteBuffer.allocate(diff.size() * (4 + 4)); //size = rgba + int
            for (PixelDifference pixelDifference : diff) {
                //List<Byte> list = new ArrayList<>();
                byte[] bytes = new byte[10];
                //TODO: its not actually storing the bytes
                bytes = new byte[encodeIndex(pixelDifference.index, bytes)];
                //diffBuffer.putInt(pixelDifference.index);
                diffBuffer.put(bytes);
                diffBuffer.put(pixelDifference.rgba);
            }
            diffBuffer.flip();
            //CULog.dbg("diff size " + diff.size() * (4 + 4));
            CULog.dbg("diff size " + diffBuffer.limit());
            ByteBuffer processed2 = compress(diffBuffer);
            CULog.dbg("diff size compressed " + processed2.limit());
        }

        WatutMod.getPlayerStatusManagerClient().getStatusLocal().getScreenData().setTexturePixelDataDiff(newData);*/

        return processed;
    });

    /*public static int encodeIndex(int index, byte[] encodedBytes) {
        int position = 0; // Keep track of the number of bytes written

        while ((index & ~0x7F) != 0) { // If more than 7 bits are needed
            encodedBytes[position++] = (byte) ((index & 0x7F) | 0x80); // Store 7 bits, set MSB
            index >>>= 7; // Shift to process the next 7 bits
        }
        encodedBytes[position++] = (byte) (index & 0x7F); // Store the last 7 bits

        return position; // Return the number of bytes written
    }

    // Method to calculate differences between two images
    public static List<PixelDifference> calculateDifferences(byte[] image1, byte[] image2) {
        if (image1.length != image2.length) {
            throw new IllegalArgumentException("Images must have the same size!");
        }

        List<PixelDifference> differences = new ArrayList<>();

        for (int i = 0; i < image1.length; i += 4) { // RGBA = 4 bytes per pixel
            if (image1[i] != image2[i] || image1[i + 1] != image2[i + 1] ||
                    image1[i + 2] != image2[i + 2] || image1[i + 3] != image2[i + 3]) {
                // Store the pixel difference
                differences.add(new PixelDifference(i, new byte[] {
                        image2[i], image2[i + 1], image2[i + 2], image2[i + 3]
                }));
            }
        }

        return differences;
    }

    static class PixelDifference {
        int index; // The index of the pixel in the byte array
        byte[] rgba; // The new RGBA value

        PixelDifference(int index, byte[] rgba) {
            this.index = index;
            this.rgba = rgba;
        }
    }*/

    public static void guiRender(GuiGraphics guiGraphics) {
        long gameTime = 0;
        if (Minecraft.getInstance().level != null) {
            gameTime = Minecraft.getInstance().level.getGameTime();
        }

        for (PlayerStatus playerStatus : WatutMod.getPlayerStatusManagerClient().lookupPlayerToStatus.values()) {

            ScreenData screenData = playerStatus.getScreenData();

            if ((screenData.getIsBufferReady().get() && screenData.needsNewRenderFromPixelData() && screenData.getTexturePixelData() != null && screenData.getGameTicksSinceLastScreenReceiveAndRender() + ConfigClient.tickReceiveAndRenderRateOfGUIUpdates < gameTime)) {
                screenData.markNeedsNewRenderFromPixelData(false);
                screenData.setGameTicksSinceLastScreenReceiveAndRender(gameTime);

                ScreenParticleRenderer.getInstance().checkSetup();

                if (playerStatus.getScreenData().getParticleRenderType() == null) {
                    playerStatus.getScreenData().initClient();
                }

                if (screenData.getImage() == null) {
                    screenData.setImage(new DynamicTexture("watut_screen", screenData.getWidth(), screenData.getHeight(), true));
                    screenData.registerTexture();
                } else {
                    //detect a resolution change and remake buffer, only used if experimental rendering of entire screen config is on
                    //CULog.dbg("screendata sizes " + screenData.getWidth() + " " + screenData.getHeight());
                    if (screenData.getImage().getPixels().getWidth() != screenData.getWidth() || screenData.getImage().getPixels().getHeight() != screenData.getHeight()) {
                        screenData.closeImage();
                        screenData.setImage(new DynamicTexture("watut_screen", screenData.getWidth(), screenData.getHeight(), true));
                        screenData.invalidateCachedRenderType();
                        screenData.registerTexture();
                        CULog.dbg("screendata image resized to " + screenData.getWidth() + " " + screenData.getHeight());
                    }
                }
                //hack into NativeImage to directly inject pixel data, working around its requirements for a PNG format parse
                long nativeImagePixelMemoryAddress = ((NativeImageAccessor)((Object)screenData.getImage().getPixels())).pixels();
                if (nativeImagePixelMemoryAddress != -1) {
                    copyReadbackPixelsToNativeImage(screenData.getDecompressionBuffer(), nativeImagePixelMemoryAddress,
                            screenData.getWidth(), screenData.getHeight());
                }

                screenData.getImage().upload();
                playerStatus.getScreenData().getIsBufferReady().set(false);

            }
        }
    }

    public static boolean useDynamicGUISystem() {
        if (ConfigServerControlledSyncedToClient.dynamicGuiUseOldSimpleGUIVisual) return false;
        if (ConfigClient.dontSendDetailedGUIInfo) return false;
        return true;
    }

    public static synchronized void renderWithTooltipEnd(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (!useDynamicGUISystem()) return;
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) {
            return;
        }

        PlayerStatus playerStatusLocal = WatutMod.getPlayerStatusManagerClient().getStatusLocal();

        if (processor.hasProcessedBuffers()) {
            try {
                ByteBuffer result = processor.getProcessedBuffer();
                if (result != null) {
                    ScreenData screenDataLocal = playerStatusLocal.getScreenData();
                    screenDataLocal.setTexturePixelData(result);
                    WatutMod.getPlayerStatusManagerClient().sendScreenRenderData(playerStatusLocal);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        boolean needsScreenUpdate = playerStatusLocal.getScreenData().isNeedsNewRenderToPixelData();



        if (needsScreenUpdate && !processor.hasWork()) {
            playerStatusLocal.getScreenData().setNeedsNewRenderToPixelData(false);
            ScreenParticleRenderer.getInstance().checkSetup();
            // In 1.21.6+, Screen.renderWithTooltip*() no longer does GPU work (two-phase rendering).
            // Build a screen-only GuiRenderState by re-running Screen.renderWithTooltipAndSubtitles()
            // into an isolated
            // GuiGraphics (CPU-side draw-list generation only). GuiRendererCaptureMixin will render that
            // isolated state to WATUT's offscreen target later in the same frame when fog/uniform buffers exist.
            prepareScreenOnlyCaptureRenderState(pMouseX, pMouseY, pPartialTick);
            pendingGuiOnlyCapturePrepared = false;
            pendingCapture = true;
        }
    }

    private static void prepareScreenOnlyCaptureRenderState(int pMouseX, int pMouseY, float pPartialTick) {
        pendingScreenOnlyCaptureRenderState = null;
        xaeroGuiMapCaptureActive = false;

        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.screen;
        if (screen == null) return;

        GuiRenderState captureState = new GuiRenderState();
        GuiGraphics captureGraphics = new GuiGraphics(mc, captureState);

        try {
            if (ConfigServerControlledSyncedToClient.dynamicGuiDisableBackgroundRendering) {
                ScreenParticleRenderer.isRenderingParticleGUI2 = true;
            }
            performingOwnRender = true;
            xaeroGuiMapCaptureActive = isXaeroGuiMap(screen);

            screen.renderWithTooltipAndSubtitles(captureGraphics, pMouseX, pMouseY, pPartialTick);
            pendingScreenOnlyCaptureRenderState = captureState;
        } catch (Throwable t) {
            pendingScreenOnlyCaptureRenderState = null;
            if (!loggedGuiCapturePrepareFailure) {
                loggedGuiCapturePrepareFailure = true;
                LOGGER.error("WATUT GUI-only capture state preparation failed (logging once)", t);
            }
        } finally {
            performingOwnRender = false;
            ScreenParticleRenderer.isRenderingParticleGUI = false;
            ScreenParticleRenderer.isRenderingParticleGUI2 = false;
        }
    }

    public static synchronized GuiRenderState consumePendingScreenOnlyCaptureRenderState() {
        GuiRenderState state = pendingScreenOnlyCaptureRenderState;
        pendingScreenOnlyCaptureRenderState = null;
        return state;
    }

    /**
     * Called by GameRendererCaptureMixin at the end of GameRenderer.render().
     *
     * In 1.21.6 the GUI is rendered via GuiRenderer draw-lists. WATUT replays those draw-lists to its
     * own offscreen target earlier in the frame (see GuiRenderer mixin), then this method performs
     * the blur and async readback from that GUI-only target.
     */
    public static void captureScreenAfterGuiRender() {
        if (!pendingCapture) return;
        pendingCapture = false;
        boolean guiOnlyCapturePrepared = pendingGuiOnlyCapturePrepared;
        pendingGuiOnlyCapturePrepared = false;
        if (!guiOnlyCapturePrepared) {
            pendingScreenOnlyCaptureRenderState = null;
        }

        if (!useDynamicGUISystem()) return;
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) return;
        if (Minecraft.getInstance().screen == null) return;
        boolean useXaeroMainTargetFallback = xaeroGuiMapCaptureActive;
        if (!guiOnlyCapturePrepared && !useXaeroMainTargetFallback) {
            return;
        }

        ScreenParticleRenderer spr = ScreenParticleRenderer.getInstance();
        spr.checkSetup();

        // Xaero World Map does not fully participate in the 1.21.6 GuiRenderState draw-list path,
        // so fall back to the vanilla main target after GUI render for that screen only.
        GpuTextureView sourceView = useXaeroMainTargetFallback
                ? Minecraft.getInstance().getMainRenderTarget().getColorTextureView()
                : spr.getMainRenderTarget().getColorTextureView();
        if (sourceView == null) return;

        spr.setCaptureSourceOverride(sourceView);

        try {
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            if (ConfigServerControlledSyncedToClient.dynamicGuiShowClientsEntireScreen) {
                guiScale = 1;
            }
            int croppedWidth = (int) (spr.widthScaledDown * guiScale);
            int croppedHeight = (int) (spr.heightScaledDown * guiScale);

            int centerX = spr.width / 2;
            int centerY = spr.height / 2;
            int x1 = centerX - (croppedWidth / 2);
            int x2 = centerX + (croppedWidth / 2);
            int y1 = centerY - (croppedHeight / 2);
            int y2 = centerY + (croppedHeight / 2);
            float minU = (float) x1 / (float) spr.width;
            float maxU = (float) x2 / (float) spr.width;
            float minV = (float) y1 / (float) spr.height;
            float maxV = (float) y2 / (float) spr.height;

            x1 = 0;
            x2 = spr.widthScaledDown;
            y1 = 0;
            y2 = spr.heightScaledDown;

            boolean useBlur = true;
            if (useBlur) {
                spr.innerBlitCustomShaderHorizontal(
                        x1, x2, y1, y2, 0, minU, maxU, minV, maxV);

                spr.innerBlitCustomShaderVertical(
                        0, spr.widthScaledDown, 0, spr.heightScaledDown, 0, 0, 1, 0, 1);
            } else {
                spr.innerBlitCustomShader(
                        x1, x2, y1, y2, 0, minU, maxU, minV, maxV);
            }

            // Async readback from scaled down framebuffer
            getPixelDataFromFrameBufferAsync((pixelBuffer) -> {
                boolean useThread = true;
                if (useThread) {
                    processor.submitForProcessing(pixelBuffer);
                } else {
                    PlayerStatus playerStatusLocal = WatutMod.getPlayerStatusManagerClient().getStatusLocal();
                    ByteBuffer byteBuffer = compress(pixelBuffer);
                    playerStatusLocal.getScreenData().setTexturePixelData(byteBuffer);
                    WatutMod.getPlayerStatusManagerClient().sendScreenRenderData(playerStatusLocal);
                }
            });
        } finally {
            spr.clearCaptureSourceOverride();
        }
    }

    public static ByteBuffer compress(ByteBuffer inputBuffer) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);

        // Copy ByteBuffer data into a byte array
        byte[] inputBytes = new byte[inputBuffer.remaining()];
        inputBuffer.get(inputBytes);
        deflater.setInput(inputBytes);
        deflater.finish();

        // Use a direct buffer for compressed data
        ByteBuffer outputBuffer = ByteBuffer.allocateDirect(inputBytes.length + 512); // Allow extra space
        byte[] temp = new byte[1024];

        while (!deflater.finished()) {
            int compressedBytes = deflater.deflate(temp);
            if (outputBuffer.remaining() < compressedBytes) {
                // Expand the direct buffer dynamically
                ByteBuffer newBuffer = ByteBuffer.allocateDirect(outputBuffer.capacity() * 2);
                outputBuffer.flip();
                newBuffer.put(outputBuffer);
                outputBuffer = newBuffer;
            }
            outputBuffer.put(temp, 0, compressedBytes);
        }
        deflater.end();

        outputBuffer.flip(); // Prepare buffer for reading
        inputBuffer.flip();
        return outputBuffer;
    }

    public static ByteBuffer decompress(ScreenData screenData, ByteBuffer compressedBuffer, int expectedSize) throws Exception {
        Inflater inflater = new Inflater();

        // Copy compressed data into a byte array
        byte[] compressedBytes = new byte[compressedBuffer.remaining()];
        compressedBuffer.get(compressedBytes);
        inflater.setInput(compressedBytes);

        ByteBuffer decompressionBuffer = screenData.getDecompressionBuffer();

        // Use a direct buffer for decompressed data
        if (decompressionBuffer == null) {
            CULog.dbg("Creating new buffer for decompression");
            decompressionBuffer = MemoryUtil.memAlloc(expectedSize); // Allocate initial space
            screenData.setDecompressionBuffer(decompressionBuffer);
        } else {
            decompressionBuffer.clear(); // Reset the buffer for writing
        }

        byte[] temp = new byte[1024];

        while (!inflater.finished()) {
            int decompressedBytes = inflater.inflate(temp);

            // Ensure there is enough space in the buffer
            if (decompressionBuffer.remaining() < decompressedBytes) {
                // Resize the buffer by creating a new one with double the capacity
                int newCapacity = Math.max(decompressionBuffer.capacity() * 2, decompressionBuffer.capacity() + decompressedBytes);
                CULog.dbg("adjusting size of buffer for decompression");
                ByteBuffer newBuffer = MemoryUtil.memAlloc(newCapacity);
                decompressionBuffer.flip(); // Prepare for reading
                newBuffer.put(decompressionBuffer); // Copy old data to new buffer
                MemoryUtil.memFree(decompressionBuffer);
                decompressionBuffer = newBuffer;
                screenData.setDecompressionBuffer(decompressionBuffer);
            }

            decompressionBuffer.put(temp, 0, decompressedBytes);
        }
        inflater.end();

        decompressionBuffer.flip(); // Prepare buffer for reading

        return decompressionBuffer;
    }

    public static ByteBuffer decompress2(ScreenData screenData, ByteBuffer compressedBuffer, int expectedSize) throws Exception {
        Inflater inflater = new Inflater();

        // Copy compressed data into a byte array
        byte[] compressedBytes = new byte[compressedBuffer.remaining()];
        compressedBuffer.get(compressedBytes);
        inflater.setInput(compressedBytes);

        ByteBuffer decompressionBuffer = screenData.getDecompressionBuffer();

        // Use a direct buffer for decompressed data
        if (decompressionBuffer == null) {
            decompressionBuffer = ByteBuffer.allocateDirect(expectedSize); // Allocate space for expected size
            screenData.setDecompressionBuffer(decompressionBuffer);
        } else {
            decompressionBuffer.clear();
        }
        //ByteBuffer outputBuffer = ByteBuffer.allocateDirect(expectedSize); // Allocate space for expected size
        byte[] temp = new byte[1024];

        while (!inflater.finished()) {
            int decompressedBytes = inflater.inflate(temp);
            if (decompressionBuffer.remaining() < decompressedBytes) {
                throw new IllegalStateException("Decompressed size exceeds expected size!");
            }
            decompressionBuffer.put(temp, 0, decompressedBytes);
        }
        inflater.end();

        decompressionBuffer.flip(); // Prepare buffer for reading
        return decompressionBuffer;
    }

    public static ByteBuffer decompressGZIP(ByteBuffer compressedBuffer) throws IOException {
        // Extract the byte array from the input ByteBuffer
        byte[] compressedBytes = new byte[compressedBuffer.remaining()];
        compressedBuffer.get(compressedBytes);

        // Use a ByteArrayInputStream to wrap the compressed data
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(compressedBytes);

        // Create a GZIPInputStream for decompression
        GZIPInputStream gzipInputStream = new GZIPInputStream(byteArrayInputStream);

        // Read decompressed data into a ByteArrayOutputStream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = gzipInputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, bytesRead);
        }

        // Close streams
        gzipInputStream.close();
        byteArrayInputStream.close();

        // Get the decompressed data as a byte array
        byte[] decompressedBytes = byteArrayOutputStream.toByteArray();

        // Create a direct ByteBuffer and put the decompressed data into it
        ByteBuffer directBuffer = ByteBuffer.allocateDirect(decompressedBytes.length);
        directBuffer.put(decompressedBytes);
        directBuffer.flip(); // Flip the buffer to prepare it for reading

        return directBuffer;
    }

    public static ByteBuffer compressGZIP(ByteBuffer inputBuffer) throws IOException {
        // Extract bytes from the input ByteBuffer
        byte[] inputBytes = new byte[inputBuffer.remaining()];
        inputBuffer.get(inputBytes);

        // Create a ByteArrayOutputStream to hold the compressed data
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        // Use GZIPOutputStream to compress the data
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(inputBytes);
        }

        // Get the compressed data as a byte array
        byte[] compressedBytes = byteArrayOutputStream.toByteArray();

        // Wrap the compressed data in a ByteBuffer and return it
        return ByteBuffer.wrap(compressedBytes);
    }

    /**
     * Copy captured framebuffer bytes into NativeImage backing memory.
     *
     * Readback normalization (alpha/channel fixes) is now done at capture time before compression,
     * so upload only needs a raw copy here.
     */
    private static void copyReadbackPixelsToNativeImage(ByteBuffer sourcePixels, long nativeImagePixelMemoryAddress, int width, int height) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(sourcePixels), nativeImagePixelMemoryAddress,
                (long) width * height * ScreenParticleRenderer.bytesPerPixel);
    }

    public static void getPixelDataFromFrameBufferAsync(java.util.function.Consumer<ByteBuffer> callback) {
        int width = ScreenParticleRenderer.getInstance().widthScaledDown;
        int height = ScreenParticleRenderer.getInstance().heightScaledDown;

        GpuTexture texture = ScreenParticleRenderer.getInstance().getMainRenderTargetScaledDown().getColorTexture();
        if (texture == null) {
            int bufferSize = width * height * ScreenParticleRenderer.bytesPerPixel;
            callback.accept(ByteBuffer.allocateDirect(bufferSize));
            return;
        }

        int pixelSize = texture.getFormat().pixelSize();
        int bufferSize = width * height * pixelSize;
        if (pixelSize != ScreenParticleRenderer.bytesPerPixel) {
            CULog.log("watut: unexpected framebuffer pixel size " + pixelSize + ", expected " + ScreenParticleRenderer.bytesPerPixel);
        }

        // Create a GPU buffer for readback (MAP_READ | COPY_DST = 9)
        GpuBuffer readbackBuffer = RenderSystem.getDevice().createBuffer(
                () -> "watut_readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, bufferSize);

        // Following MC Screenshot pattern: mapEncoder created before, buffer closed inside callback
        CommandEncoder mapEncoder = RenderSystem.getDevice().createCommandEncoder();
        RenderSystem.getDevice().createCommandEncoder()
                .copyTextureToBuffer(texture, readbackBuffer, 0, () -> {
                    ByteBuffer pixelBuffer = ByteBuffer.allocateDirect(bufferSize);
                    try (GpuBuffer.MappedView mappedView = mapEncoder.mapBuffer(readbackBuffer, true, false)) {
                        ByteBuffer gpuData = mappedView.data();
                        if (gpuData != null && gpuData.remaining() >= bufferSize) {
                            gpuData.limit(gpuData.position() + bufferSize);
                            pixelBuffer.put(gpuData);
                            pixelBuffer.flip();
                        }
                    }
                    readbackBuffer.close();
                    callback.accept(pixelBuffer);
                }, 0);
    }

    public static boolean validatePixelByteBuffer(ByteBuffer byteBuffer, int expectedSize, int expectedAlignment) {

        if (byteBuffer == null) return false;

        if (byteBuffer.limit() != expectedSize) {
            return false;
        }

        //GL30.glPixelStorei(GL30.GL_UNPACK_ALIGNMENT, expectedAlignment);

        return true;
    }

    public static void readPixelsTest() {
        int width = ScreenParticleRenderer.getInstance().width;
        int height = ScreenParticleRenderer.getInstance().height;

        /*int width = ScreenParticleRenderer.getInstance().widthScaledDown;
        int height = ScreenParticleRenderer.getInstance().heightScaledDown;*/

        //TODO: readPixelsTest needs rework for 1.21.5 GPU abstraction
        CULog.dbg("readPixelsTest: not implemented for 1.21.5");
    }

    private static boolean hasNonZeroData(ByteBuffer buffer) {
        int pos = buffer.position();
        int limit = Math.min(buffer.limit(), pos + 1024);
        for (int i = pos; i < limit; i++) {
            if (buffer.get(i) != 0) return true;
        }
        return false;
    }

}
