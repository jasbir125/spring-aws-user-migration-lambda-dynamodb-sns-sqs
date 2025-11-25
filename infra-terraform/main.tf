resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# S3 Buckets
resource "aws_s3_bucket" "lambda_bucket" {
  bucket = "lambda-artifacts-${random_id.bucket_suffix.hex}"
}

resource "aws_s3_bucket" "migration_input_bucket" {
  bucket        = "user-migration-input-bucket"
  force_destroy = false
}

# DynamoDB Table with Streams enabled
resource "aws_dynamodb_table" "user_table" {
  name         = "user_migration_record"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "id"

  stream_enabled   = true
  stream_view_type = "NEW_IMAGE"

  attribute {
    name = "id"
    type = "S"
  }
}

# SNS Topics for chaining
resource "aws_sns_topic" "ingest_to_transform" {
  name = "user-migration-ingest-to-transform"
}

resource "aws_sns_topic" "transform_to_dynamo" {
  name = "user-migration-transform-to-dynamo"
}

resource "aws_sns_topic" "user_migration_fanout_topic" {
  name = "user_migration_fanout_topic"
}

# IAM Role for Lambdas
resource "aws_iam_role" "lambda_exec_role" {
  name = "lambda_exec_role_shared"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "sts:AssumeRole"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_logs" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "lambda_custom_policy" {
  role = aws_iam_role.lambda_exec_role.id
  name = "lambda_custom_permissions"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["s3:GetObject","s3:ListBucket"]
        Resource = [
          aws_s3_bucket.migration_input_bucket.arn,
          "${aws_s3_bucket.migration_input_bucket.arn}/*"
        ]
      },
      {
        Effect = "Allow"
        Action = ["sns:Publish","sns:Subscribe"]
        Resource = [
          aws_sns_topic.ingest_to_transform.arn,
          aws_sns_topic.transform_to_dynamo.arn
        ]
      },
      {
        Effect = "Allow"
        Action = ["dynamodb:PutItem","dynamodb:BatchWriteItem","dynamodb:UpdateItem","dynamodb:GetItem"]
        Resource = aws_dynamodb_table.user_table.arn
      },
      {
        Effect = "Allow",
        Action = ["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],
        Resource = "arn:aws:logs:*:*:*"
      }
    ]
  })
}

# Lambda Definitions and Environment Variables
locals {
  lambdas = {
    user-migration-s3-ingest = {
      handler = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
      jar     = var.jar_lambda-s3-ingest
      env     = {
        SNS_INGEST_TO_TRANSFORM_TOPIC_ARN = aws_sns_topic.ingest_to_transform.arn
      }
    }
    user-migration-transform = {
      handler = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
      jar     = var.jar_lambda-transform
      env     = {
        SNS_TRANSFORM_TO_DYNAMO_TOPIC_ARN = aws_sns_topic.transform_to_dynamo.arn
      }
    }
    user-migration-dynamo-writer = {
      handler = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
      jar     = var.jar_lambda-dynamo-writer
      env     = {
        DYNAMO_TABLE_NAME = aws_dynamodb_table.user_table.name
      }
    }
    user-migration-sns-dispatcher = {
      handler = "org.springframework.cloud.function.adapter.aws.FunctionInvoker::handleRequest"
      jar     = var.jar_lambda-sns-dispatcher
      env     = {
        SNS_USER_MIGRATION_FANOUT_TOPIC_ARN = aws_sns_topic.user_migration_fanout_topic.arn
      }
    }
  }
}

resource "aws_s3_object" "lambda_jars" {
  for_each = local.lambdas

  bucket = aws_s3_bucket.lambda_bucket.id
  key    = "jars/${each.key}.jar"
  source = each.value.jar
  etag   = filemd5(each.value.jar)
}

resource "aws_lambda_function" "multi" {
  for_each = local.lambdas

  function_name = each.key
  handler       = each.value.handler
  runtime       = "java21"

  role             = aws_iam_role.lambda_exec_role.arn
  s3_bucket        = aws_s3_bucket.lambda_bucket.id
  s3_key           = aws_s3_object.lambda_jars[each.key].key
  source_code_hash = filebase64sha256(each.value.jar)

  environment {
    variables = each.value.env
  }
  memory_size = 1024
  timeout     = 60

  depends_on = [aws_s3_object.lambda_jars, aws_iam_role_policy.lambda_custom_policy]
}

# Permissions and triggers for Lambdas

# S3 to S3 Ingest Lambda
resource "aws_lambda_permission" "allow_s3_invoke" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.multi["user-migration-s3-ingest"].function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.migration_input_bucket.arn
}

resource "aws_s3_bucket_notification" "migration_bucket_notification" {
  bucket = aws_s3_bucket.migration_input_bucket.id

  lambda_function {
    lambda_function_arn = aws_lambda_function.multi["user-migration-s3-ingest"].arn
    events              = ["s3:ObjectCreated:*"]
    filter_prefix       = "users/"
  }

  depends_on = [aws_lambda_permission.allow_s3_invoke, aws_lambda_function.multi]
}

# SNS to Transform Lambda
resource "aws_lambda_permission" "allow_sns_invoke_transform" {
  statement_id  = "AllowSNSInvokeTransform"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.multi["user-migration-transform"].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.ingest_to_transform.arn
}

resource "aws_sns_topic_subscription" "transform_subscription" {
  topic_arn = aws_sns_topic.ingest_to_transform.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.multi["user-migration-transform"].arn

  depends_on = [aws_lambda_permission.allow_sns_invoke_transform]
}

# SNS to Dynamo Writer Lambda
resource "aws_lambda_permission" "allow_sns_invoke_dynamo_writer" {
  statement_id  = "AllowSNSInvokeDynamoWriter"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.multi["user-migration-dynamo-writer"].function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.transform_to_dynamo.arn
}

resource "aws_sns_topic_subscription" "dynamo_writer_subscription" {
  topic_arn = aws_sns_topic.transform_to_dynamo.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.multi["user-migration-dynamo-writer"].arn

  depends_on = [aws_lambda_permission.allow_sns_invoke_dynamo_writer]
}

# DynamoDB Stream to SNS Dispatcher Lambda
resource "aws_lambda_permission" "allow_dynamodb_stream_invoke_dispatcher" {
  statement_id  = "AllowDynamoDBInvokeDispatcher"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.multi["user-migration-sns-dispatcher"].function_name
  principal     = "dynamodb.amazonaws.com"
  source_arn    = aws_dynamodb_table.user_table.stream_arn
}

resource "aws_lambda_event_source_mapping" "dynamodb_stream_to_sns_dispatcher" {
  event_source_arn  = aws_dynamodb_table.user_table.stream_arn
  function_name     = aws_lambda_function.multi["user-migration-sns-dispatcher"].arn
  starting_position = "LATEST"

  depends_on = [aws_lambda_function.multi, aws_dynamodb_table.user_table]
}

output "lambda_names" {
  value = keys(aws_lambda_function.multi)
}

output "lambda_arns" {
  value = { for k, v in aws_lambda_function.multi: k => v.arn }
}

output "migration_input_bucket" {
  value = aws_s3_bucket.migration_input_bucket.bucket
}

output "sns_topic_ingest_to_transform_arn" {
  value = aws_sns_topic.ingest_to_transform.arn
}

output "sns_topic_transform_to_dynamo_arn" {
  value = aws_sns_topic.transform_to_dynamo.arn
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.user_table.name
}
