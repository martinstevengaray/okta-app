
#from okat admin:
# add scope (security -> api -> add scopo) -get scope from here
# add application (applicationa -> applications -> Create App Integration) -get clientId and client_secret from here

#OKTA_URL_PREFIX="okta_url_prefix"
#API_CLIENT_ID="client_id"
#API_CLIENT_SECRET="client-secret"
#LAMBDA_URL="lambda url"
#SCOPE="scope"
source local/export_variables.sh

TOKEN=$(curl -s "https://$OKTA_URL_PREFIX.okta.com/oauth2/default/v1/token" \
  -u "$API_CLIENT_ID:$API_CLIENT_SECRET" \
  -d "grant_type=client_credentials&scope=$SCOPE" | jq -r .access_token)

curl -s "$LAMBDA_URL/hello?who=world" \
      -H "Authorization: Bearer $TOKEN" \
      -H "content-type: application/json" \
      -d '{"greeting": "hi from curl"}' | jq