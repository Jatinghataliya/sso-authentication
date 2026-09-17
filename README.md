# Spring Boot SSO — Auth0 + GitHub

A complete Spring Boot 3.2 application demonstrating **Single Sign-On (SSO)** with:

- 🔐 **Auth0** via OpenID Connect (OIDC) — full ID token, roles, silent refresh
- 🐙 **GitHub** via OAuth2 — avatar, username, public profile
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

## Local Configuration

Create `src/main/resources/application-local.yml` (this file is gitignored):

```yaml
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
        provider:
          okta:
            issuer-uri: https://YOUR_AUTH0_DOMAIN/
```

> **Do not commit this file.** It is listed in `.gitignore`.

---

## Running the App

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Visit [http://localhost:8081](http://localhost:8081)

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
