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

1. Clona el repositorio
2. Configura la variable de entorno `MONGODB_URI` con tu connection string de MongoDB Atlas:

desde la Terminal:

export MONGODB_URI="mongodb+srv://usuario:password@cluster.mongodb.net/tvmaze?retryWrites=true&w=majority"
mvn spring-boot:run

### Endpoints 

http://localhost:8080/shows/1

http://localhost:8080/search/shows?searchQuery=bads100

