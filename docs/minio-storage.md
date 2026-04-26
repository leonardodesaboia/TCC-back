# Storage com MinIO — Documentacao Tecnica

Modulo responsavel por todo upload, download e gerenciamento de arquivos da AllSet, usando MinIO como backend S3-compativel.

---

## Indice

1. [Visao geral e motivacao](#1-visao-geral-e-motivacao)
2. [Por que MinIO](#2-por-que-minio)
3. [Estrategia de upload](#3-estrategia-de-upload)
4. [Buckets e politicas de acesso](#4-buckets-e-politicas-de-acesso)
5. [Chaves de objeto vs URLs](#5-chaves-de-objeto-vs-urls)
6. [Endpoints novos e refatorados](#6-endpoints-novos-e-refatorados)
7. [StorageService — interface publica](#7-storageservice--interface-publica)
8. [Validacao de MIME e tamanho](#8-validacao-de-mime-e-tamanho)
9. [Configuracao e variaveis de ambiente](#9-configuracao-e-variaveis-de-ambiente)
10. [Integracao com docker-compose](#10-integracao-com-docker-compose)
11. [Estrutura de pacotes](#11-estrutura-de-pacotes)
12. [Mudancas em modulos existentes](#12-mudancas-em-modulos-existentes)
13. [Escopo fora deste plano](#13-escopo-fora-deste-plano)

---

## 1. Visao geral e motivacao

Hoje todos os campos de arquivo no banco (`avatar_url`, `professional_documents.file_url`, `order_photos.url`, `messages.attachment_url`, `service_areas.icon_url`, etc.) recebem uma **string qualquer do cliente** — sem validacao de MIME, sem limite de tamanho, sem controle de acesso. Isso e fraco, inseguro e impossivel de auditar.

Este modulo cria um **ponto unico de storage** (`StorageService`) e expoe endpoints de upload aninhados ao recurso dono do arquivo, com validacao completa antes de gravar.

---

## 2. Por que MinIO

| Criterio | Decisao |
|---|---|
| Custo | MinIO e gratuito e roda localmente no `docker compose` |
| Compatibilidade | API 100% S3-compativel — trocar para AWS S3 em producao e so mudar configuracao |
| Testes | Testcontainers tem `MinIOContainer` oficial — sem mock, integracoes reais |
| Privacidade | Nao precisa de conta AWS para desenvolvimento |

---

## 3. Estrategia de upload

O cliente envia `multipart/form-data` para o backend. O backend valida MIME e tamanho, faz o `putObject` no MinIO, e persiste a **chave do objeto** (nao a URL) no banco.

```
[Cliente] POST /api/v1/.../upload  (multipart/form-data)
                │
                ▼
           Spring Controller
                │  @RequestPart MultipartFile file
                ▼
           StorageService.upload(bucket, keyPrefix, file)
                ├── valida tamanho (max por bucket)
                ├── detecta MIME real (magic bytes)
                ├── valida MIME permitido para o bucket
                ├── gera chave: {keyPrefix}/{UUID}.{ext}
                └── minioClient.putObject(...)
                │
                ▼
           Persiste chave no banco (ex: professional_documents.file_url)
                │
                ▼
           Retorna StorageRefResponse { key, downloadUrl, urlExpiresAt }
```

A estrategia de **presigned PUT** (cliente sobe direto no MinIO) esta disponivel como `StorageService.generatePresignedUpload(...)` para uso futuro quando o MinIO estiver exposto publicamente (ex: app mobile).

---

## 4. Buckets e politicas de acesso

| Bucket logico | Bucket real (com prefixo) | Visibilidade | MIMEs aceitos | Tamanho max |
|---|---|---|---|---|
| `AVATARS` | `{prefix}-avatars` | Privado (presigned) | `image/jpeg`, `image/png` | 5 MB |
| `DOCUMENTS` | `{prefix}-documents` | Privado (presigned) | `image/jpeg`, `image/png`, `application/pdf` | 5 MB |
| `ORDER_PHOTOS` | `{prefix}-order-photos` | Privado (presigned) | `image/jpeg`, `image/png` | 5 MB |
| `CHAT_ATTACHMENTS` | `{prefix}-chat-attachments` | Privado (presigned) | `image/jpeg`, `image/png` | 5 MB |
| `CATALOG_ICONS` | `{prefix}-catalog-icons` | **Publico** (leitura anonima) | `image/png`, `image/svg+xml` | 1 MB |
| `DISPUTE_EVIDENCES` | `{prefix}-dispute-evidences` | Privado (presigned) | `image/jpeg`, `image/png` | 5 MB |
| `DATA_EXPORTS` | `{prefix}-data-exports` | Privado (presigned) | `application/zip` | 100 MB |

### Inicializacao automatica no startup

`MinioBucketInitializer` (`ApplicationRunner`) cria todos os buckets do enum no startup e aplica as policies. Em producao, desabilitar via `MINIO_AUTO_CREATE_BUCKETS=false` se o bucket e provisionado por DevOps.

---

## 5. Chaves de objeto vs URLs

**Decisao: persistir chaves, nao URLs absolutas.**

- URLs presigned expiram (TTL curto de 15 min por padrao). Gravar no banco algo que expira e incorreto.
- Chaves sao estaveis e portaveis — se o endpoint do MinIO mudar ou migrar para S3, as chaves continuam validas.
- Para buckets publicos, a URL e montada sob demanda no mapper: `{publicEndpoint}/{bucket}/{key}`.

### Estrutura das chaves

```
avatars/{userId}/{uuid}.{ext}
documents/{professionalId}/{docType}/{uuid}.{ext}
order-photos/{orderId}/{photoType}/{uuid}.{ext}
chat-attachments/{conversationId}/{messageId}.{ext}
catalog-icons/areas/{areaId}.{ext}
catalog-icons/categories/{categoryId}.{ext}
dispute-evidences/{disputeId}/{uuid}.{ext}
data-exports/{userId}/{requestId}.zip
```

A extensao e derivada do MIME detectado por magic bytes, nao do nome do arquivo enviado pelo cliente.

---

## 6. Endpoints novos e refatorados

| Metodo | Caminho | Tipo | Acesso | Descricao |
|---|---|---|---|---|
| `POST` | `/api/users/{id}/avatar` | multipart | Proprio usuario ou admin | Upload de avatar |
| `DELETE` | `/api/users/{id}/avatar` | — | Proprio usuario ou admin | Remove avatar atual |
| `POST` | `/api/v1/professionals/{professionalId}/documents` | multipart (refatorado) | Proprio profissional ou admin | Upload de documento KYC |
| `POST` | `/api/v1/orders/{id}/photos` | multipart | Cliente dono do pedido | Anexa foto do problema (antes da 1a proposta) |
| `POST` | `/api/v1/orders/{id}/complete` | multipart (refatorado) | Profissional do pedido | Foto comprobatoria de conclusao |
| `POST` | `/api/v1/conversations/{id}/messages/image` | multipart | Participante da conversa | Mensagem com imagem |
| `PUT` | `/api/v1/service-areas/{id}/icon` | multipart | Admin | Icone da area de servico |
| `DELETE` | `/api/v1/service-areas/{id}/icon` | — | Admin | Remove icone |
| `PUT` | `/api/v1/service-categories/{id}/icon` | multipart | Admin | Icone da categoria |
| `DELETE` | `/api/v1/service-categories/{id}/icon` | — | Admin | Remove icone |
| `POST` | `/api/v1/disputes/{id}/evidences/photo` | multipart | Participante ou admin | Evidencia por foto (plano 5 fase 2) |

**Sem endpoint generico `/api/v1/storage/*`** — todo upload e aninhado ao recurso dono, garantindo autorizacao correta pelo controller do dominio.

### Exemplo de response (StorageRefResponse)

```json
{
  "key": "professionals/abc/cnh/xyz.jpg",
  "downloadUrl": "http://localhost:9000/allset-dev-documents/professionals/abc/cnh/xyz.jpg?X-Amz-Signature=...",
  "urlExpiresAt": "2026-04-25T15:15:00Z"
}
```

Para buckets publicos (icones de catalogo), `downloadUrl` e uma URL publica sem assinatura e `urlExpiresAt` e null.

---

## 7. StorageService — interface publica

```
StorageService
├── upload(bucket, keyPrefix, file) -> StoredObject
├── upload(bucket, keyPrefix, inputStream, size, contentType, filename) -> StoredObject
├── generateDownloadUrl(bucket, key) -> String (presigned ou publica)
├── generatePresignedUpload(bucket, keyPrefix, contentType) -> PresignedUpload
├── delete(bucket, key) -> void (idempotente)
└── exists(bucket, key) -> boolean
```

Implementacao: `MinioStorageService` com `MinioClient` injetado.

### Exclusao apos commit

Para evitar deletar arquivo quando a transacao e revertida, a exclusao e disparada via `@TransactionalEventListener(phase = AFTER_COMMIT)`. Se o MinIO falhar ao deletar, o arquivo fica orfao — um job futuro de garbage collection resolve isso.

---

## 8. Validacao de MIME e tamanho

O `Content-Type` enviado pelo cliente nao e confiavel. A validacao real usa **magic bytes**:

1. Ler os primeiros bytes do arquivo.
2. `URLConnection.guessContentTypeFromStream(InputStream)` — suficiente para JPEG/PNG/PDF no MVP.
3. Se o MIME detectado nao estiver no `StorageBucket.allowedMimeTypes()` -> `InvalidFileTypeException` (400).
4. Se o tamanho exceder o limite do bucket -> `FileTooLargeException` (413).

O Spring Boot tem limite global de upload em `spring.servlet.multipart.max-file-size`. Para aceitar ate 5 MB por arquivo, ajustar:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 15MB
```

O limite efetivo por bucket e validado pelo `StorageService` — o limite do Spring e apenas o teto bruto da requisicao.

---

## 9. Configuracao e variaveis de ambiente

Isoladas em `MinioProperties` (`@ConfigurationProperties(prefix = "minio")`) — separado do `AppProperties` existente.

| Variavel | Default (dev) | Descricao |
|---|---|---|
| `MINIO_ENDPOINT` | `http://localhost:9000` | URL interna do MinIO (usada pelo backend) |
| `MINIO_PUBLIC_ENDPOINT` | `http://localhost:9000` | URL publica para presigned URLs e buckets publicos |
| `MINIO_ACCESS_KEY` | `minioadmin` | Credencial |
| `MINIO_SECRET_KEY` | `minioadmin` | Credencial |
| `MINIO_REGION` | `us-east-1` | Simbolico (MinIO ignora, SDK exige) |
| `MINIO_BUCKET_PREFIX` | `allset` | Prefixo aplicado a todos os buckets |
| `MINIO_AUTO_CREATE_BUCKETS` | `true` | Criar buckets no startup |
| `MINIO_PRESIGNED_URL_TTL_MINUTES` | `15` | TTL das URLs de download privado |
| `MINIO_PRESIGNED_URL_TTL_LONG_HOURS` | `24` | TTL para downloads grandes (export LGPD) |
| `MINIO_MAX_AVATAR_SIZE_MB` | `5` | Limite por avatar |
| `MINIO_MAX_DOCUMENT_SIZE_MB` | `5` | Limite por documento KYC |
| `MINIO_MAX_ORDER_PHOTO_SIZE_MB` | `5` | Limite por foto de pedido |
| `MINIO_MAX_CHAT_ATTACHMENT_SIZE_MB` | `5` | Limite por anexo de chat |
| `MINIO_MAX_CATALOG_ICON_SIZE_MB` | `1` | Limite por icone de catalogo |
| `MINIO_MAX_DISPUTE_EVIDENCE_SIZE_MB` | `5` | Limite por evidencia de disputa |
| `MINIO_MAX_DATA_EXPORT_SIZE_MB` | `100` | Limite por export LGPD |
| `MINIO_API_PORT` | `9000` | Porta local do MinIO (somente compose) |
| `MINIO_CONSOLE_PORT` | `9001` | Porta do console web (somente compose) |

---

## 10. Integracao com docker-compose

Adicionar servico `minio` ao `docker-compose.yml` com volume persistente `minio_data`. O servico `app` passa a depender do MinIO via `depends_on: minio: condition: service_healthy`.

Console web do MinIO acessivel em `http://localhost:9001` (login: `minioadmin`/`minioadmin` em dev).

---

## 11. Estrutura de pacotes

```
shared/storage/
├── service/
│   ├── StorageService.java              # Interface
│   └── MinioStorageService.java         # Implementacao com MinIO SDK
├── domain/
│   ├── StorageBucket.java               # Enum — buckets, visibilidade, MIMEs
│   └── StoredObject.java                # Record — { bucket, key, contentType, sizeBytes }
├── config/
│   ├── MinioClientConfig.java           # @Bean MinioClient + @EnableConfigurationProperties
│   └── MinioBucketInitializer.java      # ApplicationRunner — cria buckets e aplica policies
├── exception/
│   ├── StorageUploadException.java      # 500
│   ├── InvalidFileTypeException.java    # 400
│   ├── FileTooLargeException.java       # 413
│   └── StorageObjectNotFoundException.java  # 404
└── event/
    └── ObjectDeletionRequestedEvent.java   # Disparado para limpeza pos-commit
```

---

## 12. Mudancas em modulos existentes

### DTOs que mudam

| DTO atual | Mudanca |
|---|---|
| `CreateProfessionalDocumentRequest` | Remove `fileUrl: String` — endpoint vira multipart |
| `ProfessionalDocumentResponse` | `fileUrl: String` -> `file: StorageRefResponse` |
| `CreateExpressOrderRequest` | Remove `photoUrl: String` (foto vai para `POST /orders/{id}/photos`) |
| `CompleteByProRequest` | Remove `photoUrl: String` — endpoint vira multipart |
| `OrderResponse` | Adiciona `photos: List<StorageRefResponse>` |
| `UpdateUserRequest` | Remove `avatarUrl: String` (usar endpoint dedicado) |
| `UserResponse` | `avatarUrl: String` -> `avatar: StorageRefResponse` (nullable) |
| `CreateServiceAreaRequest` / `UpdateServiceAreaRequest` | Remove `iconUrl: String` |
| `ServiceAreaResponse` | `iconUrl: String` -> `icon: StorageRefResponse` (nullable) |
| Idem para `ServiceCategory` | — |
| `MessageResponse` | `attachmentUrl: String` -> `attachment: StorageRefResponse` (nullable) |

### `GlobalExceptionHandler`

| Exception | HTTP |
|---|---|
| `InvalidFileTypeException` | 400 |
| `FileTooLargeException` | 413 |
| `StorageObjectNotFoundException` | 404 |
| `StorageUploadException` | 500 |
| `MaxUploadSizeExceededException` (Spring) | 413 |
| `MultipartException` (Spring) | 400 |

### Migration opcional (`V18__convert_storage_urls_to_keys.sql`)

Renomeia as colunas `*_url` para `*_key` para refletir que guardam chaves de objeto, nao URLs. Pode ser adiada — como hoje todas estao vazias, a coerencia e garantida desde o primeiro upload real.

---

## 13. Escopo fora deste plano

| Item | Pre-requisito |
|---|---|
| Migracao para AWS S3 em producao | DevOps prod — so mudar configuracao |
| CDN na frente do MinIO publico | Decisao de DevOps |
| Antivirus em uploads (ClamAV) | Analise de risco de produto |
| Resize/thumbnail server-side | Definir tamanhos canonicos com produto |
| Upload retomavel (resumable multipart S3) | Caso de uso real com arquivos grandes |
| Presigned PUT para mobile | Quando MinIO/S3 estiver publicamente acessivel |
| Garbage collection de objetos orfaos | Job dedicado em fase posterior |

---

*All Set — Projeto Integrador 1 — UNIFOR — 2026*
