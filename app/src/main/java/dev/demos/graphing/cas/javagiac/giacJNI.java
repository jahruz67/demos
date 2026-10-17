package javagiac;

/**
 * Minimal declarations for the public SWIG entry points in upstream libjavagiac.
 * The package and method names are part of that native ABI; do not obfuscate.
 * Pointer-owning calls are used only by NativeGiac on its serialized worker.
 */
public final class giacJNI {
    private giacJNI() {}

    public static native long new_context__SWIG_0();
    public static native void delete_context(long pointer);
    public static native long new_gen__SWIG_25(String text, long context, Object owner);
    public static native long gen_eval(long pointer, Object owner, int level, long context, Object contextOwner);
    public static native long gen_evalf(long pointer, Object owner, int level, long context, Object contextOwner);
    public static native String gen_print__SWIG_0(long pointer, Object owner, long context, Object contextOwner);
    public static native short gen_type_get(long pointer, Object owner);
    public static native byte gen_subtype_get(long pointer, Object owner);
    public static native void delete_gen(long pointer);
}
