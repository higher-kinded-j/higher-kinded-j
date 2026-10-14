// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * How protobuf-java reads and writes a repeated or a map field whole: the message's getter, {@code
 * getTagsList()} or {@code getPointsMap()}, and its builder's method that adds a whole collection
 * to what the builder holds, {@code addAllTags(Iterable)} or {@code putAllPoints(Map)}, beside the
 * {@code clearTags()} or {@code clearPoints()} protobuf writes for every field. A map field's
 * {@code getPoints()} is deprecated, and a repeated field's {@code getTags(int)} reads one element.
 *
 * @since 0.5.0
 */
public enum ProtobufCollection {

  /**
   * A repeated field, read by {@code getTagsList()} and added to by {@code addAllTags(Iterable)}.
   */
  REPEATED("addAll", "java.lang.Iterable", "List", "java.util.List"),

  /** A map field, read by {@code getPointsMap()} and added to by {@code putAllPoints(Map)}. */
  MAP("putAll", "java.util.Map", "Map", "java.util.Map");

  /**
   * The type every protobuf-java message implements, on the full and lite runtimes alike, whose
   * repeated and map fields these are.
   */
  public static final String MESSAGE = "com.google.protobuf.MessageLite";

  /** The type every protobuf-java message's builder implements, by its canonical name. */
  public static final String MESSAGE_BUILDER = "com.google.protobuf.MessageLite.Builder";

  private final String adderPrefix;
  private final String adderParameter;
  private final String getterSuffix;
  private final String getterType;

  ProtobufCollection(
      String adderPrefix, String adderParameter, String getterSuffix, String getterType) {
    this.adderPrefix = adderPrefix;
    this.adderParameter = adderParameter;
    this.getterSuffix = getterSuffix;
    this.getterType = getterType;
  }

  /**
   * The prefix protobuf spells the builder method that adds a whole collection with.
   *
   * @return {@code addAll} or {@code putAll}
   */
  public String adderPrefix() {
    return adderPrefix;
  }

  /**
   * Whether a builder method's parameter takes a whole collection of this kind, read through its
   * erasure: an {@code Iterable} or a {@code Map}, not the single element another field's adder
   * takes, as {@code addAllTags(String)} does for a repeated field named {@code all_tags}.
   *
   * @param types the round's type utilities; must not be null
   * @param elements the round's element utilities; must not be null
   * @param parameter the parameter's type; must not be null
   * @return true when it takes a whole collection of this kind
   */
  public boolean takesWhole(Types types, Elements elements, TypeMirror parameter) {
    return types.isSubtype(
        types.erasure(parameter), types.erasure(elements.getTypeElement(adderParameter).asType()));
  }

  /**
   * The suffix of the message's getter that reads the whole field.
   *
   * @return {@code List} or {@code Map}
   */
  public String getterSuffix() {
    return getterSuffix;
  }

  /**
   * The canonical name of the container that getter answers.
   *
   * @return {@code java.util.List} or {@code java.util.Map}
   */
  public String getterType() {
    return getterType;
  }
}
