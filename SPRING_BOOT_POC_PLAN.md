# Spring Boot Integration - Proof of Concept Plan

## Overview

This document outlines a concrete implementation plan for creating a working proof-of-concept Spring Boot starter for higher-kinded-j.

---

## POC Module Structure

```
higher-kinded-j/
├── hkj-api/
├── hkj-core/
├── hkj-annotations/
├── hkj-processor/
├── hkj-processor-plugins/
├── hkj-examples/
├── hkj-benchmarks/
└── hkj-spring/                              # NEW: Spring Boot integration
    ├── autoconfigure/
    │   ├── src/main/java/
    │   │   └── org/higherkindedj/spring/
    │   │       ├── autoconfigure/
    │   │       │   ├── HkjAutoConfiguration.java
    │   │       │   ├── HkjWebMvcAutoConfiguration.java
    │   │       │   ├── HkjJacksonAutoConfiguration.java
    │   │       │   └── HkjProperties.java
    │   │       ├── web/
    │   │       │   ├── returnvalue/
    │   │       │   │   ├── EitherReturnValueHandler.java
    │   │       │   │   ├── ValidatedReturnValueHandler.java
    │   │       │   │   └── EitherTReturnValueHandler.java
    │   │       │   ├── argumentresolver/
    │   │       │   │   └── EitherArgumentResolver.java
    │   │       │   └── exception/
    │   │       │       └── GlobalEitherExceptionHandler.java
    │   │       ├── json/
    │   │       │   ├── EitherSerializer.java
    │   │       │   ├── EitherDeserializer.java
    │   │       │   ├── ValidatedSerializer.java
    │   │       │   └── ValidatedDeserializer.java
    │   │       ├── validation/
    │   │       │   └── ValidatedValidator.java
    │   │       └── data/
    │   │           └── EitherRepositorySupport.java
    │   ├── src/main/resources/
    │   │   └── META-INF/spring/
    │   │       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │   └── build.gradle.kts
    │
    ├── starter/
    │   ├── src/main/resources/
    │   │   └── META-INF/
    │   │       └── spring.provides
    │   └── build.gradle.kts
    │
    ├── starter-web/                         # Optional: Web-specific features
    │   └── build.gradle.kts
    │
    ├── starter-data/                        # Optional: Data access features
    │   └── build.gradle.kts
    │
    └── example/
        ├── src/main/java/
        │   └── org/higherkindedj/spring/example/
        │       ├── HkjSpringExampleApplication.java
        │       ├── domain/
        │       │   ├── User.java
        │       │   ├── Order.java
        │       │   ├── OrderItem.java
        │       │   └── errors/
        │       │       ├── DomainError.java
        │       │       ├── UserNotFoundError.java
        │       │       └── ValidationError.java
        │       ├── controller/
        │       │   ├── UserController.java
        │       │   └── OrderController.java
        │       ├── service/
        │       │   ├── UserService.java
        │       │   └── OrderService.java
        │       └── repository/
        │           ├── UserRepository.java
        │           └── OrderRepository.java
        ├── src/main/resources/
        │   └── application.yml
        └── build.gradle.kts
```

**Gradle Configuration** (`settings.gradle.kts`):
```kotlin
rootProject.name = "higher-kinded-j"
include(
    "hkj-core",
    "hkj-processor",
    "hkj-examples",
    "hkj-annotations",
    "hkj-api",
    "hkj-processor-plugins",
    "hkj-benchmarks",
    // Spring Boot modules - organized under hkj-spring/
    "hkj-spring:autoconfigure",
    "hkj-spring:starter",
    "hkj-spring:starter-web",
    "hkj-spring:starter-data",
    "hkj-spring:example"
)
```

---

## Implementation Details

### 1. Core Auto-Configuration

**File:** `hkj-spring/autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/HkjAutoConfiguration.java`

```java
package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.hkt.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(Kind.class)
@EnableConfigurationProperties(HkjProperties.class)
public class HkjAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EitherInstances eitherInstances() {
        return EitherInstances.instances();
    }

    @Bean
    @ConditionalOnMissingBean
    public OptionalInstances optionalInstances() {
        return OptionalInstances.instances();
    }

    @Bean
    @ConditionalOnMissingBean
    public ListInstances listInstances() {
        return ListInstances.instances();
    }

    @Bean
    @ConditionalOnMissingBean
    public ValidatedInstances validatedInstances() {
        return ValidatedInstances.instances();
    }
}
```

### 2. Configuration Properties

**File:** `hkj-spring/autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/HkjProperties.java`

