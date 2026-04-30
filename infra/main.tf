terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.0"
    }
  }

  backend "s3" {
    bucket         = "wfm-terraform-state-982940000233"
    key            = "wfm/dev/terraform.tfstate"
    region         = "eu-west-2"
    dynamodb_table = "wfm-terraform-locks"
    encrypt        = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = "wfm-service"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
