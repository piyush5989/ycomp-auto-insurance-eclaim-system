# eClaims – Non-Functional Requirements Summary

> **Single-page NFR Summary** · YCompany eClaims Modernisation · v1.0 · April 2026

---

## NFR Coverage Matrix

| # | NFR Area | Requirement | Measurement / SLA | Implementation Strategy |
|---|----------|-------------|-------------------|------------------------|
| 1 | **Performance** | 99% of all service requests completed in < 5000ms (peak and off-peak) | p99 latency via Prometheus; measured at API Gateway | Redis caching (TTL 15 min for reference data); CDN for static assets; async document upload via S3 pre-signed URLs; read replicas for reporting queries |
| 2 | **Availability** | System must be up 24×7; auto-restart on crash | Uptime ≥ 99.9% (< 9 hrs downtime/year) | Multi-AZ ECS Fargate deployment; ECS health checks with auto-restart; ALB health routing; Circuit Breaker (Resilience4j) per service |
| 3 | **Scalability** | Handle increased load in future; support both on-prem and cloud with auto-scaling | Scale from baseline to 3× during peak without manual intervention | ECS Fargate Auto Scaling (CPU > 70%); Kafka partition scaling; RDS read replicas; containerised services support on-prem Kubernetes deployment |
| 4 | **Security – Authentication** | RBAC; role actions configurable without code changes | All endpoints protected; no unauthorised access | Keycloak IdP (OIDC/OAuth2); JWT Bearer tokens; MFA (TOTP); role claims in JWT validated per service |
| 5 | **Security – Data Protection** | Encryption at rest and in transit; OWASP Top 10 compliance | Pen test quarterly; no Critical/High findings | TLS 1.3 (all communications); AES-256 (RDS encrypted volumes, S3 SSE-KMS); WAF with OWASP rule groups; OWASP Dependency-Check in CI/CD |
| 6 | **Security – No Repudiation** | All claim actions must be traceable and non-deniable | 100% coverage of write operations in audit log | Kafka `audit-events` topic (append-only, immutable, 7yr retention); every write logged with userId + timestamp + payload hash |
| 7 | **Fraud Detection** | System must identify potentially fraudulent claims | Fraud-flagged claims surfaced in reporting; < 1hr detection | Rule-based engine (duplicate incidents, claim amount anomalies, suspicious patterns); fraud ageing matrix in reporting dashboard |
| 8 | **Reliability & Recovery** | Distributed backup; RTO < 1 hour; RPO < 15 minutes | Measured via DR drill (quarterly) | Active-passive DR (us-east-1 → us-west-2); RDS cross-region replication; Route 53 DNS failover (TTL 60s); S3 Cross-Region Replication |
| 9 | **Logging & Observability** | Sufficient logging to debug any error condition; monitor and maintain system | All requests traceable via correlation ID; P1 alert < 5 min | ELK Stack (centralised logs); Prometheus + Grafana (metrics & dashboards); Jaeger / OpenTelemetry (distributed tracing); CloudWatch alarms |
| 10 | **Maintainability** | Testability, configurability, upgradeability | ≥ 80% unit test coverage; zero-downtime deployments | GitHub Actions CI/CD; blue-green deployment; SonarQube code quality gate; Testcontainers integration tests; API versioning (v1, v2) |
| 11 | **Compliance & Archival** | All claims data and communications archived for auditing | 7-year document retention; WORM (immutable) storage | AWS S3 Object Lock (WORM policy); S3 Lifecycle (Glacier after 30 days); Audit log retention 7 years; DMS metadata in PostgreSQL |
| 12 | **Configurability** | Role permissions configurable without deployment | Role changes take effect without code release | Keycloak Admin Console for role/permission management; feature flags via environment configuration |

---

## NFR Risk & Mitigation Summary

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Kafka consumer lag causes delayed notifications | Medium | High | Consumer lag monitoring (Grafana); auto-scaling consumers; DLQ for retries |
| RDS write bottleneck under peak load | Medium | High | Connection pooling (HikariCP); read replicas; async writes where applicable |
| Keycloak single point of failure | Low | Critical | Keycloak HA cluster (2 nodes, active-active); session persistence in PostgreSQL |
| S3 upload timeout for large documents | Medium | Medium | Pre-signed URLs (direct upload bypass API); max file size enforced at gateway |
| DR failover RPO exceeded | Low | Critical | Continuous RDS replication monitoring; automated RPO alerting in CloudWatch |
| Fraudulent claims not detected in real time | Medium | High | Phase 1 rules engine + Phase 2 ML scoring; manual audit override by Case Manager |

---

*See [q1-solution-approach.md](./q1-solution-approach.md) for the full architecture document.*
