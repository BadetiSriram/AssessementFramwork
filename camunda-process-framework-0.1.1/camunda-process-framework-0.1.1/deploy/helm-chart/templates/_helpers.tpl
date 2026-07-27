{{/*
Expand the name of the chart.
*/}}
{{- define "camunda-service.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this
(by the DNS naming spec).
If release name contains the chart name it will be used as a full name.
*/}}
{{- define "camunda-service.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart label (chart name + version, with any '+' replaced by '_').
*/}}
{{- define "camunda-service.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels applied to every resource so kubectl / Helm can identify and
manage the full set of objects owned by this release.
*/}}
{{- define "camunda-service.labels" -}}
helm.sh/chart: {{ include "camunda-service.chart" . }}
{{ include "camunda-service.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels used by the Deployment / Service selector.
These must be immutable after initial deploy (changing them requires a
delete-and-recreate of the Deployment).
*/}}
{{- define "camunda-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "camunda-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Resolve the ServiceAccount name to use for pods.
If serviceAccount.create is true and no override name is given, the fullname
template is used so the SA and the Deployment reference the same name.
*/}}
{{- define "camunda-service.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "camunda-service.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}
