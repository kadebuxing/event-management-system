# Event Management System

Simple Java event manager for my Agile Software Design 3 project.

It is a Java 17 console app for creating events, registering attendees, managing capacity and tracking attendance. Data is saved in a local file.

## Run

Git Bash:

```bash
./mvnw clean package
java -jar target/event-management-system-1.0.0.jar
```

Windows:

```bat
mvnw.cmd clean package
java -jar target\event-management-system-1.0.0.jar
```

The data file is `data/events.db` by default. You can override it with `-Devent.data.file=<path>`.

Jira board: <https://1951783639.atlassian.net/jira/software/projects/OPEN/boards/34/backlog>
