# Image Service for the iBeetle-Base project

## Development

Prerequisites:
- [quarkus-cli](https://quarkus.io/guides/cli-tooling) is installed.

To run the application in dev mode, run:
```bash
quarkus dev
```

These commands might be useful after a long time not working on this project:

- `quarkus update`: Update all quarkus dependencies to latest versions
- `./mvnw -N wrapper:wrapper -Dmaven=3.8.7`: Update maven wrapper to new version, in this case `3.8.7`
- `quarkus test`: Ensure all the tests still pass

## Deployment

To build docker image:
```
quarkus build -Dquarkus.container-image.build=true
```

To build without running tests:
```
quarkus build -Dquarkus.container-image.build=true -DskipTests
```

Prerequisites before running the docker container:
- `/images` must be mounted to a writeable directory for storing uploaded images.
- An elasticsearch instance is accessible at `es:9200` for storing image metadata.

Optional:
- `/data/ibeetle_images.tsv` will be loaded if available. The format is the same as the example file `./sample_data/ibeetle_images.tsv`.
