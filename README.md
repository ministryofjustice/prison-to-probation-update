# prison-to-probation-update

[![CircleCI](https://circleci.com/gh/ministryofjustice/prison-to-probation-update/tree/main.svg?style=svg)](https://circleci.com/gh/ministryofjustice/prison-to-probation-update)
[![Docker Repository on Quay](https://quay.io/repository/hmpps/prison-to-probation-update/status "Docker Repository on Quay")](https://quay.io/repository/hmpps/prison-to-probation-update)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://prison-to-probation-update.prison.service.justice.gov.uk/swagger-ui.html)

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
* Initial processing - where an attempt to is made to synchronise data with probation
* Retry processing - where further attempts are made if there was a failure during initial processing

#### Validation:  SQS subscriptions and DynamoDB

A Topic subscription is made to the  `offender_events.topic` which is a topic for prison offender events. The subscriptions are defined in the [Cloud Platform terraform](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live-1.cloud-platform.service.justice.gov.uk/offender-events-prod/resources/prison_to_probation_update-sub-queue.tf).

The listener for the queue is defined in *PrisonerChangesListenerPusher*.

Once a message has been validated it will be added to the data store ready to be picked up for processing. This is a DynamoDB database that is again defined in [Cloud Platform terraform](https://github.com/ministryofjustice/cloud-platform-environments/blob/main/namespaces/live-1.cloud-platform.service.justice.gov.uk/prison-to-probation-update-prod/resources/dynamodb.tf). This, essentially, is a queue of messages ready to be processed.

The scheduling of the message is processed by *MessageRetryService*.

#### Initial processing: Scheduler and DynamoDB

The main processing loop is invoked by *SerialiseBookingScheduler* which is a Spring Scheduler that itself uses DynamoDB as a persistent store for locking - this ensures only a single pod is processing messages at a time.  

Events are held back for a period of time (one hour in production); this is so multiple similar messages are processed for a record all in one go, there is no drip feeding of small changes, e.g. each of the key sentence dates are all changed at once.

Events are sorted and grouped so for each run of the processing events for a particular booking and are processed together rather than being processed by different pods simultaneously. This is done by the *MessageAggregator*.

Successful processing of an event leads to the DynamoDB record being marked as processed by setting the *processedDate*.
Unsuccessful processing can lead to different paths depending on the event being processed:
 * IMPRISONMENT_STATUS-CHANGED - will be retried for a period of time. Given this indicates a significant change to the prisoner's status, e.g. conviction, it may take some time before the associated Delius record has been created or is in the correct state. It will retry for around a month.
 * EXTERNAL_MOVEMENT_RECORD-INSERTED - only tried once and then discarded
 * BOOKING_NUMBER-CHANGED - only tried once and then discarded
 * SENTENCE_DATES-CHANGED - only tried once and then discarded
 * CONFIRMED_RELEASE_DATE-CHANGED - only tried once and then discarded

If there is a *system* exception while processing any of these messages they will be retried. However business "exceptions", for instance if the prisoner does not exist in Delius, are not retried since they are not expected to succeed because of the passing of time.

#### IMPRISONMENT_STATUS-CHANGED is a special event

The IMPRISONMENT_STATUS-CHANGED event is used as a trigger to indicate there has possibly been a new conviction that therefore means the NOMIS and Delius record should be synchronised.
Once it has been established the prisoner has a current sentence the initial matching with the Delius record begins. This is a multi-stage process:
 * Search for the person in probation-offender-search
 * if successful check they have a matching sentence (*EVENT*) in Delius via *community-api*
 * if successful attempt to set the NOMS number in Delius via *community-api*
 * if successful (or already set) attempt to set the book number in Delius via *community-api*
 * if successful (or already set) attempt to set the prison location in Delius via *community-api*
 * if successful (or already set) attempt to set the key dates in Delius via *community-api*
 * if successful mark record as processed or retry again later

#### Retry processing: CRON Scheduler

Retrying events marked as requiring a retry is orchestrated by the *RetryScheduler*. This uses 3 simple CRON expressions to simulate back-off retry processing.
 * RETRY_SCHEDULES_SHORT_CRON: "0 0 * * * *"
 * RETRY_SCHEDULES_MEDIUM_CRON: "0 15 */4 * * *"
 * RETRY_SCHEDULES_LONG_CRON: "0 30 23 * * *"

Which retry schedule a failed record goes into is determined by the Message *retryCount*. So it will retry once an hour (for 4 hours), then every 4 hours (for 6 hours) and finally once a day until the record is auto-deleted.

#### Deletion

The Message *deleteBy* attribute is set at initial creation and can be extended for a couple of reasons:
  * A record should be retried until the sentence ends since Delius has no SLA for data is not in the correct state (e.g. multiple custodial events)
  * A record has been processed, but we wish to retain for a period of time for reporting purposes.

Once the date specified by the *deleteBy* value is reached DynamoDB will automatically delete the record.

## Run Book

#### SQS and Dead Letter Queue

If an alert is raised in the *dps_alert* channel indicating the Dead Letter Queue (DLQ) is filling with messages it means something is going wrong when validating a message.

Given validation is relatively simple it is likely to be related to the *prison-api* call to get the booking failing.

Check Application Insights to show any errors, while the traces will indicate what messages have been received

```bigquery
exceptions
| where cloud_RoleName == "prison-to-probation-update"
| order by timestamp desc 

```

```bigquery
traces
| where cloud_RoleName == "prison-to-probation-update"
| order by timestamp desc 
```

It will likely be a transient issue with either retrieving an HMPPS auth token or *prison-api* being down.
Once the transient issue has been resolved, the messages from the DLQ should be moved back to the main queue. Currently, there is no endpoint to do this so messages will have to be received and acknowledged manually and sent to the main queue.

Secrets for the AWS credentials are stored in namespace `prison-to-probation-update-prod` under `ptpu-sqs-dl-instance-output` for the DLQ and `ptpu-sqs-instance-output` for the main queue. 

#### DynamoDB issues

A message could also appear on the DLQ if we were unable to add the message to the DynamoDB table. 

The [health check](https://prison-to-probation-update.prison.service.justice.gov.uk/health) captures the health of DynamoDB. It also displays the table name that is dynamically created, and the number of rows currently on the table. 
Further details about the table state can be seen by running the AWS CLI commands, again secrets for accessing can be retrieved from the namespace as *message-dynamodb-output* which also includes the table ARN.

```shell
aws dynamodb describe-table --table-name <table name>
```

This will show the dynamic provisioned throughput e.g.

```json
        "ProvisionedThroughput": {
            "LastIncreaseDateTime": "2021-01-28T10:16:00.613000+00:00",
            "LastDecreaseDateTime": "2021-01-28T00:04:04.338000+00:00",
            "NumberOfDecreasesToday": 1,
            "ReadCapacityUnits": 665,
            "WriteCapacityUnits": 1
        }
```

#### Application insights queries

All telemetry events are prefixed by `P2P` so this query will show all significant events:

```bigquery
customEvents
| where cloud_RoleName in ("prison-to-probation-update", "community-api")
| where name startswith "P2P" or name startswith "KeyDate" 
| summarize count() by name
```
Description for each event is as follows:

*Prison transfers*

* prison-to-probation-update service
    * `P2PExternalMovement` - Movement message is about to be processed
    * `P2PTransferProbationUpdated` -  Location has been updated or was already correct so required no change.
    * `P2PTransferProbationRecordNotFound` -  Location could not be updated since prisoner or event has not been found
    * `P2PTransferIgnored` -  Location movement is no longer valid
* community-api service
    * `P2PTransferPrisonUpdated` -  Location has been updated
    * `P2PTransferPrisonUpdateIgnored` -  Location was already correct
    * `P2PTransferOffenderNotFound` -  Location could not be updated since prisoner was not found
    * `P2PTransferBookingNumberNotFound` -  Location could not be updated since event not found
    * `P2PTransferBookingNumberHasDuplicates` -  Location could not be updated since mutiple events found with book number

*Sentence dates changes*

* prison-to-probation-update service
    * `P2PSentenceDatesRecordNotFound` -  Dates could not be updated since prisoner or event has not been found
    * `P2PSentenceDatesChanged` - Dates have been updated or were correct already
    * `P2PSentenceDatesChangeIgnored` - Dates changes are ignored since the dates or booking is no longer valid
* community-api service
    * `keyDatesBulkUnchanged` - No dates required updating 
    * `keyDatesBulkSummary` - Dates were successfully updated  
    * `keyDatesBulkAllRemoved` - All dates were updated, this may indicate the sentence has changed to a Life sentence, they have been deported or new dates are about to be added
    * `KeyDateDeleted` - One of the key the dates was deleted (also fired POM handover dates)
    * `KeyDateUpdated` - Existing date was updated (also fired POM handover dates)
    * `KeyDateAdded` - New date was added (also fired POM handover dates)

*Imprisonment status change and offender matching*

* prison-to-probation-update service
  * `P2POffenderNoMatch` - Prisoner not found in Delius
  * `P2POffenderTooManyMatches` - Prisoner found in Delius but multiple hits found 
  * `P2POffenderMatch` - Details of the prisoner matching - a successful match would be represented by the number of times we see this event minus the P2POffenderNoMatch and P2POffenderTooManyMatches count
  * `P2POffenderNumberSet` - NOMS number set in Delius (new offender has been matched)
  * `P2PImprisonmentStatusUpdated` - Successfully completed matching and setting the dates and location
  * `P2PImprisonmentStatusNoSentenceStartDate` - Change is being ignored since the prisoner has no sentence start date 
  * `P2PImprisonmentStatusIgnored` - Change is being ignored since booking is no longer relevant, e.g. the booking is no longer active and they have been released
  * `P2PBookingNumberNotAssigned` - Book number can be set in Delius, typically because there are multiple active events
  * `P2PLocationNotUpdated` - Location can not be set for the matched offender, typically where there are multiple custodial events
  * `P2PKeyDatesNotUpdated` - Dates can not be set for the matched offender, typically where there are multiple custodial events

* community-api service
  * `P2PImprisonmentStatusCustodyEventsHasDuplicates` - book number can not be set due multiple matching events
  * `P2PImprisonmentStatusCustodyEventNotFound` - book number can not be set due no matching events
  * `P2PImprisonmentStatusBookingNumberUpdated` - book number updated
  * `P2PImprisonmentStatusBookingNumberInserted` - book number inserted
  * `P2PImprisonmentStatusBookingNumberAlreadySet` - book number already correct

*Book number changed*

* prison-to-probation-update service
  * `P2PBookingNumberChanged` - NOMS number updated
  * `P2PBookingNumberChangedOffenderNotFound` - Offender not found so can't update NOMS number


#### community-api update requests

For the above telemetry events that are emitted from the community-api there would be an associated request - use the queries below to show these requests:

* Set NOMS number 
```bigquery
requests
| where cloud_RoleName in ("community-api")
| where name == "PUT CustodyResource/updateOffenderNomsNumber"
| summarize count() by resultCode
```

* Set Book number
```bigquery
requests
| where cloud_RoleName in ("community-api")
| where name == "PUT CustodyResource/updateCustodyBookingNumber"
| summarize count() by resultCode
```

* Set prison location
```bigquery
  requests
  | where cloud_RoleName in ("community-api")
  | where name == "PUT CustodyResource/updateCustody"
  | summarize count() by resultCode
```
* Set key dates
```bigquery
  requests
  | where cloud_RoleName in ("community-api")
  | where name == "POST CustodyKeyDatesController/replaceAllCustodyKeyDateByNomsNumberAndBookingNumber"
  | summarize count() by resultCode
```
* Replace NOMS number
```bigquery
  requests
  | where cloud_RoleName in ("community-api")
  | where name == "PUT CustodyResource/replaceOffenderNomsNumber"
  | summarize count() by resultCode
```

#### Grafana Dashboard

There is a [Grafana Dashboard](https://grafana.cloud-platform.service.justice.gov.uk/d/ptpu-prison-to-probation-update-prod/prison-to-probation-update-prison-to-probation-update-prod?orgId=1) that shows the totals for major processing counts.


#### Reports

There are a number of CSV reports that are documented [here](https://prison-to-probation-update.prison.service.justice.gov.uk/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config).

To run the reports you require the `ROLE_PTPU_REPORT` role. We would recommend asking the HMPPS Auth administrators (DPS Tech team) to create personal production client credentials and just add that single role. Use Postman or cUrl to request the report.    

