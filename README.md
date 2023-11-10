# Image Service for the iBeetle-Base project

## Running the application in dev mode

```
./mvnw quarkus:dev
```

## Packaging and containerizing

The application can be packaged and containerized by one command (docker required)

```
./mvnw package
```

## Developing

These commands might be useful after a long time not working on this project:

- `quarkus update`: Update all quarkus dependencies to latest versions
- `./mvnw -N wrapper:wrapper -Dmaven=3.8.7`: Update maven wrapper to new version, in this case ``3.8.7`
