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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.higherkindedj.optics.processing.util.Diagnostics;

/**
 * Discovers the JavaBeans property model of a bean-shaped wire type for {@code @GenerateMapping}
 * (issue #628). A slice-B bean is a mutable JavaBean: a public no-args constructor plus canonical
 * {@code getX}/{@code isX} getters paired with {@code setX} setters. The property set is the
 * intersection of readable and writable names — the canonical mutable property — so a computed
 * getter with no setter, or a setter with no getter, is not treated as a mappable component.
 *
 * <p>Getters and setters are gathered from {@link javax.lang.model.util.Elements#getAllMembers}, so
 * a bean inherits properties from its superclasses (as JAXB-generated beans do); methods declared
 * on {@link Object} (notably {@code getClass}) and non-public or static methods are excluded.
 */
final class BeanPropertyAnalyser {

  private final ProcessingEnvironment env;

  BeanPropertyAnalyser(ProcessingEnvironment env) {
    this.env = env;
  }

  /**
   * Analyses {@code bean} into a {@link WireShape.BeanShape}, or reports a what/why/fix diagnostic
   * and returns null. Reports when a getter and setter disagree on type, or when the bean is
   * neither constructible (no no-args constructor) nor has any mappable property.
   */
  WireShape.BeanShape analyse(TypeElement spec, TypeElement bean, String tag) {
    Map<String, ExecutableElement> getters = new LinkedHashMap<>();
    Map<String, ExecutableElement> setters = new LinkedHashMap<>();
    collectAccessors(bean, getters, setters);

    List<WireShape.BeanProperty> properties = new ArrayList<>();
    for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
      String name = entry.getKey();
      ExecutableElement setter = setters.get(name);
      if (setter == null) {
        continue; // getter-only: a computed/read-only property (degraded tiers arrive in slice D).
      }
      TypeMirror getterType = entry.getValue().getReturnType();
      TypeMirror setterType = setter.getParameters().getFirst().asType();
      if (!env.getTypeUtils().isSameType(getterType, setterType)) {
        Diagnostics.error(
            env.getMessager(),
            spec,
            tag,
            "bean property '"
                + name
                + "' on '"
                + bean.getSimpleName()
                + "' has a getter and setter of different types ("
                + getterType
                + " vs "
                + setterType
                + ").",
            "A mappable property has one type; the mapper cannot guess which of the two the"
                + " component should carry.",
            "Align the getter and setter types on the bean, or drop one of them.");
        return null;
      }
      properties.add(
          new WireShape.BeanProperty(
              name,
              getterType,
              entry.getValue().getSimpleName().toString(),
              setter.getSimpleName().toString()));
    }

    if (!hasPublicNoArgsConstructor(bean) || properties.isEmpty()) {
      Diagnostics.error(
          env.getMessager(),
          spec,
          tag,
          "'"
              + bean.getSimpleName()
              + "' is not a usable bean-shaped wire: it needs a public no-args constructor and at"
              + " least one getter/setter property pair.",
          "The mapper builds the wire with 'new "
              + bean.getSimpleName()
              + "()' and fills it through setters, reading it back through getters; "
              + bean.getSimpleName()
              + (hasPublicNoArgsConstructor(bean)
                  ? " exposes no getter/setter property pair."
                  : " has no public no-args constructor."),
          "Give it a public no-args constructor and matching getX/setX pairs, use a record, or a"
              + " builder-based bean (support arrives with the builder strategy).");
      return null;
    }
    return new WireShape.BeanShape(
        bean, properties, new WireShape.ConstructionStrategy.NoArgsSetters());
  }

  /** The number of canonical getter/setter property pairs, for the registry's parse arithmetic. */
  int propertyCount(TypeElement bean) {
    Map<String, ExecutableElement> getters = new LinkedHashMap<>();
    Map<String, ExecutableElement> setters = new LinkedHashMap<>();
    collectAccessors(bean, getters, setters);
    return (int) getters.keySet().stream().filter(setters::containsKey).count();
  }

  private void collectAccessors(
      TypeElement bean,
      Map<String, ExecutableElement> getters,
      Map<String, ExecutableElement> setters) {
    for (Element member : env.getElementUtils().getAllMembers(bean)) {
      if (member.getKind() != ElementKind.METHOD) {
        continue;
      }
      ExecutableElement method = (ExecutableElement) member;
      Set<Modifier> modifiers = method.getModifiers();
      if (!modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.STATIC)) {
        continue;
      }
      if (declaredOnObject(method)) {
        continue;
      }
      String methodName = method.getSimpleName().toString();
      int params = method.getParameters().size();
      if (params == 0 && method.getReturnType().getKind() != TypeKind.VOID) {
        if (methodName.length() > 3 && methodName.startsWith("get")) {
          getters.putIfAbsent(decapitalise(methodName.substring(3)), method);
        } else if (methodName.length() > 2
            && methodName.startsWith("is")
            && method.getReturnType().getKind() == TypeKind.BOOLEAN) {
          // The JavaBeans 'is' getter is for primitive boolean only.
          getters.putIfAbsent(decapitalise(methodName.substring(2)), method);
        }
      } else if (params == 1 && methodName.length() > 3 && methodName.startsWith("set")) {
        // Void or fluent (returns the bean) setters both work: build calls the setter as a
        // statement, discarding any fluent return.
        setters.putIfAbsent(decapitalise(methodName.substring(3)), method);
      }
    }
  }

  private boolean declaredOnObject(ExecutableElement method) {
    Element enclosing = method.getEnclosingElement();
    return enclosing instanceof TypeElement type
        && type.getQualifiedName().contentEquals("java.lang.Object");
  }

  private boolean hasPublicNoArgsConstructor(TypeElement bean) {
    return ElementFilter.constructorsIn(bean.getEnclosedElements()).stream()
        .anyMatch(c -> c.getParameters().isEmpty() && c.getModifiers().contains(Modifier.PUBLIC));
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
