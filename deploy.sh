#!/usr/bin/env bash
# Build the Lambda zip and deploy it. Extra args are passed to `terraform apply`
# (e.g. ./deploy.sh -auto-approve). One-time setup: see README "Deploy".
set -euo pipefail
cd "$(dirname "$0")"

./gradlew build
VERSION=$(./gradlew -q printVersion)

# Raw org-specific values (OKTA_URL_PREFIX, CLIENT_ID, CLIENT_SECRET, AWS_ACCOUNT_ID).
source local/export_variables.sh

# Terraform reads TF_VAR_<name> env vars as input variables (see terraform/variables.tf).
export TF_VAR_okta_issuer="https://${OKTA_URL_PREFIX}.okta.com/oauth2/default"
export TF_VAR_okta_client_id="$CLIENT_ID"
export TF_VAR_okta_client_secret="$CLIENT_SECRET"

# Skipped once initialized — if the backend or providers change, delete terraform/.terraform to re-init.
if [ ! -d terraform/.terraform ]; then
  terraform -chdir=terraform init -backend-config="bucket=tfstate-${AWS_ACCOUNT_ID}" -input=false
fi

terraform -chdir=terraform apply -var "app_version=$VERSION" "$@"
