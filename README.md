# RedSalud Andina

Plataforma de teleconsulta para una red rural: 14 centros, 9 especialistas y 5 centros con enlaces de 2 Mbps con cortes diarios.
Los datos clínicos no pueden salir del país (una sola región de AWS).

Spring Boot 4.2.0, Java 21, Maven, paquete base `co.redsalud.andina`.

## Arquitectura

Es un **monolito modular** (Spring Modulith). Un módulo por contexto delimitado y un esquema de PostgreSQL por módulo.
No es una arquitectura hexagonal: dentro de cada módulo el código son capas simples (servicio -> repositorio). Los puertos y
adaptadores aparecen solo donde hay una frontera real: el almacenamiento de adjuntos, el sistema legado, el almacén de
auditoría, la farmacia y el almacén del read model.

| Módulo | Es dueño de | API pública (lo que otros módulos pueden usar) |
|---|---|---|
| `identidad` | Mapeo del JWT de Cognito, roles, relaciones de cuidado | `CurrentUser`, `AccessPolicy`, `CareRelationships`, `Role` |
| `agenda` | Cupos y reservas de los especialistas | `SlotService`, `BookingService`, `SlotBooked` |
| `episodio` | Episodios y su ciclo de vida, sincronización offline, adjuntos | `EpisodeService`, `EpisodeSyncService`, `AttachmentService`, `AdjuntoStorage`, `EpisodeOpened` |
| `receta` | Recetas, outbox hacia la farmacia | `PrescriptionService`, `PharmacyDispatcher`, `PrescriptionSigned` |
| `interconsulta` | Máquina de estados, alertas, lista de seguimiento | `ConsultService`, `ConsultStatusChanged` |
| `legado` | Capa anticorrupción y read model del historial del legado | `LegacyHistoryService`, `PatientHistory` |
| `auditoria` | Auditoría append-only y el reporte "quién vio esto" | `AuditTrail`, `AuditStore`, `ReadReportService` |
| `analitica` | Indicadores por centro, seudonimizados | `IndicatorsService` |
| `api` | Puntos de entrada REST, mapeo de errores, orquestadores entre módulos | nada (módulo hoja) |

Reglas, verificadas por `ModularityTests` (`ApplicationModules.of(...).verify()`) y `ArchitectureTests` (ArchUnit):

- Ningún módulo lee ni escribe las tablas de otro. Solo el paquete raíz del módulo es público; `internal` es privado.
- No hay relaciones JPA (`@ManyToOne`, `@OneToMany`, ...) en ninguna parte, así que ninguna entidad puede llegar a la tabla de otro módulo.
- Las entidades viven en paquetes `internal`; los controladores viven solo en `api`.
- Los módulos hablan por servicios públicos o por eventos (`ApplicationEventPublisher`). Los eventos que cruzan módulos son
  records en el paquete raíz de quien los publica.
- Las dependencias van en un solo sentido: `api` -> módulos. `auditoria` e `identidad` no dependen de nadie, así que cualquier
  módulo puede llamarlos sin ciclos.

### Pocos controladores, orquestadores solo donde hacen falta

Los controladores no viven en los módulos. El módulo `api` tiene cuatro: `SchedulingController` (agenda e
interconsultas), `ClinicalController` (episodios, sincronización, adjuntos, recetas, historial del legado),
`AuditController` y `AnalyticsController`, más un único `ApiExceptionHandler` para todo el mapeo de errores.

Una llamada que toca un solo módulo va directo del controlador al servicio de ese módulo. Un orquestador existe
solo para un caso de uso que necesita dos módulos a la vez: `ConsultationOrchestrator` reserva el cupo (agenda) y pasa
la interconsulta a `SCHEDULED` en una sola transacción. Un canal nuevo (una app móvil) agrega controladores y reutiliza
los servicios sin tocarlos.

### Datos y consistencia

- Un esquema por módulo y una carpeta de Flyway por módulo: `db/migration/<módulo>/`. Las versiones llevan el espacio de
  nombres del módulo para no chocar: identidad `1.x`, agenda `2.x`, episodio `3.x`, receta `4.x`, interconsulta `5.x`,
  legado `6.x`, auditoria `7.x`, analitica `8.x`, compartido `0.x`. `out-of-order` está activo para que un módulo pueda
  agregar `1.1` después de aplicada la `8.0`.
