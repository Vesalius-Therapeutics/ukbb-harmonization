#!/usr/bin/env python3
from aws_cdk import App, Environment
import os
from cdk_templates.postgres_rds_stack import UKBBRDSStack
import json

# Define your account id to make import vpc work
env_cn = Environment(account=os.environ.get("AccountId"), region=os.environ.get("AwsRegion"))
input_tags = json.loads(os.environ.get("Tags"))

# Start execution of deployment
secret_rds_app = App()

input_tags['BillingService'] = 'RDS'
ukbb_rds_stack = UKBBRDSStack(secret_rds_app, os.environ.get("RDSStackName"), env=env_cn,
                                            description="This stack will deploy the UKBB RDS Postgres Instance and its secret.",
                                            tags=input_tags)

secret_rds_app.synth()