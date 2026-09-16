# Spring Boot SSO with Okta

A complete Spring Boot 3 application demonstrating **Single Sign-On (SSO)** using **Okta** as the Identity Provider via **OpenID Connect (OIDC)**.

## Features

- 🔐 SSO login via Okta OIDC
- 🛡️ Role-based access control (`ROLE_ADMIN`)
- 👤 User profile page showing JWT claims
- 🚪 Single logout (clears session)
- 🌐 Thymeleaf UI with Spring Security integration

## Project Structure

```
src/main/
├── java/com/example/sso/
│   ├── SsoOktaApplication.java        # Entry point
│   ├── config/
│   │   └── SecurityConfig.java        # Spring Security + OIDC config
│   └── controller/
│       ├── HomeController.java         # Public home & login trigger
│       └── DashboardController.java    # Protected dashboard + REST /api/me
└── resources/
    ├── application.yml                 # App & Okta config (uses env vars)
    └── templates/
        ├── home.html                   # Public landing page
        ├── dashboard.html              # Protected user dashboard
        └── admin.html                  # Admin-only page
```

## Prerequisites

- Java 17+
- Maven 3.8+
- An [Okta Developer Account](https://developer.okta.com/) (free)

## Okta Setup

1. Log in to [Okta Developer Console](https://developer.okta.com/)
2. Go to **Applications → Create App Integration**
3. Choose **OIDC - OpenID Connect** → **Web Application**
4. Set **Sign-in redirect URI** to: `http://localhost:8081/login/oauth2/code/okta`
5. Set **Sign-out redirect URI** to: `http://localhost:8081`
6. Copy the **Client ID**, **Client Secret**, and your **Okta Domain**

## Running the App

### 1. Set Environment Variables

```bash
# Copy the example file
cp .env.example .env

# Fill in your values:
# OKTA_CLIENT_ID=your-okta-client-id
# OKTA_CLIENT_SECRET=your-okta-client-secret
# OKTA_DOMAIN=dev-xxxxxxxx.okta.com
```

### 2. Run with Maven

```bash
# Pass env vars directly
OKTA_CLIENT_ID=xxx OKTA_CLIENT_SECRET=yyy OKTA_DOMAIN=dev-xxx.okta.com mvn spring-boot:run

# Or on Windows PowerShell:
$env:OKTA_CLIENT_ID="xxx"; $env:OKTA_CLIENT_SECRET="yyy"; $env:OKTA_DOMAIN="dev-xxx.okta.com"; mvn spring-boot:run
```

### 3. Open in Browser

Visit: [http://localhost:8081](http://localhost:8081)

## Endpoints

| URL | Access | Description |
|-----|--------|-------------|
| `/` | Public | Home page |
| `/login` | Public | Triggers Okta OIDC login |
| `/dashboard` | Authenticated | Shows user profile + JWT claims |
| `/api/me` | Authenticated | Returns user info as JSON |
| `/admin` | `ROLE_ADMIN` only | Admin panel |
| `/logout` | Authenticated | Clears session and redirects to `/` |

## Tech Stack

- **Spring Boot 3.2**
- **Spring Security 6**
- **Spring OAuth2 Client** (OIDC)
- **Thymeleaf** + **thymeleaf-extras-springsecurity6**
- **Java 17**
