# Spring Boot SSO — Auth0 + GitHub + Google

A complete Spring Boot 3.2 application demonstrating **Single Sign-On (SSO)** with:

- 🔐 **Auth0** via OpenID Connect (OIDC) — full ID token, roles, silent refresh
- 🐙 **GitHub** via OAuth2 — avatar, username, public profile
- 🔵 **Google** via OpenID Connect (OIDC) — profile picture, email, name
- 🛡️ **Role-based access control** (ADMIN / USER) driven by Auth0 Actions
- 🔄 **Silent token refresh** — refresh_token auto-renew, no redirects
- 🚪 **Single logout** — Auth0 `/v2/logout` + session/cookie clear

---

## Project Structure

```
src/main/
├── java/com/example/sso/
│   ├── SsoOktaApplication.java          # @SpringBootApplication entry point
│   ├── config/
│   │   ├── SecurityConfig.java          # OAuth2 login, RBAC, logout, entry point
│   │   ├── Auth0RolesExtractor.java     # Reads https://my-app.com/roles JWT claim
│   │   └── TokenRefreshFilter.java      # Silent refresh filter (Auth0 only)
│   └── controller/
│       ├── HomeController.java          # GET / and /login
│       └── DashboardController.java     # Dashboard, admin, user-profile, /api/me
└── resources/
    ├── application.yml                  # All values via ${ENV_VAR} or defaults
    ├── application-local.yml            # ← GITIGNORED — real credentials for local dev
    └── templates/
        ├── home.html                    # Public: Auth0 + GitHub login buttons
        ├── dashboard.html               # Protected: profile, roles, token info
        ├── admin.html                   # ROLE_ADMIN only
        ├── user-profile.html            # ROLE_USER only
        └── access-denied.html           # 403 page
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- An [Auth0 account](https://auth0.com/) (free tier works)
- A [GitHub OAuth App](https://github.com/settings/developers) (free)

---

## Auth0 Setup

### 1. Create Application
1. Log in to [Auth0 Dashboard](https://manage.auth0.com/)
2. Go to **Applications → Create Application**
3. Choose **Regular Web Application**
4. Set **Allowed Callback URLs**: `http://localhost:8081/login/oauth2/code/okta`
5. Set **Allowed Logout URLs**: `http://localhost:8081/`
6. Set **Allowed Web Origins**: `http://localhost:8081`
7. Under **Advanced Settings → Grant Types**, enable **Refresh Token**
8. Copy **Client ID**, **Client Secret**, and your **Domain** (e.g. `my-testing-domain.us.auth0.com`)

### 2. Add Roles to Tokens via Auth0 Action

Auth0 does not inject roles into ID tokens by default. You must add an **Action** to the **Login flow**.

#### Create the Action
1. In Auth0 Dashboard → **Actions → Library → Create Action**
2. Choose **Build from scratch**, name it `Add Roles to Token`, trigger = **Login / Post Login**
3. Paste this code:

```javascript
exports.onExecutePostLogin = async (event, api) => {
  const namespace = 'https://my-app.com/roles';
  const roles = event.authorization?.roles ?? [];
  api.idToken.setCustomClaim(namespace, roles);
  api.accessToken.setCustomClaim(namespace, roles);
};
```

4. Click **Deploy**

#### Add the Action to the Login Flow
1. Go to **Actions → Flows → Login**
2. Drag your `Add Roles to Token` action between **Start** and **Complete**
3. Click **Apply**

#### Assign Roles to Your User
1. Go to **User Management → Users** → click your user
2. Click the **Roles** tab → **Assign Roles**
3. Create roles named `admin` and/or `user` first under **User Management → Roles**
4. Assign the desired roles to your user

> The roles will appear as `https://my-app.com/roles: ["admin", "user"]` in the ID token.  
> Spring Security maps them to `ROLE_ADMIN` and `ROLE_USER` automatically.

---

## GitHub OAuth App Setup

