/*
 * Headless inspection of the real shared renderer on Linux, using Mesa EGL.
 * Build :core:classes first. Compile/run with core/build/classes/kotlin/main,
 * kotlin-stdlib and JNA on the classpath. JNA is already a desktop dependency.
 * Run with EGL_PLATFORM=surfaceless LIBGL_ALWAYS_SOFTWARE=1 LP_NUM_THREADS=1.
 * Args: output-directory [size=420] [look=all] [mood=CALM]
 * This observes renderer output; it does not validate the Android UI or GPU.
 */
import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.kuafuai.andee.ui.ball.EmotionBallRenderer;
import net.kuafuai.andee.ui.ball.Mood;
import net.kuafuai.andee.ui.ball.gl.GLES20;
import net.kuafuai.andee.ui.ball.gl.GlBackend;

public final class CielAvatarRender {
    private static final NativeLibrary EGL = NativeLibrary.getInstance("EGL");
    private static final Map<String, Function> GL = new HashMap<>();
    private static Function gl(String name) {
        return GL.computeIfAbsent(name, key -> {
            Pointer address = EGL.getFunction("eglGetProcAddress").invokePointer(new Object[]{key});
            if (address == null) throw new IllegalStateException("Missing GL function " + key);
            return Function.getFunction(address);
        });
    }
    private static int egl(String name, Object... args) {
        return EGL.getFunction(name).invokeInt(args);
    }
    private static Pointer eglPointer(String name, Object... args) {
        return EGL.getFunction(name).invokePointer(args);
    }
    private static void require(boolean ok, String step) {
        if (!ok) throw new IllegalStateException(step + ": EGL 0x" + Integer.toHexString(egl("eglGetError")));
    }
    private static Pointer buffer(Buffer data) {
        if (data == null) return null;
        int stride = data instanceof FloatBuffer || data instanceof IntBuffer ? 4
            : data instanceof ShortBuffer ? 2 : 1;
        return Native.getDirectBufferPointer(data).share((long) data.position() * stride);
    }
    private static String infoLog(String kind, int id) {
        int[] length = new int[1];
        gl("glGet" + kind + "iv").invokeVoid(new Object[]{id, 0x8B84, length});
        if (length[0] <= 1) return "";
        try (Memory data = new Memory(length[0])) {
            gl("glGet" + kind + "InfoLog").invokeVoid(new Object[]{id, length[0], null, data});
            return data.getString(0, "UTF-8");
        }
    }
    private static GlBackend backend() {
        return (GlBackend) Proxy.newProxyInstance(GlBackend.class.getClassLoader(), new Class<?>[]{GlBackend.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("adaptShader")) return args[0];
                if (name.equals("glGetProgramInfoLog")) return infoLog("Program", (int) args[0]);
                if (name.equals("glGetShaderInfoLog")) return infoLog("Shader", (int) args[0]);
                if (name.equals("glShaderSource")) {
                    byte[] bytes = ((String) args[1]).getBytes(StandardCharsets.UTF_8);
                    try (Memory text = new Memory(bytes.length + 1L)) {
                        text.write(0, bytes, 0, bytes.length);
                        text.setByte(bytes.length, (byte) 0);
                        gl(name).invokeVoid(new Object[]{args[0], 1, new PointerByReference(text), new int[]{bytes.length}});
                    }
                    return null;
                }
                Object[] nativeArgs = args == null ? new Object[0] : args.clone();
                if (name.equals("glBufferData")) {
                    nativeArgs[1] = new NativeLong((int) args[1]);
                    nativeArgs[2] = buffer((Buffer) args[2]);
                } else if (name.equals("glBufferSubData")) {
                    nativeArgs[1] = new NativeLong((int) args[1]);
                    nativeArgs[2] = new NativeLong((int) args[2]);
                    nativeArgs[3] = buffer((Buffer) args[3]);
                } else if (name.equals("glDepthMask")) {
                    nativeArgs[0] = (byte) ((boolean) args[0] ? 1 : 0);
                } else if (name.equals("glDrawElements")) {
                    nativeArgs[3] = Pointer.createConstant((int) args[3]);
                } else if (name.equals("glVertexAttribPointer")) {
                    nativeArgs[3] = (byte) ((boolean) args[3] ? 1 : 0);
                    nativeArgs[5] = Pointer.createConstant((int) args[5]);
                } else if (name.equals("glGenBuffers") || name.equals("glGetProgramiv") || name.equals("glGetShaderiv")) {
                    int arrayAt = name.equals("glGenBuffers") ? 1 : 2;
                    int[] out = (int[]) args[arrayAt];
                    int offset = (int) args[arrayAt + 1];
                    int count = name.equals("glGenBuffers") ? (int) args[0] : 1;
                    int[] result = new int[count];
                    nativeArgs = Arrays.copyOf(args, arrayAt + 1);
                    nativeArgs[arrayAt] = result;
                    gl(name).invokeVoid(nativeArgs);
                    System.arraycopy(result, 0, out, offset, count);
                    return null;
                } else if (name.equals("glUniform3fv")) {
                    nativeArgs = new Object[]{args[0], args[1], Arrays.copyOfRange((float[]) args[2], (int) args[3], (int) args[3] + 3 * (int) args[1])};
                } else if (name.equals("glUniformMatrix4fv")) {
                    nativeArgs = new Object[]{args[0], args[1], (byte) ((boolean) args[2] ? 1 : 0), Arrays.copyOfRange((float[]) args[3], (int) args[4], (int) args[4] + 16 * (int) args[1])};
                }
                if (method.getReturnType() == int.class) return gl(name).invokeInt(nativeArgs);
                gl(name).invokeVoid(nativeArgs);
                return null;
            });
    }
    private static void save(File output, int size) throws Exception {
        try (Memory pixels = new Memory((long) size * size * 4)) {
            gl("glFinish").invokeVoid(new Object[]{});
            gl("glReadPixels").invokeVoid(new Object[]{0, 0, size, size, 0x1908, 0x1401, pixels});
            int error = gl("glGetError").invokeInt(new Object[]{});
            if (error != 0) throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error));
            byte[] rgba = pixels.getByteArray(0, (long) size * size * 4 > Integer.MAX_VALUE ? 0 : size * size * 4);
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
            int[] argb = new int[size * size];
            int drawn = 0;
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int i = ((size - 1 - y) * size + x) * 4;
                int a = rgba[i + 3] & 255;
                if (a != 0) drawn++;
                argb[y * size + x] = (a << 24) | ((rgba[i] & 255) << 16) | ((rgba[i + 1] & 255) << 8) | (rgba[i + 2] & 255);
            }
            if (drawn < size * size / 100) throw new IllegalStateException("Avatar frame is empty");
            image.getRaster().setDataElements(0, 0, size, size, argb);
            ImageIO.write(image, "png", output);
            System.out.println(output + " pixels=" + drawn);
        }
    }
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        if (!out.isDirectory() && !out.mkdirs()) throw new IllegalStateException("Cannot create output directory");
        int size = args.length > 1 ? Integer.parseInt(args[1]) : 420;
        if (size < 64 || size > 2048) throw new IllegalArgumentException("Size must be 64..2048");
        String[] looks = args.length < 3 || args[2].equals("all") ? new String[]{"andee", "moss", "nova"} : new String[]{args[2]};
        Mood[] moods = args.length > 3 && args[3].equals("all") ? Mood.values()
            : new Mood[]{Mood.valueOf(args.length > 3 ? args[3] : "CALM")};
        Pointer display = eglPointer("eglGetDisplay", Pointer.NULL);
        require(display != null, "display");
        require(egl("eglInitialize", display, new int[1], new int[1]) != 0, "initialize");
        PointerByReference config = new PointerByReference();
        int[] count = new int[1];
        int[] attrs = {0x3033, 1, 0x3040, 4, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3025, 16, 0x3038};
        require(egl("eglChooseConfig", display, attrs, config, 1, count) != 0 && count[0] > 0, "config");
        require(egl("eglBindAPI", 0x30A0) != 0, "bind GLES");
        Pointer context = eglPointer("eglCreateContext", display, config.getValue(), Pointer.NULL, new int[]{0x3098, 2, 0x3038});
        require(context != null, "context");
        Pointer surface = eglPointer("eglCreatePbufferSurface", display, config.getValue(), new int[]{0x3057, size, 0x3056, size, 0x3038});
        require(surface != null, "surface");
        try {
            require(egl("eglMakeCurrent", display, surface, surface, context) != 0, "make current");
            System.out.println("Renderer: " + gl("glGetString").invokePointer(new Object[]{0x1F01}).getString(0));
            GLES20.INSTANCE.setBackend(backend());
            for (String look : looks) {
                EmotionBallRenderer renderer = new EmotionBallRenderer();
                renderer.setPreviewLook(look);
                renderer.onSurfaceCreated();
                renderer.onSurfaceChanged(size, size);
                for (Mood mood : moods) {
                    renderer.setMood(mood);
                    renderer.setListening(mood == Mood.LISTENING);
                    long deadline = System.nanoTime() + 1_300_000_000L;
                    do {
                        renderer.onDrawFrame();
                        Thread.sleep(16);
                    } while (System.nanoTime() < deadline);
                    save(new File(out, look + "-" + mood.name().toLowerCase() + ".png"), size);
                }
            }
        } finally {
            egl("eglMakeCurrent", display, Pointer.NULL, Pointer.NULL, Pointer.NULL);
            egl("eglDestroySurface", display, surface);
            egl("eglDestroyContext", display, context);
            egl("eglTerminate", display);
        }
    }
}
