// Fixture for hkj-book/src/optics/iso.md
//
// The page converts a Point to a tuple, cents to dollars and a date to a string, and weaves the
// last two into comprehensions. The domain and the instances those need are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateIsos;
import org.higherkindedj.optics.annotations.GenerateLenses;

record Point(int x, int y) {}

@GenerateLenses
record Person(String name, LocalDate birthDate) {}

record Celsius(double value) {}

record Fahrenheit(double value) {}

@GenerateLenses
record Department(String name, int budget) {}

class Converters {

  static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
    return Iso.of(
        point -> Tuple.of(point.x(), point.y()), tuple -> new Point(tuple._1(), tuple._2()));
  }
}

class Fixture {

  static final Point point = new Point(10, 20);

  static final Point myPoint = point;

  static final Iso<Point, Tuple2<Integer, Integer>> pointToTupleIso = Converters.pointToTuple();

  static final Lens<Tuple2<Integer, Integer>, Integer> tupleFirstElementLens =
      Lens.of(Tuple2::_1, (t, v) -> Tuple.of(v, t._2()));

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final MonadZero<ListKind.Witness> listMonad = Instances.monadZero(list());

  static final List<Celsius> temperatures = List.of(new Celsius(20), new Celsius(30));

  static final Iso<Celsius, Fahrenheit> celsiusToFahrenheitIso =
      Iso.of(
          c -> new Fahrenheit(c.value() * 9 / 5 + 32),
          f -> new Celsius((f.value() - 32) * 5 / 9));

  static final Iso<Integer, Double> centsToDollars =
      Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

  static final Department department = new Department("Sales", 70000);

  static final Lens<Department, Integer> budgetLens = DepartmentLenses.budget();
}
