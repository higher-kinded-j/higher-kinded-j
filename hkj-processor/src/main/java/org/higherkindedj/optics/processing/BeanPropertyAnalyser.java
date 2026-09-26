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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;
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
 * getter with no writer is not treated as a mappable component. Where a property's writer is
 * overloaded, the overload whose parameter is the getter's type writes it, whatever order the two
 * are declared in. Where that intersection is empty under every strategy, the bean maps one way if
 * it offers nothing at all in the other: a bean with getters and no way to be written maps
 * parse-only over all of its getters, and one that can be written but declares no getter maps
 * build-only over all of its writers. A bean that reads some names and writes others is refused,
 * since a misspelt accessor is a likelier story than a wire meant to be crossed one way. A
 * getter-only {@code List} counts as written only on a bean that also has a setter, or whose every
 * getter is such a list: a {@code List} getter among read-only getters belongs to a read model,
 * which maps parse-only.
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

  /** The interface every protobuf-java message implements, on the full and lite runtimes alike. */
  private static final String PROTOBUF_MESSAGE = "com.google.protobuf.MessageLite";

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
    Map<String, List<ExecutableElement>> setters = setterWritten ? collectSetters(bean) : Map.of();

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
        ExecutableElement setter = pairedWriter(setters.get(name), beanType, getterType);
        if (setter != null) {
          if (typesDiffer(
                  spec,
                  bean,
                  entry.getValue(),
                  tag,
                  name,
                  getterType,
                  paramType(beanType, setter),
                  report)
              || writerAmbiguous(
                  spec, bean, tag, name, getterType, setter, setters.get(name), beanType, report)) {
            return null;
          }
          properties.add(
              readWrite(name, getterType, getter, new WireShape.WriteSite.Setter(setter)));
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
    Map<String, List<ExecutableElement>> builderSetters = Map.of();
    if (builder != null) {
      builderSetters = collectBuilderSetters(bean, builder);
      DeclaredType builderType = builder.builderType();
      List<WireShape.BeanProperty> properties = new ArrayList<>();
      List<WireShape.UnpairedAccessor> unpaired = new ArrayList<>();
      for (Map.Entry<String, ExecutableElement> entry : getters.entrySet()) {
        String name = entry.getKey();
        TypeMirror getterType = getterType(beanType, entry.getValue());
        ExecutableElement builderSetter =
            pairedWriter(builderSetters.get(name), builderType, getterType);
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
                report)
            || writerAmbiguous(
                spec,
                bean,
                tag,
                name,
                getterType,
                builderSetter,
                builderSetters.get(name),
                builderType,
                report)) {
          return null;
        }
        properties.add(
            readWrite(
                name,
                getterType,
                entry.getValue().getSimpleName().toString(),
                new WireShape.WriteSite.Setter(builderSetter)));
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
          setters.isEmpty() ? builderSetters.keySet() : setters.keySet(),
          neverBuilt);
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
   * The writer a getter pairs with among a property's {@code overloads}, or null when the property
   * has none: the one whose parameter, as {@code owner} declares it, is the getter's type, since
   * {@code build} passes a value of that type. When none is, the {@link #representative} stands for
   * the property, and the pair is refused as read and written at different types.
   */
  private ExecutableElement pairedWriter(
      List<ExecutableElement> overloads, DeclaredType owner, TypeMirror getterType) {
    if (overloads == null) {
      return null;
    }
    return overloads.stream()
        .filter(writer -> env.getTypeUtils().isSameType(paramType(owner, writer), getterType))
        .findFirst()
        .orElse(representative(overloads));
  }

  /**
   * The overload that stands for a property no getter's type chooses a writer for: the first one
   * met. It gives a writer nothing reads its type in a diagnostic, and a bean that is only written
   * the type of its property, where javac binds the call {@code build} makes by the value passed.
   */
  private static ExecutableElement representative(List<ExecutableElement> overloads) {
    return overloads.getFirst();
  }

  /**
   * Whether javac could not choose the {@code writer} a getter pairs with from among its {@code
   * overloads}, and reports so when {@code report}. Another overload that takes the value {@code
   * build} passes ({@link #takesValue}) leaves javac to call the more specific of the two, and the
   * call is ambiguous when {@code writer} is not that one. A primitive value never meets this,
   * since an overload that takes it only by widening or boxing is always the less specific.
   */
  private boolean writerAmbiguous(
      TypeElement spec,
      TypeElement bean,
      String tag,
      String name,
      TypeMirror getterType,
      ExecutableElement writer,
      List<ExecutableElement> overloads,
      DeclaredType owner,
      boolean report) {
    if (getterType.getKind().isPrimitive()) {
      return false;
    }
    Types types = env.getTypeUtils();
    TypeMirror written = paramType(owner, writer);
    Optional<ExecutableElement> rival =
        overloads.stream()
            .filter(other -> !other.equals(writer))
            .filter(
                other -> {
                  TypeMirror parameter = paramType(owner, other);
                  return takesValue(getterType, parameter)
                      && (types.isSameType(written, parameter)
                          || !types.isSubtype(written, parameter));
                })
            .findFirst();
    if (rival.isPresent() && report) {
      Diagnostics.error(
          env.getMessager(),
          spec,
          tag,
          "bean property '"
              + name
              + "' on '"
              + bean.getSimpleName()
              + "' has two writers the generated call cannot choose between: "
              + writerSignature(writer)
              + " and "
              + writerSignature(rival.get())
              + ".",
          "build passes each property a value of its getter's type, "
              + ProcessorUtils.simpleTypeName(getterType)
              + ", and both writers take it with neither more specific than the other, so javac"
              + " would refuse the call as ambiguous.",
          "Remove or rename one of the two where they are declared, so that one writer takes the"
              + " property.");
    }
    return rival.isPresent();
  }

  /**
   * Whether a writer whose parameter is {@code parameter} takes the value {@code build} passes for
   * a property read at {@code getterType}. The value has the getter's type, except where the
   * mapping hands over a {@link ContainerCopy}: a generic method's result, whose element type javac
   * infers afresh for each writer. A copied {@code List<String>} can be a {@code List<Object>}, and
   * so a {@code Collection<Object>}, but never a {@code Collection<Integer>}. So a parameter the
   * container is no subtype of still takes its copy when it is a supertype of the container whose
   * type argument either is a supertype of the element there or has a lower bound, and always when
   * the container is raw. An argument with an upper bound, or none, that admitted the element would
   * have made the container itself a subtype, since every container copied with a supertype of
   * another erasure has one type argument.
   */
  private boolean takesValue(TypeMirror getterType, TypeMirror parameter) {
    Types types = env.getTypeUtils();
    if (types.isSubtype(getterType, parameter)) {
      return true;
    }
    if (ContainerCopy.of(getterType) == null
        || getterType.getKind() != TypeKind.DECLARED
        || parameter.getKind() != TypeKind.DECLARED) {
      return false;
    }
    // Captured, so an element declared through a wildcard compares as the type it stands for.
    TypeMirror container =
        ProcessorUtils.supertypeOf(
            types, types.capture(getterType), (TypeElement) ((DeclaredType) parameter).asElement());
    if (container == null) {
      return false;
    }
    List<? extends TypeMirror> elements = ((DeclaredType) container).getTypeArguments();
    List<? extends TypeMirror> arguments = ((DeclaredType) parameter).getTypeArguments();
    // A raw container names no element, so javac infers the copy's from the writer alone.
    return elements.isEmpty()
        || IntStream.range(0, arguments.size())
            .allMatch(
                index ->
                    arguments.get(index).getKind() == TypeKind.WILDCARD
                        ? ((WildcardType) arguments.get(index)).getSuperBound() != null
                        : types.isSubtype(elements.get(index), arguments.get(index)));
  }

  /** A writer as its declaration spells it, so the reader can find the one to change. */
  private static String writerSignature(ExecutableElement writer) {
    return writer.getSimpleName()
        + "("
        + ProcessorUtils.simpleTypeName(writer.getParameters().getFirst().asType())
        + ")";
  }

  /**
   * The writers no getter shares a name with, each at its type as {@code owner} (the bean, or the
   * builder as its factory instantiates it) declares it, a property's {@link #representative}
   * standing for its overloads.
   */
  private List<WireShape.UnpairedAccessor> unpairedWriters(
      Map<String, ExecutableElement> getters,
      Map<String, List<ExecutableElement>> writers,
      DeclaredType owner,
      WireShape.UnpairedAccessor.Role role) {
    return writers.entrySet().stream()
        .filter(entry -> !getters.containsKey(entry.getKey()))
        .map(entry -> Map.entry(entry.getKey(), representative(entry.getValue())))
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
   * the builder as the factory instantiates it for a builder setter. With no getter to match, a
   * property's {@link #representative} decides its type.
   */
  private WireShape.BeanShape writeOnly(
      TypeElement bean,
      DeclaredType owner,
      Map<String, List<ExecutableElement>> writers,
      WireShape.ConstructionStrategy strategy) {
    List<WireShape.BeanProperty> properties =
        writers.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), representative(entry.getValue())))
            .map(
                entry ->
                    new WireShape.BeanProperty(
                        entry.getKey(),
                        paramType(owner, entry.getValue()),
                        Optional.<String>empty(),
                        Optional.<WireShape.WriteSite>of(
                            new WireShape.WriteSite.Setter(entry.getValue()))))
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

  /**
   * The bean's setters, keyed by property, each property's overloads in the order they are met.
   * Void or fluent (returns the bean) setters both work: build calls the setter as a statement,
   * discarding any fluent return.
   */
  private Map<String, List<ExecutableElement>> collectSetters(TypeElement bean) {
    return byProperty(
        publicInstanceMethods(bean).stream()
            .filter(
                method ->
                    method.getParameters().size() == 1
                        && isSetName(method.getSimpleName().toString())),
        3);
  }

  private static boolean isSetName(String methodName) {
    return methodName.length() > 3 && methodName.startsWith("set");
  }

  /**
   * Writers grouped by the property their name gives after {@code prefix} characters, in the order
   * each property is first met, with each property's overloads in the order they are met.
   */
  private static Map<String, List<ExecutableElement>> byProperty(
      Stream<ExecutableElement> writers, int prefix) {
    return writers.collect(
        Collectors.groupingBy(
            method -> decapitalise(method.getSimpleName().toString().substring(prefix)),
            LinkedHashMap::new,
            Collectors.toUnmodifiableList()));
  }

  /**
   * The builder's setters, keyed by property, each property's overloads in the order they are met.
   * Both the property-named convention ({@code name(T)}, as Lombok/Immutables/AutoValue emit) and
   * the {@code setX} convention (as protobuf emits) are accepted; when a property has both, its
   * property-named overloads come first, so one of them wins whenever it fits the getter as well. A
   * method taking the bean or the builder itself copies a whole value in ({@code from(Bean)},
   * {@code mergeFrom(Builder)}), so it is no property's setter.
   */
  private Map<String, List<ExecutableElement>> collectBuilderSetters(
      TypeElement bean, BuilderModel builder) {
    List<ExecutableElement> writers =
        publicInstanceMethods(builder.builderElement()).stream()
            .filter(method -> method.getParameters().size() == 1)
            .filter(method -> !copiesWhole(method, bean, builder))
            .toList();
    Map<String, List<ExecutableElement>> merged =
        new LinkedHashMap<>(
            byProperty(
                writers.stream().filter(method -> isSetName(method.getSimpleName().toString())),
                3));
    byProperty(writers.stream().filter(method -> !isSetName(method.getSimpleName().toString())), 0)
        .forEach(
            (name, propertyNamed) ->
                merged.merge(
                    name,
                    propertyNamed,
                    (setX, named) -> Stream.concat(named.stream(), setX.stream()).toList()));
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
      return new WireShape.ConstructionStrategy.Builder(factory, buildMethod, builderType);
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
   * bean whose getters and writers never share a name. {@code sparse} marks a sparse update's PATCH
   * bean, which a record cannot replace.
   */
  private void reportUnusable(
      TypeElement spec,
      TypeElement bean,
      String tag,
      Set<String> reads,
      Set<String> writes,
      boolean sparse) {
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
        protobufFix(env, bean, sparse)
            .orElse(
                "Align each getter with its setter (or builder setter), which a misspelt accessor"
                    + " usually explains, or remove the accessors of the direction the wire is not"
                    + " crossed in."));
  }

  /**
   * The fix for a refusal a protobuf-java message meets, or empty for any other wire: convert the
   * message by hand to a record, or for a PATCH body to a bean whose getters answer {@code null}
   * until set, and map that. A message reads as a builder bean, but the properties that reading
   * finds are not the fields it carries: protobuf generates companion accessors that pair up like
   * properties, and gives a repeated or map field no setter, so a refusal's own fix, which works
   * within the mapping, cannot reach a working one.
   *
   * @param env the processing environment, to look protobuf-java up on the path
   * @param wire the wire a refusal names
   * @param sparse whether the wire is a sparse update's PATCH body, which a record cannot be
   * @return the fix line, when {@code wire} is a protobuf-java message
   */
  static Optional<String> protobufFix(ProcessingEnvironment env, TypeElement wire, boolean sparse) {
    TypeElement message = env.getElementUtils().getTypeElement(PROTOBUF_MESSAGE);
    Types types = env.getTypeUtils();
    if (message == null || !types.isSubtype(types.erasure(wire.asType()), message.asType())) {
      return Optional.empty();
    }
    return Optional.of(
        "Convert '"
            + wire.getSimpleName()
            + "' by hand to "
            + (sparse ? "a PATCH bean whose getters answer null until set" : "a record")
            + ", and map that instead: a protobuf-java message is not supported yet,"
            + " since the companion accessors protobuf generates (such as getXBytes() beside a"
            + " string field, getXValue() beside a proto3 enum, and getUnknownFields()) pair up as"
            + " properties, and a repeated or map field, having no setter, is no property at all.");
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
