// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.higherkindedj.optics.processing.util.ProcessorUtils;

/**
 * Discovers the JavaBeans property model of a bean-shaped wire type for {@code @GenerateMapping}. A
 * bean is read through {@code getX} getters, and {@code isX} getters of {@code boolean} or {@code
 * Boolean}, and constructed through one of a fixed ladder of strategies, tried in order:
 *
 * <ol>
 *   <li>a no-args constructor with {@code setX} setters (and, for a getter-only {@code List}, the
 *       JAXB collection convention {@code getX().addAll(...)});
 *   <li>a builder: a static {@code builder()} or {@code newBuilder()} returning a builder whose
 *       property-named or {@code setX} setters fill it and whose {@code build()} yields the wire.
 * </ol>
 *
 * <p>The mapped property set is the intersection of readable and writable names, so a computed
 * getter with no writer is not treated as a mappable component. Where that intersection is empty
 * under every strategy, the bean maps one way if it offers nothing at all in the other: a bean with
 * getters and no way to be written maps parse-only over all of its getters, and one that can be
 * written but declares no getter maps build-only over all of its writers. A bean that reads some
 * names and writes others is refused, since a misspelt accessor is a likelier story than a wire
 * meant to be crossed one way. A getter-only {@code List} counts as written only on a bean that
 * also has a setter, or whose every getter is such a list: a {@code List} getter among read-only
 * getters belongs to a read model, which maps parse-only.
 *
 * <p>A two-way bean records the accessors it leaves out, each getter nothing writes and each writer
 * nothing reads, so that the processor can refuse one whose omission would lose a value: this
 * analysis knows the bean, and only the spec knows which names its mapping needs.
 *
 * <p>Getters and setters are gathered from {@link javax.lang.model.util.Elements#getAllMembers}, so
 * a bean inherits properties from its superclasses (as JAXB-generated beans do); {@link Object}
 * methods and non-public or static accessors are excluded.
 *
 * <p>Whatever this analysis reads from a bean declared in source must stay reachable from {@link
 * WaitingSpecs}, which decides whether a spec can be classified yet: a read it cannot see is a type
 * a spec may meet before another processor has written it.
 */
final class BeanPropertyAnalyser {

  private static final String LIST = "java.util.List";

  private static final String BOOLEAN = "java.lang.Boolean";

  private final ProcessingEnvironment env;

  BeanPropertyAnalyser(ProcessingEnvironment env) {
    this.env = env;
  }

  /**
   * Analyses {@code bean} into a {@link WireShape.BeanShape}, or reports a what/why/fix diagnostic
   * and returns null. Reports when a getter and its writer disagree on type, or when the bean fits
   * no reading at all: nothing to read or write, or readable and writable properties that never
   * share a name.
   */
  WireShape.BeanShape analyse(TypeElement spec, TypeElement bean, String tag) {
    return analyse(spec, bean, tag, true, false);
  }

  /**
   * The same analysis for a sparse update's PATCH bean, which the generated Impl only reads and
   * never constructs. A setter shows that something writes the property, such as a deserialiser
   * that can call a private constructor, and so can leave it unset, which is all the sparse tier
   * asks; so the setters count whatever constructor the bean declares. A bean with a builder keeps
   * the builder as its writer, as it does wherever its constructor is out of reach. The shape's
   * strategy then names the kind of writer a fix line speaks of, not a way to construct the bean.
   */
  WireShape.BeanShape analysePatch(TypeElement spec, TypeElement bean, String tag) {
    return analyse(spec, bean, tag, true, true);
  }

  /**
   * The same analysis, reporting nothing. The registry reads every spec's wire before any spec is
   * validated, so a bean this refuses registers as nothing to nest, and its own spec says why. One
   * analysis serves both, so a spec is never registered as one shape and generated as another.
   */
  Optional<WireShape.BeanShape> surface(TypeElement spec, TypeElement bean) {
    return Optional.ofNullable(analyse(spec, bean, "", false, false));
  }

