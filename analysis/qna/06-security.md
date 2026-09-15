# Q&A Discussion Script: Category 6 - Security

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Explain authentication versus authorization in your architecture.

**Spoken Answer:**
"Authentication and authorization answer two distinct security questions at different points in our architecture:

**Authentication (Who are you?)**:
- Handled at the Identity Tier (AWS Cognito for external policyholders, Keycloak for employees and repair shops).
- The user provides credentials (username/password) plus multi-factor authentication (MFA SMS or TOTP).
- Upon verification, the IdP issues an RS256-signed JSON Web Token (JWT) proving the caller's identity.

**Authorization (What are you permitted to do?)**:
- Evaluated at Amazon API Gateway and inside Spring Boot microservices.
- It inspects the JWT's claims (`realm_access.roles`, `scope`, and user context).
- For example, an authenticated employee might have identity proved by Keycloak, but when they attempt `POST /api/v1/claims/{id}/override`, our authorization engine evaluates whether their role is `CASE_MANAGER` and whether their regional jurisdiction covers that claim."

**Technical Bullets:**
- Authentication: Identity verification -> JWT issuance (Subject `sub`, email, issuance timestamp).
- Authorization: Permission enforcement -> RBAC (Role-Based) and ABAC (Attribute-Based).
- Enforcement: API Gateway enforces coarse routing; Spring Security `@PreAuthorize` enforces fine-grained method and data access.

---

### Q2: Why Cognito for customers?

**Spoken Answer:**
"We chose AWS Cognito for our **200 million policyholders** because of **consumer elasticity, built-in self-service workflows, and pay-per-active-user cloud economics**.

Managing 200 million consumer records in a traditional enterprise IAM system (like Keycloak, Okta, or PingFederate) would be economically prohibitive in licensing and operationally complex in database scaling. 

Cognito gives us:
1. True Serverless Elasticity: Handles millions of concurrent user registrations and logins during catastrophe surges without provisioning database servers.
2. Built-in Consumer Features: Self-registration, email/phone verification, self-service password reset, and SMS MFA out of the box.
3. Cost Efficiency: First 50,000 monthly active users are free; subsequent MAUs cost fractions of a cent ($0.0055/MAU), meaning YCompany pays strictly for active claimants rather than paying per registered inactive policyholder."

**Technical Bullets:**
- Scalability: Serverless user pool scales horizontally to hundreds of millions of identities.
- Protocols: Native OAuth 2.0 / OpenID Connect (OIDC) compliant.
- Security Features: Built-in compromised credential detection, adaptive authentication, and SMS/TOTP MFA.

---

### Q3: Why Keycloak for employees?

**Spoken Answer:**
"While Cognito is ideal for high-volume consumers, internal insurance operations demand **complex enterprise identity federation, dynamic role hierarchies, and zero-downtime permission reconfigurability**.

Keycloak 24 gives our enterprise workforce:
1. Enterprise Active Directory / SAML Federation: Internal adjusters, surveyors, and case managers log in using their existing corporate Azure AD or LDAP single sign-on (SSO) credentials.
2. Fine-Grained RBAC & User-Managed Access (UMA 2.0): Supports multi-level approval hierarchies (e.g., Adjuster Junior vs Adjuster Senior vs Regional Director) and temporary task delegation.
3. Configurable Permissions Without Code Deployment: As required by our assignment, an administrator can create a new role, modify permission scopes, or delegate authority in the Keycloak admin console in real time without recompiling or redeploying Java microservices."

**Technical Bullets:**
- Federation: Native SAML 2.0 / OIDC connectors for corporate Active Directory / Okta.
- Granularity: Resource-based authorization services (UMA 2.0) and attribute policies.
- Compliance: Immediate token revocation and session invalidation via Redis clustering.

---

### Q4: Why not use Cognito for everyone?

**Spoken Answer:**
"Using Cognito for internal insurance staff falls short on enterprise governance requirements:

1. Lack of Native Active Directory / LDAP Synchronization: Cognito's SAML integration is coarse-grained; it does not support dynamic LDAP attribute group mapping or Kerberos desktop SSO required by corporate IT.
2. Primitive Role and Permission Management: Cognito only provides basic 'User Groups'. It lacks fine-grained permission matrices, resource-based policies (UMA 2.0), and dynamic policy evaluation without writing custom Lambda authorizers.
3. No Centralized Session Revocation: In Cognito, once a JWT access token is issued, it cannot be revoked server-side before it expires. If an adjuster is terminated for suspected fraud, corporate IT must be able to kill their active session across all devices instantly, which Keycloak handles natively."

**Technical Bullets:**
- Limitation: Cognito groups lack hierarchical permission inheritance.
- Session Control: No native single-click administrator session termination across all active JWTs.
- Custom Code Overhead: Emulating enterprise RBAC in Cognito requires maintaining complex custom Lambda hooks.

