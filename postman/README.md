# Probar RedSalud Andina en Postman

## Preparación local

Requisitos: Java 21, Node.js y Docker.
Ejecutar desde la raíz de RedSalud-Andina.

En macOS con `openjdk@21` instalado por Homebrew, seleccionar Java 21 en cada
terminal que vaya a ejecutar Maven:

```sh
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
```

1. Levantar PostgreSQL:

   ```sh
   docker compose up -d postgres
   ```

   Esta base es exclusiva del proyecto: se publica solo en `127.0.0.1:55432`, usa
   la base `redsalud_postman` y no toca ningún PostgreSQL que escuche en `5432`.
   Esperar a que aparezca como saludable con `docker compose ps`.

2. En una terminal, generar los JWT y mantener disponible su clave pública:

   ```sh
   node scripts/postman-jwt.mjs --serve
   ```

   Genera `postman/RedSalud-Andina.local.postman_environment.json`, listo para importar,
   y JWT RS256 para paciente, médico rural, especialista, auditor y gestor. Caducan en
   24 horas. Para renovarlos, repetir el comando y volver a importar el ambiente.
   La clave privada se conserva en `.local-jwt/`, excluido de Git. El servidor solo
   escucha en 127.0.0.1:9090 y solo publica `/jwks.json`; no sirve tokens ni claves privadas.
   `node scripts/postman-jwt.mjs` genera los archivos sin levantar el servidor.

3. En otra terminal, iniciar la app:

   ```sh
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=postman
   ```

   El perfil `postman` acepta exclusivamente el emisor local configurado. Mantiene
   la validación de firma, emisor, vencimiento y las reglas de autorización. También
   configura la base aislada en el puerto `55432` y la API en `8080`. No activarlo
   en despliegues. Si `8080` estuviera ocupado, iniciar con `SERVER_PORT=8082` y cambiar
   `baseUrl` en el ambiente Postman al mismo puerto.

4. Importar estos dos archivos en Postman y seleccionar el ambiente local:

   - `RedSalud-Andina.postman_collection.json`
   - `RedSalud-Andina.local.postman_environment.json`

## Ejecutar

En Collection Runner ejecutar las carpetas **00 a 05**, en orden, con una iteración.
La colección genera los identificadores y guarda el cupo creado automáticamente.
Los tokens se eligen por petición; no es necesario pegarlos en cada request.
El paciente local tiene el mismo `sub` que la variable de ambiente `patientId`.
La primera petición Health reinicia los identificadores del escenario para repetirlo.
Las pruebas crean datos ficticios en la base de datos configurada y no los eliminan.

Incluye autenticación 401, autorización 403, sincronización e idempotencia,
lectura con y sin motivo, disponibilidad, reserva directa por paciente,
ciclo de interconsulta, recetas, legado, auditoría, indicadores y monitoreo.
La analítica se actualiza por eventos; si aún no aparecen cifras, volver a consultar.

La carpeta **06** se ejecuta manualmente después de abrir el episodio: seleccionar
un archivo en Body → form-data → file, enviarlo y luego descargarlo. No incluir
esta carpeta en Runner sin configurar antes el archivo local.

Los valores de `patientToken`, `ruralDoctorToken`, `specialistToken`, `auditorToken`
y `networkManagerToken` están en el ambiente generado. También se guardan en
`.local-jwt/tokens.json`, sin versionar. Son credenciales de prueba locales, no de Cognito.

## Usar Cognito real

Iniciar la app con su configuración habitual, sin el perfil `postman`, y configurar
`COGNITO_ISSUER_URI`. Importar `RedSalud-Andina.postman_environment.json` y completar
los cinco tokens con JWT válidos emitidos por ese pool. Completar también `patientId`
con el `sub` del paciente, y `specialty` con la especialidad del especialista.
El token debe incluir los grupos y atributos usados por `CurrentUser`.
El backend no tiene un endpoint de login ni puede emitir un JWT de Cognito sin su proveedor.

Referencia: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
