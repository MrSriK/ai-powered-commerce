# AI-Powered Commerce Backend

REST API backend for an AI-powered e-commerce platform built with Spring Boot 4.1.0, Java 21, PostgreSQL 17, Flyway, and Spring AI (Google Gemini).

Features include product catalog management, cart management, checkout and order lifecycle, an automated order progression scheduler, and a conversational AI shopping assistant with bounded prompt injection protection. Authentication and identity management are powered by Auth0 using the Resource Owner Password Grant (ROPG) flow with role-based access control (`CUSTOMER` and `ADMIN`).

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Configuration & Environment Variables](#configuration--environment-variables)
- [Auth0 Setup Guide](#auth0-setup-guide)
- [Running the Application](#running-the-application)
- [API Documentation (Swagger UI)](#api-documentation-swagger-ui)
- [Running Tests](#running-tests)
- [Automated Order Progression Smoke-Test](#automated-order-progression-smoke-test)

---

## Prerequisites

- **Java 21** (JDK 21+)
- **Maven** (or use the bundled `./mvnw`)
- **Docker & Docker Compose** (for PostgreSQL database, Testcontainers test execution, and the background order progression scheduler)
- **Auth0 Tenant Account** (free tier is sufficient)
- **Google AI Studio API Key** (for Gemini LLM shopping assistant)

---

## Configuration & Environment Variables

The application externalizes all sensitive credentials and environment configurations. For local development, create a `.env` file in the project root by copying `.env.example`:

```bash
cp .env.example .env
```

Spring Boot loads `.env` automatically at startup via `spring.config.import=optional:file:.env[.properties]`.

### Environment Variable Reference

| Variable | Description | Example / Default | Secret |
| :--- | :--- | :--- | :---: |
| `SPRING_DATASOURCE_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5433/ai_powered_commerce` | No |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` | No |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` | **Yes** |
| `APP_AUTH_AUDIENCE` | Auth0 API Audience identifier | `https://api.ai-powered-commerce.com` | No |
| `AUTH0_CLIENT_ID` | Auth0 Application Client ID | `your-auth0-client-id` | No |
| `AUTH0_CLIENT_SECRET` | Auth0 Application Client Secret | `your-auth0-client-secret` | **Yes** |
| `AUTH0_DOMAIN` | Auth0 tenant domain (issuer derived automatically as `https://${AUTH0_DOMAIN}/`) | `dev-xxxxxx.us.auth0.com` | No |
| `AUTH0_CONNECTION` | Auth0 Database Connection name | `Username-Password-Authentication` | No |
| `APP_INTERNAL_API_KEY` | Shared secret header (`X-Internal-Api-Key`) for internal scheduler calls | `your-internal-api-key-here` | **Yes** |
| `SPRING_AI_GOOGLE_GENAI_API_KEY` | Google Gemini API key from AI Studio | `AIzaSy...` | **Yes** |
| `SPRING_AI_GOOGLE_GENAI_CHAT_MODEL` | Gemini LLM model name | `gemini-2.5-flash` | No |

---

## Auth0 Setup Guide

The application uses Auth0 for identity management and Resource Owner Password Grant authentication. Follow these steps to configure your Auth0 tenant:

### 1. Create the API (Audience)
1. In the Auth0 Dashboard, navigate to **Applications** → **APIs** → **Create API**.
2. **Name**: `AI-Powered Commerce API` (or any display name).
3. **Identifier**: `https://api.ai-powered-commerce.com` (this value corresponds to `APP_AUTH_AUDIENCE`).
4. **Signing Algorithm**: Ensure `RS256` is selected.
5. Click **Create**.

### 2. Configure the Application
1. Navigate to **Applications** → **Applications** and select your application (or create a **Regular Web Application**).
2. Note your **Domain**, **Client ID**, and **Client Secret**.
3. Under the **Settings** tab:
   - Scroll to **Advanced Settings** at the bottom → **Grant Types**.
   - Check the **Password** checkbox to enable Resource Owner Password Grant (ROPG).
   - Click **Save Changes**.

### 3. Verify Database Connection
1. Navigate to **Authentication** → **Database**.
2. Ensure `Username-Password-Authentication` exists and is enabled for your application.
3. In **Settings** → **Tenant Settings** → **General** → **API Authorization Settings**, verify that the **Default Directory** is set to `Username-Password-Authentication` (or matches your `AUTH0_CONNECTION` setting).

### 4. User Registration (Admin & Customer)
User creation is handled through the backend signup endpoint (`POST /api/auth/signup`), which provisions the user in Auth0 and creates the local database profile. No manual SQL updates or placeholder identities are required:

- **Register an Admin User:**
  ```bash
  curl -X POST http://localhost:3000/api/auth/signup \
    -H "Content-Type: application/json" \
    -d '{
      "firstName": "Admin",
      "lastName": "User",
      "email": "admin@example.com",
      "password": "SuperSecretPassword123!",
      "role": "ADMIN"
    }'
  ```
  *(Note: Password must be at least 15 characters).*

- **Register a Customer User:**
  ```bash
  curl -X POST http://localhost:3000/api/auth/signup \
    -H "Content-Type: application/json" \
    -d '{
      "firstName": "Jane",
      "lastName": "Doe",
      "email": "jane@example.com",
      "password": "CustomerSecretPassword123!",
      "role": "CUSTOMER"
    }'
  ```

- **Log In to Obtain an Access Token:**
  ```bash
  curl -X POST http://localhost:3000/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "email": "jane@example.com",
      "password": "CustomerSecretPassword123!"
    }'
  ```
  Response returns a Bearer access token:
  ```json
  {
    "access_token": "eyJhbGciOiJSUzI1NiIs...",
    "token_type": "Bearer",
    "expires_in": 86400
  }
  ```

---

## Running the Application

### 1. Start PostgreSQL
Launch the local PostgreSQL 17 database container (mapped to host port `5433` to prevent collision with any existing local PostgreSQL service):

```bash
docker compose up -d postgres
```

### 2. Start the Backend Server
Run the Spring Boot application using Maven:

```bash
./mvnw spring-boot:run
```

On startup:
- Flyway automatically applies migrations `V1` through `V10` in version order.
- `V10__seed_demo_data.sql` seeds catalog reference data: 5 categories (`Cameras`, `Audio`, `Personal Care`, `Skincare`, `Footwear`) and 5 active products referencing bundled images in the `images/` directory.
- Hibernate validates the schema against all JPA entities (`spring.jpa.hibernate.ddl-auto=validate`).
- The application begins listening on port `3000` (`http://localhost:3000`).

---

## API Documentation (Swagger UI)

Interactive OpenAPI 3.0 documentation and Swagger UI are served directly by the application:

- **Swagger UI**: [http://localhost:3000/swagger-ui.html](http://localhost:3000/swagger-ui.html)
- **OpenAPI 3.0 JSON Spec**: [http://localhost:3000/v3/api-docs](http://localhost:3000/v3/api-docs)

### Authorizing Requests in Swagger UI:
1. Click the **Authorize** button (top right).
2. For **bearerAuth**: Paste the `access_token` obtained from `POST /api/auth/login` (without the `Bearer ` prefix).
3. For **internalApiKey**: Enter your `APP_INTERNAL_API_KEY` value for calling `/internal/**` endpoints.

---

## Running Tests

The test suite consists of unit tests, architecture tests, and full integration tests executing against isolated PostgreSQL 17 containers via **Testcontainers**. Docker must be running.

### Run All Tests:
```bash
./mvnw test
```

*(Note for macOS Docker Desktop users: if `DOCKER_HOST` is required by your environment, run with `export DOCKER_HOST="unix://$HOME/.docker/run/docker.sock" && ./mvnw test`).*

### Run Full Verification (including Checkstyle/packaging):
```bash
./mvnw verify
```

---

## Automated Order Progression Smoke-Test

The automated order progression service runs on a 2-minute cadence via a lightweight Docker container (`scheduler`) that invokes the internal progression endpoint (`POST /internal/orders/advance`) authenticated by `X-Internal-Api-Key`.

### Prerequisites
1. Ensure PostgreSQL is running:
   ```bash
   docker compose up -d postgres
   ```
2. Start the Spring Boot backend application:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(Ensure `APP_INTERNAL_API_KEY` is configured in your `.env` file).*

3. Start the scheduler container:
   ```bash
   docker compose up -d scheduler
   ```

### Verification Steps
1. **Create an Order:**
   - Authenticate as a registered customer and check out a cart via `POST /api/orders` to create a new order in `PENDING` status.
   - Note the returned order `id`.

2. **Observe Progression Timeline (8 to 12 minutes total):**
   - **Minute 0–2 (Run 1):** The scheduler invokes `POST /internal/orders/advance`. The order transitions from `PENDING` to `CONFIRMED`.
   - **Minute 2–4 (Run 2):** The next scheduler invocation advances the order from `CONFIRMED` to `SHIPPED` and sets `shipped_at` to the current timestamp.
   - **Minute 4–9:** The order remains in `SHIPPED` status while the configured `app.orders.delivery-delay` (default: 5 minutes) elapses.
   - **Minute 8–12 (Run 5 or 6):** Once `shipped_at` is at least 5 minutes in the past, the subsequent scheduler invocation advances the order from `SHIPPED` to `DELIVERED`.

3. **Verify Outcomes:**
   - Query the order status via `GET /api/orders/{id}` using the customer's JWT token to confirm the status has reached `DELIVERED`.
   - Inspect the scheduler container logs to observe the HTTP 200 responses with progression counts:
     ```bash
     docker compose logs -f scheduler
     ```
     Expected log output shows successful JSON responses:
     ```json
     {"confirmed":1,"shipped":0,"delivered":0,"failed":0}
     ...
     {"confirmed":0,"shipped":1,"delivered":0,"failed":0}
     ...
     {"confirmed":0,"shipped":0,"delivered":1,"failed":0}
     ```

4. **Stop scheduler when done:**
   ```bash
   docker compose stop scheduler
   ```
