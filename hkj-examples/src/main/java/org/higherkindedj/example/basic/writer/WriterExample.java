// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.writer;

import module java.base;
import module org.higherkindedj.core;

import static org.higherkindedj.hkt.writer.WriterKindHelper.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Unit;
import org.jspecify.annotations.NonNull;

/** see {<a href="https://higher-kinded-j.github.io/writer_monad.html">Writer Monad</a>} */
public class WriterExample {

  Monoid<String> stringMonoid = new StringMonoid();

  WriterMonad<String> writerMonad = new WriterMonad<>(stringMonoid);

  Kind<WriterKind.Witness<String>, Integer> initialValue = WRITER.value(stringMonoid, 5);

  Kind<WriterKind.Witness<String>, Unit> logStart = WRITER.tell("Starting calculation; ");

  Function<Integer, Kind<WriterKind.Witness<String>, Integer>> addAndLog =
      x -> {
        int result = x + 10;
        String logMsg = "Added 10 to " + x + " -> " + result + "; ";
        // Assuming direct creation with log and value if Writer.create was meant for that
        return WRITER.widen(new Writer<>(logMsg, result));
      };

  Function<Integer, Kind<WriterKind.Witness<String>, String>> multiplyAndLogToString =
      x -> {
        int result = x * 2;
        String logMsg = "Multiplied " + x + " by 2 -> " + result + "; ";
        return WRITER.widen(new Writer<>(logMsg, "Final:" + result));
      };

  /** Java 25 instance main method - no static modifier or String[] args required. */
  void main() {
    runWriterExample();
  }

  public void runWriterExample() {
    Kind<WriterKind.Witness<String>, Integer> computationStart = writerMonad.of(0);

    Kind<WriterKind.Witness<String>, Integer> afterLogStart =
        writerMonad.flatMap(ignoredUnit -> initialValue, logStart);

    // step1Log will be Kind<..., Unit>
    Kind<WriterKind.Witness<String>, Unit> step1Log = WRITER.tell("Initial value set to 5; ");

    Kind<WriterKind.Witness<String>, Integer> calcPart1 =
        writerMonad.flatMap(ignoredUnit -> addAndLog.apply(5), WRITER.tell("Starting with 5; "));

    Kind<WriterKind.Witness<String>, String> finalComputation =
        writerMonad.flatMap(
            intermediateValue -> multiplyAndLogToString.apply(intermediateValue), calcPart1);

    Kind<WriterKind.Witness<String>, Integer> initialValForMap = WRITER.value(stringMonoid, 100);
    Kind<WriterKind.Witness<String>, String> mappedVal =
        writerMonad.map(i -> "Value is " + i, initialValForMap);

    Writer<String, String> finalResultWriter = WRITER.runWriter(finalComputation);
    String finalLog = finalResultWriter.log();
    String finalValue = finalResultWriter.value();

    System.out.println("Final Log: " + finalLog);
    System.out.println("Final Value: " + finalValue);

    String justValue = WRITER.run(finalComputation);
    String justLog = WRITER.exec(finalComputation);

    System.out.println("Just Value: " + justValue);
    System.out.println("Just Log: " + justLog);

    Writer<String, String> mappedResult = WRITER.runWriter(mappedVal);
    System.out.println("Mapped Log: " + mappedResult.log());
    System.out.println("Mapped Value: " + mappedResult.value());
  }
}

class StringMonoid implements Monoid<String> {
  @Override
  public @NonNull String empty() {
    return "";
  }

  @Override
  public @NonNull String combine(@NonNull String x, @NonNull String y) {
    return x + y;
  }
}
