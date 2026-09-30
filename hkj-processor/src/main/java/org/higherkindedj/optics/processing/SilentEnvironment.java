// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * The compiler's environment with its diagnostics dropped and its writes refused, for classifying a
 * spec to learn what it nests without reporting anything or generating its Impl. Where the registry
 * has a spec with two halves to give, it classifies each spec that could take them this way before
 * any spec is processed (see {@code MappingProcessor.withInferredHalves}), so what a spec nests is
 * found by the same classification that later generates it, not by a second account of it that
 * could disagree.
 *
 * <p>A write is refused with the {@link FilerException} a write the compiler refuses throws, so the
 * generator's own handling of a refused write applies, silently, should a classification ever reach
 * one. Everything else is the compiler's.
 */
final class SilentEnvironment implements ProcessingEnvironment {

  private static final Messager SILENT =
      new Messager() {
        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg) {}

        @Override
        public void printMessage(Diagnostic.Kind kind, CharSequence msg, Element e) {}

        @Override
        public void printMessage(
            Diagnostic.Kind kind, CharSequence msg, Element e, AnnotationMirror a) {}

        @Override
        public void printMessage(
            Diagnostic.Kind kind,
            CharSequence msg,
            Element e,
            AnnotationMirror a,
            AnnotationValue v) {}
      };

  private final ProcessingEnvironment env;
  private final Filer filer;

  SilentEnvironment(ProcessingEnvironment env) {
    this.env = env;
    this.filer = new RefusingFiler(env.getFiler());
  }

  @Override
  public Map<String, String> getOptions() {
    return env.getOptions();
  }

  @Override
  public Messager getMessager() {
    return SILENT;
  }

  @Override
  public Filer getFiler() {
    return filer;
  }

  @Override
  public Elements getElementUtils() {
    return env.getElementUtils();
  }

  @Override
  public Types getTypeUtils() {
    return env.getTypeUtils();
  }

  @Override
  public SourceVersion getSourceVersion() {
    return env.getSourceVersion();
  }

  @Override
  public Locale getLocale() {
    return env.getLocale();
  }

  @Override
  public boolean isPreviewEnabled() {
    return env.isPreviewEnabled();
  }

  /** A filer that reads what the compiler's reads and writes nothing. */
  private record RefusingFiler(Filer filer) implements Filer {

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements)
        throws IOException {
      throw refused(name);
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements)
        throws IOException {
      throw refused(name);
    }

    @Override
    public FileObject createResource(
        JavaFileManager.Location location,
        CharSequence moduleAndPkg,
        CharSequence relativeName,
        Element... originatingElements)
        throws IOException {
      throw refused(relativeName);
    }

    @Override
    public FileObject getResource(
        JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName)
        throws IOException {
      return filer.getResource(location, moduleAndPkg, relativeName);
    }

    private static FilerException refused(CharSequence name) {
      return new FilerException("a silent classification writes nothing: " + name);
    }
  }
}
