# ECS (Fargate) POC

This repo deploys by **registering a new task definition revision** whose container images are updated, then **forcing a service rollout**. Env vars and secrets already on the task in AWS are preserved.

## Challenges (short)

- **IAM** - GitHub Actions needs AWS access (OIDC to an IAM role is recommended).
- **First task definition** - Create once in the AWS console (or Terraform) with `prod` profile env, secrets, and **container names** exactly `eclaims-backend` and `eclaims-frontend`.
- **Keycloak** - `VITE_KEYCLOAK_URL` must be whatever the **browser** uses (ALB or CloudFront URL), set as a GitHub Actions variable when building the frontend image.
- **Data plane** - RDS, ElastiCache, MSK, and Keycloak must be reachable from the task subnets and security groups.
- **`application-prod.yml` + Redis** - production config enables Redis TLS. If your cache has TLS off, add an override (profile or env) so the app can connect.

## One-time AWS setup

1. Create **OIDC** provider for GitHub and an IAM role trusted by `token.actions.githubusercontent.com` (restrict `sub` to your repo, e.g. `repo:ORG/REPO:ref:refs/heads/ci-cd`).
2. Attach policies to that role: **ECR** push/pull as needed, `ecs:RegisterTaskDefinition`, `ecs:UpdateService`, `ecs:DescribeTaskDefinition`, `ecs:DescribeServices`, and **`iam:PassRole`** on the **task execution role** (and task role if any) used by the task definition - required for `register-task-definition`.
3. Create **ECR** repositories `eclaims-backend` and `eclaims-frontend` (or let the workflow create them if the role allows `ecr:CreateRepository`).
4. Create **ECS cluster**, **task execution role** (and optional task role), **CloudWatch log group** (e.g. `/ecs/eclaims-poc`).
5. Register a **task definition** (Fargate, awsvpc) with two containers:
   - `eclaims-backend` - port **8090**, health check optional (compose uses liveness).
   - `eclaims-frontend` - port **80**, `dependsOn` backend `HEALTHY` or `START` as you prefer.
6. Create **ECS service** (Fargate) with an **ALB** target group on **port 80** (frontend). Backend **8090** can stay internal to the task.

## GitHub repository variables

| Variable | Purpose |
|----------|---------|
| `AWS_ROLE_ARN` | IAM role ARN for OIDC (deploy + ECR if used in same job). |
| `AWS_REGION` | e.g. `us-east-1`. |
| `VITE_KEYCLOAK_URL` | Public Keycloak base URL for the SPA build. |
| `VITE_KEYCLOAK_REALM` | Optional override (default `eclaims`). |
| `VITE_KEYCLOAK_CLIENT_ID` | Optional override (default `eclaims-web`). |
| `ECS_CLUSTER` | Cluster name for `deploy-ecs`. |
| `ECS_SERVICE` | Service name for `deploy-ecs`. |
| `ECS_TASK_FAMILY` | Task definition **family** name (latest revision is cloned and re-registered). |

Optional: GitHub Environment **`poc`** with required reviewers for production-like gates.

Optional variable **`POC_ALB_URL`**: base URL of the public load balancer (no trailing slash). After deploy, the workflow runs `curl $POC_ALB_URL/actuator/health/readiness` (proxied by nginx to the API).

## Branches

- **Images**: pushes to `main`, `develop`, and `ci-cd` publish to **GHCR**.
- **ECR + ECS deploy**: pushes to **`main`** and **`ci-cd`** only (when the ECS variables above are set).
