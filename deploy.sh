#!/bin/sh
set -e -x

# Read Arguments
export EnvironVarLower=$1
echo "Running for the environment/config variable : ${EnvironVarLower} ."

## Initialise Variables
source configs/deploy_config.env ${EnvironVarLower}

# Deploy All Stacks
cdk deploy --app "python app_secrets_rds.py" --all --require-approval never
cdk deploy --app "python app_user.py" --all --require-approval never
cdk deploy --app "python app_ec2.py" --all --require-approval never
cdk deploy --app "python app_glue.py" --all --require-approval never --verbose

echo '{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": {
                "Service": "ec2.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        },
        {
            "Effect": "Allow",
            "Principal": {
                "AWS": "arn:aws:iam::'$AccountId':user/'$UKBBIAMUserName'",
                "Service": "ec2.amazonaws.com"
            },
            "Action": "sts:AssumeRole"
        }
    ]
}' > update_trust_relationship.json

# Update the Account Id in json file first.
aws iam update-assume-role-policy --role-name $EC2InstanceRoleName --policy-document file://update_trust_relationship.json