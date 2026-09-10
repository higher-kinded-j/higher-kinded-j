// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeSpec;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.annotations.MappingIndexEntry;

/**
 * The classpath index of generated mappings: what {@code @GenerateMapping} writes beside each Impl
 * so that a later compilation can find the specs compiled into its dependencies.
 *
 * <p>javac exposes a dependency's classes only by name, or by listing one package: {@link
 * Elements#getTypeElement} and {@link PackageElement#getEnclosedElements()}. A resource under
 * {@code META-INF} cannot serve as an index, because the {@code Filer} returns the first classpath
 * match and no other. So the index is a package. Every generated Impl is accompanied by one empty
 * class in {@link #INDEX_PACKAGE} carrying {@link MappingIndexEntry} with the spec's canonical
 * name; a downstream compilation lists the package, which javac assembles from every classpath
 * entry, and re-reads each named spec from its class file. The class file carries everything
 * registration needs: the {@code MappingSpec} supertype with its type arguments, the type
 * parameters, the abstract and default leaves, the record and sealed shapes of the pair.
 *
 * <p>The index is written and read only in the unnamed module. A package present in two named
 * modules is a split package, which the module system refuses at run time, and a plain jar used as
 * an automatic module is subject to the same rule; so a spec compiled inside a named module writes
 * no entry, and a compilation of a named module reads none.
 */
final class MappingIndexes {

  /** The package every index entry is written to, shared by all compilations on one classpath. */
  static final String INDEX_PACKAGE = "org.higherkindedj.mapping.index";

  private static final ClassName MAPPING_INDEX =
      ClassName.get("org.higherkindedj.optics.annotations", "MappingIndexEntry");

  private MappingIndexes() {}

  /**
   * The processor option that turns the index off for a compilation ({@code
   * -Ahkj.mapping.index=false}): no entry is written and none is read. A library that will be
   * placed on a module path beside other spec-carrying jars sets it, since the index package would
   * be a split package there.
   */
  static final String OPTION = "hkj.mapping.index";

  /**
   * Whether the index is in use for a compilation, decided once from any element of the round, and
   * if not, why: the answer the writer, the scan and the fix sentences all share.
   */
  sealed interface IndexUse {
    /** Entries are written and read. */
    record Usable() implements IndexUse {}

    /** The compilation is a named module, which can neither write into nor read the package. */
    record NamedModule() implements IndexUse {}

    /** The option turned the index off. */
    record Off() implements IndexUse {}

    /**
     * A named module on the module path (a spec-carrying jar used as an automatic module) owns the
     * package as this compilation sees it: the compiler refuses an entry written into it here, and
     * resolves the package to the module before the classpath, so entries in classpath jars are
     * unreachable behind it.
     */
    record OwnedBy(ModuleElement module) implements IndexUse {}
  }

  static IndexUse indexUse(ProcessingEnvironment env, Element inRound) {
    Elements elements = env.getElementUtils();
    if ("false".equals(env.getOptions().get(OPTION))) {
      return new IndexUse.Off();
    }
    // getModuleOf is null only below --release 9, where no spec can compile (records).
    if (!elements.getModuleOf(inRound).isUnnamed()) {
      return new IndexUse.NamedModule();
    }
    return Optional.ofNullable(elements.getPackageElement(INDEX_PACKAGE))
        .map(elements::getModuleOf)
        .filter(module -> !module.isUnnamed())
        .<IndexUse>map(IndexUse.OwnedBy::new)
        .orElseGet(IndexUse.Usable::new);
  }

  /**
   * Whether {@code spec} is being compiled from source here rather than read from a class file. An
   * index entry generated in an earlier round of this compilation lists such a spec, which is this
   * compilation's own, not a dependency's.
   */
  static boolean compiledHere(Elements elements, TypeElement spec) {
    return Optional.ofNullable(elements.getFileObjectOf(spec))
        .map(JavaFileObject::getKind)
        .filter(JavaFileObject.Kind.SOURCE::equals)
        .isPresent();
  }

  /**
   * The entry class for a spec: its canonical name with {@code $} for each dot. A package and a
   * type cannot share a name (JLS 7.1), and a top-level {@code Outer$Inner} beside a nested {@code
   * Outer.Inner} would share a binary name, which javac refuses; so two specs share an entry only
   * when one of them is named with a {@code $} of its own ({@code com.z$M} beside {@code com.z.M}),
   * which the language reserves for generated code, and that collision is reported at the entry
   * write.
   */
  static ClassName entryName(TypeElement spec) {
    return ClassName.get(INDEX_PACKAGE, spec.getQualifiedName().toString().replace('.', '$'));
  }

  /** The entry to write for {@code spec}: an empty, uninstantiable class carrying the marker. */
  static TypeSpec entry(TypeElement spec, ClassName generated) {
    return TypeSpec.classBuilder(entryName(spec))
        .addOriginatingElement(spec)
        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
        .addAnnotation(generated)
        .addAnnotation(
            AnnotationSpec.builder(MAPPING_INDEX)
                .addMember("spec", "$S", spec.getQualifiedName())
                .build())
        // Named in prose, not linked: a {@link} would import the spec, and a package-private spec
        // cannot be imported from the index package.
        .addJavadoc(
            "Index entry for {@code $L}, so that a compilation depending on this one can nest,"
                + " dispatch to and merge through its mapping. Not for hand use.\n",
            spec.getQualifiedName())
        .addMethod(MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build())
        .build();
  }

  /**
   * The specs the classpath's index entries name, sorted by canonical name so that diagnostics list
   * them in one order whatever the classpath's. An entry whose spec is no longer on the classpath
   * is passed over: it describes nothing a use site could resolve to.
   */
  static List<TypeElement> classpathSpecs(Elements elements) {
    PackageElement index = elements.getPackageElement(INDEX_PACKAGE);
    if (index == null) {
      return List.of();
    }
    return ElementFilter.typesIn(index.getEnclosedElements()).stream()
        .map(entry -> entry.getAnnotation(MappingIndexEntry.class))
        .filter(Objects::nonNull)
        .map(marker -> elements.getTypeElement(marker.spec()))
        .filter(Objects::nonNull)
        .sorted(Comparator.comparing(spec -> spec.getQualifiedName().toString()))
        .toList();
  }
}
