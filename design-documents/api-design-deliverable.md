# API Design - Client Ready Deliverable

## Executive Summary

This document presents the comprehensive API design for the system, following RESTful principles and enterprise-grade best practices. The design ensures scalability, security, and maintainability for production environments.

## 1. API Architecture Overview

### 1.1 Design Principles
- **RESTful Architecture**: Standard HTTP methods and status codes
- **Resource-Oriented**: Clear resource identification and hierarchical structure
- **Stateless**: No server-side session state
- **Consistent Response Format**: Standardized response wrapper across all endpoints
- **Versioning Strategy**: URL-based versioning (`/api/v1/`)
- **Security First**: Authentication, authorization, and input validation

### 1.2 Base URL Structure
```
Production: https://api.yourdomain.com/api/v1/
Staging: https://api-staging.yourdomain.com/api/v1/
Development: https://api-dev.yourdomain.com/api/v1/
```

## 2. Standard Response Format

### 2.1 Success Response
```json
{
  "status": "success",
  "data": {
    // Response payload
  },
  "metadata": {
    "timestamp": "2026-05-14T12:33:00Z",
    "version": "v1",
    "requestId": "uuid-here"
  }
}
```

### 2.2 Error Response
```json
{
  "status": "error",
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": [
      {
        "field": "email",
        "message": "Invalid email format"
      }
    ]
  },
  "metadata": {
    "timestamp": "2026-05-14T12:33:00Z",
    "version": "v1",
    "requestId": "uuid-here"
  }
}
```

### 2.3 Paginated Response
```json
{
  "status": "success",
  "data": {
    "items": [],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 150,
      "totalPages": 8,
      "hasNext": true,
      "hasPrevious": false
    }
  }
}
```

## 3. Core API Endpoints

### 3.1 Claims Management

#### 3.1.1 Submit New Claim
```http
POST /api/v1/claims
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "policyNumber": "POL-2026-001234",
  "vehicleRegistration": "ABC-1234",
  "incidentDate": "2026-05-10",
  "incidentLocation": "Mumbai, Maharashtra",
  "description": "Rear-end collision at traffic signal",
  "policeReportFiled": true,
  "policeReportNumber": "FIR-2026-001",
  "claimType": "ACCIDENT"
}
```
*Note: `customerId` and `customerEmail` are securely extracted from the JWT Bearer token claims (`sub` and `email`) and are not required in the payload.*

**Response (201 Created):**
```json
{
  "status": "success",
  "data": {
    "id": "claim-uuid-here",
    "claimNumber": "CLM-2026-001234",
    "status": "SUBMITTED",
    "policyNumber": "POL-2026-001234",
    "estimatedProcessingTime": "7-10 business days",
    "nextSteps": [
      "Document upload required",
      "Surveyor assignment pending"
    ]
  }
}
```

#### 3.1.2 Get Claim Details
```http
GET /api/v1/claims/{claimId}
Authorization: Bearer {jwt-token}
```

#### 3.1.3 List Customer Claims
```http
GET /api/v1/claims/my-claims?page=1&size=10&sortBy=createdAt&sortOrder=desc
Authorization: Bearer {jwt-token}
```

#### 3.1.4 Update Claim Status
```http
PATCH /api/v1/claims/{claimId}/status
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "status": "UNDER_REVIEW",
  "reason": "Additional documentation received"
}
```
*Note: `updatedBy` is securely extracted from the JWT token and not required in the payload.*

#### 3.1.5 Check Duplicate Claims
```http
POST /api/v1/claims/check-duplicates
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "vehicleRegistration": "ABC-1234",
  "incidentDate": "2026-05-10",
  "policyNumber": "POL-2026-001234"
}
```

### 3.2 Document Management

#### 3.2.1 Upload Documents
```http
POST /api/v1/claims/{claimId}/documents
Content-Type: multipart/form-data
Authorization: Bearer {jwt-token}

files: [File, File, ...]
documentTypes: ["VEHICLE_PHOTOS", "POLICE_REPORT"]
```

#### 3.2.2 Get Document List
```http
GET /api/v1/claims/{claimId}/documents
Authorization: Bearer {jwt-token}
```

#### 3.2.3 Download Document
```http
GET /api/v1/documents/{documentId}/download
Authorization: Bearer {jwt-token}
```

### 3.3 Workshop Management

#### 3.3.1 Search Workshops
```http
GET /api/v1/workshops/search?location=Mumbai&radius=25&vehicleType=CAR
Authorization: Bearer {jwt-token}
```

#### 3.3.2 Get Workshop Details
```http
GET /api/v1/workshops/{workshopId}
Authorization: Bearer {jwt-token}
```

### 3.4 Payment & Settlement

#### 3.4.1 Get Payment Status
```http
GET /api/v1/claims/{claimId}/payment-status
Authorization: Bearer {jwt-token}
```

#### 3.4.2 Download Payment Receipt
```http
GET /api/v1/payments/{paymentId}/receipt
Authorization: Bearer {jwt-token}
```

#### 3.4.3 Initiate Payment
```http
POST /api/v1/payments
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "claimId": "claim-uuid-here",
  "paymentType": "DEDUCTIBLE",
  "amount": 5000.00,
  "currency": "INR",
  "paymentMethod": "UPI",
  "recipientType": "INTERNAL"
}
```

### 3.5 Notifications

#### 3.5.1 Get User Notifications
```http
GET /api/v1/notifications?page=1&size=20&status=UNREAD
Authorization: Bearer {jwt-token}
```

