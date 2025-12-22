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

  // Export Order Workflow packages for testing
  exports org.higherkindedj.example.order.audit;
  exports org.higherkindedj.example.order.config;
  exports org.higherkindedj.example.order.error;
  exports org.higherkindedj.example.order.model;
  exports org.higherkindedj.example.order.model.value;
  exports org.higherkindedj.example.order.resilience;
  exports org.higherkindedj.example.order.runner;
  exports org.higherkindedj.example.order.service;
  exports org.higherkindedj.example.order.service.impl;
  exports org.higherkindedj.example.order.workflow;
}
