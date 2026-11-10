# NOTAS: preparación backend (proyecto TVMaze middleware)

Fecha de compilación: 19 de octubre de 2026. Estado: Día 1 en curso.

> Compilado a partir de la conversación de trabajo. Los puntos marcados con ✎ son motivos redactados por mí que conviene reescribir con tus propias palabras antes del simulacro del Día 4. Todo lo marcado como **Pendiente** o **Confirmar** no está cerrado.

---

## 0. Método de trabajo

1. Test primero. El rojo debe ser de **comportamiento**, no de compilación ni de ruta inexistente (crear antes un esqueleto mínimo).
2. Código mínimo para ponerlo en verde, sin adelantarse a los casos siguientes.
3. Si un test pasa a la primera, hacer la **prueba de fuego**: romper temporalmente el código que protege y comprobar que falla. Después restaurar.
4. En los tests usar valores que no coincidan con valores por defecto (página 3, `showId` 999, no 0).
5. Rutas y `type` de error escritos como **literales** en los tests, nunca con las constantes del código (así el test fija el contrato).
6. Correr `mvn test` sin `-q` de vez en cuando: con `-q` un código de salida 0 no distingue "pasaron" de "no corrió ninguno".
7. Cada decisión de diseño se anota con tres partes: qué se decide, por qué y qué costo se acepta.

---

## 1. Estado del proyecto y hallazgos iniciales

Stack: Java 21, Spring Boot 3.3.4 (Spring 6.1.13), `RestClient`, MongoDB, Mockito y `@WebMvcTest`.

| # | Hallazgo inicial | Estado |
|---|---|---|
| 1 | Compilaba con Java 17 | Resuelto (JDK 21) |
| 2 | Sin `@RestControllerAdvice` | Resuelto |
| 3 | El 404 de TVMaze salía como "no disponible" | Resuelto |
| 4 | `RestClient` sin timeouts | **Pendiente** |
| 5 | `POST /ratings` devolvía 200 con `"error"` | Resuelto (201, 409 y 422) |
| 6 | `RatingRequest` sin validación | Resuelto |
| 7 | N+1 en `searchShows` | Resuelto |
| 8 | Búsqueda sin resultados lanzaba excepción | **Confirmar** estado actual (ver §7) |
| 9 | `ShowDetailResponse` es DTO externo, documento de Mongo y respuesta a la vez | Pendiente (deuda de diseño) |
| 10 | Shows guardados en Mongo sin expiración | Pendiente (ver §5, el caché sostiene una regla de negocio) |
| 11 | Sin versionado ni paginación | Versionado resuelto; paginación en curso |
| 12 | `ShowResponse` con setters y dos clases `Rating` | Pendiente |

---

## 2. Java 21 y virtual threads

- JDK 21.0.12.1 verificado con `mvn -version`. En tests se usó Corretto 21.0.12.1.
- Con Java 25, Mockito falla al crear el mock de `TVClient`. Mantener el JDK 21 en IntelliJ y en Maven.
- Idea pendiente: usar `maven.compiler.release` en 21 en lugar de `source`/`target`.
- `switch` con pattern matching probado en `Utilerias`: type patterns sobre `Object` y `case null, default`. Pendiente practicar el guard con `when` y el record pattern.

### Comparación de virtual threads (evidencia)

| `spring.threads.virtual.enabled` | Nombre del hilo | `isVirtual()` |
|---|---|---|
| `true` | `tomcat-handler-N` | `true` |
| `false` | `nio-8080-exec-N` | `false` |

- Con pocas peticiones, el nombre del hilo **no** prueba nada por sí solo (el pool de Tomcat también crea hilos distintos al inicio). La prueba válida es `isVirtual()` más el prefijo del nombre.
- Log temporal retirado. `AGENTS.md` actualizado a Java 21.

### Preguntas de entrevista (para el simulacro)

