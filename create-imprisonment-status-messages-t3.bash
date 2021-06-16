#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4566 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"IMPRISONMENT_STATUS-CHANGED"}}' \
    --message '{"eventType":"IMPRISONMENT_STATUS-CHANGED","eventDatetime":"2020-01-13T11:33:23.790725","bookingId":1200835,"nomisEventType":"OFF_IMP_STAT_OASYS"}'
