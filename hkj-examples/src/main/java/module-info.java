/**
 * Contains example usage of the Higher-Kinded-J library. This module is not intended for use as a
 * library dependency.
 */
@org.jspecify.annotations.NullMarked
module org.higherkindedj.examples {
  // Depends on the main library to use its features
  requires org.higherkindedj.core;
  requires org.higherkindedj.annotations;
  requires java.compiler;

  // External libraries for spec interface examples
  requires tools.jackson.databind;
  requires org.jooq;

  // openapi-generator's JsonNullable, for the mapping chapter's proofs about its models
  requires org.openapitools.jackson.nullable;

  // Eclipse Collections for cross-ecosystem portfolio risk example
  requires org.eclipse.collections.api;
  requires org.eclipse.collections.impl;

  // PCollections for the persistent-collections HKT compatibility example
  requires org.pcollections;

  // protobuf-java for the mapping chapter's protobuf example
  requires com.google.protobuf;

  // Export spec interface examples for external types
  exports org.higherkindedj.example.optics.external;

  // The messages protoc generates, which protobuf-java reads through reflection
  exports org.higherkindedj.example.book.mapping.proto to
      com.google.protobuf;

  // Export Order Workflow packages for testing
  exports org.higherkindedj.example.order.audit;
  exports org.higherkindedj.example.order.config;
  exports org.higherkindedj.example.order.error;
  exports org.higherkindedj.example.order.model;
  exports org.higherkindedj.example.order.model.value;
  exports org.higherkindedj.example.order.runner;
  exports org.higherkindedj.example.order.service;
  exports org.higherkindedj.example.order.service.impl;
  exports org.higherkindedj.example.order.workflow;

  // Export Payment Processing packages for testing
  exports org.higherkindedj.example.payment.model;
  exports org.higherkindedj.example.payment.effect;
  exports org.higherkindedj.example.payment.interpreter;
  exports org.higherkindedj.example.payment.service;
}
