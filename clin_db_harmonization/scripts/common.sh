
case "$-" in
    *i*) ;;
    *) set -e ;;
esac

set-env-from-json() {
    for s in $(jq -r 'to_entries|map("\(.key)=\(.value|tostring)")|.[]'); do
        echo "$s"
    done
}

set-db-vars() {
    # sets $username, $password
    export $(aws --region us-east-1 --output text \
                 secretsmanager get-secret-value \
                 --secret-id $AWS_DB_SECRET_ARN \
                 --query 'SecretString' | set-env-from-json)

    # sets $AccessKeyId, $SecretAccessKey, $Token
    METADATA_SERVICE=http://169.254.169.254/latest/meta-data
    IAM_ROLE=$(curl $METADATA_SERVICE/iam/security-credentials)
    export $(curl $METADATA_SERVICE/iam/security-credentials/$IAM_ROLE \
                 | set-env-from-json) 
}

aws-psql() {
    if [[ -z $password ]]; then set-db-vars; fi

    set -u # error on unset variables
    connstr="postgresql://$username:$password@$AWS_DB_HOSTNAME/$AWS_DB_NAME"
    echo Connecting to "$connstr" ...

    echo "$@"
    psql "$connstr" -e -v ON_ERROR_STOP=on "$@"
}

pv-sql() {
    lines=$(
        awk '/;/{l++;} /^\s*[A-Za-z]/{l++;} /s3.table/{l+=4;} END{print l}' \
            < $1
         )
    pv -l -s $lines
}
