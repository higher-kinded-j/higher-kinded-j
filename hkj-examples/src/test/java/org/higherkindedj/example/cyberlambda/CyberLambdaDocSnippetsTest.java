// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.cyberlambda;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.vtask.VTaskMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compile-and-run fixture that verifies every Java snippet in {@code MONADS-IN-DISGUISE.md}
 * actually builds and produces the expected output.
 */
@DisplayName("MONADS-IN-DISGUISE.md snippets")
class CyberLambdaDocSnippetsTest {

  // ===== Section VI: Defining the World =====

  /** The Ambush: a failure that speaks its name. */
  public sealed interface Decepticon permits Decepticon.Megatron, Decepticon.Starscream {
    record Megatron(String reason) implements Decepticon {}

    record Starscream(String treachery) implements Decepticon {}
  }

  /** The Spark: something that might be missing. */
  public record Spark(int powerLevel) {}

  // ===== Section VI: The Transbot — EitherT =====

  static class ScoutTransbot {

    public EitherT<VTaskKind.Witness, Decepticon, Spark> findSpark(String coordinates) {

      VTask<Either<Decepticon, Spark>> task =
          VTask.of(
              () -> {
                if ("The Void".equals(coordinates)) {
                  return Either.left(new Decepticon.Megatron("Access Denied!"));
                }
                return Either.right(new Spark(100));
              });

      Kind<VTaskKind.Witness, Either<Decepticon, Spark>> kind = task;
      return EitherT.fromKind(kind);
    }
  }

  // ===== Section VI: The Commander — ReaderT =====

  /** The Matrix of Leadership (our configuration). */
  public record Matrix(String encryptionKey, int fleetSize) {}

  static class UltraMagnus {

    public ReaderT<VTaskKind.Witness, Matrix, String> executeOrder() {
      return ReaderT.of(
          matrix ->
              VTaskMonad.INSTANCE.of("Mission decrypted with key: " + matrix.encryptionKey()));
    }
  }

  // ===== Section VII: The Path API =====

  /**
   * The "before" picture — imperative wreckage with nested try/catch, null checks, and a mutable
   * accumulator. Models the same domain as {@link CyberLambdaEngine#deploy} but with classic
   * defensive Java instead of an Effect Path.
   */
  static class DecepticonSprawl {

    /** Searches a sector. May ambush with a runtime exception. */
    static Spark searchSector(String coord) {
      if ("The Void".equals(coord)) {
        throw new RuntimeException("Access Denied!");
      }
      if ("Kaon".equals(coord)) {
        throw new IllegalStateException("Betrayal: Starscream rerouted the beacon");
      }
      return new Spark(100);
    }

    public String deploy(String coord) {
      String result;
      try {
        if (coord == null) {
          // First Decepticon: the silent Void.
          result = "Defeat: No Coordinates";
        } else {
          Spark spark = null;
          try {
            spark = searchSector(coord);
          } catch (IllegalStateException starscream) {
            // Second Decepticon: the betrayer who looks like one thing and is another.
            return starscream.getMessage();
          } catch (RuntimeException megatron) {
            // Third Decepticon: the screaming Ambush.
            return "Defeat: " + megatron.getMessage();
          }
          if (spark == null) {
            // Fourth Decepticon: the Null Void slipping back in through the side door.
            result = "Defeat: Empty Sector";
          } else {
            result = "Victory! Power: " + spark.powerLevel();
          }
        }
      } catch (Exception unexpected) {
        // The "just in case" catch-all every veteran adds after one too many 3am pages.
        result = "Defeat: " + unexpected.getMessage();
      }
      return result;
    }
  }

  static class CyberLambdaEngine {

    public String deploy(String coord) {
      EitherPath<Decepticon, Spark> mission =
          Path.<String>maybe(coord)
              .<Decepticon>toEitherPath(new Decepticon.Megatron("No Coordinates"))
              .via(c -> Path.<Decepticon, Spark>right(searchSector(c)));

      Either<Decepticon, Spark> result = mission.run();

      return switch (result) {
        case Either.Right<Decepticon, Spark>(var spark) -> "Victory! Power: " + spark.powerLevel();
        case Either.Left<Decepticon, Spark>(Decepticon.Megatron m) -> "Defeat: " + m.reason();
        case Either.Left<Decepticon, Spark>(Decepticon.Starscream s) ->
            "Betrayal: " + s.treachery();
      };
    }

    private static Spark searchSector(String coord) {
      return new Spark(100);
    }

    public VTaskPath<Either<Decepticon, Spark>> findSparkAsync(String coord) {
      return Path.vtask(
          () -> {
            if ("The Void".equals(coord)) {
              return Either.<Decepticon, Spark>left(new Decepticon.Megatron("Access Denied!"));
            }
            return Either.<Decepticon, Spark>right(new Spark(100));
          });
    }
  }

  // ===== Tests =====

  @Test
  @DisplayName("Decepticon sealed hierarchy and Spark record exist as illustrated")
  void decepticonAndSparkTypesCompile() {
    Decepticon m = new Decepticon.Megatron("Division by Zero");
    Decepticon s = new Decepticon.Starscream("backstab");
    Spark spark = new Spark(42);

    assertThat(((Decepticon.Megatron) m).reason()).isEqualTo("Division by Zero");
    assertThat(((Decepticon.Starscream) s).treachery()).isEqualTo("backstab");
    assertThat(spark.powerLevel()).isEqualTo(42);
  }

