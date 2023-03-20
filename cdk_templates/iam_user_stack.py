from aws_cdk import (
    Stack,
    aws_iam as iam,
    aws_secretsmanager as sm
)
from constructs import Construct
import os


class IAMUserStack(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        # # Create IAM Role for IAM User
        # Add required policies for particular accesses.
        statements = [iam.PolicyStatement(actions=["secretsmanager:*"], resources=["arn:aws:secretsmanager:%s:%s:secret:%s" % (os.environ.get("AwsRegion"), os.environ.get("AccountId"), os.environ.get("IAMUserCredentialsSecretName"))]),
                      iam.PolicyStatement(actions=["rds:*"], resources=["arn:aws:rds:%s:%s:db:%s" % (os.environ.get(
                          "AwsRegion"), os.environ.get("AccountId"), os.environ.get("UKBBRDSInstanceIdentifier"))]),
                      iam.PolicyStatement(actions=["s3:*"], resources=[
                                          "arn:aws:s3:::%s/*" % os.environ.get('S3BasketBucketName')]),
                      ]



        # definition: sfn.IChainable
        user = iam.User(self, "MyUser", user_name=os.environ.get("UKBBIAMUserName"))

        for statement in statements:
            user.add_to_policy(statement)
        
        # Creates a new IAM user, access and secret keys, and stores the secret access key in a Secret.
        access_key = iam.AccessKey(self, "AccessKey", user=user)

        # Create UKBB Clin Data Loading User User Password Secret
        stack = IAMUserStack.of(self)
        createUKBBClinDataUserSecret = sm.Secret(self, "createUKBBClinDataUserSecret",
                                            description="Credentials for UKBB Clin Data Loading User",
                                            secret_name=os.environ.get(
                                                "IAMUserCredentialsSecretName"),
                                            generate_secret_string=sm.SecretStringGenerator(
                                                exclude_characters="{`~!|#$%^&*()_-+={[]}}\:;\"'<,>.?/}",
                                                password_length=10,
                                                generate_string_key="dummy_password",
                                                secret_string_template=stack.to_json_string({
                                                    'username': os.environ.get("UKBBIAMUserName"),
                                                    'access_key': access_key.access_key_id,
                                                    'secret_access_key': access_key.secret_access_key.to_string()
                                                })
                                            )
                                            )