1. Por qué un hilo virtual es barato y por qué crear uno por petición no es problema.
2. Cuándo ayudan y cuándo no. Ayudan en E/S bloqueante (mejoran la concurrencia, no la latencia de cada petición). No ayudan en trabajo de CPU. Siguen existiendo los recursos limitados (pool de conexiones, límite de TVMaze).
3. Qué cambia si hay virtual threads pero no hay timeouts: los hilos no se agotan, pero cada petición colgada sigue reteniendo recursos y el síntoma tarda más en aparecer.
4. *Pinning*: qué lo causa (sincronización con `synchronized`) y cómo detectarlo.

---

## 3. Manejo de errores

### 3.1 Cadena y familias de excepción

`TVClient` lanza una excepción de dominio → `GlobalExceptionHandler` la atrapa → responde un `ProblemDetail` con su `type` y su código HTTP.

- `RestClientResponseException`: TVMaze **respondió** con un error HTTP (4xx/5xx).
- `ResourceAccessException`: **no hubo respuesta** (fallo de E/S). Su `cause` es `ConnectException` en una conexión rechazada o no resuelta.
- Son hermanas, no padre e hija. Por eso se necesitan `catch` separados, y atrapar `Exception` a secas esconde bugs propios.
- Se recorre la cadena de causas con el helper `hasCause(Throwable, Class)`, comparando por **tipo** y no por texto del mensaje.

### 3.2 Situaciones y respuesta de la API

| Situación | Excepción de dominio | HTTP | `type` |
|---|---|---|---|
| TVMaze responde 404 en `/shows/{id}` | `TvNotShowException` | 404 | `show-not-found` |
| TVMaze responde 429 | `TvMazeRateLimitException` | 429 | `rate-limited` |
| TVMaze responde 5xx | `TvMazeUnavailableException` | 503 | `upstream-unavailable` |
| Fallo de conexión (sin respuesta) | `TvMazeUnavailableException` | 503 | `upstream-unavailable` |
| TVMaze responde 200 sin cuerpo (`null`) | `TvMazeUnavailableException` | 503 | `upstream-unavailable` |
| Error inesperado propio | handler genérico | 500 | `internal-error` |

- El 500 se reserva para **bugs propios**. Un fallo de la dependencia no es un 500.
- El stack trace se registra en el log y nunca se expone al cliente.
- 429: según la documentación y los foros de TVMaze existe un límite por IP (alrededor de 20 llamadas cada 10 segundos) con bloqueo temporal. Verificar la cifra en `https://www.tvmaze.com/api#rate-limiting`. Como tu API es un middleware, todas las peticiones salen por tu IP, así que el 429 es realista.
- **Decisión pendiente:** si propagar 429 tal cual al cliente. Quien excedió el límite es tu aplicación frente a TVMaze, no el cliente frente a ti. Pendiente definir el uso de `Retry-After` y la redacción del mensaje (no asignar el sujeto "cliente").

### 3.3 `ResponseEntityExceptionHandler`

- `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler`: cubre 405, 415, 406, parámetros faltantes y errores de conversión con el status correcto, sin escribir un handler por excepción.
- Los `@ExceptionHandler` propios quedan solo para excepciones de negocio.
- Se sobrescriben `handleMethodArgumentNotValid` (lista `errors` con `campo: motivo`) y `handleHttpMessageNotReadable`.
- `handleExceptionInternal` reemplaza `about:blank` por `urn:tvmaze:problem:<status>` (por ejemplo `method-not-allowed`) y no toca los `type` que ya tienen valor propio.
- Antes de este cambio, `"rating": "abc"`, el método incorrecto y la ruta inexistente salían como **500**, porque el handler genérico se tragaba excepciones de Spring MVC que son errores del cliente.
- Cada `type` derivado del status es un contrato: probarlo con un test por caso.

### 3.4 Catálogo de `type` (contrato público)