  /**
   * Whether a bean declares setters at all, for the note naming its parse-only tier. A parse-only
   * bean that has setters has them out of reach, its no-args constructor being one the generated
   * Impl cannot call, so the constructor is what the note tells the author to change.
   */
  boolean declaresSetters(TypeElement bean) {
    return !collectSetters(bean).isEmpty();
  }

  private WireShape.BeanShape analyse(
      TypeElement spec, TypeElement bean, String tag, boolean report, boolean neverBuilt) {
    // A bean inherits properties from its superclasses, so a member read off its declaring element
    // speaks that element's variables: 'T getId()' on BaseDto<T> is String on UserDto.
    DeclaredType beanType = (DeclaredType) bean.asType();
    Map<String, ExecutableElement> getters = collectGetters(bean);
    // Setters write a bean the Impl can construct, and a PATCH bean without a builder, which only
    // something else constructs.
    boolean setterWritten =
        hasUsableNoArgsConstructor(spec, bean) || (neverBuilt && findBuilderModel(bean) == null);
    Map<String, ExecutableElement> setters = setterWritten ? collectSetters(bean) : Map.of();

    if (setterWritten) {
      // A getter-only List is written through its own getter, the JAXB convention, but only on a
      // bean that writes something else as well, or nothing but such lists: a List getter among
      // read-only getters belongs to a read model, which is never filled, and reads parse-only.
      boolean collectionsWrite =
          !setters.isEmpty()
              || getters.values().stream().allMatch(getter -> isList(getterType(beanType, getter)));
      List<WireShape.BeanProperty> properties = new ArrayList<>();
      List<WireShape.UnpairedAccessor> unpaired = new ArrayList<>();
      for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
        String name = entry.getKey();
        TypeMirror getterType = getterType(beanType, entry.getValue());
        String getter = entry.getValue().getSimpleName().toString();
        ExecutableElement setter = setters.get(name);
        if (setter != null) {
          if (typesDiffer(
              spec,
              bean,
              entry.getValue(),
              tag,
              name,
              getterType,
              paramType(beanType, setter),
              report)) {
            return null;
          }
          properties.add(
              readWrite(
                  name,
                  getterType,
                  getter,
                  new WireShape.WriteSite.Setter(setter.getSimpleName().toString())));
        } else if (collectionsWrite && isList(getterType)) {
          properties.add(
              readWrite(name, getterType, getter, new WireShape.WriteSite.CollectionAdd(getter)));
        } else {
          unpaired.add(unpairedGetter(bean, name, entry.getValue(), getterType));
        }
      }
      if (!properties.isEmpty()) {
        unpaired.addAll(
            unpairedWriters(getters, setters, beanType, WireShape.UnpairedAccessor.Role.SETTER));
        return new WireShape.BeanShape(
            bean,
            properties,
            Optional.of(new WireShape.ConstructionStrategy.NoArgsSetters()),
            WireShape.Direction.BIDIRECTIONAL,
            unpaired);
      }
    }