1. Go to [GitHub → Settings → Developer Settings → OAuth Apps](https://github.com/settings/developers)
2. Click **New OAuth App**
3. Set **Homepage URL**: `http://localhost:8081`
4. Set **Authorization callback URL**: `http://localhost:8081/login/oauth2/code/github`
5. Copy **Client ID** and generate a **Client Secret**

---

## Google OAuth App Setup

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create or select a project
3. Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**
4. Application type: **Web application**
5. Add **Authorized redirect URIs**: `http://localhost:8081/login/oauth2/code/google`
6. Copy **Client ID** and **Client Secret**

> Spring Boot has built-in Google provider support — no extra provider config needed.

---

## Local Configuration

### Required Properties

All credentials are read from environment variables defined in `application.yml`.
For local development, set them in `application-local.yml` (gitignored) **or** as OS environment variables.

#### Auth0

| Property | Env Var | Where to find it |
|---|---|---|
| `client-id` | `AUTH0_CLIENT_ID` | Auth0 Dashboard → Applications → your app → **Client ID** |
| `client-secret` | `AUTH0_CLIENT_SECRET` | Auth0 Dashboard → Applications → your app → **Client Secret** |
| `issuer-uri` | `AUTH0_DOMAIN` | Auth0 Dashboard → Applications → your app → **Domain** (e.g. `my-testing-domain.us.auth0.com`) |

#### GitHub

| Property | Env Var | Where to find it |
|---|---|---|
| `client-id` | `GITHUB_CLIENT_ID` | GitHub → Settings → Developer Settings → OAuth Apps → your app → **Client ID** |
| `client-secret` | `GITHUB_CLIENT_SECRET` | GitHub → Settings → Developer Settings → OAuth Apps → your app → **Client Secret** |

#### Google

| Property | Env Var | Where to find it |
|---|---|---|
| `client-id` | `GOOGLE_CLIENT_ID` | Google Cloud Console → APIs & Services → Credentials → your OAuth client → **Client ID** |
| `client-secret` | `GOOGLE_CLIENT_SECRET` | Google Cloud Console → APIs & Services → Credentials → your OAuth client → **Client Secret** |

#### App (optional)

| Property | Env Var | Default | Description |
|---|---|---|---|
| `app.base-url` | `APP_BASE_URL` | `http://localhost:8081` | Base URL used in Auth0 logout `returnTo` redirect |
| `server.port` | `SERVER_PORT` | `8081` | HTTP port the server listens on |

---

### Option A — `application-local.yml` (recommended for local dev)

Create `src/main/resources/application-local.yml` — this file is **gitignored**, safe to store real values:

```yaml
server:
  port: 8081

app:
  base-url: http://localhost:8081

spring:
  security:
    oauth2:
      client:
        registration:
          okta:
            client-id: YOUR_AUTH0_CLIENT_ID
            client-secret: YOUR_AUTH0_CLIENT_SECRET
          github:
            client-id: YOUR_GITHUB_CLIENT_ID
            client-secret: YOUR_GITHUB_CLIENT_SECRET
          google:
            client-id: YOUR_GOOGLE_CLIENT_ID
            client-secret: YOUR_GOOGLE_CLIENT_SECRET
        provider:
          okta:
            issuer-uri: https://YOUR_AUTH0_DOMAIN/
```

Run with:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Option B — Environment variables (CI/CD, Docker, production)

Set these before running the app:

```powershell
# PowerShell
$env:AUTH0_CLIENT_ID     = "your-auth0-client-id"
$env:AUTH0_CLIENT_SECRET = "your-auth0-client-secret"
$env:AUTH0_DOMAIN        = "your-tenant.us.auth0.com"
$env:GITHUB_CLIENT_ID    = "your-github-client-id"
$env:GITHUB_CLIENT_SECRET= "your-github-client-secret"
$env:GOOGLE_CLIENT_ID    = "your-google-client-id"
$env:GOOGLE_CLIENT_SECRET= "your-google-client-secret"
```

```bash
# bash / Linux / macOS
export AUTH0_CLIENT_ID=your-auth0-client-id
export AUTH0_CLIENT_SECRET=your-auth0-client-secret
export AUTH0_DOMAIN=your-tenant.us.auth0.com
export GITHUB_CLIENT_ID=your-github-client-id
export GITHUB_CLIENT_SECRET=your-github-client-secret
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
```

> **Never commit real credentials.** `.env`, `*.env`, and `application-local.yml` are all listed in `.gitignore`.

---

## Running the App

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Visit [http://localhost:8081](http://localhost:8081)

---

## Running with Docker

### Prerequisites
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) installed and running

### 1. Create your `.env` file

```bash
cp .env.example .env
# Edit .env and fill in all credentials
```

### 2. Build and start

```bash
docker compose up --build
```

The app will be available at [http://localhost:8081](http://localhost:8081).

### Other useful commands

```bash
docker compose up -d          # start in background (detached)
docker compose logs -f        # follow logs
docker compose down           # stop and remove container
docker compose build          # rebuild image without starting
```

### How the Dockerfile works

```
Stage 1 — Build  (eclipse-temurin:17-jdk-alpine)
  └─ mvn package -DskipTests  →  target/sso-okta-*.jar

Stage 2 — Runtime  (eclipse-temurin:17-jre-alpine)
  └─ copies fat JAR only
  └─ runs as non-root user (appuser)
  └─ exposes port 8081
  └─ reads all credentials from environment variables
```

> Secrets are **never baked into the image** — all credentials are injected at runtime via environment variables.

---

## Endpoints

| URL | Access | Description |
|-----|--------|-------------|
| `/` | Public | Home page — Auth0 & GitHub login buttons |
| `/dashboard` | Authenticated | Profile, roles, token expiry info |
| `/api/me` | Authenticated | User info + roles as JSON |
| `/user/profile` | `ROLE_USER` | User profile page |
| `/admin` | `ROLE_ADMIN` | Admin panel |
| `/access-denied` | Public | 403 error page |
| `/logout` | Authenticated | Auth0 single logout + session clear |

---

## How Roles Work

```
Auth0 Login Flow
      │
      ▼
"Add Roles to Token" Action
      │  sets https://my-app.com/roles: ["admin","user"]
      ▼
ID Token arrives at Spring Boot
      │
      ▼
Auth0RolesExtractor.extractAuthorities()
      │  reads claim → adds ROLE_ADMIN, ROLE_USER
      ▼
Spring Security RBAC (@PreAuthorize, hasRole())
```

---

## Tech Stack

- **Spring Boot 3.2**
- **Spring Security 6** — OAuth2 Client, OIDC
- **Thymeleaf** + `thymeleaf-extras-springsecurity6`
- **Auth0** (OIDC, refresh tokens, custom roles via Actions)
- **GitHub** (OAuth2, user profile)
- **Java 17**
