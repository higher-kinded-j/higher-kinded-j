// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

public interface Validation {
  static CoreTypeValidator coreType() {
    return CoreTypeValidator.CORE_TYPE_VALIDATOR;
  }

  static FunctionValidator function() {
    return FunctionValidator.FUNCTION_VALIDATOR;
  }

  static KindValidator kind() {
    return KindValidator.KIND_VALIDATOR;
  }

  static TransformerValidator transformer() {
    return TransformerValidator.TRANSFORMER_VALIDATOR;
  }
}
