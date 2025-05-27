// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterMonad;

/** see {<a href="https://higher-kinded-j.github.io/writer_monad.html">Writer Monad</a>} */
public class WriterExample {

  Monoid<String> stringMonoid = new StringMonoid();

  // Monad instance for computations logging Strings
  // F_WITNESS here is WriterKind.Witness<String>
  WriterMonad<String> writerMonad = new WriterMonad<>(stringMonoid);

  // Writer with an initial value and empty log
  Kind<WriterKind.Witness<String>, Integer> initialValue =
      value(stringMonoid, 5); // Log: "", Value: 5

  // Writer that just logs a message (value is Void/null)
  Kind<WriterKind.Witness<String>, Void> logStart =
      tell(stringMonoid, "Starting calculation; "); // Log: "Starting calculation; ", Value: null

  // A function that performs a calculation and logs its step
  Function<Integer, Kind<WriterKind.Witness<String>, Integer>> addAndLog =
      x -> {
        int result = x + 10;
        String logMsg = "Added 10 to " + x + " -> " + result + "; ";
        // Create a Writer directly then wrap with helper or use helper factory
        return wrap(Writer.create(logMsg, result));
      };

  Function<Integer, Kind<WriterKind.Witness<String>, String>> multiplyAndLogToString =
      x -> {
        int result = x * 2;
        String logMsg = "Multiplied " + x + " by 2 -> " + result + "; ";
        return wrap(Writer.create(logMsg, "Final:" + result));
      };

  public static void main(String[] args) {
    new WriterExample().runWriterExample();
  }

  public void runWriterExample() {
    // Chain the operations:
    // Start with a pure value 0 in the Writer context (empty log)
    Kind<WriterKind.Witness<String>, Integer> computationStart = writerMonad.of(0);

    // 1. Log the start
    Kind<WriterKind.Witness<String>, Integer> afterLogStart =
        writerMonad.flatMap(ignoredVoid -> initialValue, logStart);

    Kind<WriterKind.Witness<String>, Integer> step1Value = value(stringMonoid, 5); // ("", 5)
    Kind<WriterKind.Witness<String>, Void> step1Log =
        tell(stringMonoid, "Initial value set to 5; "); // ("Initial value set to 5; ", null)

    // Start -> log -> transform value -> log -> transform value ...
    Kind<WriterKind.Witness<String>, Integer> calcPart1 =
        writerMonad.flatMap(
            ignored -> addAndLog.apply(5), // Apply addAndLog to 5, after logging "start"
            tell(stringMonoid, "Starting with 5; "));

    // calcPart1: Log: "Starting with 5; Added 10 to 5 -> 15; ", Value: 15
    Kind<WriterKind.Witness<String>, String> finalComputation =
        writerMonad.flatMap(
            intermediateValue -> multiplyAndLogToString.apply(intermediateValue), calcPart1);
    // finalComputation: Log: "Starting with 5; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30; ",
    // Value: "Final:30"

    // Using map: Only transforms the value, log remains unchanged from the input Kind
    Kind<WriterKind.Witness<String>, Integer> initialValForMap =
        value(stringMonoid, 100); // Log: "", Value: 100
    Kind<WriterKind.Witness<String>, String> mappedVal =
        writerMonad.map(i -> "Value is " + i, initialValForMap); // Log: "", Value: "Value is 100"

    // Get the final Writer record (log and value)
    Writer<String, String> finalResultWriter = runWriter(finalComputation);
    String finalLog = finalResultWriter.log();
    String finalValue = finalResultWriter.value();

    System.out.println("Final Log: " + finalLog);
    // Output: Final Log: Starting with 5; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30;
    System.out.println("Final Value: " + finalValue);
    // Output: Final Value: Final:30

    // Or get only the value or log
    String justValue = run(finalComputation); // Extracts value from finalResultWriter
    String justLog = exec(finalComputation); // Extracts log from finalResultWriter

    System.out.println("Just Value: " + justValue); // Output: Just Value: Final:30
    System.out.println(
        "Just Log: "
            + justLog); // Output: Just Log: Starting with 5; Added 10 to 5 -> 15; Multiplied 15 by
    // 2 -> 30;

    Writer<String, String> mappedResult = runWriter(mappedVal);
    System.out.println("Mapped Log: " + mappedResult.log()); // Output: Mapped Log
    System.out.println(
        "Mapped Value: " + mappedResult.value()); // Output: Mapped Value: Value is 100
  }
}

class StringMonoid implements Monoid<String> {
  @Override
  public String empty() {
    return "";
  }

  @Override
  public String combine(String x, String y) {
    return x + y;
  }
}
