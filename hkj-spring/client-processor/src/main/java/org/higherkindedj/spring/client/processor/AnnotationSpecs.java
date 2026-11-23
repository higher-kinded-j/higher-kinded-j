// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleAnnotationValueVisitor14;

/**
 * Writes an annotation the client carries, or inherits, into generated source as it was written,
 * and says whether the package the client is generated in can write it.
 *
 * <p>javapoet's {@link AnnotationSpec#get(AnnotationMirror)} leaves out an array written empty,
 * which changes the annotation where the member's default is not empty, and does not compile where
 * the member has no default. {@link #copyOf} keeps it, at any depth.
 *
 * <p>A client restates the methods it inherits, and a base interface in another package may have
 * annotated them with something only that package can name. {@link #writabilityIn} reads that from
 * the annotation alone, so each place that copies one decides from the same reading. {@link
 * #copiesOf} writes one declaration's annotations from it: those the package cannot write are left
 * out, and the warnings a deprecated one draws are suppressed.
 */
final class AnnotationSpecs {

  private static final ClassName SUPPRESS_WARNINGS = ClassName.get(SuppressWarnings.class);

  /** An array written empty, as {@link #copyOf} writes it. */
  private static final CodeBlock EMPTY_ARRAY = CodeBlock.of("{}");

  /** An annotation, or a part of one, that the package can write with no warning. */
  private static final Writable CLEAN = new Writable(Set.of());

  private AnnotationSpecs() {
    // Utility class - prevent instantiation
  }

  /** A lint category a copy can draw where it is written, in name order. */
  enum Lint {
    /** Drawn by an element that is deprecated. */
    DEPRECATION("deprecation"),
    /** Drawn by an element that is deprecated for removal. */
    REMOVAL("removal");

    private final String category;

    Lint(String category) {
      this.category = category;
    }

    /** The category as {@code @SuppressWarnings} names it. */
    String category() {
      return category;
    }
  }

  /**
   * Whether a package can write an annotation: its type, each member written, and every class, enum
   * constant and annotation its values name. Only the members written count, as javapoet writes no
   * others.
   */
  sealed interface Writability permits Writable, Unwritable {

    /** Both parts together: the first that cannot be written, or else the warnings of both. */
    default Writability and(Writability other) {
      return switch (this) {
        case Unwritable unwritable -> unwritable;
        case Writable mine ->
            switch (other) {
              case Unwritable unwritable -> unwritable;
              case Writable theirs ->
                  new Writable(
                      Stream.concat(mine.warnings().stream(), theirs.warnings().stream())
                          .collect(toLints()));
            };
      };
    }
  }

  /**
   * The package can write the annotation. Where a part of it is deprecated, the copy draws a
   * warning there that the author never wrote, and {@code warnings} holds what it draws; it is
   * empty for a clean copy.
   */
  record Writable(Set<Lint> warnings) implements Writability {

    Writable {
      EnumSet<Lint> copy = EnumSet.noneOf(Lint.class);
      copy.addAll(warnings);
      warnings = Collections.unmodifiableSet(copy);
    }
  }

  /** The package cannot write the annotation, for the reason a part of it gives. */
  sealed interface Unwritable extends Writability permits Missing, Unresolved, Skewed, NotPublic {}

  /**
   * The annotation names {@code named}, a type javac could not resolve. A class file can name a
   * type its own module could see and this one cannot.
   */
  record Missing(String named) implements Unwritable {}

  /**
   * In source, a value set on {@code member} of the annotation type {@code annotation} names a
   * class or a constant javac could not resolve. javac hands it over as a mark with no name, so the
   * member is what can be named.
   */
  record Unresolved(String annotation, String member) implements Unwritable {}

  /**
   * The annotation was written against another version of {@code type} than this compilation's
   * classpath has. {@code differs} says how the version here differs, read after the type's name:
   * "has no constant HIGH", "has no member level()", "declares level() as another type", "declares
   * since() with no default", or "is no longer an annotation interface".
   */
  record Skewed(String type, String differs) implements Unwritable {}

  /**
   * The annotation names {@code named}, a type or enum constant that is private, or not public and
   * in another package, so the package cannot name it. {@code inPackage} says whether it is
   * declared in the package, where package access would do.
   */
  record NotPublic(String named, boolean inPackage) implements Unwritable {}

