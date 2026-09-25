# APITVMaze


# API para consumir los servicios expuestos por TVMaze

## Arquitectura

### Componentes Principales

Se implementó un modelo de Arquitecura básico que incluye los siguientes componentes:

client/
- TVMazeClient: Cliente para comunicación con la API de TVMaze
controller/
- TVMazeController: Controlador que maneja las solicitudes y respuestas de la API
dto/
- Records y DTO para el mapeor de datos de la API
exception/
- Manejo de excepciones específicas de la API
service/
- TVMazeService: Servicio que contiene la lógica de negocio para interactuar con la API

## Instalación y Configuración

### Requisitos Previos

- Java 17+
- Maven 3.6+

### Construcción del Proyecto

mvn clean install

### Ejecución
mvn spring-boot:run 

### Endpoints 

http://localhost:8080/shows/1

http://localhost:8080/search/shows?searchQuery=bads100

