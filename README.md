# APITVMaze


# API para consumir los servicios expuestos por TVMaze

## Arquitectura

### Componentes Principales

Se implementó un modelo de Arquitecura básico que incluye los siguientes componentes:

client/
- TVMazeClient: Cliente para comunicación con la API de TVMaze

controller/
- TVMazeController: Controlador que maneja las solicitudes y respuestas de la API
- RatingController: Expone `POST /ratings` para guardar calificaciones de shows

dto/
- Records y DTO para el mapeor de datos de la API

exception/
- Manejo de excepciones específicas de la API 

service/
- TVMazeService: Servicio que contiene la lógica de negocio para interactuar con la API
- RatingService: Valida que el show exista y que el usuario no haya calificado ya ese show

repository/
- Acceso a MongoDB para shows y ratings

## Instalación y Configuración

### Requisitos Previos

- Java 21+
- Maven 3.6+

### Construcción del Proyecto

mvn clean install

### Ejecución

1. Clona el repositorio
2. Configura la variable de entorno `MONGODB_URI` con tu connection string de MongoDB Atlas:

desde la Terminal:

export MONGODB_URI="mongodb+srv://usuario:password@cluster.mongodb.net/tvmaze?retryWrites=true&w=majority"
mvn spring-boot:run

3. Accede a la API en http://localhost:8080

4. La aplicación ya esta conectada con la base de datos de MongoDB Atlas y no hay restrcciones de IP

### Endpoints 

GET: http://localhost:8080/shows/1

GET: http://localhost:8080/search/shows?searchQuery=girls

POST: http://localhost:8080/ratings

    {
    "showId": 1,
    "userId": "2",
    "comment": "Excelente serie",
    "rating": 5
    }

Validaciones: `showId`, `userId` y `comment` son obligatorios; `rating` debe estar entre 0 y 5.

Respuesta exitosa (201 Created):

    {
    "status": "success",
    "message": "Rating saved successfully",
    "ratingId": "abc123"
    }

## Manejo de errores y códigos HTTP

Los errores se devuelven como `ProblemDetail` (RFC 7807) con un `type` estable de la forma `urn:tvmaze:problem:<slug>`.

| HTTP | type | Escenario |
|------|------|-----------|
| 200 | - | Consultas GET exitosas |
| 201 | - | Rating creado (`POST /ratings`) |
| 400 | `validation-error` | Body inválido, vacío, mal formado o con tipos incorrectos |
| 404 | `show-not-found` / `search-no-results` / `rating-show-not-registered` | Show inexistente, búsqueda sin resultados, o show sin registrar al listar ratings |
| 409 | `rating-already-exists` | El usuario ya calificó ese show |
| 422 | `rating-show-not-registered` | El show del rating no existe en nuestra base de datos |
| 429 | `rate-limited` | TVMaze limitó las peticiones |
| 500 | `internal-error` | Error inesperado |
| 503 | `upstream-unavailable` | TVMaze no está disponible |

### Decisiones de diseño

- **201 en `POST /ratings`**: se crea un recurso nuevo; la respuesta incluye `ratingId`.
- **400 vs 422 vs 409**: 400 se reserva para requests mal formados o que no pasan validación. 422 indica que la request es válida sintácticamente pero referencia un show que no existe en nuestra base. 409 indica que la request es válida pero choca con el estado actual (ya existe un rating del mismo `userId` para el `showId`).
- **Rating duplicado**: antes se respondía con éxito HTTP y `status: "error"` en el body. Ahora se lanza `RatingAlreadyExistsException` y se responde 409 con `showId`, `userId` y el `ratingId` existente, para que el cliente pueda distinguirlo sin inspeccionar el body de un 201.
- **Errores de TVMaze**: 429 y 503 se propagan como tales; el detalle interno no se expone al cliente.
