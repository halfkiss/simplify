package org.cf.util;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntObjectMap;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.ClassUtils;
import org.cf.smalivm.type.LocalInstance;
import org.jf.dexlib2.writer.builder.BuilderTypeList;
import org.jf.dexlib2.writer.builder.BuilderTypeReference;

public class Utils {

    private static final Pattern ParameterIndividuator = Pattern.compile("(\\[*(?:[BCDFIJSZ]|L[^;]+;))");
    private static final Pattern ParameterIsolator = Pattern.compile("\\([^\\)]+\\)");

    public static void deDuplicate(TIntList list) {
        for (int i = 0; i < list.size(); i++) {
            int item = list.get(i);
            int firstIndex = list.indexOf(item);
            while (firstIndex != list.lastIndexOf(item)) {
                int lastIndex = list.lastIndexOf(item);
                list.removeAt(lastIndex);
            }
        }
    }

    public static String getArrayDimensionString(Object array) {
        if (!array.getClass().isArray()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        Object current = array;
        int len = Array.getLength(current);
        sb.append('[').append(len).append(']');

        while (len > 0) {
            current = Array.get(current, 0);
            if ((current == null) || !current.getClass().isArray()) {
                break;
            }
            len = Array.getLength(current);
            sb.append('[').append(len).append(']');
        }

        return sb.toString();
    }

    public static Object getArrayInstanceFromSmaliTypeReference(String typeReference, int dimension,
                    boolean useLocalClass) throws ClassNotFoundException {
        return getArrayInstanceFromSmaliTypeReference(typeReference, new int[] { dimension }, useLocalClass);
    }

    public static Object getArrayInstanceFromSmaliTypeReference(String typeReference, int[] dimensions,
                    boolean useLocalClass) throws ClassNotFoundException {
        String baseClassName = SmaliClassUtils.getBaseClass(typeReference);
        String javaClassName;
        if (useLocalClass) {
            javaClassName = LocalInstance.class.getName();
        } else {
            javaClassName = SmaliClassUtils.smaliClassToJava(baseClassName);
        }

        int dimensionCount = getDimensionCount(typeReference) - 1;
        String classNameWithDimensions = addDimensionsToClassName(javaClassName, dimensionCount);
        Class<?> klazz = ClassUtils.getClass(classNameWithDimensions);
        Object result = Array.newInstance(klazz, dimensions);

        if (useLocalClass) {
            populateLocalInstanceArray(result, baseClassName);
        }

        return result;
    }

    public static int getDimensionCount(String typeReference) {
        String baseClassName = typeReference.replace("[", "");

        return typeReference.length() - baseClassName.length();
    }

    public static List<String> getParameterTypes(String methodDescriptor) {
        // Only use this for non-local methods.
        // For local methods, there's VirtualMachine#getParameterTypes.
        Matcher m = ParameterIsolator.matcher(methodDescriptor);
        List<String> result = new ArrayList<String>();
        if (m.find()) {
            String params = m.group();
            m = ParameterIndividuator.matcher(params);
            while (m.find()) {
                result.add(m.group());
            }
        }

        return result;
    }

    public static <E> Collection<E> makeCollection(Iterable<E> iter) {
        Collection<E> list = new ArrayList<E>();
        for (E item : iter) {
            list.add(item);
        }
        return list;
    }

    public static <T> void shiftIntegerMapKeys(int startKey, int shift, TIntObjectMap<T> intToObject) {
        if (shift == 0) {
            return;
        }

        TIntList keysToShift = new TIntArrayList(intToObject.keys());
        // Exclude anything before and including startKey
        for (int currentKey : keysToShift.toArray()) {
            if (currentKey <= startKey) {
                keysToShift.remove(currentKey);
            }
        }

        keysToShift.sort();
        if (shift > 0) {
            // Shifting keys up, so start at the end to avoid overwriting keys.
            keysToShift.reverse();
        }

        for (int currentKey : keysToShift.toArray()) {
            T obj = intToObject.get(currentKey);
            intToObject.remove(currentKey);
            intToObject.put(currentKey + shift, obj);
        }
    }

    private static String addDimensionsToClassName(String className, int dimensionCount) {
        // Apache's ClassUtils.forName expects someArray[] instead of [someArray
        StringBuilder sb = new StringBuilder(className);
        for (int i = 0; i < dimensionCount; i++) {
            sb.append("[]");
        }

        return sb.toString();
    }

    private static void populateLocalInstanceArray(Object array, String className) {
        for (int i = 0; i < Array.getLength(array); i++) {
            Object element = Array.get(array, i);
            if (element == null) {
                if (array.getClass().getName().startsWith("[[")) {
                    // Uninitialized inner array
                    break;
                } else {
                    for (int j = 0; j < Array.getLength(array); j++) {
                        Array.set(array, j, new LocalInstance(className));
                    }
                }
            } else if (element.getClass().isArray()) {
                populateLocalInstanceArray(element, className);
            }
        }
    }

    public static int getRegisterSize(List<String> parameterTypeNames) {
        int size = 0;
        for (String name : parameterTypeNames) {
            size += "J".equals(name) || "D".equals(name) ? 2 : 1;
        }

        return size;
    }

    public static int getRegisterSize(Class<?>[] parameterTypes) {
        return getRegisterSize(SmaliClassUtils.javaClassToSmali(parameterTypes));
    }

    public static List<String> builderTypeListToStringList(BuilderTypeList typeList) {
        List<String> typeNames = new LinkedList<String>();
        for (BuilderTypeReference type : typeList) {
            typeNames.add(type.getType());
        }

        return typeNames;
    }

    public static int getRegisterSize(BuilderTypeList typeList) {
        return getRegisterSize(builderTypeListToStringList(typeList));
    }

    public static Integer getIntegerValue(Object obj) {
        Integer intValue = (Integer) castToPrimitiveWrapper(obj, "Ljava/lang/Integer;");

        return intValue;
    }

    public static Float getFloatValue(Object obj) {
        Float floatValue = (Float) castToPrimitiveWrapper(obj, "Ljava/lang/Float;");

        return floatValue;
    }

    public static Double getDoubleValue(Object obj) {
        Double doubleValue = (Double) castToPrimitiveWrapper(obj, "Ljava/lang/Double;");

        return doubleValue;
    }

    public static Long getLongValue(Object obj) {
        Long longValue = (Long) castToPrimitiveWrapper(obj, "Ljava/lang/Long;");

        return longValue;
    }

    public static Object castToPrimitiveWrapper(Object value, String targetType) {
        // TODO: add tests for this + confirm dalvik works this way

        // Type information is not always available beyond "const" because Dalvik handles multiple types like integers.
        // This is to make easier the casting of that number to the correct type.
        if (value instanceof Number) {
            Number castValue = (Number) value;
            if ("B".equals(targetType) || "Ljava/lang/Byte;".equals(targetType)) {
                return castValue.byteValue();
            } else if ("D".equals(targetType) || "Ljava/lang/Double;".equals(targetType)) {
                return castValue.doubleValue();
            } else if ("F".equals(targetType) || "Ljava/lang/Float;".equals(targetType)) {
                return castValue.floatValue();
            } else if ("I".equals(targetType) || "Ljava/lang/Integer;".equals(targetType)) {
                return castValue.intValue();
            } else if ("L".equals(targetType) || "Ljava/lang/Long;".equals(targetType)) {
                return castValue.longValue();
            } else if ("S".equals(targetType) || "Ljava/lang/Short;".equals(targetType)) {
                return castValue.shortValue();
            } else if ("C".equals(targetType) || "Ljava/lang/Character;".equals(targetType)) {
                return (char) castValue.intValue();
            } else if ("Z".equals(targetType) || "Ljava/lang/Boolean;".equals(targetType)) {
                return castValue.intValue() != 0 ? true : false;
            }
        } else if (value instanceof Boolean) {
            Boolean castValue = (Boolean) value;
            if ("Z".equals(targetType) || "Ljava/lang/Boolean;".equals(targetType)) {
                return castValue;
            } else if ("B".equals(targetType) || "Ljava/lang/Byte;".equals(targetType)) {
                return castValue ? 1 : 0;
            } else if ("I".equals(targetType) || "Ljava/lang/Integer;".equals(targetType)) {
                return castValue ? 1 : 0;
            } else if ("S".equals(targetType) || "Ljava/lang/Short;".equals(targetType)) {
                return castValue ? 1 : 0;
            }
        } else if (value instanceof Character) {
            Character castValue = (Character) value;
            Integer intValue = (int) castValue;
            if ("Z".equals(targetType) || "Ljava/lang/Boolean;".equals(targetType)) {
                return (int) castValue != 0 ? true : false;
            } else if ("B".equals(targetType) || "Ljava/lang/Byte;".equals(targetType)) {
                return intValue.byteValue();
            } else if ("I".equals(targetType) || "Ljava/lang/Integer;".equals(targetType)) {
                return intValue;
            } else if ("S".equals(targetType) || "Ljava/lang/Short;".equals(targetType)) {
                return intValue.shortValue();
            }
        }

        return value;
    }

}