| `type` | HTTP | Significado |
|---|---|---|
| `show-not-found` | 404 | `/shows/{id}` no existe en TVMaze |
| `search-no-results` | 404 | Búsqueda sin coincidencias (**confirmar** que sigue vigente, ver §7) |
| `upstream-unavailable` | 503 | TVMaze no disponible o respuesta inválida |
| `rate-limited` | 429 | Límite de peticiones frente a TVMaze |
| `internal-error` | 500 | Bug propio |
| `validation-error` | 400 | Campos del cuerpo inválidos (propiedad `errors`), cuerpo ilegible o `sort` no permitido (propiedad `field`) |
| `bad-request` | 400 | Derivado de Spring: parámetro ausente o error de conversión |
| `method-not-allowed` | 405 | Verbo no permitido (con header `Allow`) |
| `not-found` | 404 | Ruta inexistente |
| `unsupported-media-type` | 415 | `Content-Type` no soportado |
| `rating-already-exists` | 409 | Calificación duplicada (propiedades `showId`, `userId`, `ratingId`) |
| `rating-show-not-registered` | 422 en `POST`, 404 en `GET` listado | El show no está registrado localmente |

✎ **Por qué conviven `validation-error` y `bad-request`:** `validation-error` lo producen mis propios handlers (validación del cuerpo, cuerpo ilegible, `sort` no permitido). `bad-request` es el `type` que se deriva automáticamente del status para errores genéricos de Spring (parámetro ausente, conversión). Lo que cambia es **quién genera el error y por qué mecanismo llega al handler**.

### 3.5 Límites conocidos

- Los mensajes por defecto de Spring salen en inglés (`title` y `detail` de `bad-request` y `not-found`, por ejemplo `Required parameter 'searchQuery' is not present.` y `No static resource ...`). ✎ Motivo: no son errores de negocio y ningún cliente depende hoy de ese texto. El `detail` de `not-found` además revela cómo Spring resuelve las rutas internamente.
- La asimetría de códigos con el mismo `type` en `rating-show-not-registered` (ver §8).

### 3.6 Pendiente de resiliencia (Día 3)

- Timeouts de conexión y lectura en `RestClient` (hallazgo 4). Probar con una IP que no enruta (`10.255.255.1`).
- Distinguir timeout de conexión rechazada: mirar `HttpTimeoutException`, `SocketTimeoutException` y `ConnectException` en la cadena de causas. Verificar la clase real con el depurador.
- 429 como error reintentable (Retry con backoff). No reintentar a ciegas.
- Pruebas con WireMock para timeout, 500, 404 y 429.

---

## 4. Validación de `RatingRequest`

```java
public record RatingRequest(
        @NotNull Long showId,
        @NotBlank String userId,
        @NotBlank String comment,
        @NotNull @Min(0) @Max(5) Integer rating) {}
```

- Rango del rating: **0 a 5**. `@Min`/`@Max` no rechazan `null`, por eso se necesita `@NotNull`.
- Valores límite a probar: -1, 0, 5 y 6. También un `comment` de solo espacios.
- `userId` viene del body de forma **provisional**. Con JWT (Día 2) debe salir del token.
- Agregar `userId` obligatorio es un cambio **incompatible** (ver §9).

---

## 5. `POST /ratings`

| Caso | HTTP | `type` |
|---|---|---|
| Creación exitosa | 201 (`@ResponseStatus(HttpStatus.CREATED)`) | n/a |
| Show no registrado localmente | 422 | `rating-show-not-registered` |
| Calificación duplicada (mismo usuario y show) | 409 | `rating-already-exists` |
| Cuerpo inválido o ilegible | 400 | `validation-error` |

- Ruta real: `POST /api/v1/shows/ratings` (ver §8).
- Respuesta de éxito: `{"status":"success","message":"...","ratingId":"<id de Mongo>"}`. `status` y `message` son redundantes con el código HTTP, pero inofensivos. Cambiarlos rompería a quien ya los consuma.
- Bug corregido: se pasaba el `userId` como `id` de `Rating`. El `id` debe ir en `null` para que Mongo lo genere y se lee de `save(...)`.
- Sin header `Location`: no existe un endpoint que devuelva un rating por id.
- Se usa `findById` solo para comprobar existencia. Hay una alternativa más directa, pero es un detalle menor en este caso.
- **Regla de negocio:** solo se califican shows que ya se consultaron. La fuente de verdad es **Mongo**, no TVMaze. El flujo no llama a TVMaze, por lo que 503 y 429 no se producen ahí (sus tests en el `POST` verifican el handler, no el flujo).
- **Costo aceptado:** dependencia de orden oculta. Un show que existe en TVMaze pero nunca se consultó por id se rechaza. El mensaje debe decir "no registrado en nuestra base", no "no existe", y la regla debe documentarse en OpenAPI y en el README.
- **Riesgo:** el caché de Mongo sostiene una regla de negocio. Si algún día se limpia la colección o se agrega un TTL, los ratings empezarían a fallar sin que la regla cambie.
- **Pendiente (Día 2):** índice único compuesto `(showId, userId)` y traducir el error de clave duplicada a 409. La comprobación "buscar y luego guardar" no es atómica: dos peticiones simultáneas pueden pasarla ambas.