---

### Q5: Why not Keycloak for everyone?

**Spoken Answer:**
"Using Keycloak for 200 million external consumer policyholders would be an **operational and database scaling anti-pattern**.

Keycloak persists its user accounts, credentials, and realm definitions in a relational database (like PostgreSQL). Storing 200 million user records in a relational Keycloak database would require:
1. Massive database clusters with hundreds of gigabytes of RAM just to index consumer login tables.
2. Sizing compute clusters for massive thundering herds when thousands of drivers log in during a regional storm, creating database connection pool saturation.
3. Complex multi-region database replication to maintain active user sessions across geographies.

By using Cognito for consumers, AWS handles the storage, scaling, and high-availability SLA for 200M accounts, leaving Keycloak to handle our lean, high-security internal workforce of 5,000 employees and partner workshops."

**Technical Bullets:**
- Database Bloat: Storing 200M user entities in PostgreSQL crushes Keycloak's relational schema performance.
- Cost: Massive self-hosted infrastructure footprint vs serverless pay-per-active-user Cognito.
- Right-Sizing: Use the right tool for the job - Cognito for massive consumer B2C; Keycloak for enterprise B2E/B2B.

---

### Q6: How does API Gateway validate tokens from two different issuers?

**Spoken Answer:**
"Amazon API Gateway seamlessly validates tokens from both Cognito and Keycloak using a **Dual-Authorizer Architecture**:

1. Path-Based / Audience Routing:
- Customer endpoints (`/api/v1/customer/**`, `/api/v1/claims/submit`) use a native **Amazon Cognito User Pool Authorizer**.
- Staff and workshop endpoints (`/api/v1/internal/**`, `/api/v1/workshops/**`) use a standard **JWT / OIDC Authorizer** pointing to Keycloak's OpenID discovery endpoint (`/.well-known/openid-configuration`).

2. Multi-Issuer Lambda Authorizer (For Shared Endpoints):
For endpoints accessed by both customers and staff (e.g., `GET /api/v1/claims/{claimId}`):
We deploy an Amazon API Gateway Lambda Authorizer. The Lambda inspects the unverified JWT header to read the `iss` (issuer) claim:
- If `iss` matches the Cognito User Pool URL, it validates the cryptographic signature against Cognito's JWKS endpoint.
- If `iss` matches the Keycloak realm URL, it validates against Keycloak's JWKS endpoint.
The JWKS keys are cached in Lambda memory for 1 hour, ensuring sub-5ms token verification."

**Technical Bullets:**
- Native: HTTP API JWT authorizers support OpenID discovery.
- Shared Endpoints: Lambda Authorizer evaluates `token.iss` and validates against cached JWKS.
- Performance: In-memory JWKS caching eliminates network round-trips on every request.

---

### Q7: How are roles represented?

**Spoken Answer:**
"Roles are represented as structured, cryptographically signed claims inside the JWT token:

1. External Customers (Cognito):
The token contains the claim:
`"cognito:groups": ["CUSTOMER"]`
along with custom claims like `"custom:policyNumber": "POL-992182"` and `"sub": "a1b2c3d4-..."`.

2. Internal Staff and Partners (Keycloak):
Keycloak encapsulates roles hierarchically within the standard OIDC structure:
```json
"realm_access": {
  "roles": ["ADJUSTER", "SENIOR_ADJUDICATOR"]
},
"resource_access": {
  "eclaims-backend": {
    "roles": ["claims:read", "claims:approve", "payout:authorize_up_to_25000"]
  }
},
"facility_id": "WRK-TEXAS-401"
```
Our Spring Boot security context maps these tokens into standard Spring `GrantedAuthority` objects (`ROLE_ADJUSTER`, `ROLE_CUSTOMER`)."

**Technical Bullets:**
- Standard: Nested JSON array in JWT payload.
- Mapping: Spring `JwtAuthenticationConverter` converts JSON claims to `SimpleGrantedAuthority`.
- Scope Constraints: Workshop roles include `facility_id` attribute for tenant scoping.

---

### Q8: How are permissions changed without deploying code?

**Spoken Answer:**
"Our assignment explicitly requires: *'Proposed system should utilize an identity management system with role based user authentication and authorization. It should be possible to configure role permissions without changing code.'*

We achieve this through **Keycloak's Administrative Console and Dynamic Policy Engine (Keycloak Authorization Services / UMA 2.0)**:

1. Code Agnostic Decoupling: In our Spring Boot Java code, endpoints and services check generic abstract scopes (e.g., `@PreAuthorize("hasAuthority('claim:approve')")`), rather than hardcoding business names.
2. Admin Console Configuration: In Keycloak's web console, an enterprise security administrator can:
   - Create a new role (e.g., `CATASTROPHE_RESERVE_ADJUSTER`).
   - Assign existing permissions (e.g., attach `claim:approve` and `claim:override` to this role).
   - Adjust approval financial limit attributes (`max_approval_limit = 50000`).