  /** The annotation as written, each member in the order it was written. */
  static AnnotationSpec copyOf(AnnotationMirror annotation) {
    // javapoet writes each value, one block per array element; only an empty array and a nested
    // annotation, which may hold one, are written here instead.
    Map<String, List<CodeBlock>> written = AnnotationSpec.get(annotation).members();
    AnnotationSpec.Builder copy =
        AnnotationSpec.builder(
            ClassName.get((TypeElement) annotation.getAnnotationType().asElement()));
    annotation
        .getElementValues()
        .forEach(
            (member, value) -> {
              String name = member.getSimpleName().toString();
              List<CodeBlock> blocks = written.getOrDefault(name, List.of());
              value.accept(
                  new SimpleAnnotationValueVisitor14<Void, Void>() {
                    @Override
                    protected Void defaultAction(Object unusedValue, Void unused) {
                      copy.addMember(name, blockOf(value, blocks.getFirst()));
                      return null;
                    }

                    @Override
                    public Void visitArray(List<? extends AnnotationValue> values, Void unused) {
                      if (values.isEmpty()) {
                        copy.addMember(name, EMPTY_ARRAY);
                      }
                      for (int i = 0; i < values.size(); i++) {
                        copy.addMember(name, blockOf(values.get(i), blocks.get(i)));
                      }
                      return null;
                    }
                  },
                  null);
            });
    return copy.build();
  }

  /** One value as written: javapoet's block, or a nested annotation copied by {@link #copyOf}. */
  private static CodeBlock blockOf(AnnotationValue value, CodeBlock written) {
    return value.accept(
        new SimpleAnnotationValueVisitor14<CodeBlock, Void>(written) {
          @Override
          public CodeBlock visitAnnotation(AnnotationMirror nested, Void unused) {
            return CodeBlock.of("$L", copyOf(nested));
          }
        },
        null);
  }

  /**
   * The annotations one generated declaration carries, each as written, with the warnings their
   * deprecated parts would draw there suppressed.
   */
  static List<AnnotationSpec> copiesOf(
      List<? extends AnnotationMirror> annotations, String targetPackage, Elements elements) {
    return copiesOf(annotations, targetPackage, elements, AnnotationSpecs::copyOf);
  }

  /**
   * The annotations one generated declaration carries, each written by {@code copy}, with the
   * warnings their deprecated parts would draw there suppressed on that declaration. The
   * suppression covers the whole declaration, as {@code @SuppressWarnings} does. A deprecation
   * belongs where the annotation is written, as javac reports it there, so the copy keeps the
   * annotation, which Spring reads, and drops only the warning. A {@code @SuppressWarnings} the
   * declaration already carries takes the categories in, since a declaration can carry only one.
   *
   * <p>An annotation the package cannot write is left out. The processor refuses the client first
   * for one Spring could read, which would change the request; an annotation Spring never reads is
   * the compiler's alone, and leaving it out changes nothing Spring sends. In source, an annotation
   * javac could not resolve waits for the round that brings its type, so none is left out here that
   * a later round would have resolved.
   */
  static List<AnnotationSpec> copiesOf(
      List<? extends AnnotationMirror> annotations,
      String targetPackage,
      Elements elements,
      Function<AnnotationMirror, AnnotationSpec> copy) {
    Walk walk = new Walk(targetPackage, elements::isDeprecated);
    record Reading(AnnotationMirror annotation, Writability writability) {}
    List<Reading> readings =
        annotations.stream()
            .map(annotation -> new Reading(annotation, walk.of(annotation)))
            .toList();
    // In name order, as the enum declares them, so the copy reads the same on every run.
    Set<Lint> warnings =
        readings.stream()
            .flatMap(
                reading ->
                    reading.writability() instanceof Writable writable
                        ? writable.warnings().stream()
                        : Stream.empty())
            .collect(toLints());
    List<AnnotationSpec> copies =
        readings.stream()
            .filter(reading -> reading.writability() instanceof Writable)
            .map(reading -> copy.apply(reading.annotation()))
            .toList();
    return suppressing(warnings, copies);
  }

  /** One declaration's annotations with the given lint categories suppressed on it. */
  private static List<AnnotationSpec> suppressing(
      Set<Lint> warnings, List<AnnotationSpec> annotations) {
    if (warnings.isEmpty()) {
      return annotations;
    }
    List<CodeBlock> wanted =
        warnings.stream().map(warning -> CodeBlock.of("$S", warning.category())).toList();
    if (annotations.stream().noneMatch(AnnotationSpecs::isSuppressWarnings)) {
      return Stream.concat(annotations.stream(), Stream.of(suppressWarnings(wanted))).toList();
    }
    return annotations.stream()
        .map(
            annotation ->
                isSuppressWarnings(annotation)
                    ? suppressWarnings(
                        Stream.concat(
                                annotation.members().getOrDefault("value", List.of()).stream()
                                    .filter(block -> !block.equals(EMPTY_ARRAY)),
                                wanted.stream())
                            .toList())
                    : annotation)
        .toList();
  }