#### 3.5.2 Mark Notification as Read
```http
PATCH /api/v1/notifications/{notificationId}
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "status": "READ"
}
```

### 3.6 Customer Management

#### 3.6.1 Get Customer Profile
```http
GET /api/v1/customers/me
Authorization: Bearer {jwt-token}
```

#### 3.6.2 Update Customer Profile
```http
PATCH /api/v1/customers/me
Content-Type: application/json
Authorization: Bearer {jwt-token}

{
  "phoneNumber": "+91-9876543210",
  "addressLine1": "123 New Street",
  "city": "Mumbai",
  "state": "Maharashtra",
  "postalCode": "400001",
  "communicationPref": {
    "email": true,
    "sms": true,
    "push": true
  }
}
```

### 3.7 Reporting & Analytics

#### 3.7.1 Get Regional Claims Summary
```http
GET /api/v1/reports/regional-summary?region=WEST&startDate=2026-01-01&endDate=2026-05-14
Authorization: Bearer {jwt-token}
```

#### 3.7.2 Get Fraud Ageing Matrix
```http
GET /api/v1/reports/fraud-ageing
Authorization: Bearer {jwt-token}
```

## 4. Authentication & Authorization

### 4.1 JWT Token Structure
```json
{
  "sub": "user-id",
  "email": "user@example.com",
  "roles": ["CUSTOMER", "ADMIN"],
  "iat": 1642780800,
  "exp": 1642867200,
  "iss": "your-domain.com"
}
```

### 4.2 Authorization Headers
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 4.3 Role-Based Access Control
- **CUSTOMER**: Access own claims, documents, workshops, profile
- **SURVEYOR**: Access assigned claims, update assessments
- **ADJUSTOR**: Approve/reject claims, settlement processing
- **CASE_MANAGER**: View all regional claims, override capabilities
- **WORKSHOP**: View linked claims, submit estimates, update repair status
- **ADMIN**: Full system access, user management

## 5. Error Handling

### 5.1 HTTP Status Codes
- `200 OK` - Successful GET, PATCH
- `201 Created` - Successful POST
- `204 No Content` - Successful DELETE
- `400 Bad Request` - Invalid request format/data
- `401 Unauthorized` - Missing/invalid authentication
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `409 Conflict` - Duplicate resource/business rule violation
- `422 Unprocessable Entity` - Validation errors
- `429 Too Many Requests` - Rate limiting
- `500 Internal Server Error` - System errors

### 5.2 Error Codes
```
VALIDATION_ERROR - Input validation failed
UNAUTHORIZED_ACCESS - Authentication required
FORBIDDEN_OPERATION - Insufficient permissions
RESOURCE_NOT_FOUND - Requested resource not found
DUPLICATE_RESOURCE - Resource already exists
BUSINESS_RULE_VIOLATION - Business logic constraint failed
RATE_LIMIT_EXCEEDED - Too many requests
EXTERNAL_SERVICE_ERROR - Third-party service failure
SYSTEM_ERROR - Internal server error
```

## 6. Rate Limiting

### 6.1 Rate Limits by Role
- **Anonymous**: 100 requests/hour
- **Customer**: 1000 requests/hour
- **Internal Users**: 5000 requests/hour
- **Admin**: Unlimited

### 6.2 Rate Limit Headers
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 999
X-RateLimit-Reset: 1642867200
```

## 7. API Versioning Strategy

### 7.1 Version Lifecycle
- **v1**: Current stable version
- **v2**: Future version (backward compatible for 12 months)
- **Deprecation**: 6-month notice before version retirement

### 7.2 Version Headers
```http
API-Version: v1
Accept-Version: v1
```

## 8. Security Considerations

### 8.1 Input Validation
- Request size limits (10MB for file uploads)
- Input sanitization and validation
- SQL injection prevention
- XSS protection

### 8.2 Security Headers
```http
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000
```

### 8.3 CORS Configuration
```http
Access-Control-Allow-Origin: https://yourdomain.com
Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE
Access-Control-Allow-Headers: Content-Type, Authorization
```

## 9. Performance & Monitoring

### 9.1 Response Time SLAs
- **GET operations**: < 200ms (95th percentile)
- **POST operations**: < 500ms (95th percentile)
- **File uploads**: < 2s (95th percentile)

### 9.2 Monitoring Headers
```http
X-Request-ID: unique-request-identifier
X-Response-Time: 145ms
X-Service-Version: 1.2.3
```

## 10. API Documentation

### 10.1 OpenAPI Specification
- Complete OpenAPI 3.0 specification available
- Interactive documentation via Swagger UI
- Postman collection for testing

### 10.2 SDK Availability
- JavaScript/TypeScript SDK
- Java SDK
- Python SDK
- Mobile SDKs (iOS/Android)

---

## Appendix A: Sample Integration Code

### Frontend Integration (TypeScript)
```typescript
import { claimsApi } from './api/claimsApi';

// Submit new claim
const submitClaim = async (claimData) => {
  try {
    const response = await claimsApi.submit(claimData);
    return response.data;
  } catch (error) {
    console.error('Claim submission failed:', error);
    throw error;
  }
};
```

### Backend Integration (Java)
```java
@RestController
@RequestMapping("/api/v1/claims")
public class ClaimsController {
    
    @PostMapping
    public ResponseEntity<ApiResponse<ClaimResponse>> submitClaim(
            @Valid @RequestBody ClaimSubmissionRequest request) {
        // Implementation
    }
}
```

---