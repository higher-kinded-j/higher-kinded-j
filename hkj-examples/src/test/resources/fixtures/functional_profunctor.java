// Fixture for hkj-book/src/functional/profunctor.md
//
// The page adapts one validation function to three different input and output shapes. The domain
// and the profunctor instance the later snippets reuse are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.time.LocalDate;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;

record User(String name, String email, LocalDate birthDate) {}

record UserDto(String fullName, String emailAddress, String birthDateString) {}

record ValidationResult(boolean isValid, String message) {}

record ApiResponse<T>(T body, String status, int code) {}

class Fixture {

  static final FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;
}