  private static boolean isSuppressWarnings(AnnotationSpec annotation) {
    return annotation.type().equals(SUPPRESS_WARNINGS);
  }

  /**
   * {@code @SuppressWarnings} naming each category once, in the order given. A {@code CodeBlock} is
   * equal to another that reads the same.
   */
  private static AnnotationSpec suppressWarnings(List<CodeBlock> categories) {
    AnnotationSpec.Builder builder = AnnotationSpec.builder(SUPPRESS_WARNINGS);
    for (CodeBlock category : categories.stream().distinct().toList()) {
      builder.addMember("value", category);
    }
    return builder.build();
  }

  /** Collects lint categories into a set read in name order. */
  private static Collector<Lint, ?, Set<Lint>> toLints() {
    return Collectors.collectingAndThen(
        Collectors.toCollection(() -> EnumSet.noneOf(Lint.class)), Collections::unmodifiableSet);
  }

  /**
   * Whether {@code targetPackage} can write the annotation, and what writing it there draws. A part
   * that cannot be written decides it, the first met in the order written: the annotation's type,
   * then each member and its value. A deprecation is read from {@code @Deprecated}, and from the
   * mark javac keeps for a javadoc {@code @deprecated} tag, in source and in a class file alike.
   */
  static Writability writabilityIn(
      AnnotationMirror annotation, String targetPackage, Elements elements) {
    return new Walk(targetPackage, elements::isDeprecated).of(annotation);
  }

  /**
   * Whether {@code targetPackage} can write the annotation with no warning: a type-use annotation
   * the client restates is kept only then. A deprecation is read from {@code @Deprecated} alone, as
   * naming a type takes no {@code Elements}, so one written only as a javadoc tag is not seen.
   */
  static boolean writesCleanlyIn(AnnotationMirror annotation, String targetPackage) {
    return new Walk(targetPackage, element -> false).of(annotation) instanceof Writable writable
        && writable.warnings().isEmpty();
  }

  /**
   * One reading: the package the copy is written into, and whether an element carries the mark of a
   * deprecation written as a javadoc tag.
   *
   * <p>hkj-processor's {@code ProcessorUtils.writable} keeps the same rules for the type-use
   * annotations generated optics restate, apart from three differences: it answers only yes or no,
   * where this gives the reason a refusal names; it reads no javadoc tag, as naming a type there
   * takes no {@code Elements}; and it needs no check that a member is declared, since a member
   * javac made up has no type, which no value fits. A change to one belongs in the other.
   */
  private record Walk(String targetPackage, Predicate<Element> deprecatedByTag) {

    Writability of(AnnotationMirror annotation) {
      DeclaredType type = annotation.getAnnotationType();
      return Stream.of(
              Stream.of(ofType(type), shapeOf(type, annotation)),
              annotation.getElementValues().entrySet().stream()
                  .map(
                      entry ->
                          memberOf(type, entry.getKey())
                              .and(
                                  ofValue(
                                      entry.getValue(),
                                      entry.getKey(),
                                      entry.getKey().getReturnType()))))
          .flatMap(parts -> parts)
          .reduce(CLEAN, Writability::and);
    }

    /**
     * Whether the annotation type on this classpath can take the annotation as written: it is an
     * annotation interface still, and each member it declares with no default is set.
     */
    private Writability shapeOf(DeclaredType type, AnnotationMirror annotation) {
      if (type.getKind() != TypeKind.DECLARED) {
        return CLEAN;
      }
      Element element = type.asElement();
      if (element.getKind() != ElementKind.ANNOTATION_TYPE) {
        return new Skewed(nameOf(element), "is no longer an annotation interface");
      }
      return ElementFilter.methodsIn(element.getEnclosedElements()).stream()
          .filter(member -> member.getDefaultValue() == null)
          .filter(member -> !annotation.getElementValues().containsKey(member))
          .<Writability>map(
              member ->
                  new Skewed(
                      nameOf(element), "declares " + member.getSimpleName() + "() with no default"))
          .findFirst()
          .orElse(CLEAN);
    }

    /**
     * A member written: one the annotation type on this classpath declares, and whether it is
     * deprecated. javac makes up a member a class file names that the type lacks.
     */
    private Writability memberOf(DeclaredType type, ExecutableElement member) {
      return type.getKind() == TypeKind.DECLARED
              && !type.asElement().getEnclosedElements().contains(member)
          ? new Skewed(nameOf(type.asElement()), "has no member " + member.getSimpleName() + "()")
          : deprecationOf(member);
    }

