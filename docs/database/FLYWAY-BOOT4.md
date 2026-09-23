# Flyway + Spring Boot 4.1

Spring Boot 4.x exposes Flyway auto-configuration from the `spring-boot-flyway` module.
All PostgreSQL-backed services therefore use:

- `org.springframework.boot:spring-boot-starter-flyway`
- `org.flywaydb:flyway-database-postgresql`

Migration scripts live at `classpath:db/migration` and are enabled explicitly in `application.yml`.
Development JVMs should use UTC (`-Duser.timezone=UTC`).
