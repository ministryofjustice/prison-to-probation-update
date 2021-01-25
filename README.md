# prison-to-probation-update

[![CircleCI](https://circleci.com/gh/ministryofjustice/prison-to-probation-update/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prison-to-probation-update)
[![Known Vulnerabilities](https://snyk.io/test/github/ministryofjustice/prison-to-probation-update/badge.svg)](https://snyk.io/test/github/ministryofjustice/prison-to-probation-update)

Self-contained fat-jar micro-service to listen for events from Prison systems (NOMIS) to update offender sentence information in Probation systems (Delius)

### Pre-requisite

`Docker` Even when running the tests docker is used by the integration test to load `localstack` (for AWS services). The build will automatically download and run `localstack` on your behalf.

### Building

```./gradlew build```

### Running

`localstack` is used to emulate the AWS SQS service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `locastack` run the tests with the environment `AWS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

Any commands in `localstack/setup-sns.sh` will be run when `localstack` starts, so this should contain commands to create the appropriate queues.

Running all services locally:
```bash
TMPDIR=/private$TMPDIR docker-compose up 
```
Queues and topics will automatically be created when the `localstack` container starts.

Running all services except this application (hence allowing you to run this in the IDE)

```bash
TMPDIR=/private$TMPDIR docker-compose up --scale prison-to-probation-update=0 
```

Check the docker-compose file for sample environment variables to run the application locally.

Or to just run `localstack` which is useful when running against an a non-local test system

```bash
TMPDIR=/private$TMPDIR docker-compose up localstack 
```

In all of the above the application should use the host network to communicate with `localstack` since AWS Client will try to read messages from localhost rather than the `localstack` network.
### Experimenting with messages

There are two handy scripts to add messages to the queue with data that matches either the T3 test environment or data in the test Docker version of the apps

T3 test data:
```bash
./create-prison-movements-messages-t3.bash 
```
local test data:
```bash
./create-prison-movements-messages-local.bash 
```

### Testing

There are 3 source sets to handle the unit, integration and smoke tests.  Note that `check` only runs the unit tests and you have to run the integration tests manually.

#### Unit Tests

The source set `test` contains the unit tests.  These are run with the command `./gradlew test` and are also included in the check when running command `./gradlew check`.

#### Integration Tests

The source set `testIntegration` contains the integration tests.  These are run with the command `./gradlew testIntegration` but are NOT included in the check when running command `./gradlew check` and so must be run manually.

Note that the integration tests currently use TestContainers to start localstack and so you do not need to start localstack manually.

If you DO wish to run localstack manually (as is done in the Circle build) then you must:
* start localstack with command `TMPDIR=/private$TMPDIR docker-compose up localstack`
* run the tests with command `AWS_PROVIDER=localstack ./gradlew testIntegration`

#### Smoke Tests

The source set `testSmoke` contains the smoke tests.

These tests are not intended to be run locally, but instead are run against a deployed application (as happens in the Circle build).

For more information on the smoke tests see the project `dps-smoketest`.

## Live support

### Architecture

Understanding the architecture makes live support easier:

The service subscribes to a number of prison offender events

* IMPRISONMENT_STATUS-CHANGED
* EXTERNAL_MOVEMENT_RECORD-INSERTED
* BOOKING_NUMBER-CHANGED
* SENTENCE_DATES-CHANGED
* CONFIRMED_RELEASE_DATE-CHANGED

Each message is processed in up to 3 phases:

* Validation - where the associated booking is inspected to see if the message should be processed
* Initial processing - where an attempt to is made to synchronise data with probation is made
* Retry processing - where further attempts are made of there was a failure during initial processing

#### Validation:  SQS subscriptions and DynamoDB

A Topic subscription is made to the  `offender_events.topic` that is a topic for prison offender events. The subscriptions are defined in the [Cloud Platform terraform](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live-1.cloud-platform.service.justice.gov.uk/offender-events-prod/resources/prison_to_probation_update-sub-queue.tf)

The listener for the queue is defined in *PrisonerChangesListenerPusher* 

Once a message has been validated it will be added to the data store ready to be picked up for processing. This is a DynamoDB database that is again is defined in [Cloud Platform terraform](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live-1.cloud-platform.service.justice.gov.uk/prison-to-probation-update-prod/resources/dynamodb.tf). This, essentially, is a queue of messages ready to be processed.

The scheduling of the message is processed by *MessageRetryService* 

#### Initial processing: Scheduler and DynamoDB

The main processing loop is invoked by *SerialiseBookingScheduler* which is a Spring Scheduler that itself uses DynamoDB has a persistent store for locking - this ensures only a single pod is processing messages at a time.  


