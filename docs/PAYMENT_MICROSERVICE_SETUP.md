# Payment Microservice Setup (Spring Boot)

## Goal
Add a dedicated payment microservice so users must complete payment before they can use the SmartDoc application.

## Recommended Default Stack
- **Provider**: Stripe
- **Pattern**: Hosted checkout + webhook-driven entitlements
- **Architecture**: Separate Spring Boot payment microservice
- **Access control**: Server-side entitlement checks in the main application

## Initial Scope
The first version should handle:
- one-time payment or subscription checkout
- webhook verification
- entitlement activation/deactivation
- access blocking until payment is confirmed
- audit logging of payment events

## Proposed Service Responsibilities
### Payment microservice
Owns:
- checkout session creation
- payment verification through webhooks
- subscription lifecycle updates
- refund/cancellation handling
- entitlement state
- payment audit log

### Main application
Owns:
- user authentication
- feature access checks
- redirecting unpaid users to checkout
- UI paywall messaging

## Suggested Flow
1. User signs in to the main app.
2. Main app checks entitlement status.
3. If unpaid, redirect user to payment checkout.
4. Payment service creates a Stripe checkout session.
5. User completes payment on Stripe-hosted page.
6. Stripe sends webhook to payment service.
7. Payment service verifies webhook and updates entitlement.
8. Main app allows access only after entitlement is active.

## Suggested API Endpoints
### Public / authenticated endpoints
- `POST /api/v1/payments/checkout-session`
- `GET /api/v1/payments/status/{userId}`
- `POST /api/v1/payments/webhooks/stripe`
- `POST /api/v1/payments/refund` (admin only)
- `GET /api/v1/payments/entitlement/{userId}`

## Suggested Data Model
### Tables
- `users`
- `plans`
- `subscriptions`
- `payment_sessions`
- `transactions`
- `entitlements`
- `payment_events`
- `refunds`

### Important fields
- provider session id
- provider payment intent id
- user id
- plan id
- status
- amount
- currency
- event id
- created at / updated at

## Environment Variables
- `PAYMENT_PROVIDER=stripe`
- `PAYMENT_CHECKOUT_MODE=stripe`
- `PAYMENT_CHECKOUT_BASE_URL=https://checkout.stripe.com/pay`
- `PAYMENT_CHECKOUT_RETURN_URL=http://localhost:4200/billing/success`
- `PAYMENT_CHECKOUT_CANCEL_URL=http://localhost:4200/billing/cancel`
- `PAYMENT_STRIPE_WEBHOOK_SECRET=...`
- `PAYMENT_INTERNAL_TOKEN=...`
- `PAYMENT_WEBHOOK_TOLERANCE_SECONDS=300`
- `PAYMENT_DEFAULT_PLAN_ID=pro-monthly`
- `PAYMENT_DEFAULT_PLAN_NAME=SmartDoc Pro Monthly`
- `PAYMENT_DEFAULT_PLAN_PRICE_CENTS=1999`
- `PAYMENT_DEFAULT_PLAN_CURRENCY=eur`
- `PAYMENT_DEFAULT_PLAN_INTERVAL=month`
- `PAYMENT_DEFAULT_PLAN_ACTIVE=true`

The local scaffold also includes a `starter-pack` plan and uses `eur` by default for both plans.

## Security Rules
- Never trust the frontend for payment success.
- Always verify provider webhooks server-side.
- Use idempotency keys for checkout creation.
- Store raw webhook events for audit.
- Block access unless entitlement is active.

## Milestones
### Milestone 1
- Create payment microservice skeleton in `payment-service-spring/`
- Add database schema
- Add checkout session endpoint
- Add webhook endpoint

### Milestone 2
- Add entitlement checks to the main app
- Add user paywall UI
- Add subscription cancellation and refund handling

### Milestone 3
- Add tests for checkout and webhook flows
- Add observability and structured logging
- Add deployment configuration

## Next Step
The Spring Boot scaffold now exists in `payment-service-spring/`. Next, connect it to the main SmartDoc application and replace the in-memory repository with a database-backed implementation.

