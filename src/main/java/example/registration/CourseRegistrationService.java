package example.registration;

import java.util.*;

/**
 * In-memory implementation for exactly one course offering.
 * All state-changing public methods are synchronized so capacity and queue changes
 * are atomic within this single JVM.
 */
public final class CourseRegistrationService {
    public enum Status {
        ENROLLED, WAITLISTED, ALREADY_ENROLLED, ALREADY_WAITLISTED, UPDATED,
        PREREQUISITE_FAILED, DROPPED, NOT_ENROLLED, INVALID_REQUEST
    }

    public record Result(Status status, String learnerId, String message) {}
    public record Snapshot(int capacity, Set<String> enrolled, List<String> waitlist) {}

    private final int capacity;
    private final LinkedHashSet<String> enrolled = new LinkedHashSet<>();
    private final LinkedHashSet<String> waitlist = new LinkedHashSet<>();
    private final Map<String, Boolean> prerequisiteSatisfied = new HashMap<>();

    public CourseRegistrationService(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1");
        this.capacity = capacity;
    }

    /**
     * The caller supplies the learner's current prerequisite status.
     * A failed prerequisite is not enrolled or queued.
     */
    public synchronized Result enroll(String learnerId, boolean prerequisitesSatisfied) {
        if (!validId(learnerId)) return result(Status.INVALID_REQUEST, learnerId, "learnerId must not be blank");
        if (enrolled.contains(learnerId))
            return result(Status.ALREADY_ENROLLED, learnerId, "learner is already enrolled");
        if (waitlist.contains(learnerId))
            return result(Status.ALREADY_WAITLISTED, learnerId, "learner is already waitlisted");
        prerequisiteSatisfied.put(learnerId, prerequisitesSatisfied);
        if (!prerequisitesSatisfied)
            return result(Status.PREREQUISITE_FAILED, learnerId, "required prerequisite is not satisfied");

        if (enrolled.size() < capacity) {
            enrolled.add(learnerId);
            return result(Status.ENROLLED, learnerId, "enrollment confirmed");
        }
        waitlist.add(learnerId);
        return result(Status.WAITLISTED, learnerId, "course is full; added to waitlist");
    }

    /**
     * Drops enrollment. Promotion scans FIFO order and removes ineligible entries
     * permanently (skip-and-remove policy) until one eligible learner is found.
     */
    public synchronized Result drop(String learnerId) {
        if (!validId(learnerId)) return result(Status.INVALID_REQUEST, learnerId, "learnerId must not be blank");
        if (!enrolled.remove(learnerId))
            return result(Status.NOT_ENROLLED, learnerId, "learner is not currently enrolled");

        promoteNextEligible();
        return result(Status.DROPPED, learnerId, "enrollment dropped; promotion processed");
    }

    /**
     * Refreshes a waitlisted learner's prerequisite status, e.g. after a prerequisite
     * completion/revocation. This does not trigger promotion by itself.
     */
    public synchronized Result updatePrerequisiteStatus(String learnerId, boolean satisfied) {
        if (!validId(learnerId)) return result(Status.INVALID_REQUEST, learnerId, "learnerId must not be blank");
        prerequisiteSatisfied.put(learnerId, satisfied);
        return result(Status.UPDATED, learnerId, "prerequisite status updated");
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(capacity,
                Collections.unmodifiableSet(new LinkedHashSet<>(enrolled)),
                List.copyOf(waitlist));
    }

    private void promoteNextEligible() {
        if (enrolled.size() >= capacity) return;
        Iterator<String> iterator = waitlist.iterator();
        while (iterator.hasNext()) {
            String candidate = iterator.next();
            iterator.remove();
            if (Boolean.TRUE.equals(prerequisiteSatisfied.get(candidate))) {
                enrolled.add(candidate);
                return;
            }
            // Ineligible candidate is skipped and removed, not left blocking the queue.
        }
    }

    private static boolean validId(String id) {
        return id != null && !id.isBlank();
    }

    private static Result result(Status status, String id, String message) {
        return new Result(status, id, message);
    }
}
