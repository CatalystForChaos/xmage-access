package xmageaccess.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared reflection utilities for accessing XMage classes that are
 * not on the agent's compile-time classpath.
 *
 * <p>All lookups (Method by name, Field by name with or without class-hierarchy
 * walking) are cached. Negative results — i.e. "no such method/field on this
 * class" — are cached as well, so a missing member is never re-resolved.
 * Caches are per-class and effectively permanent for the agent's lifetime;
 * XMage classes are loaded once and never redefined.
 */
public final class ReflectionUtils {

    private static final ConcurrentHashMap<String, FieldResult> FIELD_DEEP_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FieldResult> FIELD_DECL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, FieldResult> FIELD_SPECIFIC_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MethodResult> METHOD_NOARG_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MethodResult> METHOD_ARG_CACHE = new ConcurrentHashMap<>();

    private ReflectionUtils() {}

    /** Walk the class hierarchy of target.getClass() to find and read a field by name. */
    public static Object findFieldDeep(Object target, String name) {
        if (target == null) return null;
        Class<?> rootClass = target.getClass();
        String key = rootClass.getName() + '#' + name;
        FieldResult cached = FIELD_DEEP_CACHE.get(key);
        if (cached == null) {
            cached = new FieldResult(walkAndFindField(rootClass, name));
            FIELD_DEEP_CACHE.put(key, cached);
        }
        if (cached.field == null) return null;
        try {
            return cached.field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    /** Like {@link #findFieldDeep} but only returns the value if it matches expected type. */
    @SuppressWarnings("unchecked")
    public static <T> T findFieldTyped(Object target, String name, Class<T> type) {
        Object val = findFieldDeep(target, name);
        if (type.isInstance(val)) return (T) val;
        return null;
    }

    /** Read a declared field from a specific class (no hierarchy walk). */
    @SuppressWarnings("unchecked")
    public static <T> T getField(Object target, Class<?> clazz, String name, Class<T> type) {
        String key = clazz.getName() + '#' + name;
        FieldResult cached = FIELD_SPECIFIC_CACHE.get(key);
        if (cached == null) {
            Field f = null;
            try {
                f = clazz.getDeclaredField(name);
                f.setAccessible(true);
            } catch (Exception ignored) {
                // field absent — cache negative
            }
            cached = new FieldResult(f);
            FIELD_SPECIFIC_CACHE.put(key, cached);
        }
        if (cached.field == null) return null;
        try {
            Object val = cached.field.get(target);
            if (type.isInstance(val)) return (T) val;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Find a declared field by walking the class hierarchy starting at clazz. */
    public static Field findField(Class<?> clazz, String name) {
        if (clazz == null) return null;
        String key = clazz.getName() + '#' + name;
        FieldResult cached = FIELD_DECL_CACHE.get(key);
        if (cached == null) {
            cached = new FieldResult(walkAndFindField(clazz, name));
            FIELD_DECL_CACHE.put(key, cached);
        }
        return cached.field;
    }

    /** Invoke a no-argument method by name. Returns null on null target or failure. */
    public static Object callMethod(Object obj, String methodName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        String key = clazz.getName() + '#' + methodName;
        MethodResult cached = METHOD_NOARG_CACHE.get(key);
        if (cached == null) {
            Method m = null;
            try {
                m = clazz.getMethod(methodName);
            } catch (Exception ignored) {
            }
            cached = new MethodResult(m);
            METHOD_NOARG_CACHE.put(key, cached);
        }
        if (cached.method == null) return null;
        try {
            return cached.method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public static String callString(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        return result != null ? result.toString() : null;
    }

    public static int callInt(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Number) return ((Number) result).intValue();
        return 0;
    }

    public static boolean callBool(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Boolean) return (Boolean) result;
        return false;
    }

    /** Invoke a single-argument method by name. */
    public static Object callMethodWithArg(Object obj, String methodName, Class<?> argType, Object arg) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        String key = clazz.getName() + '#' + methodName + '(' + argType.getName() + ')';
        MethodResult cached = METHOD_ARG_CACHE.get(key);
        if (cached == null) {
            Method m = null;
            try {
                m = clazz.getMethod(methodName, argType);
            } catch (Exception ignored) {
            }
            cached = new MethodResult(m);
            METHOD_ARG_CACHE.put(key, cached);
        }
        if (cached.method == null) return null;
        try {
            return cached.method.invoke(obj, arg);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field walkAndFindField(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static final class FieldResult {
        final Field field;
        FieldResult(Field f) { field = f; }
    }

    private static final class MethodResult {
        final Method method;
        MethodResult(Method m) { method = m; }
    }
}
