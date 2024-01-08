# Image Service for the iBeetle-Base project

## Development

Prerequisites:
- [quarkus-cli](https://quarkus.io/guides/cli-tooling) is installed.

To run the application in dev mode
```bash
quarkus dev
```

These commands might be useful after a long time not working on this project:

- `quarkus update`: Update all quarkus dependencies to latest versions
- `./mvnw -N wrapper:wrapper -Dmaven=3.8.7`: Update maven wrapper to new version, in this case `3.8.7`
- `quarkus test`: Ensure all the tests still pass

## Deployment

```
quarkus build -Dquarkus.container-image.build=true
```
