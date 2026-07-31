# JavaSOC — Discord Clone

Backend completo de um clone do Discord em **Java 25 + Spring Boot 3.5**, construído em 6 fases progressivas. Realtime via WebSocket nativo, persistência JPA, e **voz por WebRTC com o Java como signaling server**.

## Stack

- Java 25 (Gradle toolchain) + Spring Boot 3.5
- WebSocket nativo (`spring-boot-starter-websocket`) — chat + signaling de voz no mesmo endpoint
- Spring Data JPA — PostgreSQL (prod) / H2 in-memory (dev)
- JWT HS256 (jjwt) + BCrypt (`spring-security-crypto`, sem Spring Security)
- Jackson para mensagens JSON

## Como rodar

```bash
gradle bootRun        # sobe em http://localhost:8080
gradle test           # 74 testes unitários
```

Abra `http://localhost:8080` (client de teste `src/main/resources/static/index.html`) em 2 abas, registre/login como `alice`/`bob` e use o painel.

Config (dev, H2): `src/main/resources/application.yml`. Para PostgreSQL troque o datasource e `ddl-auto` para `validate`.

## Endpoints REST

| Método | Rota | Descrição |
|--------|------|-----------|
| POST | `/auth/register` | Registra usuário (`username`, `email`, `password`) → `{token}` |
| POST | `/auth/login` | Login → `{token}` |
| GET | `/auth/me` | Perfil do usuário (Bearer token) |
| POST | `/api/attachments` | Upload de arquivo (multipart, até 10 MB, Bearer token) |
| GET | `/api/attachments/{id}/download` | Download do arquivo |
| GET | `/api/webrtc/config` | STUN/TURN servers para o `RTCPeerConnection` |

## Protocolo WebSocket (`/ws/chat`)

Todos os frames são JSON. Tipos em `MessageType`.

### Chat (Fases 1–5)

Client → server: `AUTH` (token), `HEARTBEAT`, `DIRECT_MESSAGE`, `SUBSCRIBE`, `UNSUBSCRIBE`, `CHANNEL_MESSAGE`, `EDIT_MESSAGE`, `DELETE_MESSAGE`, `REACT`, `UNREACT`.

Server → client: `AUTHENTICATED`, `DELIVERED`, `PUBLISHED`, `SUBSCRIBED`, `UNSUBSCRIBED`, `HISTORY`, `PRESENCE_UPDATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `REACTION_ADDED`, `REACTION_REMOVED`, `ERROR`.

### Voz (Fase 6) — signaling

O servidor **nunca toca no áudio**: só gerencia salas e reencaminha SDP/ICE entre peers da mesma sala (`server:channel`). Media trafega P2P.

| Tipo | Direção | Descrição |
|------|---------|-----------|
| `VOICE_JOIN` | C→S | Entrar em canal de voz (`server`, `channel`) |
| `VOICE_LEAVE` | C→S | Sair do canal de voz |
| `SDP_OFFER` / `SDP_ANSWER` / `ICE_CANDIDATE` | C→S→C | Relé para `to` (username); payload em `content` |
| `VOICE_JOINED` | S→C | Ack com lista de participantes |
| `VOICE_USER_JOINED` / `VOICE_USER_LEFT` | S→C | Anúncio de entrada/saída de peer |

Quem entra na sala é o *offerer* para cada participante existente; os demais apenas respondem (evita conexões duplicadas). Candidatos ICE são fila no client até o `remoteDescription` ficar pronto.

## Arquitetura (package-by-feature)

```
com.felipeb.discordclone
├── auth/          # registro/login, JwtService, PasswordHasher, UserService
├── user/          # entidade User
├── server/        # Server, Membership, Role, ServerBootstrap (seeder)
├── channel/       # Channel, Message, ChannelService, ChannelSeeder, ChannelType
├── reaction/      # Reaction + repositório
├── attachment/    # Attachment + upload/download REST
├── permission/    # PermissionService (READ_ONLY, ADMIN/OWNER, canais de voz)
├── presence/      # PresenceRegistry (online/idle/offline + scheduler)
├── session/       # SessionRegistry (username → WebSocketSession)
├── subscription/  # ChannelSubscriptions (join por canal, TOCTOU-safe)
├── broker/        # MessageBroker (fan-out para canal/usuário)
├── webrtc/        # VoiceRoomRegistry, VoiceSignalingHandler, config endpoint
├── api/           # DTOs: ChatMessage, OutgoingMessage, HistoryMessage, MessageType
└── chat/          # ChatWebSocketHandler (routing principal)
```

Pontos de integração são abstraídos por interface (`MessageBroker`, `ChannelSubscriptions`, `PresenceRegistry`, `VoiceRoomRegistry`) — prontos para virar Redis quando houver múltiplas instâncias.

## Fases

1. **Auth** — JWT + BCrypt, REST `/auth`.
2. **Servers & channels** — `Server`, `Channel`, `Membership`, `Role`, seeder padrão.
3. **Realtime** — WebSocket, broker, subscriptions, presença.
4. **Persistência** — JPA com `Message`, histórico via `JOIN FETCH` (sem `LazyInitializationException`).
5. **Ricos** — editar/apagar, reações, attachments, permissões por canal.
6. **Voz** — WebRTC com signaling server em Java, canais `VOICE`, salas P2P.
