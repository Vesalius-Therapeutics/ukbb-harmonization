from aws_cdk import (
    Stack,
    aws_ec2 as ec2,
    aws_rds as rds,
    aws_secretsmanager as sm,
    CfnOutput,
    RemovalPolicy
)
from constructs import Construct
import os

class UKBBRDSStack(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        # Get VPC details
        Ivpc = ec2.Vpc.from_lookup(self, "VPC", vpc_id=os.environ.get("VpcId"))

        # Create UKBB RDS Secret
        stack = UKBBRDSStack.of(self)
        createUKBBRDSDbSecret = sm.Secret(self, "createUKBBRDSDbSecret",
                                        description="Credentials for UKBB RDS",
                                        secret_name=os.environ.get("UKBBRdsSecretName"),
                                        generate_secret_string=sm.SecretStringGenerator(
                                            exclude_characters="{`~!@#$%^&*()_-+={[]}}\:;\"'<,>.?/}",
                                            password_length=10,
                                            generate_string_key="password",
                                            secret_string_template=stack.to_json_string({
                                                'username': os.environ.get("UKBBRDSDbUserName")
                                                })
                                            )
                                        )


        # Get subnet selection context created
        subnet_ids_list = os.environ.get("SubnetIds").split(",")
        availability_zone_list = os.environ.get("AvailabilityZones").split(",")
        subnet_object_list = []
        count = 0
        for subnet_id in subnet_ids_list:
            subnet_object_list.append(ec2.Subnet.from_subnet_attributes(self, "subnet_%s" % count,
                                                                        subnet_id=subnet_id,
                                                                        availability_zone=availability_zone_list[count]
                                                                        ))
            count += 1

        # Create Subnet Group
        ukbb_subnet_group = rds.SubnetGroup(self, "UKBBSubnetGroup",
            description="description",
            vpc=Ivpc,
            # the properties below are optional
            removal_policy=RemovalPolicy.DESTROY,
            subnet_group_name=os.environ.get("UKBBRDSDbSubnetGroupName"),
            vpc_subnets=ec2.SubnetSelection(
                availability_zones=os.environ.get("AvailabilityZones").split(","),
                # subnet_filters=[subnet_filter],
                subnets=subnet_object_list
            )
        )


        # Create Security Group 
        createRDSSecurityGroup = ec2.SecurityGroup(self, "createRDSSecurityGroup",
                                                   vpc=Ivpc,
                                                   allow_all_outbound=True,
                                                   description="This security group will be used for UKBB RDS Instance",
                                                   security_group_name=os.environ.get("UKBBRDSSecurityGroupName")
                                                   )

        # Add VPN CIDR to access this security group for SSH and HTTPS
        createRDSSecurityGroup.add_ingress_rule(
            peer=ec2.Peer.ipv4(os.environ.get("SubnetsCidr")),
            connection=ec2.Port.tcp(5432)
        )


        # Create RDS Instance
        createUKBBRDSInstance = rds.DatabaseInstance(self, "createUKBBRDSInstance",
            engine=rds.DatabaseInstanceEngine.POSTGRES,
            instance_type=ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.LARGE),
            credentials=rds.Credentials.from_secret(
                createUKBBRDSDbSecret, os.environ.get("UKBBRDSDbUserName")),
            vpc=Ivpc,
            publicly_accessible=False,
            security_groups=[
                createRDSSecurityGroup,
                ec2.SecurityGroup.from_lookup_by_id(self, 'GetDefaultSG', os.environ.get("DefaultSG"))
                             ],
            instance_identifier=os.environ.get("UKBBRDSInstanceIdentifier"),
            subnet_group=ukbb_subnet_group,
            database_name=os.environ.get("UKBBRDSDatabaseName"),
            allocated_storage=int(os.environ.get("UKBBRDSStorageVolume"))
        )
        
        CfnOutput(self, "UKBB RDS Instance Endpoint",
                  value=createUKBBRDSInstance.db_instance_endpoint_address)
        CfnOutput(self, "UKBB RDS Instance Port",
                  value=createUKBBRDSInstance.db_instance_endpoint_port)