- Las referencias entre módulos son ids simples (sin claves foráneas entre esquemas).
- La doble reserva la impide una restricción única en `agenda.booking(slot_id)`: decide la base de datos.
- Las recetas firmadas son inmutables (guarda en la entidad más una restricción check sobre las columnas de firma).
- Las transiciones de interconsulta se validan en la entidad y las protege `@Version`.
- El outbox es el registro de publicación de eventos de Modulith (`event_publication`): el evento se guarda con la
  transacción de negocio y su listener se reintenta hasta completarse.

### Offline-first

- El cliente genera los ids (episodio, adjunto, reserva, interconsulta, receta) y el sello de tiempo del reloj del centro.
  Las escrituras son `PUT` sobre ese id, de modo que reenviar devuelve el mismo resultado y no crea nada dos veces.
- `POST /api/sync/events` recibe un lote. Los eventos se aplican en orden por agregado (`sequence`); cada id de evento se
  aplica una sola vez, su resultado se guarda en la base de datos y un reenvío lo devuelve. La clave vive en la base,
  no en memoria, así que sobrevive a un reinicio y a una segunda instancia.
- Ciclo de vida del episodio: `WAITING` -> `ADMITTED` (asistencia registrada) -> `IN_PROGRESS` -> `COMPLETED`, solo hacia adelante.
- Política de conflictos:

| Dato | Política |
|---|---|
| Campos clínicos (`REASON`, `NOTES`, `DIAGNOSIS`, `EVOLUTION_NOTE`, `VITAL_SIGNS`) | Last-writer-wins por campo. Cuando dos escritores cambiaron el mismo campo, gana uno y **ambos valores van a la auditoría**. No se descarta nada. |
| Estado del episodio | Solo hacia adelante; un evento de estado antiguo no cambia nada. |
| Adjuntos | Solo se agregan; idempotentes sobre el id del adjunto. |
| Reservas y recetas firmadas | No se resuelven en el borde: los eventos `BOOKING_REQUEST` y `PRESCRIPTION_SIGN` se rechazan con `SERVER_AUTHORITATIVE`, para que el cliente reintente directamente contra el servidor. Un cupo tomado es un `409`. |

### Seguridad y auditoría

- JWT bearer sin estado, validado contra Cognito. Los roles salen del claim `cognito:groups`: `patient`,
  `rural-doctor`, `specialist`, `auditor` (reporte de auditoría) y `network-manager` (el "Gestor de red" de RF-07 y RF-10:
  indicadores y lista de seguimiento). Atributos: `custom:center`, `custom:specialty`.
- La autorización se comprueba donde se toca el dato (`@PreAuthorize` y `AccessPolicy.requirePatientAccess` en los
  servicios), no solo en el controlador. Un profesional lee a un paciente solo mientras exista una relación de cuidado
  (se crea cuando abre un episodio o cuando se le agenda una interconsulta).
- Toda lectura clínica exige la cabecera `X-Access-Reason`. La lectura se guarda en el almacén de auditoría antes de
  devolver el dato.
- **Toda solicitud rechazada se audita** (`ACCESS_DENIED`, con quién, qué solicitud y el motivo que alegó), ya sea que
  fallara una regla de rol, de acceso al paciente, de propiedad o una regla de la cadena de filtros como la del actuator.
  Una lectura rechazada nunca se registra como lectura.
- `GET /api/audit/resources/{type}/{id}/reads` (solo auditor) responde "quién vio esto y por qué" con un reporte
  determinista: orden fijo, sin hora de generación, una versión y un SHA-256 sobre las entradas. El reporte de un conjunto
  fijo de evidencia está versionado en [docs/audit/who-read-report.json](docs/audit/who-read-report.json); un test falla si el
  código deja de producir exactamente ese archivo, y `scripts/generate-audit-report.sh` lo regenera.
- La analítica nunca lee tablas transaccionales: se alimenta de eventos y guarda solo un hash con clave del paciente.

### Historial del legado

El sistema legado es de solo lectura y nunca se modifica. `GET /api/patients/{id}/legacy-history`:

1. Si hay una copia vigente en el read model, se sirve (`source: VIEW`).
2. Si no, se consulta el legado directamente a través de `LegadoClient`, con timeout de 6 s y un circuit breaker
   (Resilience4j); luego `LegacyTranslator` lo traduce y se guarda con un TTL explícito (`source: LEGACY_DIRECT`).
3. Si el legado no responde y existe una copia vencida, se sirve esa copia (`source: STALE_VIEW`); si no hay ninguna, `503`.

La respuesta y las cabeceras `X-Data-Freshness`, `X-Data-Expires-At` y `X-Data-Source` muestran siempre qué tan fresco es el dato.
El read model es un caché Caffeine acotado por instancia. Los datos personales del payload del legado (nombre, dirección) se quedan en el traductor.

