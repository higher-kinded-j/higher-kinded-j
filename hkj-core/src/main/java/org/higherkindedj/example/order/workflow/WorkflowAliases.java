// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.order.workflow;

import org.higherkindedj.alias.GenerateHKTAlias;
import org.higherkindedj.alias.GenerateHKTAliases;
import org.higherkindedj.example.order.error.DomainError;
import org.higherkindedj.example.order.model.WorkflowModels;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.trans.either_t.EitherTKind;

@GenerateHKTAliases({
  @GenerateHKTAlias(
      name = "TestStringAlias",
      targetPackage = "org.higherkindedj.example.order.workflow.aliases",
      baseInterface = Kind.class, // Assuming Kind.class is stable
      hktWitness = String.class, // Not a real witness, just for testing class loading
      genericParameters = {
        "A"
      } // This would make it Kind<String, A> - a bit nonsensical but tests class refs
      ),
  @GenerateHKTAlias(
      name = "FutureMonadErr",
      targetPackage = "org.higherkindedj.example.order.workflow.aliases",
      baseInterface = MonadError.class,
      hktWitness = CompletableFutureKind.Witness.class,
      errorType = Throwable.class),
  @GenerateHKTAlias(
      name = "WorkflowMonadErr",
      targetPackage = "org.higherkindedj.example.order.workflow.aliases",
      baseInterface = MonadError.class,
      hktWitness = EitherTKind.Witness.class,
      f_hktWitness = CompletableFutureKind.Witness.class,
      errorType = DomainError.class),
  @GenerateHKTAlias(
      name = "WorkflowTask",
      targetPackage = "org.higherkindedj.example.order.workflow.aliases",
      baseInterface = Kind.class,
      hktWitness = EitherTKind.Witness.class,
      f_hktWitness = CompletableFutureKind.Witness.class,
      errorType = DomainError.class, // This error type is part of EitherT's witness structure
      genericParameters = {"Val"} // Results in WorkflowTask<Val> where Val is the Kind's value
      ),
  @GenerateHKTAlias(
      name = "FinalWorkflowResult",
      targetPackage = "org.higherkindedj.example.order.workflow.aliases",
      baseInterface = Kind.class,
      hktWitness = CompletableFutureKind.Witness.class,
      valueType = Either.class,
      valueTypeArgs = {DomainError.class, WorkflowModels.FinalResult.class})
})
public interface WorkflowAliases {}