---

## 6. N+1 en `searchShows`

- Medición antes: **10 consultas** a `ratings` para 10 resultados (cada una de ~80 a 100 ms contra un Mongo remoto; entre la primera y la última pasó ~1.3 s según los logs `DEBUG`, como mínimo, porque el log marca el inicio de cada consulta).
- Después: **1 consulta** con `$in` sobre los 10 `showId`.
- Solución: `findByShowIdIn(Collection<Long>)` más `Collectors.groupingBy`, con `getOrDefault(id, List.of())` para que un show sin ratings reciba lista vacía y no `null`. El mapeo a `RatingInfo(comment, rating)` conserva la privacidad: no se expone `userId`.
- El N+1 se mide por el **número de consultas**, no por cuántos documentos devuelve cada una.
- JPA frente a Mongo: en JPA el N+1 nace de asociaciones lazy (se corrige con `JOIN FETCH`, `@EntityGraph` o carga por lotes, no pasando a EAGER). Aquí nace de un bucle que llama al repositorio y se corrige pidiendo por lote.
- **Límites:** se traen a memoria todos los ratings de los shows de la búsqueda. Hoy no importa por el volumen.
- **Pendiente:** medir el tiempo total de la búsqueda antes y después en Postman.
- **Pendiente (índice):** una consulta por `showId` sin índice recorre toda la colección. Revisar con `explain` (`COLLSCAN` frente a `IXSCAN`). Un índice compuesto que empiece por `showId` sirve para `$in`, para el listado paginado y para la unicidad `(showId, userId)`. Averiguar por qué importa el orden de los campos.
- Dato de JVM: con `groupingBy`, una clave `showId` nula lanza `NullPointerException`. Se limpiaron los datos antiguos y no se defiende ese caso.

---

## 7. Búsqueda sin resultados

- TVMaze responde **`200 []`** tanto con `q=` vacío como con un texto sin coincidencias. "Sin coincidencias" es una respuesta válida del origen.
- Convención REST: una colección filtrada con cero elementos suele ser `200 []`. El 404 se reserva para un recurso concreto inexistente.
- Decisión tomada: el middleware comunica el 404 (`search-no-results`), con la asimetría documentada. ✎ Costo: el cliente no distingue "no hay coincidencias" de una ruta equivocada, y las herramientas que cuentan los 404 como fallo lo registrarán así.
- Diseño en capas: `TVClient` devuelve `[]` tal cual y trata `null` como `TvMazeUnavailableException`. `TVMazeService` decide qué significa una lista vacía.
- **Confirmar:** el último método de `searchShows` que se revisó hace `if (shows.isEmpty()) return shows;` y no lanza `TvShowSearchEmptyException`. Comprobar con `xyzxyz` en Postman qué responde hoy. Si se queda en `200 []`, eliminar la excepción, su handler, el `type` `search-no-results` y sus tests. Si se mantiene el 404, volver a lanzarla en el servicio.
- Pendiente: decidir qué hacer con `q` vacío (¿búsqueda válida o petición mal formada?).

---

## 8. Auditoría REST de los endpoints

