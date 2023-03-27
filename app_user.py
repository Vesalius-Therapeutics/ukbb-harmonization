#!/usr/bin/env python3
from aws_cdk import App, Environment
import os
from cdk_templates.iam_user_stack import IAMUserStack
import json

# Define your account id to make import vpc work
env_cn = Environment(account=os.environ.get("AccountId"),
                     region=os.environ.get("AwsRegion"))
input_tags = json.loads(os.environ.get("Tags"))

# IAM User with Access key and Secret Key in Secret Manager.
iam_app = App()
input_tags['BillingService'] = 'IAM'
iam_user_stack = IAMUserStack(iam_app, os.environ.get("IAMUserStackName"), env=env_cn,
                                          description="This stack will deploy the UKBB IAM user.",
                                          tags=input_tags)

iam_app.synth()
