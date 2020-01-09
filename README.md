# YTI termonology API

Terminology editor is a web-based vocabulary and metadata editor.

This modules purpose is to act as a backend for frontend project.
It also handles updating of elastic search index. Complete reindexing is done periodically.

## Install dependencies

Use Java 8u121 or later

Run with `./gradlew assemble` to download all dependencies.

## Running

Run with: `./gradlew bootRun`

Terminology API should respond at port `8001`.

### Using profile-specific properties

To use different configurations based on Spring profile, such as *prod*, add a new property
file if it does not exist:
```
/src/main/resources/application-prod.properties
```

and run:
```
./gradlew bootRun  --args='--spring.profiles.active=prod'

```

## Stopping

Run in a separate terminal: `curl -X POST localhost:8001/shutdown`

## Development

Get started:

  - Copy `src/main/resources/config/application-template.properties` to `application-local.properties`
    and adjust the settings.
  - Run `./gradlew assemble` to download all dependencies.

To develop the code:

  - Run java class `fi.vm.yti.terminology.api.Application` with parameter `-Dspring.profiles.active=local` to start up Spring Boot web application at [http://localhost:8001](http://localhost:8001).

Now you can start hacking the code normally.

## Build (executable) jar

Run with: `./gradlew jar --args='--spring.profiles.active=prod'`

The jar file is created into folder ./build/libs