| Endpoint (ruta real) | Decisión | Motivo |
|---|---|---|
| `GET /api/v1/shows/search?searchQuery=` | Dejar la forma de operación (`search`) en lugar de `GET /shows?q=` | No existe listado general de shows; el parámetro es obligatorio; TVMaze usa una forma similar. Se mantuvo el nombre `searchQuery` (cambiarlo rompería a quien lo use) |
| `GET /api/v1/shows/{id}` | Dejar `comments` embebido | Un solo consumidor y volumen bajo; umbral de revisión: cuando una lista de calificaciones crezca. |
| `GET /api/v1/episodes/{id}` | Dejar plano | El id del episodio es único por sí mismo (así lo usa TVMaze); el show en la URL no añade información. Listar los episodios de un show añadiría una ruta de colección anidada sin cambiar esta. |
| `POST /api/v1/shows/ratings` | Dejar plano (`showId` en el cuerpo) | Coherente con la regla de show registrado y con el 422: la referencia viaja en el cuerpo. Una ruta anidada daría un 404 que contradiría el 200 de `GET /shows/{id}`. |

- **Cambio de ruta:** la auditoría decía `/search/shows` y `POST /ratings`. El prefijo de clase `/shows` hizo que quedaran en `/shows/search` y `/shows/ratings`. Se aceptó ese resultado. **Costo conocido:** `/shows/ratings` se lee como "el show con id `ratings`". Spring da prioridad a la ruta literal sobre `{id}`, sin conflicto.
- Deuda anotada: el recurso hijo se llamaría `ratings`, pero el campo del JSON se llama `comments`. Renombrarlo sería otro cambio incompatible.
- `totalComments` **no se agrega por ahora**: es redundante mientras se devuelva la lista completa. Si se agrega a la búsqueda, sale del mismo `groupingBy`, sin consulta extra. Si se limita la lista, ya no sirve contar con `list.size()`.
- HATEOAS: se conoce y no se aplica (sobrecarga para esta API).
- Si en una entrevista dudan de la anidación: dos niveles es habitual; el problema real es de rutas de tres o más niveles.

---

## 9. Versionado

- Estrategia elegida: **versión en la URL** (`/api/v1`), con el prefijo a nivel de clase en cada controlador.

| Estrategia | Visibilidad | Facilidad de prueba | Cuánto toca el código |
|---|---|---|---|
| URL (`/api/v1/...`) | Alta: forma parte de la identidad del recurso | Muy alta: navegador y Postman sin configurar | Un prefijo por controlador o uno global |
| Header (`X-API-Version`) | Baja: pasa desapercibida | Media: no se prueba desde la barra del navegador | Condición `headers=` en cada mapeo |
| Media type (`Accept`) | La más abstracta | Baja | Condición `produces=` en cada mapeo |

- Constante compartida `ApiConstants.V1` (`/api/v1`), para que un error de dedo no pase inadvertido entre controladores. `V2` se quitó hasta que exista. Un literal usado dentro de una anotación debe ser una constante en tiempo de compilación.
- El prefijo común es `/api/v1`, **no** `/api/v1/shows` en controladores con rutas de bases distintas.
- Por qué a nivel de clase y no global (`WebMvcConfigurer`): ✎ la versión queda visible al leer el código, sin configuración oculta. Costo: se repite en cada controlador.
- **Costo aceptado de la URL:** cambiar de versión cambia todas las rutas, aunque solo un endpoint haya cambiado.
- Las rutas sin versión **no se mantienen** (único consumidor). Responden 404 con `type` `not-found`.
- Postman: la versión vive en `{{baseUrlTv}}` (`http://localhost:8080/api/v1/`). Cambiar a v2 es una sola edición. Costo: no se pueden probar v1 y v2 a la vez sin una segunda variable.
- Convivencia: dos controladores (`...V1`, `...V2`) que **reutilizan el servicio**; solo cambian controladores y DTOs. Duplicar también el servicio es el gran costo de mantener dos versiones.

### Compatible frente a incompatible

| Cambio | ¿Rompe? | Motivo |
|---|---|---|
| Agregar `ratingId` a la respuesta | No (en general) | Campo adicional; Jackson ignora por defecto los campos desconocidos al deserializar |
| `userId` obligatorio en la petición | **Sí** → v2 | Las peticiones antiguas fallarían con 400 |

- **Compatible** (sin versión nueva): agregar campos opcionales, endpoints o valores que el cliente ignora.
- **Incompatible** (versión nueva): quitar o renombrar campos, cambiar un tipo, volver obligatorio algo opcional, cambiar el significado de un campo o de un código de estado.
- Alternativa a considerar: aceptar temporalmente la petición sin el campo y avisar con el header `Deprecation`.

