#!/usr/bin/env python3
from aws_cdk import App, Environment
import os
from cdk_templates.ec2_instance_stack import EC2InstanceStack
import ast

# Define your account id to make import vpc work
env_cn = Environment(account=os.environ.get("AccountId"), region=os.environ.get("AwsRegion"))

input_tags = ast.literal_eval(os.environ.get("Tags"))

# Deploy EC2 instance .
ec2_app = App()
input_tags['BillingService'] = 'EC2'
ukbb_ec2_stack = EC2InstanceStack(ec2_app, os.environ.get("EC2StackName"), env=env_cn,
                                            description="This stack will deploy the demo EC2 Instance and setup UKBB repo.",
                                            tags=input_tags)

ec2_app.synth()


