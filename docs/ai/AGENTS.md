# Plan de trabajo e integración para agentes de IA

> Documento de referencia para cualquier agente o colaborador que vaya a planear o implementar cambios en APITVMaze. Mantén este archivo actualizado cuando cambien el propósito, la arquitectura o las reglas de integración del proyecto.

## Propósito del proyecto

APITVMaze es una API REST construida con Spring Boot que sirve como middleware para consultar información de series de TVMaze y almacenar series y calificaciones/comentarios en MongoDB. La API expone búsquedas y detalles de series, además de permitir guardar calificaciones asociadas a una serie.

## Arquitectura actual

El proyecto usa Java 17, Spring Boot 3 y Maven. La aplicación sigue una arquitectura por capas:

| Capa | Ubicación | Responsabilidad |
|---|---|---|
| Entrada de la aplicación | `src/main/java/com/mx/tvmazemiddleware/Main.java` | Arrancar Spring Boot. |
| Controladores | `controller/` | Exponer endpoints HTTP y delegar el trabajo a servicios. |
| Servicios | `service/` | Coordinar la lógica de negocio, los clientes externos y los repositorios. |
| Cliente externo | `client/` | Consultar TVMaze mediante `RestClient`, configurado con `https://api.tvmaze.com`. |
| DTO y modelos | `dto/` | Representar solicitudes y respuestas, incluidos los datos provenientes de TVMaze. |
| Persistencia | `repository/` | Acceder a MongoDB mediante repositorios Spring Data. |
| Excepciones | `exception/` | Representar errores del dominio o de comunicación con TVMaze. |

Flujo general: **HTTP → controlador → servicio → cliente TVMaze y/o repositorio MongoDB → DTO/respuesta HTTP**. Los controladores no deben contener lógica de negocio ni acceder directamente a la persistencia o a TVMaze.

### Funcionalidad existente

- `GET /search/shows?searchQuery={texto}` busca series en TVMaze y agrega comentarios guardados a cada resultado.
- `GET /shows/{id}` obtiene el detalle de una serie, lo conserva en MongoDB y devuelve también sus comentarios.
- `POST /ratings` recibe `showId`, `comment` y `rating`; guarda la calificación si la serie está almacenada.
- `TVMazeService` coordina consultas y repositorios. `RatingService` coordina el guardado de calificaciones.
- La URI de MongoDB se configura con la variable de entorno `MONGODB_URI`, referenciada desde `src/main/resources/application.properties`.

## Reglas de integración

1. **Respeta las capas:** añade endpoints en controladores, lógica de negocio en servicios, comunicación externa en `client/` y acceso a datos en `repository/`.
2. **Preserva contratos:** antes de cambiar rutas, parámetros, cuerpos o respuestas, revisa los consumidores y pruebas existentes. Evita cambios incompatibles salvo que el plan los solicite expresamente.
3. **Usa los patrones del proyecto:** constructor para inyección de dependencias, tipos explícitos y DTOs para los límites HTTP/externos. Reutiliza componentes existentes antes de introducir abstracciones nuevas.
4. **Maneja fallos con claridad:** no ocultes errores ni devuelvas respuestas de éxito cuando una operación haya fallado. Mantén el uso de excepciones específicas para los errores de TVMaze y del dominio.
5. **Protege la configuración:** usa variables de entorno o configuración externa para secretos y credenciales. Nunca agregues valores reales de conexión, tokens o contraseñas al código o a este documento.
6. **Mantén compatibilidad técnica:** conserva Java 17, Spring Boot y Maven, salvo que el plan requiera y justifique un cambio de plataforma.
7. **Prueba los cambios:** actualiza o agrega pruebas en `src/test/java/` para cubrir comportamiento nuevo y regresiones. Ejecuta las pruebas Maven pertinentes; la suite existente se puede ejecutar con `mvn test`.
8. **Limita el alcance:** no modifiques archivos ni comportamiento no relacionados con el objetivo del plan. Si una decisión afecta contratos, persistencia o arquitectura y no está definida, explica la incertidumbre y solicita aclaración antes de asumirla.
9. **Actualiza documentación:** si cambian endpoints, configuración, arquitectura o instrucciones de ejecución, actualiza también la documentación correspondiente, incluido este archivo cuando aplique.

## Plan del cambio

Completa esta sección antes de implementar una tarea. Usa pasos verificables y relaciona cada cambio con el código o contrato afectado. Si todavía no hay un cambio concreto, conserva los campos como plantilla.

### Objetivo

<!-- Describe el problema que se resolverá y el resultado esperado. -->

### Alcance

- Incluye:
- No incluye:

### Requisitos y criterios de aceptación

- [ ] 
- [ ] 

### Diseño e integración

<!-- Indica el flujo afectado, capas/clases involucradas, contratos API y cambios de persistencia/configuración. -->

### Pasos de implementación

1. [ ] 
2. [ ] 
3. [ ] 

### Pruebas y validación

- Pruebas a agregar o modificar:
- Comandos de validación:
- Resultado esperado:

### Riesgos, dependencias y decisiones pendientes

- Riesgos o dependencias:
- Decisiones por confirmar:

### Estado

- Estado: Pendiente
- Notas:
