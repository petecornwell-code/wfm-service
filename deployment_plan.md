# WFM Service - AWS Deployment Plan

## Overview

This document describes the plan for deploying the WFM Service (Workforce Management) application to AWS. The system consists of a Java/Spring Boot backend, a React frontend, and a PostgreSQL database.

## Architecture

```
                        ┌──────────────┐
                        │  CloudFront  │
                        │  (CDN)       │
                        └──────┬───────┘
                               │
                 ┌─────────────┴─────────────┐
                 │                           │
          ┌──────▼──────┐           ┌────────▼────────┐
          │  S3 Bucket  │           │  Application    │
          │  (Frontend) │           │  Load Balancer  │
          └─────────────┘           └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  ECS Fargate    │
                                    │  (Backend)      │
                                    └────────┬────────┘
                                             │
                                    ┌────────▼────────┐
                                    │  RDS PostgreSQL │
                                    │  (Database)     │
                                    └─────────────────┘
```

## Infrastructure (Terraform)

All infrastructure is defined in the `infra/` directory and managed with Terraform.

### Networking - VPC

- **Region:** eu-west-2 (London)
- Multi-AZ deployment across availability zones
- Public subnets for the ALB
- Private subnets for ECS tasks and RDS
- Security groups for network isolation between tiers

### Compute - ECS Fargate

- Fargate launch type (serverless containers, no EC2 management)
- Docker image built from the multi-stage `Dockerfile` (Eclipse Temurin 21)
- Image stored in **ECR** (Elastic Container Registry)
- Health checks via Spring Actuator (`/actuator/health`)
- Deployment strategy: rolling update (100-200%) for zero-downtime deploys
- CloudWatch log group with 30-day retention

### Database - RDS PostgreSQL

- PostgreSQL 16.4
- Encryption at rest enabled
- Automated backups with 7-day retention
- Auto-scaling storage: 20 GB initial, up to 100 GB
- Deletion protection enabled for production
- Credentials stored in **AWS Secrets Manager**
- Database migrations handled by Flyway on application startup

### Frontend - S3 + CloudFront

- React SPA built with Vite and deployed to an S3 bucket
- CloudFront distribution with origin access control (OAC)
- Cache strategy:
  - Immutable hashed assets (`/assets/*`): long-lived cache
  - `index.html`: short TTL for instant SPA updates
- ALB routes `/api/*` and `/actuator/*` to the backend; all other paths serve the SPA

### Secrets

- RDS password managed by AWS Secrets Manager
- AWS authentication via OIDC (no static credentials in CI/CD)

## CI/CD Pipeline (GitHub Actions)

### Continuous Integration (`ci.yml`)

Runs on every pull request and push to `main`:

1. **Backend:** Run tests (`./gradlew test`), build JAR (`./gradlew bootJar`)
2. **Frontend:** Install dependencies (`npm ci`), build (`npm run build`)
3. **Docker:** Build container image to verify the Dockerfile

### Continuous Deployment (`deploy.yml`)

Triggered on push to `main` (production environment):

1. **Authenticate** to AWS using OIDC federation
2. **Backend deployment:**
   - Build Docker image
   - Push to ECR
   - Update ECS task definition with new image
   - Deploy updated service to ECS
   - Wait for service stability
3. **Frontend deployment:**
   - Build React app with Vite
   - Sync build output to S3 (with appropriate cache headers)
   - Invalidate CloudFront distribution

## Environments

Multi-environment support via Terraform variables:

| Environment | Purpose                    | Trigger          |
|-------------|----------------------------|------------------|
| dev         | Development and testing     | Manual / branch  |
| staging     | Pre-production validation   | Manual           |
| prod        | Live production traffic     | Push to `main`   |

## Deployment Steps (Manual / First-Time)

### Prerequisites

- AWS CLI configured with appropriate credentials
- Terraform installed
- Docker installed

### 1. Provision Infrastructure

```bash
cd infra
terraform init
terraform plan -var="environment=prod"
terraform apply -var="environment=prod"
```

### 2. Build and Push Docker Image

```bash
aws ecr get-login-password --region eu-west-2 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.eu-west-2.amazonaws.com
docker build -t wfm-service .
docker tag wfm-service:latest <account-id>.dkr.ecr.eu-west-2.amazonaws.com/wfm-service:latest
docker push <account-id>.dkr.ecr.eu-west-2.amazonaws.com/wfm-service:latest
```

### 3. Deploy Frontend

```bash
cd frontend
npm ci
npm run build
aws s3 sync dist/ s3://<frontend-bucket>/ --delete
aws cloudfront create-invalidation --distribution-id <dist-id> --paths "/*"
```

### 4. Verify

- Check ECS service is stable and tasks are running
- Confirm health check passes: `curl https://<domain>/actuator/health`
- Verify frontend loads via the CloudFront URL

## Rollback Plan

- **Backend:** Redeploy the previous ECS task definition revision via the AWS console or CLI
- **Frontend:** Re-sync the previous build artifacts to S3 and invalidate CloudFront
- **Database:** Flyway migrations are forward-only; for critical issues, restore from the most recent RDS automated snapshot
