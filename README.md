# SeatBook: movie seat booking backend

Spring Boot REST API for browsing shows, holding seats, paying, and cancelling, built so that two people can never end up with the same seat.

**Stack:** Java 17, Spring Boot 3.3, Spring Security + JWT, Hibernate/JPA, MySQL, Redis, Docker. Tests use JUnit 5 and H2.

## How double booking is prevented

1. **Redis seat hold (fast filter).** Selecting seats does `SET key NX EX 300` per seat. Losers are rejected immediately without touching the database. If Redis is down the app fails open and relies on step 2.
2. **Conditional UPDATE in the database (source of truth).** One atomic statement flips the seats to `HELD` only where they are `AVAILABLE` (or their hold has expired). The row lock serializes concurrent callers. The service compares the updated row count with the number of seats requested and rolls back on a mismatch.
3. **Constraints and versions.** `UNIQUE(show_id, seat_label)` on `show_seats`, and `@Version` on the seat row.
4. **Idempotent payment.** `POST /bookings/{id}/pay` needs an `Idempotency-Key`. The booking row is locked (`SELECT ... FOR UPDATE`), the key is unique in `payments`, and a replay returns the original result instead of charging again.
5. **Auto-release.** A scheduled sweeper expires unpaid bookings after the hold window (5 minutes by default) and frees their seats. The seat map also treats expired holds as available immediately.

## Run it

```bash
docker compose up --build        # MySQL + Redis + the app on :8080
```

Or run only the dependencies and the app locally:

```bash
docker compose up -d mysql redis
mvn spring-boot:run
```

A first admin is created on startup: `admin@seatbook.local` / `admin12345` (override with `ADMIN_EMAIL`, `ADMIN_PASSWORD`). Set `JWT_SECRET` (32+ chars) outside local dev.

## Tests

```bash
mvn test
```

The same scenarios run twice: once with the hold store and once with it disabled, to show the database alone prevents double booking.

- 100 threads race for one seat: exactly one wins, the other 99 get `409`.
- 60 threads with overlapping seat sets: only one wins and no seat is shared.
- 20 concurrent payments with the same `Idempotency-Key`: one charge, identical results.
- Failed payment keeps the hold and can be retried with a new key.
- Expired hold cannot be paid (`410`), the sweeper frees the seat, someone else can take it.
- Cancelling frees seats; other users cannot cancel or pay your booking (`404`).

## API walkthrough

```bash
BASE=http://localhost:8080/api

# admin: create a movie, theatre (rows x seats) and a show (generates the seat grid)
ADMIN=$(curl -s $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@seatbook.local","password":"admin12345"}' | jq -r .token)

curl -s $BASE/admin/movies -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"title":"Inception","language":"en","durationMinutes":148,"description":"Dreams within dreams"}'
curl -s $BASE/admin/theatres -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"name":"PVR Noida","city":"Noida","rows":5,"seatsPerRow":10}'
curl -s $BASE/admin/shows -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -d '{"movieId":1,"theatreId":1,"startTime":"2030-01-01T18:30:00","price":250}'

# user: register, browse, hold, pay
TOKEN=$(curl -s $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Ankur","email":"ankur@example.com","password":"password123"}' | jq -r .token)

curl -s "$BASE/movies?page=0&size=12"
curl -s $BASE/movies/1/shows
curl -s $BASE/shows/1/seats

curl -s $BASE/shows/1/hold -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"seatLabels":["A1","A2"]}'

curl -s $BASE/bookings/1/pay -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)"
# add -H 'Content-Type: application/json' -d '{"simulateFailure":true}' to see a failed payment

curl -s $BASE/bookings/mine -H "Authorization: Bearer $TOKEN"
curl -s -X POST $BASE/bookings/1/cancel -H "Authorization: Bearer $TOKEN"
```

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | none |
| GET | `/api/movies`, `/api/movies/{id}`, `/api/movies/{id}/shows`, `/api/shows/{id}/seats` | none |
| POST | `/api/admin/movies`, `/api/admin/theatres`, `/api/admin/shows` | ADMIN |
| POST | `/api/shows/{id}/hold` | USER |
| POST | `/api/bookings/{id}/pay` (needs `Idempotency-Key` header) | USER |
| POST | `/api/bookings/{id}/cancel` | USER |
| GET | `/api/bookings/mine`, `/api/bookings/{id}` | USER |

Errors are JSON: `{ "status": 409, "error": "...", "timestamp": "..." }`. Seat conflicts are `409`, expired holds `410`, unknown or foreign bookings `404`.

## Not done yet

- React seat-map frontend
- Redis caching for the movie and show listings
- Load test (k6/JMeter) with numbers for the resume
- GitHub Actions CI and a live deployment
- Flyway migrations instead of `ddl-auto: update`
- Real payment gateway and refunds (payment is mocked)