    /**
     * A value, read against the type its member declares, as javac would take it written in source.
     * A class file written against another version of the annotation type can hold a value the
     * member on this classpath declares otherwise: one javac accepts as written, such as an {@code
     * int} where the member is now a {@code long}, an {@code int} that fits where it is now a
     * {@code byte}, or a single value where it is now an array of its kind, is copied as it stands.
     *
     * <p>In source, javac hands over a class literal or a constant it could not resolve as a mark
     * that visits as a string but, unlike a string, is not written in quotes.
     */
    private Writability ofValue(
        AnnotationValue value, ExecutableElement member, TypeMirror declared) {
      if (value.getValue() instanceof String && !value.toString().startsWith("\"")) {
        return new Unresolved(nameOf(member.getEnclosingElement()), member.toString());
      }
      if (declared instanceof ArrayType array && !(value.getValue() instanceof List)) {
        return ofValue(value, member, array.getComponentType());
      }
      // A member this classpath declares with a type it lacks cannot be written whatever its value,
      // as hkj-processor's twin leaves it off.
      if (declared.getKind() == TypeKind.ERROR) {
        return new Missing(declared.toString());
      }
      // javac makes up a member a class file sets that the type lacks, typed as null; that member
      // is reported on its own.
      Writability otherType =
          declared.getKind() == TypeKind.NULL
              ? CLEAN
              : new Skewed(
                  nameOf(member.getEnclosingElement()),
                  "declares " + member.getSimpleName() + "() as another type");
      return value.accept(
          new SimpleAnnotationValueVisitor14<Writability, Void>(CLEAN) {
            @Override
            public Writability visitBoolean(boolean value, Void unused) {
              return widens(TypeKind.BOOLEAN, declared.getKind()) ? CLEAN : otherType;
            }

            @Override
            public Writability visitByte(byte value, Void unused) {
              return widens(TypeKind.BYTE, declared.getKind()) || fits(value, declared.getKind())
                  ? CLEAN
                  : otherType;
            }

            @Override
            public Writability visitChar(char value, Void unused) {
              return widens(TypeKind.CHAR, declared.getKind()) || fits(value, declared.getKind())
                  ? CLEAN
                  : otherType;
            }

            @Override
            public Writability visitShort(short value, Void unused) {
              return widens(TypeKind.SHORT, declared.getKind()) || fits(value, declared.getKind())
                  ? CLEAN
                  : otherType;
            }

            @Override
            public Writability visitInt(int value, Void unused) {
              return widens(TypeKind.INT, declared.getKind()) || fits(value, declared.getKind())
                  ? CLEAN
                  : otherType;
            }

            @Override
            public Writability visitLong(long value, Void unused) {
              return widens(TypeKind.LONG, declared.getKind()) ? CLEAN : otherType;
            }

            @Override
            public Writability visitFloat(float value, Void unused) {
              return widens(TypeKind.FLOAT, declared.getKind()) ? CLEAN : otherType;
            }

            @Override
            public Writability visitDouble(double value, Void unused) {
              return widens(TypeKind.DOUBLE, declared.getKind()) ? CLEAN : otherType;
            }

            @Override
            public Writability visitString(String string, Void unused) {
              return isNamed(declared, "java.lang.String") ? CLEAN : otherType;
            }

            @Override
            public Writability visitType(TypeMirror type, Void unused) {
              return isNamed(declared, "java.lang.Class") ? ofType(type) : otherType;
            }

            @Override
            public Writability visitEnumConstant(VariableElement constant, Void unused) {
              // An enum javac could not resolve still names its constant, under an unresolved type,
              // and a constant the enum on this classpath lacks is one javac made up.
              TypeMirror enumType = constant.getEnclosingElement().asType();
              if (enumType.getKind() == TypeKind.ERROR) {
                return new Missing(enumType.toString());
              }
              if (!sameElement(declared, constant.getEnclosingElement())) {
                return otherType;
              }
              return constant.getKind() == ElementKind.ENUM_CONSTANT
                  ? nestingOf(constant)
                  : new Skewed(
                      nameOf(constant.getEnclosingElement()),
                      "has no constant " + constant.getSimpleName());
            }

            @Override
            public Writability visitAnnotation(AnnotationMirror nested, Void unused) {
              return nested.getAnnotationType().getKind() == TypeKind.ERROR
                      || sameElement(declared, nested.getAnnotationType().asElement())
                  ? of(nested)
                  : otherType;
            }

            @Override
            public Writability visitArray(List<? extends AnnotationValue> values, Void unused) {
              if (!(declared instanceof ArrayType array)) {
                return otherType;
              }
              return values.stream()
                  .map(element -> ofValue(element, member, array.getComponentType()))
                  .reduce(CLEAN, Writability::and);
            }
          },
          null);
    }

