terraform {
  required_version = ">= 1.5"

  backend "s3" {
    bucket = "tfstate-<ACCOUNT_ID>"
    key    = "okta-app-lambda/terraform.tfstate"
    region = "us-west-2"
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
