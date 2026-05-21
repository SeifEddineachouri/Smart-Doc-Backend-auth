# Frontend Payment Implementation Guide

## Goal
Make the frontend block access until the user has a valid paid entitlement from the Spring Boot payment service.

## Backend service used
The frontend must talk to the Spring Boot payment service in `payment-service-spring/`.

### Relevant backend endpoints
- `POST /api/v1/payments/checkout-session`
- `GET /api/v1/payments/entitlement/{userId}`
- `GET /api/v1/payments/status/{userId}`
- `POST /api/v1/payments/webhooks/stripe`
- `POST /api/v1/payments/refund`

## Recommended frontend flow
1. User signs in.
2. Frontend calls the payment service entitlement endpoint.
3. If entitlement is inactive, redirect the user to a paywall page.
4. From the paywall page, call `checkout-session`.
5. Redirect the browser to the returned checkout URL.
6. After checkout, poll the status endpoint or refresh entitlement state.
7. Allow access only when the entitlement becomes active.

## Frontend states
### Paid states
- `active`
- `trial` only if explicitly allowed

### Blocked states
- `inactive`
- `past_due`
- `canceled`
- `refund`

## Suggested UI components
### 1. Paywall page
Show:
- plan name
- price
- currency
- why the user is blocked
- button to start checkout

### 2. Subscription badge
Display the current payment state in the header or settings page.

### 3. Billing settings page
Allow the user to:
- view current entitlement
- open checkout again
- cancel or request refund if supported by the backend

## Suggested Angular implementation
If the frontend is Angular, implement:
- `PaymentService` for HTTP calls
- `AuthGuard` or `PaymentGuard` for route protection
- `PaywallComponent` for blocked users
- `BillingComponent` for account status

### Example guard flow
- Check authentication first.
- Then call the entitlement endpoint.
- If `active=false`, redirect to `/paywall`.
- If `active=true`, allow navigation.

## Suggested API contracts
### Checkout session request
Send:
- `userId`
- `planId` if multiple plans exist
- `idempotencyKey`
- `successUrl`
- `cancelUrl`
- `customerEmail`

### Checkout session response
Use:
- `checkoutUrl`
- `sessionId`
- `status`
- `entitlementActive`
- `planName`
- `amountCents`
- `currency`

### Entitlement response
Use:
- `active`
- `status`
- `planId`
- `updatedAt`
- `expiresAt`

## Frontend environment variables
- `PAYMENT_API_BASE_URL=http://localhost:8089`
- `PAYMENT_CHECKOUT_RETURN_URL=http://localhost:4200/billing/success`
- `PAYMENT_CHECKOUT_CANCEL_URL=http://localhost:4200/billing/cancel`

## Security rules
- Never trust the frontend alone for payment success.
- Always rely on the backend entitlement endpoint after the checkout redirect.
- Never store secret payment keys in frontend code.
- Treat checkout success as provisional until the backend confirms entitlement.

## Integration checklist
- [ ] Add payment API client
- [ ] Add paywall route
- [ ] Add route guard
- [ ] Add billing status UI
- [ ] Add checkout redirect handling
- [ ] Add entitlement refresh after payment
- [ ] Add error handling for webhook delay

## Next step
Wire this guide into the actual frontend app and make all protected routes depend on the entitlement status returned by the Spring payment service.

