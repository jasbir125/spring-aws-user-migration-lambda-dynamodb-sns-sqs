provider "aws" {
  region                      = var.aws_region
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    lambda       = "http://localhost:4566"
    s3           = "http://localhost:4566"
    dynamodb     = "http://localhost:4566"
    sns          = "http://localhost:4566"
    sqs          = "http://localhost:4566"
    iam          = "http://localhost:4566"
    sts          = "http://localhost:4566"
  }
}