output "alb_dns_name" {
  description = "ALB DNS name (API endpoint)"
  value       = aws_lb.main.dns_name
}

output "cloudfront_domain" {
  description = "CloudFront distribution domain (frontend)"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "ecr_repository_url" {
  description = "ECR repository URL"
  value       = aws_ecr_repository.app.repository_url
}

output "rds_endpoint" {
  description = "RDS instance endpoint"
  value       = aws_db_instance.main.endpoint
}

output "s3_frontend_bucket" {
  description = "S3 bucket for frontend assets"
  value       = aws_s3_bucket.frontend.id
}
