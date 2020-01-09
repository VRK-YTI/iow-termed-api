#!/bin/bash

./gradlew build
docker build -t yti-terminology-api .