### Observabilidad

- **Salud:** las sondas de liveness y readiness están abiertas (`/actuator/health/**`); el resto de endpoints del actuator
  exige el rol `network-manager`.
- **Métricas:** Micrometer, consultadas en `/actuator/prometheus`. La latencia HTTP usa buckets fijos en 100 ms, 300 ms, 1 s, 2 s
  y 6 s, de modo que los objetivos se pueden calcular sin alta cardinalidad. Métricas propias: `redsalud.legado.history.read`
  (timer con p95, etiquetado por origen), `redsalud.sync.events` (por estado) y `redsalud.access.denied`.
- **Trazas:** OpenTelemetry a través de Micrometer Tracing, exportadas por OTLP a un colector en la misma región. El muestreo es
  del 10 % por defecto. Los spans no llevan cuerpos de solicitud, cabeceras ni motivos de acceso; los ids de traza y de span
  aparecen en los logs.
- **SLO como código:** [ops/slo/prometheus-rules.yml](ops/slo/prometheus-rules.yml) define el SLO de disponibilidad de 99,5 % mensual
  (presupuesto de error de unas 3 h 36 min por cada 30 días) con alertas de velocidad de consumo en varias ventanas, y las
  alertas de latencia del historial del legado (p95 <= 2 s desde la vista, <= 6 s directo). Las reglas son YAML válido pero no
  se han pasado por `promtool`.

## Cómo correrlo

Necesita Java 21 y Docker (para PostgreSQL).

```bash
docker compose up -d                      # PostgreSQL en localhost:5432
./mvnw spring-boot:run                    # el perfil "dev" es el predeterminado
```

