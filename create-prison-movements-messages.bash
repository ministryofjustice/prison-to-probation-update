#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4575 sns publish --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"EXTERNAL_MOVEMENT_RECORD-INSERTED"}}' --message '{"eventType":"EXTERNAL_MOVEMENT_RECORD-INSERTED","bookingId":1196631}'
