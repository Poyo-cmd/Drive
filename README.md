# ☁ Drive

Un clon minimalista de Google Drive, full-stack. Monorepo con **backend Spring Boot
(Java 21)** y **frontend Vue 3**, que usa [Floci](https://github.com/floci-io/floci)
como emulador local de AWS: los archivos viven en **S3** y los metadatos (usuarios y
árbol de carpetas) en **DynamoDB**. No requiere cuenta de AWS ni credenciales reales.

Funcionalidad: registro/login con **JWT**, subida con barra de progreso y drag&drop,
carpetas anidadas, descarga, renombrar, y **papelera** (soft-delete con restaurar y
borrado definitivo). Cada usuario solo ve sus propios archivos.

```
drive/
├── backend/            # API REST en Java 21 + Spring Boot 3 + Spring Security
├── frontend/           # SPA en Vue 3 + Vite
├── docker-compose.yml  # floci + backend + frontend
└── README.md
```

## Arquitectura

```
  Navegador (JWT en Authorization)
     │  http
     ▼
  ┌─────────────┐   /api  ┌──────────────┐  AWS SDK v2   ┌──────────┐
  │  frontend   │ ──────► │   backend    │ ────────────► │  floci   │
  │ Vue3 +nginx │         │ Spring Boot  │  S3 + Dynamo  │ :4566    │
  └─────────────┘         └──────────────┘               └──────────┘
                                                          S3  → archivos (blobs)
                                                          DDB → usuarios + árbol
```

- **S3** (`drive-files`): el contenido binario de cada archivo en `blobs/{id}`.
- **DynamoDB** (`drive-nodes`): un registro por archivo o carpeta, con `ownerId`. El
  índice `parent-index` lista el contenido de una carpeta sin escanear toda la tabla.
- **DynamoDB** (`drive-users`): usuarios, con `username-index` para el login. La
  contraseña se guarda hasheada con BCrypt, nunca en texto plano.
- La raíz de cada usuario es virtual (`root#<userId>`), así dos usuarios no se mezclan.
- Bucket y tablas se **crean solos** al arrancar (`StorageBootstrap`), con reintentos
  para esperar a que Floci esté listo.

## Seguridad

- Autenticación stateless con JWT (HS256). El token lleva el id del usuario como subject.
- Todos los endpoints bajo `/api/files/**` exigen token; `/api/auth/**` es público.
- Cada operación valida que el recurso pertenezca al usuario autenticado (evita IDOR).

## Levantarlo al vuelo

Requisitos: Docker y Docker Compose.

```bash
mkdir -p ./floci-data
docker compose up --build
```

Abre **http://localhost:8088**, crea una cuenta y listo.

| Servicio | URL                   |
| -------- | --------------------- |
| Frontend | http://localhost:8088 |
| Backend  | http://localhost:8080 |
| Floci    | http://localhost:4566 |

> En producción, `DRIVE_JWT_SECRET` debe ir por variable de entorno con un valor
> largo y secreto, nunca el de ejemplo del repo.

## Desarrollo local (sin Docker para la app)

```bash
# 1) Emulador AWS
docker run --rm -p 4566:4566 floci/floci:latest

# 2) Backend (JDK 21 + Maven 3.9+)
cd backend && mvn spring-boot:run

# 3) Frontend (Node 20+)
cd frontend && npm install && npm run dev   # http://localhost:5173
```

## API

| Método   | Ruta                              | Descripción                         |
| -------- | --------------------------------- | ----------------------------------- |
| `POST`   | `/api/auth/register`              | Crea cuenta, devuelve token         |
| `POST`   | `/api/auth/login`                 | Inicia sesión, devuelve token       |
| `GET`    | `/api/files?parentId=root`        | Lista carpetas y archivos           |
| `POST`   | `/api/files` (multipart)          | Sube un archivo                     |
| `POST`   | `/api/files/folders`              | Crea una carpeta                    |
| `PATCH`  | `/api/files/{id}`                 | Renombra                            |
| `DELETE` | `/api/files/{id}`                 | Mueve a la papelera                 |
| `DELETE` | `/api/files/{id}?permanent=true`  | Borra definitivamente               |
| `POST`   | `/api/files/{id}/restore`         | Restaura desde la papelera          |
| `GET`    | `/api/files/trash`                | Lista la papelera                   |
| `DELETE` | `/api/files/trash`                | Vacía la papelera                   |
| `GET`    | `/api/files/{id}/download`        | Descarga el archivo                 |

Todas las rutas de `/api/files` requieren la cabecera `Authorization: Bearer <token>`.

## Stack

- Java 21 · Spring Boot 3.3 · Spring Security · JWT (jjwt) · AWS SDK v2 (S3 + DynamoDB)
- Vue 3 · Vite · nginx
- Floci (emulador AWS) · Docker Compose

## Qué falta para producción (a propósito, queda como ejercicio)

Paginación en listados, validación de tamaño/tipo de archivo, refresh tokens,
presigned URLs para descargas directas desde S3, multipart upload de S3 para archivos
grandes, tests de integración, y mover el provisioning a Terraform/CDK.

## Licencia

MIT.
