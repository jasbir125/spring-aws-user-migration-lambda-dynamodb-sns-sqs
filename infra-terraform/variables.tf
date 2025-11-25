variable "jar_lambda-s3-ingest" {
  description = "Path to the UserMigrationProcessor JAR"
  type        = string
}

variable "jar_lambda-sns-dispatcher" {
  description = "Path to the UserValidationLambda JAR"
  type        = string
}

variable "jar_lambda-transform" {
  description = "Path to the UserAuditLambda JAR"
  type        = string
}

variable "jar_lambda-dynamo-writer" {
  description = "Path to the UserNotificationLambda JAR"
  type        = string
}
variable "aws_region" {
  description = "AWS region for deployment"
  type        = string
  default     = "eu-west-1"
}