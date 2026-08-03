# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`plinth` (`yi.shi:plinth`, version `jre-21`) is a lightweight, single-binary web framework built on **Guice 7 + Jetty 12 (ee10)** — deliberately **not** Spring Boot. It is intended for quickly standing up lightweight monolithic web apps. It is not published to Maven Central; consumers `mvn install` it locally. The repo doubles as the framework source and a runnable demo (`yi.shi.plinth.App` + `demo.HelloWord`).

- **Java 21** is required (`maven.compiler.source/target = 21`).
- The framework itself is request-dispatch + DI + Jetty bootstrapping. DB access is built in via `DataSourceModule` (MyBatis-Guice); register it with `ServiceBooter.startFrom(...)` to enable `@Mapper` scanning.

## Common commands

```bash
mvn clean install          # build + install to local Maven repo (required for consumers)
mvn compile                # compile only
mvn test                   # run JUnit Jupiter tests
mvn test -Dtest=AppTest    # run a single test class
mvn exec:java -Dexec.mainClass=yi.shi.plinth.App   # run the demo app (or run App.main() in an IDE)
```

Notes:
- Tests: `AppTest` is a placeholder; `DataSourceModuleTest` and `UserModuleTest` give real H2-backed coverage of the DB and user modules.
- The fat-jar / shade / assembly `<build>` plugins shown in `README.md` are **not** present in `pom.xml`. `mvn package` produces a plain jar; copy the build plugin block from the README if you need a runnable fat jar.
- Config is loaded into `System.getProperties()`, so JVM `-D` flags override file values at runtime.

## Architecture

### Startup flow
`App.main()` → `ServiceBooter.startFrom(mainClass, modules...)`:
1. Static block registers `JettyModule`; any user `Module`s passed to `startFrom` are also registered with `ModuleRegister`.
2. `@PropertiesFile` on the main class names config files (classpath paths **or** `http(s):` URLs) loaded by `CoreProperties` into `System.getProperties()`.
3. `IocModule.registScanPackage(mainClass)` scans the main class's package for `@HttpService` classes.
4. `ModuleRegister.getInjector()` builds the Guice injector (`Stage.DEVELOPMENT`) from all registered modules, resolves a `JettyBootService`, and starts the Jetty `Server`.

`JettyModule` wires a `ServletContextHandler` at `/` mapped to `DispatcherServlet` + Guice's `GuiceFilter`, plus a separate static-file `ResourceHandler` context. `GuiceServletCustomContextListener` is registered as a servlet listener.

### Request dispatch flow
`DispatcherServlet.service()` → `ServletHelper.init()` (stores req/resp in a `ThreadLocal`) → initializes SA-Token context/DAO → dispatches by HTTP verb to `RestApiServiceImpl.doGet/doPost/...`.

