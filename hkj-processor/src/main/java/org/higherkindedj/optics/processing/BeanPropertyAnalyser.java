// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.processing.util.Diagnostics;

/**
 * Discovers the JavaBeans property model of a bean-shaped wire type for {@code @GenerateMapping}
 * (issue #628). A bean is read through {@code getX}/{@code isX} getters and constructed through one
 * of a fixed ladder of strategies, tried in order:
 *
 * <ol>
 *   <li>a no-args constructor with {@code setX} setters (and, for a getter-only {@code List}, the
 *       JAXB collection convention {@code getX().addAll(...)});
 *   <li>a builder: a static {@code builder()} or {@code newBuilder()} returning a builder whose
 *       property-named or {@code setX} setters fill it and whose {@code build()} yields the wire.
 * </ol>
 *
 * <p>The mapped property set is the intersection of readable and writable names, so a computed
 * getter with no writer is not treated as a mappable component. Getters and setters are gathered
 * from {@link javax.lang.model.util.Elements#getAllMembers}, so a bean inherits properties from its
 * superclasses (as JAXB-generated beans do); {@link Object} methods and non-public or static
 * accessors are excluded.
 */
final class BeanPropertyAnalyser {

  private static final String LIST = "java.util.List";

  private final ProcessingEnvironment env;

  BeanPropertyAnalyser(ProcessingEnvironment env) {
    this.env = env;
  }

  /**
   * Analyses {@code bean} into a {@link WireShape.BeanShape}, or reports a what/why/fix diagnostic
   * and returns null. Reports when a getter and its writer disagree on type, or when the bean is
   * neither a mutable JavaBean nor a builder-based bean with a readable property.
   */
  WireShape.BeanShape analyse(TypeElement spec, TypeElement bean, String tag) {
    Map<String, ExecutableElement> getters = collectGetters(bean);

    if (hasUsableNoArgsConstructor(bean)) {
      Map<String, ExecutableElement> setters = collectSetters(bean);
      List<WireShape.BeanProperty> properties = new ArrayList<>();
      for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
        String name = entry.getKey();
        TypeMirror getterType = entry.getValue().getReturnType();
        String getter = entry.getValue().getSimpleName().toString();
        ExecutableElement setter = setters.get(name);
        if (setter != null) {
          if (typesDiffer(spec, bean, tag, name, getterType, paramType(setter))) {
            return null;
          }
          properties.add(
              new WireShape.BeanProperty(
                  name,
                  getterType,
                  getter,
                  new WireShape.WriteSite.Setter(setter.getSimpleName().toString())));
        } else if (isList(getterType)) {
          properties.add(
              new WireShape.BeanProperty(
                  name, getterType, getter, new WireShape.WriteSite.CollectionAdd(getter)));
        }
      }
      if (!properties.isEmpty()) {
        return new WireShape.BeanShape(
            bean, properties, new WireShape.ConstructionStrategy.NoArgsSetters());
      }
    }

