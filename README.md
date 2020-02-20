# prison-to-probation-update

[![CircleCI](https://circleci.com/gh/ministryofjustice/prison-to-probation-update/tree/master.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prison-to-probation-update)
[![Known Vulnerabilities](https://snyk.io/test/github/ministryofjustice/prison-to-probation-update/badge.svg)](https://snyk.io/test/github/ministryofjustice/prison-to-probation-update)

Self-contained fat-jar micro-service to listen for events from Prison systems (NOMIS) to update offender sentence information in Probation systems (Delius)

### Pre-requisite

`Docker` Even when running the tests docker is used by the integration test to load `localstack` (for AWS services). The build will automatically download and run `localstack` on your behalf.

### Building

```./gradlew build```

### Running

`localstack` is used to emulate the AWS SQS service. When running the integration test this will be started automatically. If you want the tests to use an already running version of `locastack` run the tests with the environment `SQS_PROVIDER=localstack`. This has the benefit of running the test quicker without the overhead of starting the `localstack` container.

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

