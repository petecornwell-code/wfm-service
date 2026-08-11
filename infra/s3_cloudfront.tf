# ---------- S3 Bucket for Frontend ----------

resource "aws_s3_bucket" "frontend" {
  bucket = "${var.app_name}-${var.environment}-frontend-${data.aws_caller_identity.current.account_id}"

  force_destroy = var.environment != "prod"
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ---------- CloudFront Function: SPA routing ----------

# Replaces the old distribution-wide custom_error_response 403/404 -> /index.html rules, which
# also swallowed API errors. Attached to the default (S3) behavior only.
#
# A request is rewritten to /index.html only when it has no file extension — so /configuration
# and /desks/<uuid>/agents reach the SPA, while /assets/index-abc123.js still 404s honestly if
# it is genuinely missing rather than silently returning HTML.
resource "aws_cloudfront_function" "spa_router" {
  name    = "${var.app_name}-${var.environment}-spa-router"
  runtime = "cloudfront-js-2.0"
  comment = "Rewrite extensionless SPA routes to /index.html"
  publish = true

  code = <<-EOT
    function handler(event) {
      var request = event.request;
      var uri = request.uri;

      // Leave anything that looks like a real file alone (has an extension in the last segment).
      var lastSegment = uri.substring(uri.lastIndexOf('/') + 1);
      if (lastSegment.indexOf('.') !== -1) {
        return request;
      }

      request.uri = '/index.html';
      return request;
    }
  EOT
}

# ---------- CloudFront OAC ----------

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.app_name}-${var.environment}-frontend"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ---------- S3 Bucket Policy (CloudFront access) ----------

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.frontend.arn}/*"
      Condition = {
        StringEquals = {
          "AWS:SourceArn" = aws_cloudfront_distribution.frontend.arn
        }
      }
    }]
  })
}

# ---------- CloudFront Distribution ----------

resource "aws_cloudfront_distribution" "frontend" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "${var.app_name} ${var.environment} frontend"

  # S3 origin for static frontend assets
  origin {
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_id                = "s3-frontend"
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  # ALB origin for API requests
  origin {
    domain_name = aws_lb.main.dns_name
    origin_id   = "alb-api"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  # Default: serve frontend from S3
  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"

    # Rewrites extensionless paths (/configuration, /desks/<id>/agents) to /index.html so the
    # SPA router can handle them. Attached ONLY to this behavior, so /api/* and /actuator/*
    # responses are never touched — unlike custom_error_response, which is distribution-wide.
    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa_router.arn
    }

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    min_ttl     = 0
    default_ttl = 3600
    max_ttl     = 86400
  }

  # /api/* → ALB (no caching)
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "alb-api"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = true
      headers      = ["Authorization", "X-Tenant-ID", "Content-Type"]
      cookies { forward = "all" }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # /actuator/* → ALB (health checks)
  ordered_cache_behavior {
    path_pattern           = "/actuator/*"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "alb-api"
    viewer_protocol_policy = "redirect-to-https"

    forwarded_values {
      query_string = false
      cookies { forward = "none" }
    }

    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  # SPA client-side routing is handled by the spa_router CloudFront Function attached to
  # default_cache_behavior (see below), NOT by custom_error_response.
  #
  # custom_error_response is DISTRIBUTION-WIDE — it cannot be scoped to a cache behavior. The
  # previous 403->200 and 404->200 rules therefore also applied to /api/*, rewriting every API
  # 403/404 into "200 OK" serving index.html. Clients saw a success status with an HTML body and
  # failed at response.json() with "not valid JSON", masking the real status entirely.
  #
  # Do NOT reintroduce custom_error_response here while an API behavior shares this distribution.

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