3. Immediate Effect: The moment the admin saves the change, the next token refresh issued to that user contains the updated roles and permissions. Zero Java code is compiled, tested, or redeployed."

**Technical Bullets:**
- Compliance: Fulfills assignment requirement for dynamic permission configuration without code changes.
- Architecture: Role-Permission mapping is externalized to Keycloak realm database.
- Policy Refresh: Changes take effect on next token refresh (within 15 minutes or immediately via token refresh).

---

### Q9: What happens when a user's role changes but their JWT is still valid?

**Spoken Answer:**
"Stateless JWTs present a classic trade-off: because they are validated cryptographically without checking a database on every request, a modified or revoked user's token remains theoretically valid until its expiration timestamp (`exp`).

We solve this using a two-tier defense:

1. Short-Lived Access Tokens: Access tokens have a strict **15-minute lifespan**. In the worst case, a role change propagates within 15 minutes automatically.
2. Near-Real-Time Invalidation via Distributed Token Blacklist:
For high-security operations (e.g., an adjuster is terminated or their permissions are downgraded):
- Keycloak fires a `USER_ROLE_UPDATED` or `REVOKE_USER` event.
- An event consumer writes a revocation record to Amazon ElastiCache Redis:
  `SET blacklist:user:{userId} {min_issued_at_timestamp} EX 900`.
- Our Spring Boot API Gateway / Security Filter inspects Redis (sub-millisecond check). If the JWT's `iat` (issued-at) timestamp is older than the blacklist timestamp, the request is rejected immediately with **HTTP 401 Unauthorized**."

**Technical Bullets:**
- Token Lifetime: 15-minute access token limit bounds exposure.
- Active Blacklisting: Redis store keyed by `userId` storing `revocation_cutoff_time`.
- Gateway Check: Filter compares `token.iat < redis.get(token.sub)` in <1ms.

---

### Q10: How long should access tokens live?

**Spoken Answer:**
"We enforce a strict, industry-standard token lifespan hierarchy aligned with NIST SP 800-63 guidelines:

- **Access Tokens: 15 Minutes**. 
  Access tokens grant direct access to APIs and databases. Keeping their lifetime short minimizes the window of opportunity if a token is intercepted via network sniffing or client-side leakage.
- **Refresh Tokens: 8 to 24 Hours with Refresh Token Rotation (RTR)**.
  Refresh tokens are stored securely in HTTP-only, Secure, SameSite cookies (for web portals) or in encrypted OS keychain storage (iOS Keychain / Android Keystore for mobile). 
  Every time a refresh token is used to acquire a new 15-minute access token, the IdP invalidates the old refresh token and issues a new one. If a stolen refresh token is used twice, the IdP detects the replay attack and revokes the entire token family immediately."

**Technical Bullets:**
- Access Token: 15 minutes (900 seconds).
- Refresh Token: 8 hours (workforce), 24 hours (mobile customers).
- Defense: Refresh Token Rotation (RTR) detects token theft and replay.

---

### Q11: How do you revoke access?

**Spoken Answer:**
"We provide three mechanisms for revoking access depending on the emergency level:

1. Standard Logout: The client calls the IdP logout endpoint (`/protocol/openid-connect/logout`). The refresh token is invalidated in the IdP database, and the client clears local tokens.
2. Immediate Administrative User Revocation (Terminated Employee):
An administrator clicks 'Revoke All Sessions' in the Keycloak admin console or fires a SCIM API command. Keycloak invalidates all active refresh tokens and publishes a `UserRevokedEvent` to Kafka. The security filter writes the user's ID into the Redis revocation blacklist, instantly killing in-flight access tokens within 1 second.
3. System-Wide Emergency Revocation (Compromised Signing Key):
If an asymmetric private signing key is suspected of compromise, we rotate the key in KMS / Keycloak. All microservices immediately fetch the new JWKS public key, causing all tokens signed by the old key to fail cryptographic verification instantly."

**Technical Bullets:**
- Client-Initiated: Refresh token invalidation at IdP.
- Admin-Initiated: Real-time Redis blacklist lookup on `userId`.
- Emergency-Initiated: Cryptographic key rotation via JWKS.

---

### Q12: Why MFA?

**Spoken Answer:**
"Multi-Factor Authentication (MFA) is non-negotiable for insurance platforms because **credential stuffing and credential phishing account for over 80% of insurance account takeover (ATO) attacks**.

Insurance portals hold valuable targets for cybercriminals: policyholders have active direct-deposit bank details for claim payouts, and internal staff possess the authority to disburse tens of thousands of dollars in settlement checks.