  @Test
  @DisplayName("ScoutTransbot.findSpark builds an EitherT and unwraps a Right")
  void scoutTransbotReturnsRightSpark() {
    ScoutTransbot scout = new ScoutTransbot();
    EitherT<VTaskKind.Witness, Decepticon, Spark> et = scout.findSpark("Iacon");

    Kind<VTaskKind.Witness, Either<Decepticon, Spark>> inner = et.value();
    Either<Decepticon, Spark> result = ((VTask<Either<Decepticon, Spark>>) inner).run();

    assertThat(result.isRight()).isTrue();
    assertThat(((Either.Right<Decepticon, Spark>) result).value().powerLevel()).isEqualTo(100);
  }

  @Test
  @DisplayName("ScoutTransbot.findSpark unwraps a Left for The Void")
  void scoutTransbotReturnsLeftMegatron() {
    ScoutTransbot scout = new ScoutTransbot();
    EitherT<VTaskKind.Witness, Decepticon, Spark> et = scout.findSpark("The Void");

    Kind<VTaskKind.Witness, Either<Decepticon, Spark>> inner = et.value();
    Either<Decepticon, Spark> result = ((VTask<Either<Decepticon, Spark>>) inner).run();

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(Decepticon.Megatron.class);
  }

  @Test
  @DisplayName("UltraMagnus.executeOrder maps the Matrix into a decrypted message")
  void ultraMagnusReadsMatrix() {
    UltraMagnus magnus = new UltraMagnus();
    ReaderT<VTaskKind.Witness, Matrix, String> readerT = magnus.executeOrder();

    Kind<VTaskKind.Witness, String> kind = readerT.run().apply(new Matrix("AllSpark", 7));
    String message = ((VTask<String>) kind).run();

    assertThat(message).isEqualTo("Mission decrypted with key: AllSpark");
  }

  @Test
  @DisplayName("CyberLambdaEngine.deploy yields victory on a present coordinate")
  void deployVictory() {
    assertThat(new CyberLambdaEngine().deploy("Sector-7")).isEqualTo("Victory! Power: 100");
  }

  @Test
  @DisplayName("CyberLambdaEngine.deploy yields defeat on a null coordinate")
  void deployDefeat() {
    assertThat(new CyberLambdaEngine().deploy(null)).isEqualTo("Defeat: No Coordinates");
  }

  @Test
  @DisplayName("findSparkAsync returns a VTaskPath whose run yields Right(Spark)")
  void findSparkAsyncRight() {
    VTaskPath<Either<Decepticon, Spark>> path = new CyberLambdaEngine().findSparkAsync("Iacon");
    Either<Decepticon, Spark> result = path.run().run();
    assertThat(result.isRight()).isTrue();
    assertThat(((Either.Right<Decepticon, Spark>) result).value().powerLevel()).isEqualTo(100);
  }

  @Test
  @DisplayName("findSparkAsync returns a VTaskPath whose run yields Left(Megatron) for The Void")
  void findSparkAsyncLeft() {
    VTaskPath<Either<Decepticon, Spark>> path = new CyberLambdaEngine().findSparkAsync("The Void");
    Either<Decepticon, Spark> result = path.run().run();
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isInstanceOf(Decepticon.Megatron.class);
  }

  @Test
  @DisplayName("DecepticonSprawl handles the same cases the Path version does")
  void sprawlMatchesPathOnSharedCases() {
    DecepticonSprawl sprawl = new DecepticonSprawl();
    CyberLambdaEngine engine = new CyberLambdaEngine();

    assertThat(sprawl.deploy("Sector-7")).isEqualTo(engine.deploy("Sector-7"));
    assertThat(sprawl.deploy(null)).isEqualTo(engine.deploy(null));
  }

  @Test
  @DisplayName("DecepticonSprawl reproduces every error flavour through nested try/catch")
  void sprawlCoversEveryDecepticon() {
    DecepticonSprawl sprawl = new DecepticonSprawl();

    assertThat(sprawl.deploy(null)).isEqualTo("Defeat: No Coordinates");
    assertThat(sprawl.deploy("Sector-7")).isEqualTo("Victory! Power: 100");
    assertThat(sprawl.deploy("The Void")).isEqualTo("Defeat: Access Denied!");
    assertThat(sprawl.deploy("Kaon")).isEqualTo("Betrayal: Starscream rerouted the beacon");
  }

  @Test
  @DisplayName("Section VII headline methods (.via, .map, .toEitherPath, .run) all exist as named")
  void sectionSevenHeadlineMethodsAreReal() {
    Either<Decepticon, Integer> result =
        Path.<String>maybe("Sector-7")
            .<Decepticon>toEitherPath(new Decepticon.Megatron("No Coordinates"))
            .map(String::length)
            .via(n -> Path.<Decepticon, Integer>right(n * 2))
            .run();

    assertThat(result.isRight()).isTrue();
    assertThat(((Either.Right<Decepticon, Integer>) result).value()).isEqualTo(16);
  }
}
