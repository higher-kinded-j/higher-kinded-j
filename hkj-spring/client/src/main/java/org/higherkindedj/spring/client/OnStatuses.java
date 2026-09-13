// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for repeated {@link OnStatus} annotations. Not used directly — declare multiple {@link
 * OnStatus} annotations on a method instead.
 *
 * <p>Retained in the class file for the same reason as {@link OnStatus}: two or more overrides on
 * one method are stored as this container, so an inherited method keeps them only if this container
 * is retained as well.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface OnStatuses {

  /**
   * The repeated overrides.
   *
   * @return the {@link OnStatus} annotations
   */
  OnStatus[] value();
}
