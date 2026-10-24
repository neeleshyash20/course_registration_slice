# AI prompt and use record

This exercise was implemented as a minimal Java 17 in-memory fallback because no starter project was present in the supplied workspace.

## Prompt used
> Implement a bounded course-registration feature in Java with enrollment, dropping, FIFO waitlist promotion, prerequisite checks, duplicate protection, clear result statuses, and focused JUnit tests. State the waitlist policy for a learner who becomes ineligible while waiting. Preserve capacity under concurrent calls. Include design and run documentation.

## Relevant AI-generated suggestions / decisions
- Use insertion-ordered collections to represent FIFO waitlist order.
  - **Accepted**, then reviewed: `LinkedHashSet` preserves insertion order and prevents duplicate IDs.
  - **Verification:** FIFO promotion and duplicate-request tests.
- Make the state-changing operations atomic with synchronization for the in-memory prototype.
  - **Accepted with scope limitation:** `synchronized` protects a single service instance in one JVM, not multiple instances or processes.
  - **Verification:** concurrent enrollment test submits 80 unique learners against capacity 7 and asserts exactly 7 enrolled, 73 waitlisted, and no lost/duplicate state.
- Choose a consistent policy for an ineligible learner at the head of the waitlist.
  - **Accepted:** skip and remove, then continue scanning for the earliest eligible learner. Ineligible learners are not initially waitlisted; the case can arise if prerequisite status changes after queueing.
  - **Verification:** test changes the head learner's prerequisite status to false and confirms the next eligible learner is promoted.
- Use explicit result enums rather than exceptions for expected business outcomes.
  - **Accepted:** duplicate enrollment, prerequisite failure, invalid ID, and repeated drop are normal results; invalid constructor capacity throws `IllegalArgumentException`.
  - **Verification:** focused assertions check result statuses and unchanged state after invalid/repeated drops.

## Independent review notes
Review caught and corrected one subtle issue: a duplicate enrollment request must not overwrite the prerequisite status already stored for a waitlisted learner. Status changes therefore use the explicit `updatePrerequisiteStatus` operation. The implementation was checked against the acceptance criteria by mapping each criterion to a test. The tests assert both returned results and resulting state. Defensive copies are used in `snapshot()` so tests/callers cannot mutate internal collections. The synchronization scope and in-memory limitations are documented rather than presented as production-grade persistence/concurrency.

## Files informed
- `src/main/java/example/registration/CourseRegistrationService.java`
- `src/test/java/example/registration/CourseRegistrationServiceTest.java`
- `DESIGN.md`
- `README.md`

## Verification evidence
Run `mvn test`. The suite covers capacity, full course, duplicate requests, FIFO promotion, prerequisite failure, skip-and-remove behavior, invalid/repeated drops, invalid enrollment, and concurrent enrollment. Record the actual Maven test output when running in an environment with Maven installed; this generated artifact has not been executed in this chat environment.
