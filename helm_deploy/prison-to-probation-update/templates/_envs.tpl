    {{/* vim: set filetype=mustache: */}}
{{/*
Environment variables for web and worker containers
*/}}
{{- define "deployment.envs" }}
env:
  - name: SERVER_PORT
    value: "{{ .Values.image.port }}"

  - name: JAVA_OPTS
    value: "{{ .Values.env.JAVA_OPTS }}"

  - name: JWT_PUBLIC_KEY
    value: "{{ .Values.env.JWT_PUBLIC_KEY }}"

  - name: SPRING_PROFILES_ACTIVE
    value: "logstash"

  - name: OAUTH_ENDPOINT_URL
    value: "{{ .Values.env.OAUTH_ENDPOINT_URL }}"

  - name: ELITE2_ENDPOINT_URL
    value: "{{ .Values.env.ELITE2_ENDPOINT_URL }}"

  - name: COMMUNITY_ENDPOINT_URL
    value: "{{ .Values.env.COMMUNITY_ENDPOINT_URL }}"

  - name: OFFENDER_SEARCH_ENDPOINT_URL
    value: "{{ .Values.env.OFFENDER_SEARCH_ENDPOINT_URL }}"

  - name: PRISONTOPROBATION_ONLY_PRISONS
    value: "{{ .Values.env.PRISONTOPROBATION_ONLY_PRISONS }}"

  - name: APPLICATION_INSIGHTS_IKEY
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: APPINSIGHTS_INSTRUMENTATIONKEY

  - name: OAUTH_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISON_TO_PROBATION_CLIENT_ID

  - name: OAUTH_CLIENT_SECRET
    valueFrom:
      secretKeyRef:
        name: {{ template "app.name" . }}
        key: PRISON_TO_PROBATION_CLIENT_SECRET

  - name: SQS_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-instance-output
        key: access_key_id

  - name: SQS_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-instance-output
        key: secret_access_key

  - name: SQS_QUEUE_NAME
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-instance-output
        key: sqs_ptpu_name

  - name: SQS_AWS_DLQ_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-dl-instance-output
        key: access_key_id

  - name: SQS_AWS_DLQ_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-dl-instance-output
        key: secret_access_key

  - name: SQS_DLQ_NAME
    valueFrom:
      secretKeyRef:
        name: ptpu-sqs-dl-instance-output
        key: sqs_ptpu_name

  - name: DYNAMODB_TABLENAME
    valueFrom:
      secretKeyRef:
        name: message-dynamodb-output
        key: table_name

  - name: DYNAMODB_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: message-dynamodb-output
        key: access_key_id

  - name: DYNAMODB_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: message-dynamodb-output
        key: secret_access_key

  - name: DYNAMODB_SCHEDULE_TABLENAME
    valueFrom:
      secretKeyRef:
        name: schedule-dynamodb-output
        key: table_name

  - name: DYNAMODB_SCHEDULE_AWS_ACCESS_KEY_ID
    valueFrom:
      secretKeyRef:
        name: schedule-dynamodb-output
        key: access_key_id

  - name: DYNAMODB_SCHEDULE_AWS_SECRET_ACCESS_KEY
    valueFrom:
      secretKeyRef:
        name: schedule-dynamodb-output
        key: secret_access_key

{{- end -}}