`RestApiServiceImpl` is constructed once (via `DispatcherServlet.initRestApiService()`, triggered from `IocModule`'s constructor). At construction it reflects over all `@HttpService` classes and builds **per-HTTP-verb maps** keyed by `@HttpPath` value, plus parameter (`@HttpParam`) and request-body (`@HttpBody`) metadata. Duplicate paths throw `ReduplicativeMathodPathException`.

Per request, `invoke()`:
1. Matches `requestURI − contextPath` against the verb's path map (404 if no match).
2. Runs `@AUTH` checks via SA-Token (login + `andRole`/`orRole`).
3. **Instantiates a fresh controller instance by reflection** (`ReflectionUtils.newInstance` — requires a no-arg constructor) and **manually field-injects** `@Inject` (Guice or `jakarta.inject.Inject`) and `@Properties` fields. Controllers are **per-request, not Guice-managed singletons** — no constructor injection or AOP applies to them.
4. Binds `@HttpParam` (query params) / `@HttpBody` (JSON body deserialized via Jackson) and invokes the method.
5. `HttpRespHelper` serializes the return: a `ReturnType` (`JSON<T>`, `HTML`, `BINARY`) is honored; any other object is auto-wrapped in `JSON` and serialized to JSON.

### Two Guice injectors (important)
There are **two independent injectors** — do not assume one:
- `ModuleRegister.getInjector()` — built from `JettyModule` + user modules. Used for Jetty boot and for **controller field injection** inside `RestApiServiceImpl`.
- `GuiceServletCustomContextListener.getInjector()` — built from `ServletModule` (which installs `IocModule`); used by `GuiceFilter` for request/servlet scoping. Constructing `IocModule` (a static field of `ServletModule`) is what triggers the controller scan and `DispatcherServlet.initRestApiService()`.

### Defining an endpoint
- `@HttpService` on the class.
- `@GET`/`@POST`/`@PUT`/`@DELETE`/`@HEAD`/`@OPTIONS` + `@HttpPath("/path")` on the method. (Multiple method annotations can stack on one method.)
- Parameters: `@HttpParam("name")` for query params, `@HttpBody` for a single JSON request body (only one `@HttpBody` per method).
- `@AUTH(orRole=, andRole=, authUrl=)` for SA-Token-gated access; `authUrl` redirects on auth failure, otherwise 401.

### Configuration
All config lives in `System.getProperties()` (loaded by `CoreProperties`; supports classpath files and remote `http(s)` URLs). It is bound into Guice via `Names.bindProperties` in `IocModule.configure()` and is also resolvable through the custom `@Properties("key", defaultValue=...)` field annotation (String only). Property names actually read by the code (see `application.properties`):

| Property | Used by | Default |
|---|---|---|
| `server.port` | `JettyModule` | `8080` |
| `resources.folder` | `JettyModule` static handler | — |
| `resources.contextPath` | `JettyModule` static handler | `/resources` |
| `redis.host` / `redis.port` / `redis.password` | `RedisUtil` | `6379` / `""` |
| `token.expire` | `ServiceBooter` (SA-Token init) | `86400` |
| `mybatis.mapper.scan` | `DataSourceModule` (comma-separated mapper packages) | `yi.shi.plinth.db.mapper` |
| `JDBC.driver` / `JDBC.url` / `JDBC.username` / `JDBC.password` | `PooledDataSourceProvider` (mybatis-guice, via `DataSourceModule`) | - |
| `mybatis.environment.id` | `EnvironmentProvider` (mybatis-guice, via `DataSourceModule`) | - |

> Note: `README.md` references `server.resources.context` / `server.resources.folder` / `server.hybrid`, but the current `JettyModule` reads `resources.folder` / `resources.contextPath` and has no hybrid-mode handling. Trust the code over the README for these.
>
> `JDBC.*` / `mybatis.mapper.scan` apply only when an optional `DataSourceModule` is registered via `ServiceBooter.startFrom(...)` (MyBatis-Guice). `DataSourceModule` scans `@Mapper` interfaces (via the framework's `ClassUtils`, same as `@HttpService` scanning) and binds `JDBC.*` into the pooled DataSource.

### Caching
`@LocalCache(name=)` (Caffeine, 10-min write TTL, 1024 max) and `@RedisCache(name=, expire=, timeUnit=)` are applied inside `ReflectionUtils.invokeMethod`, which wraps the reflective call. Cache key is `name#md5(args)`. Redis values are JSON-serialized via `RedisCacheUtil`/`JsonUtils`.

### Auth
SA-Token is integrated and initialized **once at startup** in `ServiceBooter` (context/dao/`RoleStpInterface`/config); `DispatcherServlet` only binds the per-request context. `RedisSaTokenDao` persists tokens/sessions in Redis (reusing a single shared Lettuce connection). Roles are stored under Redis key `plinth:auth:roles:<loginId>` by `AuthHelper.login(user, roles...)` and read back by `RoleStpInterface`, so authorization is consistent across instances; `AuthHelper.logout()` clears the key. `@AUTH(orRole=, andRole=, authUrl=)` enforces access per endpoint (`orRole` = any-of, `andRole` = all-of).

### User module
Built-in user management under `yi.shi.plinth.user` + `yi.shi.plinth.db.entity.User` / `yi.shi.plinth.db.mapper.UserMapper` (scanned by `DataSourceModule`). `UserApi` (`@HttpService`) exposes register / login / logout / current / list / get / roles / password / delete / key under `/user/*`. Passwords are hashed with `PasswordEncoder` (SHA-256 + per-user random salt, no extra dependency). Login delegates to `AuthHelper.login(id, roles...)` - roles split from the comma-separated `roles` column - and returns the sa-token value; clients send it back in the `satoken` header. Admin-only endpoints use `@AUTH(orRole="admin")`. Table DDL: `src/main/resources/sql/sys_user.sql` (also stores `bucket` / `minio_access_key` / `minio_secret_key` per user). `@HttpParam` values arrive as `String` and are parsed in the service (the framework does not convert types). `UserService.ensureCredentials` lazily provisions a user's MinIO bucket name + S3 access key/secret on first `currentUser()`/`get()`; `POST /user/key` regenerates them.

### Frontend (built-in)
Server-rendered HTML UI under `yi.shi.plinth.view` using **j2html + Materialize CSS + jQuery** (all via webjars, offline self-contained). Mirrors the `ultimate-JDK-21` reference project.

- **Page pattern**: `@HttpService extends Page` + `@GET @HttpPath` returning `HTML` (`setHtmlContent(createHtml().render())`). `Page` is a template-method base: `createHtml()` = `createHead()` + `createBody()` (BusyIndicator + `/js/Init.js` + header + `createMain()` + footer). Subclasses implement `createHead()`/`createMain()`; override `createHeader()`/`createFooter()` to return null for chrome-less pages (login/register/404). `LoginPage`/`RegisterPage`/`NotFoundPage` bypass `Page` and assemble `html(head, body)` directly.
- **Layout helpers** `view/base/`: `Head` (common `<head>`), `Header` (nav + sidenav), `Menu` (login/role-aware nav), `Footer`, `Container` (card grid), `Ajax` (`$.ajax` JS generator).
- **Static assets** are served by `@HttpService`+`BINARY` (no Spring static convention): `MaterializeResources`/`JqueryResources` expose webjars from classpath `/META-INF/resources/webjars/...`; `JavaScriptAndCss` exposes `/js/*.js` - **adding a JS file requires adding a method here**.
- **Pages**: `/page/login`, `/page/register`, `/` (file browser: `GET /file/list?prefix=&bucket=` + per-file share modal), `/page/shares` (my shares: `/share/list` + revoke), `/share/view?token=` (public share page, no `@AUTH`), `/page/users` (admin, `/user/list` + per-user "Files" link), `/page/profile` (`/user/current` + S3 credentials + `POST /user/key` regenerate), `/page/404`.
- **Auth/interaction**: pages use `@AUTH(authUrl="/page/login")`; sa-token cookie carries the session; frontend AJAX calls `/file/*` (MinIO data plane) and `/user/*` directly. S3 clients (mc/aws-cli) hit `/s3/*` (separate SigV4 auth, not sa-token).

### MinIO integration (the app's purpose)
This is a **MinIO file-isolation shell**: one bucket per user (`user-<id>`, created on register via `MinioService.ensureBucket`), enforced at the app layer.

- **Data plane** (`yi.shi.plinth.minio.MinioService`, `@Singleton`): wraps `io.minio.MinioClient` built from `minio.endpoint`/`minio.accessKey`/`minio.secretKey` (admin creds). `yi.shi.plinth.file.FileApi` (`@HttpService`, `@AUTH`) exposes `/file/list|upload|download|delete|mkdir|buckets`. Normal users are pinned to their own bucket; admins may pass `bucket=` to view others. `DispatcherServlet` carries `@MultipartConfig` so `request.getPart("file")` works for uploads; `@HttpParam` still binds form fields. `BINARY.rawContentType` (consumed in `HttpRespHelper`) carries arbitrary content-types for downloads.
- **S3-compatible endpoint** (`yi.shi.plinth.proxy.MinioProxyServlet`, mounted at `/s3/*` in `JettyModule` - longer prefix beats `/*`): re-signing proxy for mc/aws-cli. Per-user access key/secret are **app-managed** (random, stored in `sys_user`, shown in Profile) - NOT MinIO IAM keys, so no MinIO Admin API is needed. Flow per request: parse SigV4 `Authorization` -> `findByAccessKey` -> verify signature with user secret over the client path `/s3/<bucket>/<key>` -> isolation check (bucket == user's) -> re-sign with admin creds over the stripped path `/<bucket>/<key>` -> stream to MinIO. `yi.shi.plinth.auth.SigV4Util` (JDK-only) does sign/verify; payload hash comes from the `x-amz-content-sha256` header so the body streams verbatim. Does **not** support `STREAMING-AWS4-HMAC-SHA256-PAYLOAD` (aws-chunked signed uploads). Uses `ModuleRegister.getInjector()` for `UserMapper` (same pattern as `RestApiServiceImpl`).
- **File sharing** (`yi.shi.plinth.share`): `sys_share` table (token/bucket/object/creator/expire/password_hash+salt/max_count/download_count/size). `ShareApi` exposes `/share/create|list|revoke` (`@AUTH`) and `/share/check|download|view` (public, no `@AUTH` - anyone with the token can download). `ShareService.consumeDownload` uses `ShareMapper.incrementDownloadCount` (atomic `UPDATE ... WHERE expire IS NULL OR expire>now AND (max_count IS NULL OR download_count<max_count)`) to prevent concurrent over-limit. Password hashed with `PasswordEncoder`. `/share/check` pre-validates the password without counting (so the public page can toast on wrong password before triggering a download). `SchemaInitializer` now loads both `/sql/sys_user.sql` and `/sql/sys_share.sql`.

## Gotchas
- Controllers need a public no-arg constructor and are re-instantiated every request; state must live in injected services, not controller fields.
- `IocModule`'s constructor scans from the root package `"yi"` (derived from its own package), so all `@HttpService` classes under `yi.*` are picked up in addition to the main class's package.
- `SatokenRedisUtil` / `RedisCacheUtil` each hold a singleton Lettuce connection initialized at class-load time, so `redis.host` must be configured before any Redis-backed call (sa-token auth, `@RedisCache`).
