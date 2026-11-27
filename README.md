# RedSalud Andina

Plataforma de teleconsulta para la red rural RedSalud Andina: 14 centros, 9 especialistas y 5 centros con enlaces de 2 Mbps
con cortes diarios. Los datos clínicos no pueden salir del país.

Monolito modular con Spring Boot 4, Java 21 y Spring Modulith.

```bash
docker compose up -d      # PostgreSQL local
./mvnw spring-boot:run    # perfil "dev"
./mvnw test               # los tests no necesitan Docker
```

La documentación de arquitectura se completa al final del historial.