We enforce MFA across both user populations:
- Customers: Adaptive MFA via SMS OTP or Time-based One-Time Passwords (TOTP via Google/Microsoft Authenticator) triggered during new device logins or when modifying bank payout coordinates.
- Internal Staff and Partners: Mandatory Phishing-Resistant MFA (FIDO2 / WebAuthn hardware security keys like YubiKeys or corporate Authenticator push notifications) before accessing claims adjudication queues."

**Technical Bullets:**
- Policy: Mandatory MFA for staff/partners; Adaptive risk-based MFA for 200M policyholders.
- Standards: FIDO2 / WebAuthn, TOTP (RFC 6238), SMS OTP.
- Regulatory: Fulfills NAIC (National Association of Insurance Commissioners) cybersecurity standards and NYDFS 23 NYCRR 500.

---

### Q13: Where are secrets stored?

**Spoken Answer:**
"Under our zero-trust engineering rules, **no secret, password, or private key is ever hardcoded in source code, committed to Git, or stored in plaintext configuration files.**

Secrets are stored in **AWS Secrets Manager** and **AWS Systems Manager (SSM) Parameter Store**:
- Sensitive Credentials (Database master passwords, Keycloak DB credentials, Stripe API private keys): Stored in AWS Secrets Manager, encrypted with AWS KMS customer-managed keys.
- Application Configuration Parameters (Kafka bootstrap URLs, feature flags, service endpoints): Stored in SSM Parameter Store (SecureString).

When our ECS Fargate tasks boot up, they do not mount static secret files. The ECS task execution IAM role securely retrieves secrets directly from AWS Secrets Manager via PrivateLink and injects them as ephemeral environment variables into the container runtime memory."

**Technical Bullets:**
- Storage: AWS Secrets Manager (credentials) + SSM Parameter Store (configs).
- Encryption: AWS KMS Customer Managed Keys (CMK).
- Injection: Native ECS task definition secret injection (`secrets: [{ name: "DB_PASS", valueFrom: "arn:aws:secretsmanager:..." }]`).

---

### Q14: How do you rotate secrets?

**Spoken Answer:**
"We implement **automated, zero-downtime secret rotation** managed by AWS Secrets Manager:

1. Database Credentials Rotation (Every 30 Days):
AWS Secrets Manager uses a pre-built Lambda rotation function:
- Step 1 (Create Secret): Lambda generates a new random password and creates a secondary credential in PostgreSQL.
- Step 2 (Set Secret): Lambda sets the password in PostgreSQL.
- Step 3 (Test Secret): Lambda executes a test query to verify the new credentials.
- Step 4 (Finish): Secrets Manager marks the new secret version as `AWSCURRENT`.
2. Application Zero Downtime:
Our Spring Boot microservices use the AWS Secrets Manager JDBC library. When the database rotates passwords, the connection pool catches the initial authentication failure, transparently fetches the new `AWSCURRENT` secret from Secrets Manager, re-authenticates, and continues processing without restarting the container!
3. Third-Party API Keys (Stripe/Twilio):
We maintain two active keys simultaneously during rotation (Key A and Key B). We roll out Key B to services, verify functionality, and then revoke Key A in the vendor dashboard."

**Technical Bullets:**
- Frequency: 30-day automated rotation cycle for RDS/Aurora credentials.
- Tooling: AWS Secrets Manager native Lambda rotation templates.
- Resilience: Two-stage credential rotation ensures zero downtime during cutover.

---

### Q15: What is KMS?

**Spoken Answer:**
"AWS Key Management Service (KMS) is a managed service that makes it easy to create and control the cryptographic keys used to encrypt data across AWS services.

In our architecture, KMS is the cryptographic foundation for data security. It uses FIPS 140-2 Level 3 validated Hardware Security Modules (HSMs) to protect our primary Customer Master Keys (CMKs). 

We use KMS for **Envelope Encryption**:
KMS does not encrypt large gigabyte files directly. Instead, KMS generates a unique, cryptographically strong **Data Encryption Key (DEK)**. Our application encrypts the file (or database block) with the DEK using AES-256-GCM. The DEK itself is then encrypted under the KMS Customer Master Key and stored alongside the ciphertext. KMS guarantees that the master private key never leaves the physical hardware security module."

**Technical Bullets:**
- Hardware: FIPS 140-2 Level 3 Hardware Security Modules (HSMs).
- Technique: Envelope Encryption (Customer Master Key encrypts Data Encryption Key).
- Audit Trail: Every single key usage is immutably logged in AWS CloudTrail for audit compliance.

---

### Q16: Encryption at rest vs encryption in transit?

**Spoken Answer:**
"We enforce ubiquitous encryption across both states throughout the entire platform:

