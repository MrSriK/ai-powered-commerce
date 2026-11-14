# AI-Powered Commerce Backend

REST API backend for an AI-powered e-commerce platform built with Spring Boot 4.1.0, Java 21, PostgreSQL 17, Flyway, and Spring AI.

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
   (Ensure `APP_INTERNAL_API_KEY` is configured in your environment or `.env` file, matching the application's configuration).

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