```java
package org.higherkindedj.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "hkj")
public class HkjProperties {

    private Web web = new Web();
    private Validation validation = new Validation();

    public Web getWeb() {
        return web;
    }

    public void setWeb(Web web) {
        this.web = web;
    }

    public Validation getValidation() {
        return validation;
    }

    public void setValidation(Validation validation) {
        this.validation = validation;
    }

    public static class Web {
        /**
         * Enable automatic Either to ResponseEntity conversion
         */
        private boolean eitherResponseEnabled = true;

        /**
         * Enable automatic Validated to ResponseEntity conversion
         */
        private boolean validatedResponseEnabled = true;

        /**
         * Default HTTP status for error responses
         */
        private int defaultErrorStatus = 400;

        /**
         * Whether to include stack traces in error responses
         */
        private boolean includeStackTrace = false;

        public boolean isEitherResponseEnabled() {
            return eitherResponseEnabled;
        }

        public void setEitherResponseEnabled(boolean eitherResponseEnabled) {
            this.eitherResponseEnabled = eitherResponseEnabled;
        }

        public boolean isValidatedResponseEnabled() {
            return validatedResponseEnabled;
        }

        public void setValidatedResponseEnabled(boolean validatedResponseEnabled) {
            this.validatedResponseEnabled = validatedResponseEnabled;
        }

        public int getDefaultErrorStatus() {
            return defaultErrorStatus;
        }

        public void setDefaultErrorStatus(int defaultErrorStatus) {
            this.defaultErrorStatus = defaultErrorStatus;
        }

        public boolean isIncludeStackTrace() {
            return includeStackTrace;
        }

        public void setIncludeStackTrace(boolean includeStackTrace) {
            this.includeStackTrace = includeStackTrace;
        }
    }

    public static class Validation {
        /**
         * Enable Validated-based validation
         */
        private boolean enabled = true;

        /**
         * Accumulate all validation errors instead of failing fast
         */
        private boolean accumulateErrors = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAccumulateErrors() {
            return accumulateErrors;
        }

        public void setAccumulateErrors(boolean accumulateErrors) {
            this.accumulateErrors = accumulateErrors;
        }
    }
}
```

### 3. Web MVC Auto-Configuration

**File:** `hkj-spring/autoconfigure/src/main/java/org/higherkindedj/spring/autoconfigure/HkjWebMvcAutoConfiguration.java`

```java
package org.higherkindedj.spring.autoconfigure;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.spring.web.returnvalue.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass({DispatcherServlet.class, Kind.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HkjWebMvcAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "hkj.web", name = "either-response-enabled", matchIfMissing = true)
    static class EitherReturnValueConfiguration implements WebMvcConfigurer {

        private final HkjProperties properties;

        EitherReturnValueConfiguration(HkjProperties properties) {
            this.properties = properties;
        }

        @Override
        public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
            handlers.add(new EitherReturnValueHandler(properties));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "hkj.web", name = "validated-response-enabled", matchIfMissing = true)
    static class ValidatedReturnValueConfiguration implements WebMvcConfigurer {

        @Override
        public void addReturnValueHandlers(List<HandlerMethodReturnValueHandler> handlers) {
            handlers.add(new ValidatedReturnValueHandler());
        }
    }
}
```

### 4. Either Return Value Handler Implementation

**File:** `hkj-spring/autoconfigure/src/main/java/org/higherkindedj/spring/web/returnvalue/EitherReturnValueHandler.java`

```java
package org.higherkindedj.spring.web.returnvalue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.higherkindedj.hkt.Either;
import org.higherkindedj.spring.autoconfigure.HkjProperties;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

public class EitherReturnValueHandler implements HandlerMethodReturnValueHandler {

    private final HkjProperties properties;
    private final ObjectMapper objectMapper;

    public EitherReturnValueHandler(HkjProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean supportsReturnType(MethodParameter returnType) {
        return Either.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public void handleReturnValue(
            Object returnValue,
            MethodParameter returnType,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest) throws IOException {

        mavContainer.setRequestHandled(true);
        HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);

        if (response == null) {
            return;
        }

        if (returnValue instanceof Either<?, ?> either) {
            either.fold(
                left -> {
                    handleError(left, response);
                    return null;
                },
                right -> {
                    handleSuccess(right, response);
                    return null;
                }
            );
        }
    }

    private void handleError(Object error, HttpServletResponse response) {
        try {
            int statusCode = determineStatusCode(error);
            response.setStatus(statusCode);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            Map<String, Object> errorBody = Map.of(
                "success", false,
                "error", error
            );

            objectMapper.writeValue(response.getWriter(), errorBody);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write error response", e);
        }
    }

    private void handleSuccess(Object value, HttpServletResponse response) {
        try {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write success response", e);
        }
    }

    private int determineStatusCode(Object error) {
        // Check for common error patterns
        String errorClassName = error.getClass().getSimpleName().toLowerCase();

        if (errorClassName.contains("notfound")) {
            return HttpStatus.NOT_FOUND.value();
        } else if (errorClassName.contains("validation") || errorClassName.contains("invalid")) {
            return HttpStatus.BAD_REQUEST.value();
        } else if (errorClassName.contains("authorization") || errorClassName.contains("forbidden")) {
            return HttpStatus.FORBIDDEN.value();
        } else if (errorClassName.contains("authentication") || errorClassName.contains("unauthorized")) {
            return HttpStatus.UNAUTHORIZED.value();
        }

        return properties.getWeb().getDefaultErrorStatus();
    }
}
```