**Encryption in Transit (Data moving over networks)**:
- External: 100% of incoming internet traffic requires **TLS 1.3** terminated at CloudFront and ALB (minimum TLS 1.2 with PFS cipher suites). Plain HTTP requests are rejected with a 301 redirect.
- Internal Service-to-Service: All communication inside our private VPC - between ALB and ECS containers, between microservices and Aurora PostgreSQL, and between consumers and Amazon MSK Kafka brokers - is encrypted using TLS.

**Encryption at Rest (Data sitting on disk)**:
- Relational Database: Amazon Aurora storage volumes encrypted via AES-256 using KMS CMKs.
- Event Streaming: Amazon MSK message logs encrypted at rest via KMS.
- Object Storage: Amazon S3 buckets enforce server-side encryption with AWS KMS (`SSE-KMS`).
- Caches: Redis encryption at rest enabled in ElastiCache."

**Technical Bullets:**
- In Transit: TLS 1.3 edge-to-ALB; TLS 1.2+ internal ALB-to-Fargate and Fargate-to-Aurora/MSK.
- At Rest: AES-256-GCM across all AWS managed storage volumes (EBS, S3, RDS, MSK).
- Enforcement: AWS Config rules automatically flag and terminate any unencrypted storage resource.

---

### Q17: Why TLS 1.3?

**Spoken Answer:**
"We enforce TLS 1.3 at our edge infrastructure for two decisive reasons: **performance and security**:

1. Faster Handshake Latency (1-RTT and 0-RTT):
TLS 1.2 requires two full round-trip network handshakes (2-RTT) between client and server to establish encryption keys before application data can be sent. TLS 1.3 reduces this to a **single round-trip (1-RTT)**, and supports 0-RTT session resumption. For mobile users filing accident reports on cellular networks with 100ms latency, TLS 1.3 shaves 100 to 200 milliseconds off initial connection setup!
2. Elimination of Vulnerable Legacy Ciphers:
TLS 1.3 completely removed obsolete, insecure cryptographic algorithms that plagued TLS 1.2 (such as RSA key exchange, CBC-mode ciphers, SHA-1, and RC4). It mandates modern, secure AEAD (Authenticated Encryption with Associated Data) ciphers like ChaCha20-Poly1305 and AES-GCM with Perfect Forward Secrecy (PFS)."

**Technical Bullets:**
- Latency Reduction: 1-RTT handshake cuts connection setup latency by 50%.
- Cipher Hardening: Eliminates weak legacy algorithms; mandates Forward Secrecy (ECDHE).
- Edge Termination: Configured via AWS CloudFront and ALB Security Policies (`ELBSecurityPolicy-TLS13-1-2-2021-06`).

---

### Q18: Where would mTLS be used?

**Spoken Answer:**
"Mutual TLS (mTLS) - where both the client and server present and validate x509 digital certificates - is deployed in two high-trust communication paths:

1. External Machine-to-Machine B2B Integrations:
When certified national repair workshop chains or vehicle rental partners (e.g., Enterprise Rent-A-Car) connect their enterprise backend servers directly to our B2B APIs to update work orders, we require mTLS at our Application Load Balancer. This guarantees that traffic originates strictly from authorized partner hardware, rendering stolen API keys useless without the corresponding private certificate.
2. High-Trust Internal Service Mesh Communication (Phase 2 EKS):
When we migrate to Amazon EKS with an Istio service mesh, mTLS is enforced automatically between all microservice pods, ensuring cryptographic zero-trust identity verification across internal container boundaries."

**Technical Bullets:**
- Standard: RFC 8705 / X.509 mutual certificate authentication.
- Termination: Terminated at AWS ALB using ALB Mutual Authentication with AWS Private CA.
- Protection: Defends against man-in-the-middle attacks and compromised B2B API credentials.

---

### Q19: What is zero trust in the context of this architecture?

**Spoken Answer:**
"In our architecture, Zero Trust means **'Never trust, always verify' - we assume the internal network perimeter is already breached.**

In traditional castle-and-moat security, once traffic passed the firewall, internal services trusted each other blindly over unencrypted plaintext HTTP without authentication.

Under our Zero Trust implementation:
1. No Implicit Trust: Being inside our private VPC does not grant access to anything. Every single request between microservices requires an authenticated identity token.
2. Mutual Authentication & Encryption: All internal traffic is encrypted via TLS; database connections require IAM DB authentication or TLS certificates.
3. Least Privilege Access: Every ECS Fargate task runs under a scoped IAM Task Role granting access *only* to its specific S3 bucket prefix, its specific KMS key, and its specific database schema.
4. Bastionless Administration: Zero open SSH ports. All operations access is mediated via AWS Systems Manager Session Manager with multi-factor authentication and session audit logging."

**Technical Bullets:**
- Principle: Identity is the new perimeter; network location does not equal trust.
- IAM Scope: Task Execution Role (bootstrap) separated from Task Role (runtime least-privilege).
- Network: No public subnets for databases or apps; PrivateLink for AWS services.