| Variable | Valor por defecto en `dev` | Obligatoria en otros perfiles |
|---|---|---|
| `DB_USER`, `DB_PASSWORD` | `andina` / `andina` (coinciden con `docker-compose.yml`) | sí |
| `COGNITO_ISSUER_URI` | valor de relleno, se resuelve de forma perezosa | sí |
| `ANALYTICS_PSEUDONYM_KEY` | clave solo para desarrollo | sí |
| `OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | opcional |
| `TRACING_SAMPLING_PROBABILITY` | `0.1` | opcional |
| `OTLP_METRICS_EXPORT_ENABLED` | `false` (las métricas se consultan por scraping) | opcional |

`dev` usa stubs locales: adjuntos en disco, un almacén de auditoría respaldado por la base de datos, un despachador de
farmacia que solo escribe en el log y un cliente del legado de mentira.

```bash
./mvnw verify                             # tests + verificación de cobertura (líneas >= 70 %)
```

Los tests corren sobre H2 en modo PostgreSQL (`application-test.yml`), así que no hace falta Docker. El reporte de cobertura está en
`target/site/jacoco/index.html`.

### Integración continua

[.github/workflows/ci.yml](.github/workflows/ci.yml) ejecuta `./mvnw verify` (tests, reglas de módulos y de arquitectura, el
reporte de auditoría versionado y la barrera de cobertura) en cada pull request y en cada push a `main`, con Java 21, caché de
Maven, permisos mínimos y los reportes como artefacto. Dependabot propone cada semana actualizaciones de Maven y de GitHub Actions.
El despliegue y el rollback forman parte del trabajo de AWS.

### Tests

| Área | Test |
|---|---|
| Reglas de módulos y de arquitectura | `ModularityTests`, `ArchitectureTests` |
| Doble reserva | `BookingTests` (secuencial, reenvío, 8 intentos concurrentes, 409), `SlotTests` |
| Idempotencia de la sincronización | `EpisodeTests`, `SyncResilienceTests`: el mismo lote tres veces en distinto orden, y otra vez tras reiniciar la aplicación sobre una base en archivo |
| Política de conflictos, ambos caminos | `EpisodeTests`: ambos valores auditados; reservas y recetas rechazadas |
| Auditoría de lecturas y de denegaciones | `AuditTrailTests`, `AccessDenialAuditTests` (cada tipo de denegación queda registrada), y las aserciones de auditoría de las demás suites |
| Reporte de auditoría versionado | `AuditReportGoldenTests` |
| Outbox | `PrescriptionTests`: una farmacia caída no pierde nada y el mensaje se entrega al reenviar |
| Máquina de estados, ACL, indicadores | `ConsultTests`, `Legacy*Tests` (copia vencida, circuit breaker, timeout), `IndicatorsTests` |
| Observabilidad | `ObservabilityTests` (sondas, buckets del SLO, reglas de acceso del actuator) |

## Cobertura de requisitos

| RF | Estado |
|---|---|
| RF-01 roles | Roles y atributos a partir de los grupos y claims de Cognito. El registro de usuarios es de Cognito; no hay tabla de perfiles. |
| RF-02 agenda | Hecho. |
| RF-03 videoconsulta | Ciclo de vida del episodio con sala de espera, admisión y asistencia. El servicio de señalización de video no está construido. |
| RF-04 adjuntos | Hecho sobre almacenamiento local; S3 con KMS está pendiente. |
| RF-05 receta | Hecho; el lado de la farmacia es un stub hasta tener SQS. |
| RF-06 historial del legado | Hecho. |
| RF-07 interconsulta | Estados, alertas en cada cambio y la lista de seguimiento (`GET /api/interconsultas/pending`): interconsultas no cerradas y sin movimiento durante un tiempo dado (24 h por defecto). |
| RF-08 auditoría | Hecho, incluidas las solicitudes rechazadas. |
| RF-09 modo degradado | Contrato del servidor hecho y probado; el cliente con la cola local no está construido. |
| RF-10 indicadores | Hecho. |

Las vistas BPMN de los cuatro procesos, con las fichas de tareas y la lista de divergencias frente a la propuesta del caso,
están en [docs/bpmn.md](docs/bpmn.md).

## Pendiente (AWS, planificado a continuación)

- Implementación de `AdjuntoStorage` con S3 + KMS; bucket sin réplica entre regiones.
- Implementación de `AuditStore` con S3 Object Lock (modo compliance).
- Publicador SQS detrás de `PharmacyDispatcher` (el outbox ya lo alimenta), con cola de mensajes muertos.
- ElastiCache o DynamoDB como segundo nivel del read model.
- Configuración de región única en `application.yml` y la guarda que impide que un cliente AWS use otra región.
- Perfil `prod`, TLS y claves KMS por contexto con rotación.
- Reglas de retención (larga para historial y auditoría, con expiración para adjuntos temporales) como políticas de ciclo de vida de S3.
- El dashboard del SLO, una sonda externa para caídas totales y el pipeline de despliegue con rollback en 10 minutos o menos.

Otros límites conocidos: los objetivos de rendimiento del legado (p95 <= 2 s en la vista, <= 6 s directo) se hacen cumplir en la
consulta directa con un timeout y se miden con un timer, pero no se han probado bajo carga; varias consultas simultáneas por
el mismo paciente consultan cada una al legado.

## ADRs

### ADR-01: Aurora PostgreSQL Multi-AZ como almacén transaccional único

- **Contexto:** Agenda, episodio, receta e interconsulta necesitan consistencia fuerte (sin doble reserva, recetas firmadas que
  nunca cambian) y comparten transacciones en algunos flujos. Se consideró usar DynamoDB por contexto.
- **Decisión:** Un clúster Aurora PostgreSQL Multi-AZ, un esquema por módulo, para el dominio clínico transaccional. Los perfiles
  de identidad también viven en el esquema `identidad`; Cognito sigue siendo el proveedor de identidad. DynamoDB es solo un
  candidato para el segundo nivel de caché del read model del legado.
- **Consecuencias:** Transacciones reales y restricciones únicas. Un solo clúster que operar, respaldar y mantener en una región.
  El aislamiento entre módulos lo imponen Modulith y la regla de no tener relaciones entre esquemas, no bases de datos separadas.
  Separar un módulo en su propia base de datos más adelante significa reemplazar su esquema por su propio datasource.

### ADR-02: CQRS + capa anticorrupción para el historial del legado

- **Contexto:** El legado no se puede modificar y solo ofrece una extracción SOAP nocturna y una base de lectura. Los
  especialistas necesitan el historial en unos 2 segundos, y el proveedor del legado puede cambiar.
- **Decisión:** Las lecturas van a un read model que guarda una copia canónica con TTL explícito y fecha de caducidad visible.
  Solo ante un fallo de caché o una caducidad se consulta el legado directamente (timeout de 6 s, circuit breaker) y el
  resultado se traduce y se guarda. Todos los formatos, códigos y datos personales del legado se quedan en `LegacyTranslator`.
- **Consecuencias:** El legado sigue siendo la fuente de verdad; la copia es eventualmente consistente y dice cuándo caduca.
  Cuando el legado está caído se sirve una copia vencida marcada `STALE_VIEW` en lugar de fallarle al especialista.
  Un cambio de proveedor solo toca `LegadoClient` y `LegacyTranslator` (como máximo un módulo, sin cambiar el dominio).

### ADR-03: Ids generados en el cliente + API idempotente

- **Contexto:** 5 centros tienen enlaces de 2 Mbps con cortes diarios. Los clientes reintentan y no deben duplicar registros.
- **Decisión:** El cliente crea ids ULID/UUID y un sello de tiempo del centro. Las escrituras son idempotentes sobre ese id; los
  eventos de sincronización llevan un id de evento y una secuencia por agregado, y su resultado se guarda en la base de datos.
  Los campos clínicos usan last-writer-wins y conservan el valor perdedor en la auditoría. Las reservas y las recetas firmadas
  las decide solo el servidor. La idempotencia es una propiedad de la base de datos (claves primarias y la tabla de eventos
  aplicados), no del controlador.
- **Consecuencias:** Reintentos seguros y cero datos duplicados, también tras un reinicio o con varias instancias. Los relojes de
  los centros importan para last-writer-wins, por eso siempre se conservan ambos valores. Las operaciones que decide el servidor
  necesitan conectividad.

### ADR-04: Auditoría append-only en S3 Object Lock

- **Contexto:** El sistema debe poder probar quién leyó qué documento clínico y por qué, y a quién se le negó el acceso.
- **Decisión:** Cada lectura y cada solicitud rechazada se convierte en un registro de auditoría que se guarda antes de
  responder, a través del puerto `AuditStore`. Producción escribe en S3 Object Lock en modo compliance; dev usa una tabla local
  a la que el código solo agrega.
- **Consecuencias:** Evidencia que nadie puede alterar, ni siquiera los administradores. Una lectura se rechaza si falta el motivo.
  El reporte es reproducible y está versionado. El adaptador de S3 está pendiente.

### ADR-05: Región única, sin réplicas externas

- **Contexto:** Los datos clínicos deben permanecer en el país.
- **Decisión:** Una sola región de AWS configurada en `application.yml`; ningún cliente de AWS puede apuntar a otra; el bucket
  de adjuntos no tiene réplica entre regiones; ningún respaldo ni exportación sale de la región.
- **Consecuencias:** La residencia se cumple por diseño, no por política escrita. La disponibilidad depende de Multi-AZ dentro
  de la región, así que una caída regional completa significa indisponibilidad. Pendiente junto con el trabajo de AWS.

### ADR-06: Monolito modular con límites que rompen el build

- **Contexto:** 14 centros, 9 especialistas y un equipo pequeño. Un canal nuevo (la app móvil del paciente) debe reutilizar al
  menos el 80 % de los servicios de dominio.
- **Decisión:** Un solo desplegable con un módulo por contexto delimitado, verificado en cada build por Spring Modulith y
  ArchUnit (sin acceso a las entrañas ni a las tablas de otro módulo, sin relaciones JPA, entidades privadas a su módulo).
  Un módulo se extrae solo cuando un atributo de calidad distinto lo exige: la señalización de video o la ingesta de auditoría.
- **Consecuencias:** Un solo despliegue, un límite transaccional donde hace falta y ninguna red entre contextos. Extraer un
  módulo más adelante es posible porque ya habla solo por su API pública y por eventos; el costo es que todos los módulos
  escalan juntos hasta entonces. La pregunta "¿por qué este contexto no se extrajo?" tiene aquí su respuesta: ninguno necesita
  todavía un perfil distinto de disponibilidad, latencia o escalado.

### ADR-07: Controladores en un solo módulo `api`, orquestadores solo entre módulos

- **Contexto:** Un controlador por módulo multiplica los puntos de entrada y dispersa el manejo de errores y las reglas de seguridad.
- **Decisión:** Todos los controladores viven en el módulo hoja `api` (cuatro) con un único manejador de excepciones. Los módulos
  exponen servicios, nunca controladores. Un caso de uso que toca un módulo va controlador -> servicio; un orquestador existe
  solo cuando un caso de uso necesita dos módulos en una transacción (hoy: agendar una interconsulta).
- **Consecuencias:** Menos puntos de entrada y un solo lugar donde se manejan y se auditan los errores y las denegaciones. El
  módulo `api` depende de todos los demás, así que nada puede depender de `api` (lo impone la verificación de módulos).

## Supuestos por confirmar

- El id del paciente es el `sub` de Cognito (un UUID), y el código de paciente del legado es ese mismo UUID (una línea en
  `LegacyHistoryService`).
- Los atributos personalizados de Cognito llegan en el token que recibe la API; normalmente solo vienen en el ID token.
- Las marcas de tiempo del legado están en `America/Bogota`.
- Solo los especialistas recetan, y solo el especialista asignado atiende y cierra una interconsulta.
