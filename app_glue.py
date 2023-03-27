#!/usr/bin/env python3
from aws_cdk import App, Environment
import os
from cdk_templates.glue_crawler_stack import GlueStack
import json

# Define your account id to make import vpc work
env_cn = Environment(account=os.environ.get("AccountId"), region=os.environ.get("AwsRegion"))
input_tags = json.loads(os.environ.get("Tags"))

# Deploy EC2 instance .
glue_app = App()
input_tags['BillingService'] = 'Glue'
ukbb_glue_stack = GlueStack(glue_app, os.environ.get("GlueStackName"), env=env_cn,
                                            description="This stack will deploy the Glue Crawler and Database.",
                                            tags=input_tags)

glue_app.synth()