---

### Q20: How do you defend against OWASP Top 10 attacks?

**Spoken Answer:**
"Our assignment explicitly mandates: *'OWASP top 10 security standards should be applied.'* We implement an end-to-end defense covering the entire OWASP Top 10 matrix:

1. A01: Broken Access Control: Defended via Spring Security method authorization (`@PreAuthorize`) and database queries strictly scoped by authenticated `customerId`.
2. A02: Cryptographic Failures: TLS 1.3 everywhere, KMS envelope encryption at rest, and zero plaintext secret storage.
3. A03: Injection (SQL/XSS): 100% parameterized queries via Spring Data JPA/Hibernate (zero string concatenation); AWS WAF SQLi inspection rules; React auto-escapes HTML against XSS.
4. A04: Insecure Design: Threat modeling across all 8 bounded contexts, rate limiting, and business state machine validation.
5. A05: Security Misconfiguration: Infrastructure as Code (Terraform) scanned by tfsec/Checkov; AWS Config rules auto-remediate non-compliant configurations.
6. A06: Vulnerable and Outdated Components: OWASP Dependency-Check and Snyk integrated into CI/CD pipeline to break builds on high/critical CVEs.
7. A07: Identification and Authentication Failures: Managed IdPs (Cognito/Keycloak), mandatory MFA, short-lived tokens (15m), and brute-force protection.
8. A08: Software and Data Integrity Failures: Pre-signed upload validation, ClamAV antivirus scanning in quarantine S3 bucket, and signed container images via AWS Signer.
9. A09: Security Logging and Monitoring Failures: All security events, logins, and permission changes stream to CloudWatch and immutable S3 audit logs.
10. A10: Server-Side Request Forgery (SSRF): AWS IMDSv2 enforced on all compute; outbound requests routed through egress proxies with strict domain whitelisting."

**Technical Bullets:**
- Compliance: Full coverage of OWASP Top 10 2021 standards.
- CI/CD Gates: Snyk / Trivy container scanning + OWASP Dependency-Check in GitHub Actions.
- Runtime Defense: AWS WAF Managed Rule Groups (Core Rule Set, SQLi, Known Bad Inputs).

---

### Q21: What does WAF protect against?

**Spoken Answer:**
"AWS Web Application Firewall (WAF) inspects incoming HTTP/HTTPS traffic at the CloudFront and ALB layer to protect against application-layer (Layer 7) web exploits.

Specifically, AWS WAF protects our eClaims platform from:
1. SQL Injection (SQLi): Inspects URI, query strings, and request bodies for malicious SQL syntax.
2. Cross-Site Scripting (XSS): Blocks script injection patterns targeting our web portals.
3. Volumetric and Layer 7 DDoS Attacks: Integrates with AWS Shield to absorb HTTP floods.
4. Automated Bot Attacks & Scraping: AWS Bot Control blocks malicious scrapers trying to harvest workshop rates or customer data.
5. IP Rate Limiting: Blocks IP addresses that exceed 2,000 requests in a 5-minute rolling window, preventing brute-force login attempts."

**Technical Bullets:**
- Rule Groups: AWSManagedRulesCommonRuleSet, AWSManagedRulesSQLiRuleSet, AWSManagedRulesKnownBadInputsRuleSet.
- Enforcement: Block actions return HTTP 403 Forbidden at AWS edge before reaching backend servers.

---

### Q22: WAF versus API Gateway?

**Spoken Answer:**
"WAF and API Gateway serve distinct, complementary roles in our edge architecture:

**AWS WAF (Security Inspector - Layer 7 Firewall)**:
- Focuses on **threat inspection and malicious traffic filtering**.
- Inspects byte patterns inside headers, URIs, and payloads for exploits (SQLi, XSS, bot signatures).
- Operates as a security filter in front of the API Gateway or ALB.

**Amazon API Gateway (Traffic Controller & Protocol Router)**:
- Focuses on **API lifecycle management, routing, and token validation**.
- Enforces OAuth/JWT token authentication and validates OpenAPI request schemas.
- Manages client usage plans, API rate limiting, and API version routing (`/api/v1`).
- Terminates WebSocket connections for real-time mobile push notifications.

In short: WAF keeps bad actors and malicious payloads out; API Gateway routes legitimate authenticated requests to the correct microservice."

**Technical Bullets:**
- Division of Labor: WAF = Deep packet inspection & threat mitigation; API Gateway = Authentication, throttling, routing.
- Deployment: WAF sits directly in front of API Gateway as an attached Web ACL.

---

### Q23: How do you prevent SQL injection?

**Spoken Answer:**
"We prevent SQL injection through a strict, four-layered defense-in-depth model:

1. Layer 1 (Perimeter): AWS WAF inspects all incoming HTTP query parameters and JSON bodies using the `AWSManagedRulesSQLiRuleSet`, blocking known SQL injection payloads at the edge.
2. Layer 2 (Application Code - The Primary Defense): We enforce **100% Parameterized Queries and Object-Relational Mapping (ORM)** via Spring Data JPA and Hibernate. We strictly prohibit raw SQL string concatenation (`"SELECT * FROM claims WHERE id = '" + id + "'"`). In our custom JPA queries, we strictly use named parameters:
   `@Query("SELECT c FROM Claim c WHERE c.id = :id AND c.customerId = :customerId")`.
   The JDBC driver sends SQL code and user data in separate protocol frames; the database engine never interprets user data as executable SQL commands.
3. Layer 3 (Static Code Analysis): SonarQube and Semgrep run in our CI pipeline to detect any dynamic query construction before code is merged.
4. Layer 4 (Least Privilege Database Users): The PostgreSQL application users have restricted grants (`SELECT`, `INSERT`, `UPDATE`); they have zero permissions to execute `DROP TABLE`, `ALTER`, or administrative commands."

**Technical Bullets:**
- Code: 100% Parameterized queries via Hibernate / Spring Data.
- Perimeter: AWS WAF SQLi managed rules.
- Static Analysis: Semgrep / SonarQube rules block dynamic string interpolation in repositories.
- DB Hardening: Least-privilege schema roles; `public` schema dropped.

---

### Q24: How do you secure S3 claim documents?

**Spoken Answer:**
"Auto accident claims involve sensitive evidence: crash photos, vehicle registrations, and police accident reports. We secure our S3 document repository through five controls:

1. Complete Public Access Block: All S3 buckets have `BlockPublicAcls`, `IgnorePublicAcls`, `BlockPublicPolicy`, and `RestrictPublicBuckets` set to `TRUE`. Zero documents are accessible publicly.
2. Pre-Signed URLs with Short TTL: Access to documents is granted exclusively through cryptographically pre-signed URLs with a **5-minute expiration**. The URL is generated only after verifying user authorization.
3. Server-Side Encryption with KMS: All S3 objects are encrypted at rest using `SSE-KMS` with customer-managed keys.
4. Bucket Policies & VPC Endpoints: S3 bucket policies enforce that reads and writes can *only* originate from our private VPC endpoints via `aws:sourceVpce`, rejecting requests even from authorized AWS users outside the VPC.
5. S3 Object Lock (WORM Storage): Enforced for regulatory compliance to prevent document tampering."

**Technical Bullets:**
- Access: 100% private bucket; S3 Block Public Access enabled at organization level.
- Encryption: `aws:kms` encryption enforced by default bucket configuration.
- Transport: Bucket policy enforces `aws:SecureTransport: true` (rejects plain HTTP).

---

### Q25: What is S3 Object Lock?

**Spoken Answer:**
"Amazon S3 Object Lock is a governance and security feature that allows us to store objects using a **Write Once, Read Many (WORM) model**.

When an accident photo or police report is uploaded, S3 Object Lock prevents the object from being deleted or overwritten for a fixed retention period (for auto insurance, we configure a **7-year retention period**).

Object Lock operates in two modes:
- Governance Mode: Users with specific IAM permissions (`s3:BypassGovernanceRetention`) can alter retention settings or delete the object if necessary during legal overrides.
- Compliance Mode: A completely immutable lock where **NO ONE** - not even the root AWS account owner or an AWS support engineer - can delete the object or shorten its retention period until the retention timer expires."

**Technical Bullets:**
- Model: WORM (Write Once, Read Many).
- Compliance Standard: Meets SEC Rule 17a-4, FINRA, and state insurance department data retention mandates.
- Feature: Must be enabled at bucket creation; requires S3 Versioning enabled.

---

### Q26: Why WORM?

**Spoken Answer:**
"WORM (Write Once, Read Many) storage is an absolute legal and regulatory requirement in the insurance industry:

1. Regulatory Mandates: State Insurance Commissioners and federal financial regulators require that claim files, accident reports, and correspondence be preserved in an immutable state for 7 years to protect consumer rights.
2. Defense Against Ransomware & Insider Sabotage: If a rogue claims adjuster or a compromised administrative account attempts to delete evidence or cover up fraudulent payouts, WORM storage makes deletion physically impossible.
3. Legal Non-Repudiation in Court: When an accident claim goes to litigation, the insurance company must prove in a court of law that the accident photos and surveyor assessments presented in evidence are identical to what was submitted on day one and have never been altered."

**Technical Bullets:**
- Legal Mandate: 7-year statutory retention across US state insurance jurisdictions.
- Security Value: Immune to ransomware encryption and malicious administrative deletion.
- Evidentiary Value: Establishes a verifiable chain of custody for court proceedings.

---

### Q27: Can even an administrator delete a document under Object Lock compliance mode?

