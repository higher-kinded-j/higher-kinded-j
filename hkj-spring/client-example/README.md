# Declarative HTTP Client Example

A standalone client application that calls the [Path Handlers Example](../example) server over HTTP
using a generated `@HkjHttpClient`, so a remote failure arrives as a **typed** `Left(ApiError)`
rather than a bare status code. This is the outbound counterpart to that server example: it
completes the loop the library is built around (the typed error survives a service-to-service call).

## What it shows

`UserClientApi` is a Path-typed `@HttpExchange` interface:

```java
@HttpExchange("/api/users")
@HkjHttpClient
public interface UserClientApi {

  @GetExchange("/{id}")
  EitherPath<ApiError, UserDto> getUser(@PathVariable String id);

  @PostExchange
  VTaskPath<Either<ApiError, UserDto>> create(@RequestBody NewUser body);
}
```

The `@HkjHttpClient` processor generates the native `@HttpExchange` interface, the
`UserClientApiClient` facade (registered as the `UserClientApi` bean), and the `@ImportHttpServices`
configuration. You autowire the **interface**; `UserClientRunner` calls it on startup and logs each
outcome:

- `getUser("1")` against the running server, a 2xx, becomes `Right(UserDto)`.
- `getUser("999")` returns the server's `{"success":false,"error":…}` 404 envelope, which decodes
  back into `Left(ApiError)` (the typed error preserved across the hop).
- `create(...)` is deferred on a virtual thread, so `withRetry`/`timeout` can be layered before
  running it.

The base URL is configuration, not code: `spring.http.serviceclient.userClientApi.base-url` in
[`application.yml`](src/main/resources/application.yml) points the client at the server.

## Running

Start the server first, then this client (it is a console application, no embedded web server):

```bash
./gradlew :hkj-spring:example:bootRun          # server on :8080
./gradlew :hkj-spring:client-example:bootRun   # this client calls it
```

Expected client log:

```
Calling the HkjSpringExampleApplication users service over HTTP...
  GET  /api/users/1   -> Right(UserDto): Alice Smith <alice@example.com>
  GET  /api/users/999 -> Left(ApiError): userId=999, message=null
  POST /api/users     -> Right(UserDto): Grace Hopper <grace@example.com>
```

If the server is not running, the client logs a single line telling you to start it.

## Related

- [Path Handlers Example](../example) — the server this client calls
- [HTTP Client Reference](../HTTP_CLIENT.md) — the full `@HkjHttpClient` reference
- [Declarative HTTP Clients](https://higher-kinded-j.github.io/spring/declarative_http_clients.html) — the narrative guide
