# Design specification

## Scope and boundary
`CourseRegistrationService` owns enrollment, drop, prerequisite-status snapshots, and waitlist promotion for one offering. It is intentionally not a catalog, identity, persistence, or HTTP layer. The caller provides a learner ID and the current prerequisite result.

## Data and API
- `enroll(learnerId, prerequisitesSatisfied)` returns a typed `Result`.
- `drop(learnerId)` returns `DROPPED`, `NOT_ENROLLED`, or `INVALID_REQUEST`.
- `updatePrerequisiteStatus(learnerId, satisfied)` refreshes the status used during later promotion.
- `snapshot()` returns a defensive, read-only snapshot for inspection/testing.
- State: fixed positive capacity; insertion-ordered enrolled IDs; insertion-ordered waitlist IDs; latest known prerequisite status by learner ID.

## Assumptions and invariants
1. A nonblank learner ID uniquely identifies a learner for this offering.
2. Prerequisites are represented by one boolean supplied by the caller; real prerequisite evaluation is outside this slice.
3. Failed prerequisites prevent enrollment and waitlisting.
4. Duplicate requests do not create duplicate records. An already enrolled learner gets `ALREADY_ENROLLED`; an already queued learner gets `ALREADY_WAITLISTED`.
5. Capacity is positive and `enrolled.size() <= capacity` always holds.
6. A learner cannot be both enrolled and waitlisted.
7. Waitlist policy: FIFO among eligible learners. On a seat opening, scan from the head. If a learner's latest known prerequisite status is false or absent, remove and skip that learner; continue until the first eligible learner is promoted or the queue is empty. Skipped entries are not automatically re-queued. This is a deliberate skip-and-remove policy so an ineligible head cannot block eligible learners behind them.
8. A prerequisite status update affects future promotion only; it does not independently create a seat or trigger promotion.

## Concurrency and persistence
All state-changing methods and `snapshot()` are synchronized on the service instance. This makes the capacity check, queue update, drop, and promotion atomic for concurrent calls in one JVM. It is not a distributed lock and does not protect multiple service instances or survive restarts. A persistent implementation should enforce uniqueness for `(offering_id, learner_id)`, transact the seat/drop/promotion changes, and lock the offering or use an equivalent optimistic concurrency strategy. A database-backed queue would need a stable sequence number for FIFO ordering.

## Tradeoffs / limitations
- In-memory state is lost on process exit.
- Prerequisite truth is caller-supplied rather than queried from a source of record.
- One offering only; no identity, authorization, API transport, expiry, or audit history.
- `updatePrerequisiteStatus` uses a dedicated `UPDATED` status.
