#!/usr/bin/env just --justfile

default:
  just --list

test:
  ./gradlew test
  ./gradlew functionalTest

dependencies:
  ./gradlew -q dependencies --configuration compileClasspath > ./dependencies.txt

# Report up-to-date dependencies by com.github.ben-manes.versions
updates:
  ./gradlew dependencyUpdates > updates.txt

# increment the provided version type and publish the repository
release type='patch': test
  ./gradlew release -P{{type}}
  git push origin tag $(git describe --tags --abbrev=0)
  ./gradlew publish