    BuilderModel builder = findBuilderModel(bean);
    if (builder != null) {
      Map<String, ExecutableElement> builderSetters = collectBuilderSetters(builder.builderType());
      List<WireShape.BeanProperty> properties = new ArrayList<>();
      for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
        String name = entry.getKey();
        ExecutableElement builderSetter = builderSetters.get(name);
        if (builderSetter == null) {
          continue;
        }
        TypeMirror getterType = entry.getValue().getReturnType();
        if (typesDiffer(spec, bean, tag, name, getterType, paramType(builderSetter))) {
          return null;
        }
        properties.add(
            new WireShape.BeanProperty(
                name,
                getterType,
                entry.getValue().getSimpleName().toString(),
                new WireShape.WriteSite.Setter(builderSetter.getSimpleName().toString())));
      }
      if (!properties.isEmpty()) {
        return new WireShape.BeanShape(
            bean,
            properties,
            new WireShape.ConstructionStrategy.Builder(builder.factory(), builder.buildMethod()));
      }
    }

    reportUnusable(spec, bean, tag);
    return null;
  }

  /** The number of mappable properties under the selected strategy, for the parse arithmetic. */
  int propertyCount(TypeElement bean) {
    Map<String, ExecutableElement> getters = collectGetters(bean);
    if (hasUsableNoArgsConstructor(bean)) {
      Map<String, ExecutableElement> setters = collectSetters(bean);
      long count =
          getters.entrySet().stream()
              .filter(e -> setters.containsKey(e.getKey()) || isList(e.getValue().getReturnType()))
              .count();
      if (count > 0) {
        return (int) count;
      }
    }
    BuilderModel builder = findBuilderModel(bean);
    if (builder != null) {
      Map<String, ExecutableElement> builderSetters = collectBuilderSetters(builder.builderType());
      long count = getters.keySet().stream().filter(builderSetters::containsKey).count();
      if (count > 0) {
        return (int) count;
      }
    }
    return 0;
  }

  private boolean typesDiffer(
      TypeElement spec,
      TypeElement bean,
      String tag,
      String name,
      TypeMirror getterType,
      TypeMirror writerType) {
    if (env.getTypeUtils().isSameType(getterType, writerType)) {
      return false;
    }
    Diagnostics.error(
        env.getMessager(),
        spec,
        tag,
        "bean property '"
            + name
            + "' on '"
            + bean.getSimpleName()
            + "' is read and written at different types ("
            + getterType
            + " vs "
            + writerType
            + ").",
        "A mappable property has one type; the mapper cannot guess which of the two the component"
            + " should carry.",
        "Align the getter and its setter (or builder setter) on the bean, or drop one of them.");
    return true;
  }

  private Map<String, ExecutableElement> collectGetters(TypeElement bean) {
    Map<String, ExecutableElement> getters = new LinkedHashMap<>();
    for (ExecutableElement method : publicInstanceMethods(bean)) {
      String methodName = method.getSimpleName().toString();
      if (!method.getParameters().isEmpty() || method.getReturnType().getKind() == TypeKind.VOID) {
        continue;
      }
      if (methodName.length() > 3 && methodName.startsWith("get")) {
        getters.putIfAbsent(decapitalise(methodName.substring(3)), method);
      } else if (methodName.length() > 2
          && methodName.startsWith("is")
          && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
        // The JavaBeans 'is' getter is for primitive boolean only.
        getters.putIfAbsent(decapitalise(methodName.substring(2)), method);
      }
    }
    return getters;
  }

  private Map<String, ExecutableElement> collectSetters(TypeElement bean) {
    Map<String, ExecutableElement> setters = new LinkedHashMap<>();
    for (ExecutableElement method : publicInstanceMethods(bean)) {
      String methodName = method.getSimpleName().toString();
      if (method.getParameters().size() == 1
          && methodName.length() > 3
          && methodName.startsWith("set")) {
        // Void or fluent (returns the bean) setters both work: build calls the setter as a
        // statement, discarding any fluent return.
        setters.putIfAbsent(decapitalise(methodName.substring(3)), method);
      }
    }
    return setters;
  }

  /**
   * The builder's setters, keyed by property. Both the property-named convention ({@code name(T)},
   * as Lombok/Immutables/AutoValue emit) and the {@code setX} convention (as protobuf emits) are
   * accepted; when a property has both, the property-named one wins.
   */
  private Map<String, ExecutableElement> collectBuilderSetters(TypeElement builderType) {
    Map<String, ExecutableElement> setX = new LinkedHashMap<>();
    Map<String, ExecutableElement> propertyNamed = new LinkedHashMap<>();
    for (ExecutableElement method : publicInstanceMethods(builderType)) {
      if (method.getParameters().size() != 1) {
        continue;
      }
      String methodName = method.getSimpleName().toString();
      if (methodName.length() > 3 && methodName.startsWith("set")) {
        setX.putIfAbsent(decapitalise(methodName.substring(3)), method);
      } else {
        propertyNamed.putIfAbsent(decapitalise(methodName), method);
      }
    }
    Map<String, ExecutableElement> merged = new LinkedHashMap<>(setX);
    merged.putAll(propertyNamed);
    return merged;
  }

  /** A builder factory + terminal build method discovered on a bean. */
  private record BuilderModel(String factory, String buildMethod, TypeElement builderType) {}

  private BuilderModel findBuilderModel(TypeElement bean) {
    ExecutableElement factory = builderFactory(bean, "builder");
    if (factory == null) {
      factory = builderFactory(bean, "newBuilder");
    }
    if (factory == null || !(factory.getReturnType() instanceof DeclaredType builderMirror)) {
      return null;
    }
    TypeElement builderType = (TypeElement) builderMirror.asElement();
    boolean buildsWire =
        publicInstanceMethods(builderType).stream()
            .anyMatch(
                m ->
                    m.getSimpleName().contentEquals("build")
                        && m.getParameters().isEmpty()
                        && env.getTypeUtils().isSameType(m.getReturnType(), bean.asType()));
    return buildsWire
        ? new BuilderModel(factory.getSimpleName().toString(), "build", builderType)
        : null;
  }

  private ExecutableElement builderFactory(TypeElement bean, String name) {
    return ElementFilter.methodsIn(bean.getEnclosedElements()).stream()
        .filter(
            m ->
                m.getSimpleName().contentEquals(name)
                    && m.getModifiers().contains(Modifier.PUBLIC)
                    && m.getModifiers().contains(Modifier.STATIC)
                    && m.getParameters().isEmpty()
                    && m.getReturnType().getKind() == TypeKind.DECLARED)
        .findFirst()
        .orElse(null);
  }

  private void reportUnusable(TypeElement spec, TypeElement bean, String tag) {
    Diagnostics.error(
        env.getMessager(),
        spec,
        tag,
        "'"
            + bean.getSimpleName()
            + "' is not a usable bean-shaped wire: no construction strategy fits it.",
        "The mapper fills a bean through a no-args constructor with getX/setX pairs (or a"
            + " getter-only List, filled with getX().addAll(...)), or through a static"
            + " builder()/newBuilder() whose setters fill it and whose build() yields the wire; '"
            + bean.getSimpleName()
            + "' matches neither with a readable property.",
        "Give it a no-args constructor with matching getX/setX pairs, a builder, or use a record.");
  }

  private List<ExecutableElement> publicInstanceMethods(TypeElement type) {
    List<ExecutableElement> methods = new ArrayList<>();
    for (Element member : env.getElementUtils().getAllMembers(type)) {
      if (member.getKind() != ElementKind.METHOD) {
        continue;
      }
      ExecutableElement method = (ExecutableElement) member;
      Set<Modifier> modifiers = method.getModifiers();
      if (modifiers.contains(Modifier.PUBLIC)
          && !modifiers.contains(Modifier.STATIC)
          && !declaredOnObject(method)) {
        methods.add(method);
      }
    }
    return methods;
  }

  private boolean declaredOnObject(ExecutableElement method) {
    Element enclosing = method.getEnclosingElement();
    return enclosing instanceof TypeElement type
        && type.getQualifiedName().contentEquals("java.lang.Object");
  }

  private boolean isList(TypeMirror type) {
    return type instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(LIST);
  }

  private TypeMirror paramType(ExecutableElement setter) {
    return setter.getParameters().getFirst().asType();
  }

  /**
   * A no-args constructor the generated impl can call. The impl is emitted in the spec's package,
   * so any non-private constructor of a co-located bean is reachable; a public one is reachable
   * from any package (as a compiled third-party bean's would be).
   */
  private boolean hasUsableNoArgsConstructor(TypeElement bean) {
    return ElementFilter.constructorsIn(bean.getEnclosedElements()).stream()
        .anyMatch(c -> c.getParameters().isEmpty() && !c.getModifiers().contains(Modifier.PRIVATE));
  }

  /** The JavaBeans {@code Introspector.decapitalize} rule: {@code getURL} -> {@code URL}. */
  static String decapitalise(String name) {
    if (name.isEmpty()) {
      return name;
    }
    if (name.length() > 1
        && Character.isUpperCase(name.charAt(0))
        && Character.isUpperCase(name.charAt(1))) {
      return name;
    }
    char[] chars = name.toCharArray();
    chars[0] = Character.toLowerCase(chars[0]);
    return new String(chars);
  }
}
