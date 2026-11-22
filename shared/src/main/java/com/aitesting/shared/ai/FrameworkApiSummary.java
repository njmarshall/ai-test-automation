package com.aitesting.shared.ai;

import com.aitesting.shared.assertions.ResponseValidator;
import com.aitesting.shared.dataprovider.TestDataFactory;
import com.aitesting.shared.http.ApiClient;
import com.aitesting.shared.reporting.AllureHelper;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a plain-text summary of the public API of the framework classes that
 * generated tests may use, by reflection, so the prompt always matches the code.
 *
 * Each class lists its public fields and its public declared methods
 * (name, parameter types, return type), sorted by name. In a CRTP class such
 * as {@code ResponseValidator<T extends ResponseValidator<T>>} the self type
 * {@code T} is shown as the class name, so fluent chains read naturally.
 */
public final class FrameworkApiSummary {

    /** The classes generated tests are allowed to use. */
    static final List<Class<?>> CLASSES = List.of(
            ApiClient.class, ResponseValidator.class, TestDataFactory.class, AllureHelper.class);

    private FrameworkApiSummary() {}

    /** Summary of {@link #CLASSES}. */
    public static String build() {
        return build(CLASSES);
    }

    static String build(List<Class<?>> classes) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> type : classes) {
            sb.append(header(type)).append('\n');
            for (String line : members(type)) {
                sb.append("  ").append(line).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    private static String header(Class<?> type) {
        String kind = Modifier.isAbstract(type.getModifiers()) ? "abstract class " : "class ";
        String typeParams = type.getTypeParameters().length == 0 ? ""
                : Arrays.stream(type.getTypeParameters()).map(TypeVariable::getName)
                        .collect(Collectors.joining(", ", "<", ">"));
        return kind + type.getName() + typeParams;
    }

    private static List<String> members(Class<?> type) {
        List<String> lines = new ArrayList<>();

        Arrays.stream(type.getDeclaredFields())
                .filter(f -> Modifier.isPublic(f.getModifiers()) && !f.isSynthetic())
                .sorted(Comparator.comparing(Field::getName))
                .forEach(f -> lines.add(staticPrefix(f.getModifiers())
                        + render(f.getGenericType(), type) + " " + f.getName()));

        Arrays.stream(type.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .map(m -> signature(m, type))
                .sorted()
                .forEach(lines::add);

        return lines;
    }

    private static String signature(Method m, Class<?> owner) {
        Type[] params = m.getGenericParameterTypes();
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < params.length; i++) {
            String p = render(params[i], owner);
            if (m.isVarArgs() && i == params.length - 1 && p.endsWith("[]")) {
                p = p.substring(0, p.length() - 2) + "...";
            }
            rendered.add(p);
        }
        String typeParams = m.getTypeParameters().length == 0 ? ""
                : Arrays.stream(m.getTypeParameters()).map(TypeVariable::getName)
                        .collect(Collectors.joining(", ", "<", "> "));
        return m.getName() + "(" + String.join(", ", rendered) + ") -> "
                + staticPrefix(m.getModifiers()) + typeParams + render(m.getGenericReturnType(), owner);
    }

    private static String staticPrefix(int modifiers) {
        return Modifier.isStatic(modifiers) ? "static " : "";
    }

    /** Simple type names; the owner's own type variable (CRTP self type) becomes the owner's name. */
    private static String render(Type t, Class<?> owner) {
        if (t instanceof Class<?> c) {
            return c.isArray() ? render(c.getComponentType(), owner) + "[]" : c.getSimpleName();
        }
        if (t instanceof ParameterizedType p) {
            String args = Arrays.stream(p.getActualTypeArguments())
                    .map(a -> render(a, owner)).collect(Collectors.joining(", "));
            return render(p.getRawType(), owner) + "<" + args + ">";
        }
        if (t instanceof TypeVariable<?> v) {
            return v.getGenericDeclaration() == owner ? owner.getSimpleName() : v.getName();
        }
        if (t instanceof WildcardType w) {
            if (w.getLowerBounds().length > 0) return "? super " + render(w.getLowerBounds()[0], owner);
            Type upper = w.getUpperBounds()[0];
            return upper == Object.class ? "?" : "? extends " + render(upper, owner);
        }
        if (t instanceof GenericArrayType g) {
            return render(g.getGenericComponentType(), owner) + "[]";
        }
        return t.getTypeName();
    }
}