### 5. Example Domain Models

**File:** `hkj-spring/example/src/main/java/org/higherkindedj/spring/example/domain/User.java`

```java
package org.higherkindedj.spring.example.domain;

import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
public record User(
    String id,
    String email,
    String firstName,
    String lastName,
    UserStatus status
) {}
```

**File:** `hkj-spring/example/src/main/java/org/higherkindedj/spring/example/domain/errors/DomainError.java`

```java
package org.higherkindedj.spring.example.domain.errors;

public sealed interface DomainError
    permits UserNotFoundError, ValidationError, DatabaseError {
    String message();
}
```

**File:** `hkj-spring/example/src/main/java/org/higherkindedj/spring/example/domain/errors/UserNotFoundError.java`

```java
package org.higherkindedj.spring.example.domain.errors;

public record UserNotFoundError(String userId) implements DomainError {
    @Override
    public String message() {
        return "User not found: " + userId;
    }
}
```

### 6. Example Controller

**File:** `hkj-spring/example/src/main/java/org/higherkindedj/spring/example/controller/UserController.java`

```java
package org.higherkindedj.spring.example.controller;

import org.higherkindedj.hkt.Either;
import org.higherkindedj.hkt.Validated;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.errors.DomainError;
import org.higherkindedj.spring.example.domain.errors.ValidationError;
import org.higherkindedj.spring.example.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Get user by ID - demonstrates Either return type
     * Success (Right) -> 200 with user data
     * Error (Left) -> 404 with error details
     */
    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
    }

    /**
     * Get all users - demonstrates Either with collections
     */
    @GetMapping
    public Either<DomainError, List<User>> getAllUsers() {
        return userService.findAll();
    }

    /**
     * Create user - demonstrates Validated for accumulating validation errors
     * All validation errors returned at once for better UX
     */
    @PostMapping
    public Validated<List<ValidationError>, User> createUser(
            @RequestBody CreateUserRequest request) {
        return userService.validateAndCreate(request);
    }

    /**
     * Update user - demonstrates optics for clean nested updates
     */
    @PutMapping("/{id}")
    public Either<DomainError, User> updateUser(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    /**
     * Demonstrates error recovery with Either.orElse
     */
    @GetMapping("/{id}/with-fallback")
    public User getUserWithFallback(@PathVariable String id) {
        return userService.findById(id)
            .getOrElse(() -> User.defaultUser());
    }
}
```

### 7. Example Service with Optics

**File:** `hkj-spring/example/src/main/java/org/higherkindedj/spring/example/service/UserService.java`

```java
package org.higherkindedj.spring.example.service;

import org.higherkindedj.hkt.*;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.spring.example.domain.User;
import org.higherkindedj.spring.example.domain.UserLenses;
import org.higherkindedj.spring.example.domain.errors.*;
import org.higherkindedj.spring.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    // Optics for clean updates
    private static final Lens<User, String> userToEmail = UserLenses.email();
    private static final Lens<User, String> userToFirstName = UserLenses.firstName();
    private static final Lens<User, String> userToLastName = UserLenses.lastName();

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Either<DomainError, User> findById(String id) {
        return userRepository.findById(id);
    }

    public Either<DomainError, List<User>> findAll() {
        return userRepository.findAll();
    }

    /**
     * Demonstrates Validated for accumulating validation errors
     */
    public Validated<List<ValidationError>, User> validateAndCreate(CreateUserRequest request) {
        Applicative<Validated.Witness<List<ValidationError>>> A =
            ValidatedInstances.applicative(Semigroup.list());

        // All validators run, errors accumulated
        Validated<List<ValidationError>, String> validEmail = validateEmail(request.email());
        Validated<List<ValidationError>, String> validFirstName = validateName(request.firstName());
        Validated<List<ValidationError>, String> validLastName = validateName(request.lastName());

        return Applicative.map3(
            A,
            validEmail,
            validFirstName,
            validLastName,
            (email, firstName, lastName) ->
                new User(generateId(), email, firstName, lastName, UserStatus.ACTIVE)
        );
    }

    /**
     * Demonstrates optics for clean updates
     */
    public Either<DomainError, User> update(String id, UpdateUserRequest request) {
        return userRepository.findById(id)
            .map(user -> {
                // Clean, composable updates using optics
                User updated = user;
                if (request.email() != null) {
                    updated = userToEmail.set(request.email(), updated);
                }
                if (request.firstName() != null) {
                    updated = userToFirstName.set(request.firstName(), updated);
                }
                if (request.lastName() != null) {
                    updated = userToLastName.set(request.lastName(), updated);
                }
                return updated;
            })
            .flatMap(userRepository::save);
    }

    private Validated<List<ValidationError>, String> validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            return Validated.invalid(List.of(
                new ValidationError("email", "Invalid email format")
            ));
        }
        return Validated.valid(email);
    }

    private Validated<List<ValidationError>, String> validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Validated.invalid(List.of(
                new ValidationError("name", "Name cannot be empty")
            ));
        }
        return Validated.valid(name);
    }

    private String generateId() {
        return java.util.UUID.randomUUID().toString();
    }
}
```

