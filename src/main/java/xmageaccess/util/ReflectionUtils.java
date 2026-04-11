package xmageaccess.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Shared reflection utilities for accessing XMage classes that are
 * not on the agent's compile-time classpath.
 */
public final class ReflectionUtils {

    private ReflectionUtils() {}

    /**
     * Walk the class hierarchy to find and read a field by name.
     * Returns null if the field is not found or an error occurs.
     */
    public static Object findFieldDeep(Object target, String name) {
        if (target == null) return null;
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Walk the class hierarchy to find a field by name, returning it
     * only if it matches the expected type.
     */
    @SuppressWarnings("unchecked")
    public static <T> T findFieldTyped(Object target, String name, Class<T> type) {
        Object val = findFieldDeep(target, name);
        if (type.isInstance(val)) return (T) val;
        return null;
    }

    /**
     * Read a declared field from a specific class (no hierarchy walk).
     * Useful for dialog handlers where the exact class is known.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getField(Object target, Class<?> clazz, String name, Class<T> type) {
        try {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            Object val = field.get(target);
            if (type.isInstance(val)) {
                return (T) val;
            }
        } catch (Exception e) {
            // Field doesn't exist or wrong type
        }
        return null;
    }

    /**
     * Invoke a no-argument method on an object via reflection.
     * Returns null if the object is null or invocation fails.
     */
    public static Object callMethod(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Invoke a no-argument method and return the result as a String.
     */
    public static String callString(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        return result != null ? result.toString() : null;
    }

    /**
     * Invoke a no-argument method and return the result as an int.
     * Returns 0 if the result is null or not a Number.
     */
    public static int callInt(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Number) return ((Number) result).intValue();
        return 0;
    }

    /**
     * Invoke a no-argument method and return the result as a boolean.
     * Returns false if the result is null or not a Boolean.
     */
    public static boolean callBool(Object obj, String methodName) {
        Object result = callMethod(obj, methodName);
        if (result instanceof Boolean) return (Boolean) result;
        return false;
    }

    /**
     * Invoke a single-argument method on an object via reflection.
     * Returns null if the object is null or invocation fails.
     */
    public static Object callMethodWithArg(Object obj, String methodName, Class<?> argType, Object arg) {
        if (obj == null) return null;
        try {
            Method method = obj.getClass().getMethod(methodName, argType);
            return method.invoke(obj, arg);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Find a declared field by walking the class hierarchy.
     * Returns the Field object (not its value), or null.
     */
    public static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