    /**
     * A type: resolved, with every type in its nesting visible from the package. A primitive can be
     * written, and an array where its component can.
     */
    private Writability ofType(TypeMirror type) {
      return switch (type.getKind()) {
        case DECLARED -> nestingOf(((DeclaredType) type).asElement());
        case ARRAY -> ofType(((ArrayType) type).getComponentType());
        case ERROR -> new Missing(type.toString());
        default -> CLEAN;
      };
    }

    /** An element and each type it is nested in: visible from the package, and whether current. */
    private Writability nestingOf(Element element) {
      return Stream.iterate(
              element,
              current -> current.getKind() != ElementKind.PACKAGE,
              Element::getEnclosingElement)
          .map(this::visibilityOf)
          .reduce(CLEAN, Writability::and);
    }

    private Writability visibilityOf(Element element) {
      Set<Modifier> modifiers = element.getModifiers();
      boolean inPackage = packageOf(element).equals(targetPackage);
      return modifiers.contains(Modifier.PRIVATE)
              || (!modifiers.contains(Modifier.PUBLIC) && !inPackage)
          ? new NotPublic(nameOf(element), inPackage)
          : deprecationOf(element);
    }

    /** The warning writing an element draws: none, or the one its deprecation says. */
    private Writability deprecationOf(Element element) {
      Deprecated deprecated = element.getAnnotation(Deprecated.class);
      if (deprecated != null) {
        return new Writable(Set.of(deprecated.forRemoval() ? Lint.REMOVAL : Lint.DEPRECATION));
      }
      return deprecatedByTag.test(element) ? new Writable(Set.of(Lint.DEPRECATION)) : CLEAN;
    }
  }

  private static boolean isNamed(TypeMirror type, String qualifiedName) {
    return type.getKind() == TypeKind.DECLARED
        && ((TypeElement) ((DeclaredType) type).asElement())
            .getQualifiedName()
            .contentEquals(qualifiedName);
  }

  private static boolean sameElement(TypeMirror type, Element element) {
    return type.getKind() == TypeKind.DECLARED && ((DeclaredType) type).asElement().equals(element);
  }

  /**
   * Whether a value of one primitive kind can be written where a member declares another: the same
   * kind, or a widening javac applies to a constant written in source.
   */
  private static boolean widens(TypeKind from, TypeKind to) {
    return from == to
        || switch (from) {
          case BYTE -> WIDER_THAN_BYTE.contains(to);
          case SHORT, CHAR -> WIDER_THAN_SHORT.contains(to);
          case INT -> WIDER_THAN_INT.contains(to);
          case LONG -> WIDER_THAN_LONG.contains(to);
          case FLOAT -> to == TypeKind.DOUBLE;
          default -> false;
        };
  }

  /**
   * Whether an integral constant fits a member declared {@code byte}, {@code short} or {@code
   * char}, where javac narrows a constant written in source.
   */
  private static boolean fits(int value, TypeKind to) {
    return switch (to) {
      case BYTE -> value == (byte) value;
      case SHORT -> value == (short) value;
      case CHAR -> value == (char) value;
      default -> false;
    };
  }

  private static final Set<TypeKind> WIDER_THAN_LONG = Set.of(TypeKind.FLOAT, TypeKind.DOUBLE);
  private static final Set<TypeKind> WIDER_THAN_INT =
      Set.of(TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE);
  private static final Set<TypeKind> WIDER_THAN_SHORT =
      Set.of(TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE);
  private static final Set<TypeKind> WIDER_THAN_BYTE =
      Set.of(TypeKind.SHORT, TypeKind.INT, TypeKind.LONG, TypeKind.FLOAT, TypeKind.DOUBLE);

  /** A type's qualified name, or a member's, such as an enum constant, under its type's. */
  private static String nameOf(Element element) {
    return element instanceof TypeElement type
        ? type.getQualifiedName().toString()
        : nameOf(element.getEnclosingElement()) + "." + element.getSimpleName();
  }

  /** The package a type is declared in, walked out from the type itself. */
  private static String packageOf(Element element) {
    Element current = element;
    while (current.getKind() != ElementKind.PACKAGE) {
      current = current.getEnclosingElement();
    }
    return ((PackageElement) current).getQualifiedName().toString();
  }
}