---

## 10. Paginación: listado de ratings de un show

### Diseño (cerrado)

| Aspecto | Decisión |
|---|---|
| Ruta | `GET /api/v1/shows/ratings?showId=` (en la misma base que el `POST`) |
| `showId` | Obligatorio; ausente → 400 `bad-request` |
| Paginación | `Slice` con envoltorio propio (no serializar `Slice`/`Page` directo) |
| Respuesta | `{ "content": [...], "page": 0, "size": 20, "hasNext": false }` |
| Elemento de `content` | `id`, `comment`, `rating`. **Nunca `userId`.** |
| `size` | Por defecto 20, máximo 100 (`spring.data.web.pageable.*`). La respuesta devuelve el `size` **efectivo** |
| Valores inválidos | Spring los autocorrige en silencio (verificar `size=0`, `page=-1`, `size=100000`) |
| Orden por defecto | `id` descendente. El `ObjectId` lleva un timestamp de 4 bytes (resolución de **segundos**: orden cronológico aproximado, no fecha exacta) |
| `sort` permitido | Solo `id` y `rating` (lista blanca) |
| Desempate | Siempre se añade `id` descendente. Sin él, los empates en `rating` hacen el orden no determinista |
| Orden de `id` pedido por el cliente | Se **ignora**: el orden por `id` es siempre descendente (el cliente que pide `sort=id,asc` recibe el opuesto, sin aviso) |
| `sort` no permitido | 400 `validation-error`, excepción propia, validación en el servicio, **sin lista de campos** en la respuesta |
| Página inexistente | 200 con `content` vacío |
| Show sin calificaciones | 200 con `content` vacío |
| Show no registrado | 404 `rating-show-not-registered` |

- Los campos permitidos (`id`, `rating`) ya son públicos en la respuesta y se documentan en el README y en OpenAPI. Lo que protege `userId` es la lista blanca, no el mensaje.
- La lista blanca de `sort` también se puede defender en el controlador: es más contrato de la API que regla de dominio.
- Por qué difiere de la búsqueda (404 si no hay coincidencias): ✎ la búsqueda es una asimetría deliberada que prefiere comunicar el 404. En los ratings, si el show existe, la colección es un contenedor válido que está vacío.
- Pendiente: decidir si `RatingItemResponse` lleva `showId`. El contrato cerrado era `id`, `comment` y `rating`. El `showId` ya viene en la consulta. La nota y el código deben coincidir.
- Pendiente de nota: índice que serviría esta consulta (filtro por `showId` y orden por `rating` e `id`). Se enlaza con el índice único del Día 2.

### El mismo `type` con dos códigos (decisión)

- `rating-show-not-registered` sale como **422** en el `POST` (la referencia va en el cuerpo) y como **404** en el `GET` (el show es el recurso de la consulta).
- ✎ El `type` identifica la **causa de negocio** (show no registrado localmente), no el código HTTP. El RFC 9457 trata `status` como orientativo y no exige un código único por `type`.
- Consecuencia para quien consume la API: debe decidir por el `type` si quiere tratar igual los dos casos, o por el código si quiere distinguirlos. Anotar cuál se recomienda en el README.
- Implementación: excepción **hermana** (`RatingShowNotFoundException`), no subclase (una subclase dependería de que Spring elija el handler más específico). Un helper privado (`showNotRegisteredProblem`) construye el `ProblemDetail` con `type`, título "Show no registrado" y la propiedad `showId`. El servicio no decide códigos HTTP.

### Estado de la implementación

