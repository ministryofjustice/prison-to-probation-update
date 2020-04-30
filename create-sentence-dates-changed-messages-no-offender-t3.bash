#!/usr/bin/env bash
aws --endpoint-url=http://localhost:4575 sns publish \
    --topic-arn arn:aws:sns:eu-west-2:000000000000:offender_events \
    --message-attributes '{"eventType" : { "DataType":"String", "StringValue":"SENTENCE_DATES-CHANGED"}}' \
    --message '{"eventType":"SENTENCE_DATES-CHANGED","eventDatetime":"2020-01-13T11:33:23.790725","bookingId":754735,"sentenceCalculationId":5628783,"nomisEventType":"S2_RESULT"}'
