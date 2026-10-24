# Course registration slice

Minimal Java 17 + Maven implementation for one course offering. No frontend, database, or external service is required.

## Requirements
- JDK 17+
- Maven 3.8+

## Run tests
From this directory:

```bash
mvn test
```

## Build
```bash
mvn clean package
```

The feature is a library service exercised by JUnit tests; there is no CLI or web server because the task only requires the scoped service slice. See `DESIGN.md` for invariants, policy, concurrency, and limitations, and `AI_USE_RECORD.md` for the prompt/use/verification record.
