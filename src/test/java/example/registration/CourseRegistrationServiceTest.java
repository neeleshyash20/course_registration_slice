package example.registration;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CourseRegistrationServiceTest {
    @Test void enrollsWhenCapacityAvailableAndRejectsMissingPrerequisite() {
        var service = new CourseRegistrationService(2);
        assertEquals(CourseRegistrationService.Status.PREREQUISITE_FAILED, service.enroll("x", false).status());
        assertEquals(CourseRegistrationService.Status.ENROLLED, service.enroll("a", true).status());
        assertEquals(1, service.snapshot().enrolled().size());
    }

    @Test void fullCourseQueuesEligibleLearnersAndRejectsIneligibleLearner() {
        var service = new CourseRegistrationService(1);
        service.enroll("a", true);
        assertEquals(CourseRegistrationService.Status.PREREQUISITE_FAILED, service.enroll("bad", false).status());
        assertEquals(CourseRegistrationService.Status.WAITLISTED, service.enroll("b", true).status());
        assertEquals(java.util.List.of("b"), service.snapshot().waitlist());
    }

    @Test void duplicateEnrollmentsDoNotDuplicateEnrollmentOrWaitlist() {
        var service = new CourseRegistrationService(1);
        service.enroll("a", true);
        assertEquals(CourseRegistrationService.Status.ALREADY_ENROLLED, service.enroll("a", true).status());
        service.enroll("b", true);
        assertEquals(CourseRegistrationService.Status.ALREADY_WAITLISTED, service.enroll("b", true).status());
        assertEquals(1, service.snapshot().enrolled().size());
        assertEquals(java.util.List.of("b"), service.snapshot().waitlist());
    }

    @Test void promotesWaitlistInFifoOrder() {
        var service = new CourseRegistrationService(1);
        service.enroll("a", true); service.enroll("b", true); service.enroll("c", true);
        assertEquals(CourseRegistrationService.Status.DROPPED, service.drop("a").status());
        assertTrue(service.snapshot().enrolled().contains("b"));
        assertEquals(java.util.List.of("c"), service.snapshot().waitlist());
    }

    @Test void skipsAndRemovesLearnerWhoBecomesIneligibleAtQueueHead() {
        var service = new CourseRegistrationService(1);
        service.enroll("a", true); service.enroll("b", true); service.enroll("c", true);
        service.updatePrerequisiteStatus("b", false);
        service.drop("a");
        assertTrue(service.snapshot().enrolled().contains("c"));
        assertTrue(service.snapshot().waitlist().isEmpty());
    }

    @Test void invalidAndRepeatedDropsDoNotChangeState() {
        var service = new CourseRegistrationService(1);
        service.enroll("a", true); service.enroll("b", true);
        var before = service.snapshot();
        assertEquals(CourseRegistrationService.Status.INVALID_REQUEST, service.drop(" ").status());
        assertEquals(CourseRegistrationService.Status.NOT_ENROLLED, service.drop("unknown").status());
        assertEquals(before, service.snapshot());
        service.drop("a");
        var afterFirstDrop = service.snapshot();
        assertEquals(CourseRegistrationService.Status.NOT_ENROLLED, service.drop("a").status());
        assertEquals(afterFirstDrop, service.snapshot());
    }

    @Test void invalidEnrollmentDoesNotMutateState() {
        var service = new CourseRegistrationService(1);
        var before = service.snapshot();
        assertEquals(CourseRegistrationService.Status.INVALID_REQUEST, service.enroll(null, true).status());
        assertEquals(before, service.snapshot());
    }

    @Test void concurrentEnrollmentsNeverExceedCapacity() throws Exception {
        int capacity = 7, requests = 80;
        var service = new CourseRegistrationService(capacity);
        var pool = Executors.newFixedThreadPool(12);
        var ready = new CountDownLatch(12);
        var start = new CountDownLatch(1);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < requests; i++) {
                final String id = "learner-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try { start.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    service.enroll(id, true);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var f : futures) f.get(5, TimeUnit.SECONDS);
            var state = service.snapshot();
            assertEquals(capacity, state.enrolled().size());
            assertEquals(requests - capacity, state.waitlist().size());
            assertEquals(requests, state.enrolled().size() + state.waitlist().size());
        } finally {
            pool.shutdownNow();
        }
    }
}