### 8. Build Configuration

**File:** `hkj-spring/autoconfigure/build.gradle.kts`

```kotlin
plugins {
    id("java-library")
    id("org.springframework.boot") version "3.5.7" apply false
}

dependencies {
    // Higher-Kinded-J
    api("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    api("io.github.higher-kinded-j:hkj-api:LATEST_VERSION")

    // Spring Boot
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.7"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    // Spring Web
    compileOnly("org.springframework:spring-web")
    compileOnly("org.springframework:spring-webmvc")
    compileOnly("jakarta.servlet:jakarta.servlet-api")

    // Jackson
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
```

**File:** `hkj-spring/starter/build.gradle.kts`

```kotlin
plugins {
    id("java-library")
}

dependencies {
    // Aggregate all autoconfigure modules
    api(project(":hkj-spring:autoconfigure"))

    // Bring in Spring Boot starter web by default
    api("org.springframework.boot:spring-boot-starter-web")

    // Annotation processor
    annotationProcessor("io.github.higher-kinded-j:hkj-processor:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}
```

### 9. Auto-Configuration Registration

**File:** `hkj-spring/autoconfigure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
org.higherkindedj.spring.autoconfigure.HkjAutoConfiguration
org.higherkindedj.spring.autoconfigure.HkjWebMvcAutoConfiguration
```

### 10. Example Application Configuration

**File:** `hkj-spring/example/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: hkj-spring-example

hkj:
  web:
    either-response-enabled: true
    validated-response-enabled: true
    default-error-status: 400
    include-stack-trace: false
  validation:
    enabled: true
    accumulate-errors: true

logging:
  level:
    org.higherkindedj: DEBUG
```

---

## Testing Strategy

### Unit Tests
- Test each return value handler independently
- Test optics generation
- Test error mapping logic

### Integration Tests
- Full Spring Boot context tests
- Test actual HTTP requests/responses
- Verify JSON serialization

### Example Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturn200ForExistingUser() throws Exception {
        mockMvc.perform(get("/api/users/123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"));
    }

    @Test
    void shouldReturn404ForMissingUser() throws Exception {
        mockMvc.perform(get("/api/users/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    void shouldReturnAllValidationErrors() throws Exception {
        String invalidRequest = """
            {
                "email": "invalid",
                "firstName": "",
                "lastName": ""
            }
            """;

        mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()").value(3)); // All errors!
    }
}
```

---

## Deployment Plan

### Phase 1: Local Development (Week 1-2)
- Set up module structure
- Implement core auto-configuration
- Create basic example application
- Local testing

### Phase 2: Feature Complete (Week 3-4)
- Implement all return value handlers
- Jackson integration
- Validation support
- Comprehensive tests

### Phase 3: Documentation (Week 5)
- API documentation
- Getting started guide
- Example applications
- Migration guide

### Phase 4: Community Feedback (Week 6+)
- Alpha release
- Gather feedback
- Iterate on design
- Performance testing

---

## Success Criteria

- [ ] Spring Boot application starts with zero configuration
- [ ] Either/Validated return types work automatically
- [ ] JSON serialization works correctly
- [ ] HTTP status codes map appropriately
- [ ] Optics integrate seamlessly
- [ ] Documentation is clear and comprehensive
- [ ] Performance overhead < 5%
- [ ] All tests pass
- [ ] Example application demonstrates all features

---

## Next Steps

1. **Create module structure** in higher-kinded-j repository
2. **Implement core auto-configuration** classes
3. **Build example application** demonstrating features
4. **Write comprehensive tests**
5. **Document everything**
6. **Release alpha version** for community feedback
