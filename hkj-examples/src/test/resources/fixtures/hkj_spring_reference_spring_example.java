// Fixture for .claude/skills/hkj-spring/reference/spring-example.md
//
// The page walks through the hkj-spring/example module, so what it elides is that module's own
// domain: the repository behind the service, and the request record the controller binds.
//
// `UserRepository.findById` returns an Optional, which is what a Spring Data repository returns. The
// page must therefore reach for `Path.optional(...)`, NOT `Path.maybe(...)`: Path.maybe takes a
// nullable value, so handed an Optional it binds A = Optional<User> and a miss becomes
// Just(Optional.empty()), which is a present value carrying an absent one. That compiles and is
// silently wrong, so the repository is typed here to make the page prove it uses the right one.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures.

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.ValidationPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.validated.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record User(String id, String name, String email) {}

record CreateUserRequest(String name, String email) {}

/** The reader's own error hierarchy; the page's Domain Model snippet declares its own. */
sealed interface DomainError {
  record UserNotFound(String id) implements DomainError {}

  record EmailAlreadyTaken(String email) implements DomainError {}
}

/** The reader's own repository, shaped like a Spring Data one: a miss is Optional.empty(). */
interface UserRepository {
  Optional<User> findById(String id);

  User save(User user);
}

/** The service the controller snippet delegates to; the Service Layer snippet declares its own. */
interface UserService {
  Either<DomainError, User> findById(String id);

  Validated<List<String>, User> validateAndCreate(String name, String email);
}

class Fixture {

  static final UserRepository repository = null;
  static final UserService userService = null;
}
