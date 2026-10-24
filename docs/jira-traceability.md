# Jira to Code Traceability

This document links each completed Jira item in the **Event Management and Staffing System** project to the Java implementation and the commits that introduced it. It supports the Phase 2 evidence for the individual screencast.

- Jira board: <https://1951783639.atlassian.net/jira/software/projects/OPEN/boards/34/backlog>
- Repository: <https://github.com/kadebuxing/event-management-system>
- Snapshot date: 24 October 2026
- Scope: the 9 items marked **Done** in Jira. The 3 items still in **To Do** are intentionally not implemented.

## Status Summary

| Jira item | Summary | Points | Jira status | Implementation | Commits |
| --- | --- | ---: | --- | --- | --- |
| OPEN-1 | Create Event | 5 | Done | `EventService.createEvent`, `nextEventId`; menu 1 in `ConsoleApp` | `427b769`, `582c044` |
| OPEN-2 | List Events | 3 | Done | `EventService.events`, `findEvent`; menu 2 with filters in `ConsoleApp` | `427b769`, `582c044` |
| OPEN-3 | Register Attendee | 5 | Done | `EventService.registerAttendee`, `nextRegistrationReference`; menu 7 in `ConsoleApp` | `e6f3d9e`, `64b3b2f`, `582c044` |
| OPEN-4 | Manage Event Capacity | 3 | Done | `EventService.updateCapacity`, capacity check in `registerAttendee`; menu 6 | `427b769`, `64b3b2f`, `582c044` |
| OPEN-5 | Track Attendance | 2 | Done | `EventService.markAttended`, `attendedCount`; menu 9 | `64b3b2f`, `582c044` |
| OPEN-6 | Cancel Registration | 2 | Done | `EventService.cancelRegistration`; menu 8 | `64b3b2f`, `582c044` |
| OPEN-7 | Generate Reports | 5 | **To Do** | Not implemented (no report view or export) | - |
| OPEN-8 | Cancel Event | 3 | Done | `EventService.cancelEvent`, `restoreEvent`; menu 5 | `427b769`, `582c044` |
| OPEN-9 | Data Persistence | 5 | Done | `FileEventRepository` atomic save, domain records; startup load in `Main` | `e6f3d9e`, `582c044` |
| OPEN-10 | Edit Event | 2 | Done | `EventService.editEvent`; menu 4 | `427b769`, `582c044` |
| OPEN-11 | Add JUnit regression suite | 5 | **To Do** | Not implemented (no `src/test`, JUnit or CI) | - |
| OPEN-12 | Add file locking for concurrent access | 3 | **To Do** | Not implemented (single-process use only) | - |

Total: **30 / 43 story points**, **9 / 12 items Done**.

## Selected Story: OPEN-3 Register Attendee

**User story:** As an Event Organiser, I want to be able to register people attending an event, so that I know how many are going.

### Acceptance criteria and evidence

| Acceptance criterion | Where it is implemented |
| --- | --- |
| The user must be given the option to add a registration to an event. | Menu item 7, `ConsoleApp.registerAttendeeFlow`, after selecting an active event. |
| Registration information must be easily available when looking at an event. | Menu 3, `ConsoleApp.showEventDetails`, lists registrations from `EventService.registrationsFor`. |
| The user must be shown how many people are currently registered. | `registerAttendeeFlow` prints registered/capacity before input; `EventService.registeredCount` is the single source. |
| The user must be notified when the venue capacity has been reached. | `EventService.registerAttendee` throws `EventServiceException` when `registered >= capacity`; the CLI prints the reason and returns to the menu. |
| A unique registration reference must be generated for each registered person. | `EventService.nextRegistrationReference` draws six characters from an unambiguous alphabet, retries on collision and returns `REG-XXXXXX`. |

### Code path

`ConsoleApp.registerAttendeeFlow` → `EventService.registerAttendee` → `registeredCount` and `nextRegistrationReference` → `new Registration(...)` → `commit(...)` → `FileEventRepository.save(...)`.

Related model and persistence: `Registration` record (reference, event ID, attendee name, timestamp, attended flag) and the `R|...` line in the single-file data format.

### Why this story was selected for the screencast

OPEN-3 is the central business flow: it is the point where an event, the capacity rule, a persistent registration and a unique reference meet. It depends on OPEN-9 (storage) and OPEN-1 (an event must exist), so it demonstrates the layered design end to end rather than an isolated screen.

## Commit History

| Commit | Message | Purpose |
| --- | --- | --- |
| `8fd2b65` | `chore: scaffold Maven Java 17 project` | Maven wrapper, compiler target 17, ignore rules |
| `e6f3d9e` | `feat(OPEN-9): add domain model and atomic file persistence` | Records, repository contract, atomic file storage |
| `427b769` | `feat(OPEN-1/2/4/8/10): implement event lifecycle and capacity rules` | Create, list, edit, cancel/restore, capacity |
| `64b3b2f` | `feat(OPEN-3/5/6): implement attendee registration, attendance and cancellation` | Registration references, capacity blocking, attendance, cancellation |
| `582c044` | `feat: add console workflows and application entry point` | Menu, validation, tables, UTF-8 startup, data-file configuration |
| `fabe54b` | `docs: add README with setup and Jira progress` | Build/run instructions and Jira mapping |

The history is layered by responsibility and by Jira key, so the commits for OPEN-3 can be inspected independently of the UI work.

## Intentionally Deferred Items

| Jira item | Why it remains To Do |
| --- | --- |
| OPEN-7 Generate Reports | Would add a report view and export path that do not exist in the current Jira scope. |
| OPEN-11 JUnit regression suite | Explicitly out of scope for the Phase 1–2 screencast; no test dependency or CI workflow is present. |
| OPEN-12 File locking | The application is documented as single-process; no lock file or optimistic version check is implemented. |

Keeping these items unimplemented is deliberate: the repository reflects the current Jira board rather than a completed future state.

## Reproducing the Evidence

```bash
./mvnw clean package
java -jar target/event-management-system-1.0.0.jar
```

Use menu 1 to create a future event, menu 7 to register an attendee, then menu 3 to view the registration. Restart the application to confirm the record is still present.
