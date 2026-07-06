provider "aws" {
  region = var.aws_region
}

locals {
  lambda_zip = "${path.module}/../build/distributions/okta-app-lambda-${var.app_version}.zip"
}

resource "aws_iam_role" "lambda" {
  name = "${var.aws_lambda_function_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "basic_execution" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "this" {
  function_name = var.aws_lambda_function_name
  role          = aws_iam_role.lambda.arn
  runtime       = "java21"
  handler       = "com.example.oktaapp.OktaAppLambda::handleRequest"

  filename         = local.lambda_zip
  source_code_hash = filebase64sha256(local.lambda_zip)

  memory_size = 512
  timeout     = 30

  environment {
    variables = {
      OKTA_ISSUER            = var.okta_issuer
      OKTA_AUDIENCE          = var.okta_audience
      OKTA_WEB_CLIENT_ID     = var.okta_web_client_id
      OKTA_WEB_CLIENT_SECRET = var.okta_web_client_secret
      OKTA_SCOPES            = var.okta_scopes
    }
  }
}

resource "aws_lambda_function_url" "this" {
  function_name      = aws_lambda_function.this.function_name
  authorization_type = "NONE"

  cors {
    allow_origins = var.aws_lambda_cors_allow_origins
    allow_methods = ["*"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 3600
  }
}

resource "aws_lambda_permission" "public_url" {
  statement_id           = "AllowPublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.this.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}
