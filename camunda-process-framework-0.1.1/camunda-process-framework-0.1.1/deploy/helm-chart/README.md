# camunda-service Helm Chart

Generic Helm 3 chart for services built on `camunda-process-framework`. All values
are parameterized; no namespaces, image registries, or secrets are hardcoded.

## Prerequisites

- Helm 3.10+
- Kubernetes 1.25+ (uses `autoscaling/v2`, `policy/v1`)
- Prometheus Operator installed if `serviceMonitor.enabled: true` (default: true)
- NetworkPolicy enforcement by the CNI plugin if `networkPolicy.enabled: true` (default: true)

## Installation

```bash
helm install <release-name> deploy/helm-chart \
  -f deploy/helm-chart/values-service-template.yaml \
  -n <namespace> \
  --create-namespace
```

Example:

```bash
helm install service-template deploy/helm-chart \
  -f deploy/helm-chart/values-service-template.yaml \
  -n camunda-services \
  --create-namespace
```

Before installing, create the Kubernetes Secrets referenced in the `envFrom` block of your
per-service values overlay (or use external-secrets / Sealed Secrets to manage them):

```bash
kubectl create secret generic service-template-camunda-secrets \
  --from-literal=CAMUNDA_CLIENT_ID=<value> \
  --from-literal=CAMUNDA_CLIENT_SECRET=<value> \
  --from-literal=CAMUNDA_CLUSTER_ID=<value> \
  --from-literal=CAMUNDA_REGION=<value> \
  -n camunda-services

kubectl create secret generic service-template-db-secrets \
  --from-literal=DB_URL=<value> \
  --from-literal=DB_USER=<value> \
  --from-literal=DB_PASSWORD=<value> \
  -n camunda-services
```

## Upgrade

```bash
helm upgrade <release-name> deploy/helm-chart \
  -f deploy/helm-chart/values-service-template.yaml \
  -n <namespace>
```

## Uninstall

```bash
helm uninstall <release-name> -n <namespace>
```

Note: PersistentVolumeClaims (if any) and Secrets are not deleted by `helm uninstall`.
Remove them manually if required.

## Values Reference

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `replicaCount` | int | `2` | Number of pod replicas. |
| `image.repository` | string | `""` | Container image repository path. **Required** in overlay. |
| `image.tag` | string | `""` | Container image tag. **Required** in overlay. |
| `image.pullPolicy` | string | `IfNotPresent` | Image pull policy. |
| `imagePullSecrets` | list | `[]` | Pull secret names for private registries. |
| `serviceAccount.create` | bool | `true` | Create a ServiceAccount for pods. |
| `serviceAccount.annotations` | object | `{}` | Annotations for the ServiceAccount (e.g. IRSA). |
| `serviceAccount.name` | string | `""` | Override auto-generated ServiceAccount name. |
| `podSecurityContext` | object | see values.yaml | Pod-level security context (`runAsNonRoot`, `runAsUser`, `fsGroup`). |
| `securityContext` | object | see values.yaml | Container security context (`allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`). |
| `service.type` | string | `ClusterIP` | Kubernetes Service type. |
| `service.port` | int | `8080` | Service port (also used as container port). |
| `service.actuatorPort` | int | `8080` | Port for actuator scraping. |
| `resources.requests.cpu` | string | `250m` | CPU request. |
| `resources.requests.memory` | string | `512Mi` | Memory request. |
| `resources.limits.cpu` | string | `1000m` | CPU limit. |
| `resources.limits.memory` | string | `1Gi` | Memory limit. |
| `probes.liveness.path` | string | `/actuator/health/liveness` | HTTP path for liveness probe. |
| `probes.liveness.initialDelaySeconds` | int | `60` | Liveness probe initial delay. |
| `probes.liveness.periodSeconds` | int | `30` | Liveness probe period. |
| `probes.readiness.path` | string | `/actuator/health/readiness` | HTTP path for readiness probe. |
| `probes.readiness.initialDelaySeconds` | int | `20` | Readiness probe initial delay. |
| `probes.readiness.periodSeconds` | int | `10` | Readiness probe period. |
| `probes.startup.path` | string | `/actuator/health/liveness` | HTTP path for startup probe. |
| `probes.startup.initialDelaySeconds` | int | `10` | Startup probe initial delay. |
| `probes.startup.periodSeconds` | int | `5` | Startup probe period. |
| `probes.startup.failureThreshold` | int | `30` | Startup probe failure threshold (max startup = failureThreshold * periodSeconds). |
| `autoscaling.enabled` | bool | `true` | Enable HPA. |
| `autoscaling.minReplicas` | int | `2` | HPA minimum replicas. |
| `autoscaling.maxReplicas` | int | `10` | HPA maximum replicas. |
| `autoscaling.targetCPUUtilizationPercentage` | int | `70` | HPA CPU scale-out threshold. |
| `autoscaling.targetMemoryUtilizationPercentage` | int | `80` | HPA memory scale-out threshold. |
| `autoscaling.customMetrics` | list | `[]` | Additional Pods-type HPA metrics `[{name, target}]`. |
| `pdb.enabled` | bool | `true` | Enable PodDisruptionBudget. |
| `pdb.minAvailable` | int | `1` | Minimum available pods during voluntary disruptions. |
| `networkPolicy.enabled` | bool | `true` | Enable NetworkPolicy. |
| `networkPolicy.egressPorts` | list | see values.yaml | Allowed egress ports `[{port, protocol}]`. |
| `serviceMonitor.enabled` | bool | `true` | Create Prometheus Operator ServiceMonitor. |
| `serviceMonitor.interval` | string | `30s` | Prometheus scrape interval. |
| `serviceMonitor.scrapeTimeout` | string | `10s` | Prometheus scrape timeout. |
| `serviceMonitor.path` | string | `/actuator/prometheus` | Metrics scrape path. |
| `env` | list | `[]` | Additional container env vars `[{name, value}]` or `[{name, valueFrom}]`. |
| `envFrom` | list | `[]` | Secret or ConfigMap refs for env injection `[{secretRef.name}]`. |
| `configMap.data` | object | `{}` | Key/value pairs created as a ConfigMap and injected via envFrom. |
| `terminationGracePeriodSeconds` | int | `60` | Pod termination grace period. |
| `preStop.enabled` | bool | `true` | Inject a preStop sleep to drain load-balancer connections. |
| `preStop.sleepSeconds` | int | `35` | preStop sleep duration (must be less than terminationGracePeriodSeconds). |