    BuilderModel builder = findBuilderModel(bean);
    Map<String, ExecutableElement> builderSetters = Map.of();
    if (builder != null) {
      builderSetters = collectBuilderSetters(bean, builder);
      DeclaredType builderType = builder.builderType();
      List<WireShape.BeanProperty> properties = new ArrayList<>();
      List<WireShape.UnpairedAccessor> unpaired = new ArrayList<>();
      for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
        String name = entry.getKey();
        ExecutableElement builderSetter = builderSetters.get(name);
        TypeMirror getterType = getterType(beanType, entry.getValue());
        if (builderSetter == null) {
          unpaired.add(unpairedGetter(bean, name, entry.getValue(), getterType));
          continue;
        }
        if (typesDiffer(
            spec,
            bean,
            entry.getValue(),
            tag,
            name,
            getterType,
            paramType(builderType, builderSetter),
            report)) {
          return null;
        }
        properties.add(
            readWrite(
                name,
                getterType,
                entry.getValue().getSimpleName().toString(),
                new WireShape.WriteSite.Setter(builderSetter.getSimpleName().toString())));
      }
      if (!properties.isEmpty()) {
        unpaired.addAll(
            unpairedWriters(
                getters,
                builderSetters,
                builderType,
                WireShape.UnpairedAccessor.Role.BUILDER_SETTER));
        return new WireShape.BeanShape(
            bean,
            properties,
            Optional.of(builder.strategy()),
            WireShape.Direction.BIDIRECTIONAL,
            unpaired);
      }
    }

    // No property is both read and written, so the bean maps one way or not at all: one way only
    // when nothing whatever crosses in the other.
    if (!getters.isEmpty() && setters.isEmpty() && builderSetters.isEmpty()) {
      return readOnly(bean, beanType, getters);
    }
    if (getters.isEmpty() && !setters.isEmpty()) {
      return writeOnly(bean, beanType, setters, new WireShape.ConstructionStrategy.NoArgsSetters());
    }
    if (getters.isEmpty() && !builderSetters.isEmpty()) {
      return writeOnly(bean, builder.builderType(), builderSetters, builder.strategy());
    }
    if (report) {
      reportUnusable(
          spec,
          bean,
          tag,
          getters.keySet(),
          setters.isEmpty() ? builderSetters.keySet() : setters.keySet());
    }
    return null;
  }

  private static WireShape.BeanProperty readWrite(
      String name, TypeMirror type, String getter, WireShape.WriteSite write) {
    return new WireShape.BeanProperty(name, type, Optional.of(getter), Optional.of(write));
  }

  private static WireShape.UnpairedAccessor unpairedGetter(
      TypeElement bean, String name, ExecutableElement getter, TypeMirror type) {
    return new WireShape.UnpairedAccessor(
        name,
        getter.getSimpleName().toString(),
        type,
        WireShape.UnpairedAccessor.Role.GETTER,
        inheritedFrom(bean, getter));
  }

  /**
   * The writers no getter shares a name with, each at its type as {@code owner} (the bean, or the
   * builder as its factory instantiates it) declares it.
   */
  private List<WireShape.UnpairedAccessor> unpairedWriters(
      Map<String, ExecutableElement> getters,
      Map<String, ExecutableElement> writers,
      DeclaredType owner,
      WireShape.UnpairedAccessor.Role role) {
    return writers.entrySet().stream()
        .filter(entry -> !getters.containsKey(entry.getKey()))
        .map(
            entry ->
                new WireShape.UnpairedAccessor(
                    entry.getKey(),
                    entry.getValue().getSimpleName().toString(),
                    paramType(owner, entry.getValue()),
                    role,
                    inheritedFrom((TypeElement) owner.asElement(), entry.getValue())))
        .toList();
  }

  /** The supertype {@code owner} inherits {@code method} from, when it does not declare it. */
  private static Optional<TypeElement> inheritedFrom(TypeElement owner, ExecutableElement method) {
    // A method's enclosing element is always the declaring type.
    TypeElement declaring = (TypeElement) method.getEnclosingElement();
    return declaring.equals(owner) ? Optional.empty() : Optional.of(declaring);
  }

  /** Every getter, read and never written: a bean with no way to be written maps parse-only. */
  private WireShape.BeanShape readOnly(
      TypeElement bean, DeclaredType beanType, Map<String, ExecutableElement> getters) {
    List<WireShape.BeanProperty> properties =
        getters.entrySet().stream()
            .map(
                entry ->
                    new WireShape.BeanProperty(
                        entry.getKey(),
                        getterType(beanType, entry.getValue()),
                        Optional.of(entry.getValue().getSimpleName().toString()),
                        Optional.<WireShape.WriteSite>empty()))
            .toList();
    return new WireShape.BeanShape(
        bean, properties, Optional.empty(), WireShape.Direction.PARSE_ONLY, List.of());
  }

  /**
   * Every writer, written and never read: a bean that declares no getter maps build-only. A writer
   * speaks its owner's variables, as a getter does, so {@code owner} is the bean for a setter and
   * the builder as the factory instantiates it for a builder setter.
   */
  private WireShape.BeanShape writeOnly(
      TypeElement bean,
      DeclaredType owner,
      Map<String, ExecutableElement> writers,
      WireShape.ConstructionStrategy strategy) {
    List<WireShape.BeanProperty> properties =
        writers.entrySet().stream()
            .map(
                entry ->
                    new WireShape.BeanProperty(
                        entry.getKey(),
                        paramType(owner, entry.getValue()),
                        Optional.<String>empty(),
                        Optional.<WireShape.WriteSite>of(
                            new WireShape.WriteSite.Setter(
                                entry.getValue().getSimpleName().toString()))))
            .toList();
    return new WireShape.BeanShape(
        bean, properties, Optional.of(strategy), WireShape.Direction.BUILD_ONLY, List.of());
  }

  private boolean typesDiffer(
      TypeElement spec,
      TypeElement bean,
      ExecutableElement getter,
      String tag,
      String name,
      TypeMirror getterType,
      TypeMirror writerType,
      boolean report) {
    if (env.getTypeUtils().isSameType(getterType, writerType)) {
      return false;
    }
    if (report) {
      Diagnostics.error(
          env.getMessager(),
          spec,
          tag,
          "bean property '"
              + name
              + "' on '"
              + bean.getSimpleName()
              + "'"
              + declaredOn(bean, getter)
              + " is read and written at different types ("
              + ProcessorUtils.simpleTypeName(getterType)
              + " vs "
              + ProcessorUtils.simpleTypeName(writerType)
              + ").",
          "A mappable property has one type; the mapper cannot guess which of the two the"
              + " component should carry.",
          "Align the getter and its setter (or builder setter) where they are declared, or drop"
              + " one of them.");
    }
    return true;
  }

  private Map<String, ExecutableElement> collectGetters(TypeElement bean) {
    List<ExecutableElement> readers =
        publicInstanceMethods(bean).stream()
            .filter(
                method ->
                    method.getParameters().isEmpty()
                        && method.getReturnType().getKind() != TypeKind.VOID)
            .toList();
    // A getX getter wins over an isX one of the same name, so which of the two reads the property
    // never turns on the order they are declared in.
    Set<String> gotten =
        readers.stream()
            .map(method -> method.getSimpleName().toString())
            .filter(BeanPropertyAnalyser::isGetName)
            .map(methodName -> decapitalise(methodName.substring(3)))
            .collect(Collectors.toSet());
    Map<String, ExecutableElement> getters = new LinkedHashMap<>();
    for (ExecutableElement method : readers) {
      String methodName = method.getSimpleName().toString();
      if (isGetName(methodName)) {
        getters.putIfAbsent(decapitalise(methodName.substring(3)), method);
      } else if (methodName.length() > 2
          && methodName.startsWith("is")
          && !gotten.contains(decapitalise(methodName.substring(2)))
          && isBoolean(getterType((DeclaredType) bean.asType(), method))) {
        // JavaBeans keeps the 'is' getter for primitive boolean, but JAXB declares an optional
        // boolean as 'Boolean isX()' beside 'setX(Boolean)', and Jackson reads either as a
        // property; left out, the setter would be a writer with no getter.
        getters.putIfAbsent(decapitalise(methodName.substring(2)), method);
      }
    }
    return getters;
  }

  private static boolean isGetName(String methodName) {
    return methodName.length() > 3 && methodName.startsWith("get");
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
   * accepted; when a property has both, the property-named one wins. A method taking the bean or
   * the builder itself copies a whole value in ({@code from(Bean)}, {@code mergeFrom(Builder)}), so
   * it is no property's setter.
   */
  private Map<String, ExecutableElement> collectBuilderSetters(
      TypeElement bean, BuilderModel builder) {
    Map<String, ExecutableElement> setX = new LinkedHashMap<>();
    Map<String, ExecutableElement> propertyNamed = new LinkedHashMap<>();
    for (ExecutableElement method : publicInstanceMethods(builder.builderElement())) {
      if (method.getParameters().size() != 1 || copiesWhole(method, bean, builder)) {
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

  /** Whether a one-argument builder method takes the bean or the builder itself. */
  private boolean copiesWhole(ExecutableElement method, TypeElement bean, BuilderModel builder) {
    TypeMirror parameter = env.getTypeUtils().erasure(method.getParameters().getFirst().asType());
    return env.getTypeUtils().isSameType(parameter, env.getTypeUtils().erasure(bean.asType()))
        || env.getTypeUtils()
            .isSameType(parameter, env.getTypeUtils().erasure(builder.builderType()));
  }

  /**
   * A builder factory + terminal build method discovered on a bean.
   *
   * <p>{@code builderType} is the factory's return type as written - {@code Builder<String>}, not
   * {@code Builder<T>}. Re-deriving it from the element would put the builder's own variables back
   * where the factory named actual arguments, and every setter would then be read at a variable the
   * bean's author never wrote.
   *
   * @param factory the static factory method's name
   * @param buildMethod the terminal build method's name
   * @param builderType the builder as the factory instantiates it
   */
  private record BuilderModel(String factory, String buildMethod, DeclaredType builderType) {

    /** The builder's element, for the members that are read off the declaration itself. */
    TypeElement builderElement() {
      return (TypeElement) builderType.asElement();
    }

    /** The construction strategy that fills the bean through this builder. */
    WireShape.ConstructionStrategy strategy() {
      return new WireShape.ConstructionStrategy.Builder(factory, buildMethod);
    }
  }

  private BuilderModel findBuilderModel(TypeElement bean) {
    ExecutableElement factory = builderFactory(bean, "builder");
    if (factory == null) {
      factory = builderFactory(bean, "newBuilder");
    }
    if (factory == null) {
      return null;
    }
    // builderFactory only returns a factory whose return kind is DECLARED, so the cast is total.
    DeclaredType builderType = (DeclaredType) factory.getReturnType();
    boolean buildsWire =
        publicInstanceMethods((TypeElement) builderType.asElement()).stream()
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

  /**
   * Refuses a bean no reading fits. Asked only once the one-way readings are ruled out, so the two
   * name sets are either both empty, a bean with nothing to read or write, or both non-empty, a
   * bean whose getters and writers never share a name.
   */
  private void reportUnusable(
      TypeElement spec, TypeElement bean, String tag, Set<String> reads, Set<String> writes) {
    if (reads.isEmpty() && declaresSetters(bean)) {
      Diagnostics.error(
          env.getMessager(),
          spec,
          tag,
          "'"
              + bean.getSimpleName()
              + "' is not a usable bean-shaped wire: it has setters but no getters, and no no-args"
              + " constructor the generated Impl can call from package '"
              + env.getElementUtils().getPackageOf(spec).getQualifiedName()
              + "'.",
          "A bean with no getters maps build-only, and a build needs a way to create the bean: a"
              + " no-args constructor for its setters, or a builder.",
          "Give '"
              + bean.getSimpleName()
              + "' a no-args constructor the generated Impl can call (public, or package-private"
              + " beside the spec), or a builder.");
      return;
    }
    if (reads.isEmpty()) {
      Diagnostics.error(
          env.getMessager(),
          spec,
          tag,
          "'"
              + bean.getSimpleName()
              + "' is not a usable bean-shaped wire: it has no property to read or write.",
          "The mapper reads a bean through getX/isX getters and writes it through a no-args"
              + " constructor with setX setters (or a getter-only List, filled with"
              + " getX().addAll(...)), or through a static builder()/newBuilder() whose setters fill"
              + " it and whose build() yields the wire; '"
              + bean.getSimpleName()
              + "' offers none of them.",
          "Give it getters, setters or a builder for the properties it carries, or use a record.");
      return;
    }
    Diagnostics.error(
        env.getMessager(),
        spec,
        tag,
        "'"
            + bean.getSimpleName()
            + "' is not a usable bean-shaped wire: no property it reads is one it can write.",
        "It reads "
            + reads
            + " and writes "
            + writes
            + ". A bean maps both ways over the properties it can read and write, and one way only"
            + " when it offers nothing at all in the other direction, so reading some names and"
            + " writing others fits neither.",
        "Align each getter with its setter (or builder setter), which a misspelt accessor usually"
            + " explains, or remove the accessors of the direction the wire is not crossed in.");
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
    // A method's enclosing element is always the declaring type.
    return ((TypeElement) method.getEnclosingElement())
        .getQualifiedName()
        .contentEquals("java.lang.Object");
  }

  private boolean isList(TypeMirror type) {
    return isDeclared(type, LIST);
  }

  private boolean isBoolean(TypeMirror type) {
    return type.getKind() == TypeKind.BOOLEAN || isDeclared(type, BOOLEAN);
  }

  private static boolean isDeclared(TypeMirror type, String qualifiedName) {
    return type instanceof DeclaredType declared
        && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals(qualifiedName);
  }

  /**
   * Names the type a property is declared on, when that is not the bean itself.
   *
   * <p>A bean inherits properties, so the accessors a reader has to go and align may be in a file
   * the bean's own source never mentions.
   *
   * @param bean the wire being analysed
   * @param getter the property's getter
   * @return a parenthetical naming the declaring type, or the empty string when it is the bean
   */
  private static String declaredOn(TypeElement bean, ExecutableElement getter) {
    return declaredOn(inheritedFrom(bean, getter));
  }

  /**
   * The parenthetical a diagnostic appends to an accessor inherited from {@code declaring}: {@code
   * (declared on 'Base')}, or the empty string for one the type declares itself.
   */
  static String declaredOn(Optional<TypeElement> declaring) {
    return declaring.map(type -> " (declared on '" + type.getSimpleName() + "')").orElse("");
  }

  private TypeMirror getterType(DeclaredType owner, ExecutableElement getter) {
    return ProcessorUtils.returnTypeIn(env.getTypeUtils(), owner, getter);
  }

  private TypeMirror paramType(DeclaredType owner, ExecutableElement setter) {
    return ProcessorUtils.firstParameterTypeIn(env.getTypeUtils(), owner, setter);
  }

  /**
   * A no-args constructor the generated impl can call. The impl is emitted in the <em>spec's</em>
   * package, so a public constructor is always reachable (as a compiled third-party bean's would
   * be), while a {@code protected} or package-private one is reachable only when the bean is
   * co-located with the spec. A non-public constructor on a bean in another package would make
   * {@code new Wire()} illegal in the generated impl, so it is not treated as usable.
   */
  private boolean hasUsableNoArgsConstructor(TypeElement spec, TypeElement bean) {
    boolean samePackage =
        env.getElementUtils().getPackageOf(spec).equals(env.getElementUtils().getPackageOf(bean));
    return ElementFilter.constructorsIn(bean.getEnclosedElements()).stream()
        .anyMatch(
            c ->
                c.getParameters().isEmpty()
                    && (c.getModifiers().contains(Modifier.PUBLIC)
                        || (samePackage && !c.getModifiers().contains(Modifier.PRIVATE))));
  }

  /**
   * The part of an accessor's name after its prefix that {@link #decapitalise} reads back as {@code
   * property}: {@code email} -> {@code Email}, {@code URL} -> {@code URL}, {@code eMail} -> {@code
   * eMail}. Capitalising alone is no inverse, since {@code getEMail} reads as {@code EMail}.
   */
  static String accessorSuffix(String property) {
    String capitalised = ProcessorUtils.capitalise(property);
    return decapitalise(capitalised).equals(property) ? capitalised : property;
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
