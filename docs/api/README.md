# API contracts

One OpenAPI document per service: [auth](auth.openapi.yaml), [user](user.openapi.yaml),
[semaphore](semaphore.openapi.yaml), [schedule](schedule.openapi.yaml) and [map](map.openapi.yaml).
Each is the source of truth for its service; [CHANGELOG.md](CHANGELOG.md) says what changed in each
version. This page holds what they all share.

## Any route can answer 503 or 504

Every route goes through the API gateway. When the service behind a route is down, the gateway
answers for it (#157):

| Status | When | `message` |
|---|---|---|
| `503 Service Unavailable` | The service cannot be reached: stopped, restarting, or not deployed | "This part of KApp is not available right now. Try again in a moment." |
| `504 Gateway Timeout` | The service did not answer within the gateway's response timeout (10 seconds) | "This part of KApp took too long to answer. Try again in a moment." |

The body is the same `ApiError` every contract defines, with `timestamp`, `status`, `error`,
`message` and `path`. It never names the service's address. Clients should show a "try again"
state for both, not an error. A `500` still means a fault, in the gateway or in a service.

The contracts do not list these two on every operation: they can happen on any of them.