**Spoken Answer:**
"**No. Under S3 Object Lock Compliance Mode, absolutely NO ONE can delete the document - not an enterprise systems administrator, not the AWS Account Root user, and not even AWS customer support.**

Once an object is written in Compliance Mode with a 7-year retention period:
- The retention period cannot be shortened by anyone.
- The WORM lock cannot be removed.
- The object cannot be deleted or overwritten until the 7-year clock has elapsed.

The only physical way to delete that object before 7 years is to delete the entire AWS cloud account, which can be protected by AWS Organizations Service Control Policies (SCPs). This provides mathematical certainty that evidence is tamper-proof."

**Technical Bullets:**
- Rule: Compliance mode is irreversible for the duration of the retention period.
- Account Root Override: Even AWS Account Root credentials cannot delete the object.
- Governance Safeguard: Protected by AWS Organizations Service Control Policies (SCPs) preventing account deletion.

---

### Q28: How do you protect customer PII?

**Spoken Answer:**
"Our platform handles sensitive Personally Identifiable Information (PII) including Social Security numbers, driver license numbers, home addresses, and phone numbers.

We protect PII using a multi-tiered data privacy framework:
1. Field-Level Application Encryption: Sensitive PII columns in PostgreSQL (like `ssn` and `driver_license_number`) are encrypted at the application layer using AES-256-GCM before being stored in the database. Even if a DBA executes a raw SQL dump, the PII is unreadable ciphertext.
2. Dynamic PII Masking: In our web portals and API responses, PII is dynamically masked based on role: adjusters view masked SSNs (`***-**-6789`), while full unmasked data is restricted strictly to authorized fraud investigators.
3. Log Sanitization: We implement custom Logback / Log4j2 masking filters that automatically redact credit card numbers, SSNs, and email addresses from application logs, preventing PII leaks into CloudWatch or OpenSearch."

**Technical Bullets:**
- Encryption: Application-level encryption using AWS KMS envelope encryption for sensitive database columns.
- Logging: Logback masking regex pattern filter prevents PII leakage to stdout.
- Data Privacy: Fulfills CCPA (California Consumer Privacy Act) and state insurance privacy frameworks.

---

### Q29: How are bank details protected?

**Spoken Answer:**
"Bank details and credit card numbers are subject to strict financial compliance standards (PCI-DSS and NACHA).

We protect financial details through **Tokenization and Zero-Data-Footprint Design**:
1. Zero Card Data on Servers: For deductible payments, customer credit card and debit card numbers NEVER touch YCompany web servers or databases. The React frontend uses **Stripe Elements / SDK**, which securely captures card details in an isolated iframe and sends them directly to Stripe. Stripe returns an opaque, single-use payment token (`tok_1N...`). Our backend only stores this token.
2. Bank Payout Accounts (ACH / Direct Deposit): When policyholders or certified repair workshops provide ACH routing and account numbers for settlement disbursements, we use **Stripe Financial Connections / Plaid** to tokenize bank accounts.
3. Database Isolation: The Payment Service database stores only the tokenized reference ID, bank name, and the last 4 digits (`**** 4321`) for UI display. Full bank account numbers are never persisted anywhere on our infrastructure."

**Technical Bullets:**
- Standard: PCI-DSS Level 1 compliance scope reduction via SAQ-A tokenization.
- Implementation: Stripe Elements iframe + Stripe Connect for partner workshop payouts.
- Persistence: Only opaque customer tokens and `last4` stored in Aurora database.

---

### Q30: How do you provide non-repudiation?

**Spoken Answer:**
"Non-repudiation guarantees that an actor (whether a customer submitting a claim, a surveyor filing an estimate, or an adjuster authorizing a $20,000 payout) cannot deny having performed that action.

We provide non-repudiation through our **Cryptographic Audit Pipeline**:

1. Immutable Audit Event Stream: Every significant state transition, approval, and financial disbursement generates an `AuditEvent` published to our dedicated `audit-events` Kafka topic.
2. Forensic Metadata Envelope: The audit event contains the authenticated user's ID, their Cognito/Keycloak subject claim, client IP address, user-agent string, exact timestamp, action payload, and the SHA-256 cryptographic hash of the uploaded document or approval decision.
3. WORM Compliance Storage: An audit worker streams these events into an Amazon S3 bucket configured with **S3 Object Lock Compliance Mode**.
4. AWS CloudTrail: All infrastructure-level modifications and database snapshot accesses are permanently recorded in AWS CloudTrail with log file validation enabled."

**Technical Bullets:**
- Envelope: Audit record includes `{ userId, ipAddress, timestamp, action, payloadHash, signature }`.
- Storage: Amazon S3 Object Lock (WORM) with 7-year compliance retention.
- Infrastructure Audit: AWS CloudTrail with SHA-256 digest file validation enabled.
