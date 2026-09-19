// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client.processor;

import com.google.auto.service.AutoService;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Claims the hkj annotations {@link HkjHttpClientProcessor} reads or writes without naming them:
 * {@code @OnStatus} and {@code @OnStatuses} on a client interface's methods, and the
 * {@code @Generated} marker on every client it generates.
 *
 * <p>javac reports an annotation no processor claims under {@code -Xlint:processing}. Claiming
 * these keeps hkj's names out of that report; the Spring annotations a client carries, such as
 * {@code @HttpExchange} or {@code @Configuration}, are reported as they are for hand-written Spring
 * code. A build that also runs {@code hkj-processor} has {@code @Generated} claimed there as well.
 * This one generates nothing, so it is registered with Gradle as isolating.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes({
  "org.higherkindedj.optics.annotations.Generated",
  "org.higherkindedj.spring.client.OnStatus",
  "org.higherkindedj.spring.client.OnStatuses"
})
public class CompanionAnnotationProcessor extends AbstractProcessor {

  /** Creates a new CompanionAnnotationProcessor. */
  public CompanionAnnotationProcessor() {}

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /** Claims these annotations in the round; there is nothing to generate for them. */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return true;
  }
}
