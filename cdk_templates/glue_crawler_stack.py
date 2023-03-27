from aws_cdk import (
    Stack,
    CfnOutput,
    aws_glue as glue,
    aws_iam as iam,
)
from constructs import Construct
import os

class GlueStack(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        # Create the UKBB database
        createUKBBDB = glue.CfnDatabase(self, "MyCfnDatabase",
                            catalog_id=os.environ.get("AccountId"),
                            database_input=glue.CfnDatabase.DatabaseInputProperty(
                                description="This database will consist of harmonized tables of UKBB Data Basket",
                                name=os.environ.get("UKBBGlueDBName"),
                            )
                        )

        # Create Role and assign policies
        glue_crawler_role = iam.Role(self, 'glue_crawler_role',
            role_name="UKBBCrawlerJobRole",
            assumed_by=iam.ServicePrincipal('glue.amazonaws.com'),
            managed_policies=[iam.ManagedPolicy.from_aws_managed_policy_name("service-role/AWSGlueServiceRole")],
        )

        statement = iam.PolicyStatement(actions=["s3:*"],
                                          resources=["arn:aws:s3:::%s/*" % os.environ.get('S3BasketBucketName')])
        glue_crawler_role.add_to_policy(statement)

        # Create Glue Crawler
        glue_crawler_ukbb_tables = glue.CfnCrawler(
            self, 'glue_crawler_ukbb_tables',
            database_name=os.environ.get("UKBBGlueDBName"),
            description="This crawler will create the UKBB tables from Data basket.",
            name="glue_crawler_ukbb_basket_tables",
            role=glue_crawler_role.role_arn,
            targets={
                "s3Targets": [{"path": "s3://%s/rds-parquet-exports/" % os.environ.get('S3BasketBucketName')}]
            },
        )