| Paso | Estado |
|---|---|
| Test del controlador: respuesta por defecto (sin `userId`, valores de los campos) | Hecho, en verde |
| Test del controlador: `showId` ausente (400, `bad-request`, `verifyNoInteractions`) | Hecho, con prueba de fuego |
| Test del controlador: show no registrado (404, `type`, `showId`) | Hecho, con prueba de fuego |
| Test del servicio: show no registrado (`999L`, sin tocar `ratingRepository`) | Hecho, en verde |
| Test del servicio: orden por defecto `id` descendente, conservando página 3 y tamaño 20 | Hecho, en verde |
| Test del servicio: `sort=rating,desc` más desempate `id` descendente | Hecho, en verde |
| Test del servicio: `sort=id,asc` se descarta (solo `id` descendente) | Test en rojo (`id: ASC, id: DESC`); **falta ponerlo en verde** |
| Validación de la lista blanca de `sort` (excepción propia, 400) | Pendiente |
| Test de `size` efectivo y valores inválidos | Pendiente |
| Tests de cada handler (`status`, `type`, `Allow`) | Pendiente |
| springdoc/OpenAPI | Pendiente |

---

## 11. Pendientes consolidados

**Corto plazo (Día 1)**
- [ ] Poner en verde `sort=id,asc` en `RatingService`.
- [ ] Lista blanca de `sort` (400 `validation-error`) y sus tests.
- [ ] Confirmar `size` efectivo y el comportamiento con valores inválidos.
- [ ] Decidir si `RatingItemResponse` lleva `showId`.
- [ ] **Confirmar** qué responde hoy la búsqueda sin resultados y limpiar el código y el catálogo en consecuencia (§7).
- [ ] Decidir qué hacer con `q` vacío.
- [ ] Tests de cada handler con `status`, `type` y `Allow` (405).
- [ ] Actualizar README: rutas versionadas, catálogo de `type`, regla de show registrado, campos permitidos de `sort`.
- [ ] Medir el tiempo de la búsqueda antes y después del N+1.
- [ ] springdoc/OpenAPI (dependencia para Boot 3) y documentar códigos de respuesta.
- [ ] Sealed interface: entendida, pendiente de aplicar (candidato: *fallback* de Resilience4j en el Día 3). ✎ Ventaja: el compilador obliga a cubrir todos los casos del `switch`. Frente a excepciones: se pierde el handler centralizado y hay que mapear en cada controlador. Criterio: resultado **esperado del negocio** → valor devuelto; fallo **excepcional de infraestructura** → excepción.

**Día 2 (JWT y seguridad)**
- [ ] `userId` desde el token, no desde el body.
- [ ] Índice único compuesto `(showId, userId)` y traducir el error de clave duplicada a 409.
- [ ] Entidad "favoritos del usuario" con Mongo protegida con JWT.
- [ ] Spring Data MongoDB: modelado de documentos, índices, cuándo embeber y cuándo referenciar.

**Día 3 (resiliencia)**
- [ ] Timeouts en `RestClient` (hallazgo 4) y prueba con IP que no enruta.
- [ ] Distinguir timeout de conexión rechazada.
- [ ] Resilience4j sobre `TVClient`: Timeout, Retry (backoff y jitter), Circuit Breaker, Bulkhead, Fallback.
- [ ] 429: `Retry-After` y reintentos con criterio.
- [ ] WireMock para simular TVMaze.

**Día 4 (pruebas y simulacro)**
- [ ] Pruebas de integración con Testcontainers (MongoDB).
- [ ] `@ParameterizedTest` para los códigos 5xx (hecho en cliente) y para los casos de `sort`.
- [ ] Repaso oral de los escenarios 1 a 6, cronometrado.

**Deuda de diseño anotada (sin fecha)**
- [ ] Separar DTO externo, documento de Mongo y respuesta de la API (`ShowDetailResponse`).
- [ ] Renombrar el campo `comments` a `ratings` (cambio incompatible).
- [ ] TTL o expiración de los shows guardados en Mongo, y el efecto sobre la regla de show registrado.
- [ ] Mensajes en español para los errores por defecto de Spring.
- [ ] `ShowResponse` como `record` y renombrar una de las dos clases `Rating`.
- [ ] Traer a memoria todos los ratings de la búsqueda: revisar si el volumen crece.


**La protección CSRF defiende frente a credenciales que el navegador adjunta automáticamente (cookies).**

Esta API usa Authorization: Bearer, que solo lo añade el código de la aplicación cliente y nunca se 
adjunta solo; además la API es stateless y no hay sesión. Por eso se desactiva. Condición: 
si algún día el token pasa a una cookie, hay que reactivarla.


