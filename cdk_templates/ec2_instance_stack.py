from aws_cdk import (
    Stack,
    CfnOutput,
    aws_ec2 as ec2,
    aws_iam as iam,
    Duration
)
from constructs import Construct
import os


class EC2InstanceStack(Stack):

    def __init__(self, scope: Construct, id: str, **kwargs) -> None:
        super().__init__(scope, id, **kwargs)

        # Get VPC details
        Ivpc = ec2.Vpc.from_lookup(self, "VPC", vpc_id=os.environ.get("VpcId"))

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


        # Create IAM Role for EC2 Instance
        createEC2InstanceRole = iam.Role(self, "createEC2InstanceRole",
                                         assumed_by=iam.ServicePrincipal("ec2.amazonaws.com"),
                                         description="This instance role will be used by the EC2 instance",
                                         role_name=os.environ.get("EC2InstanceRoleName"),
                                         max_session_duration=Duration.seconds(43100),
                                         managed_policies=[
                                             iam.ManagedPolicy.from_aws_managed_policy_name(
                                                 "AmazonSSMManagedInstanceCore"),
                                             iam.ManagedPolicy.from_aws_managed_policy_name(
                                                 'service-role/AWSGlueServiceRole')
                                         ],
                                         )

        # Add required policies for particular accesses.
        statements = [iam.PolicyStatement(actions=["secretsmanager:GetResourcePolicy", "secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret", "secretsmanager:ListSecretVersionIds"], 
                                          resources=["arn:aws:secretsmanager:%s:%s:secret:%s*" % (os.environ.get("AwsRegion"), os.environ.get("AccountId"), os.environ.get("IAMUserCredentialsSecretName")),
                                                     "arn:aws:secretsmanager:%s:%s:secret:%s*" % (os.environ.get("AwsRegion"), os.environ.get("AccountId"), os.environ.get("UKBBRdsSecretName"))]),
                      iam.PolicyStatement(actions=["rds:*"], resources=["arn:aws:rds:%s:%s:db:%s" % (os.environ.get(
                          "AwsRegion"), os.environ.get("AccountId"), os.environ.get("UKBBRDSInstanceIdentifier"))]),
                      iam.PolicyStatement(actions=["s3:*"], resources=["arn:aws:s3:::%s/*" % os.environ.get(
                          'S3BasketBucketName'), "arn:aws:s3:::%s" % os.environ.get('S3BasketBucketName')])
                      ]

        for statement in statements:
            createEC2InstanceRole.add_to_policy(statement)

        # Create Instance Profile
        createInstanceProfile = iam.CfnInstanceProfile(self, "createInstanceProfile",
                                                       roles=[
                                                           createEC2InstanceRole.role_name],
                                                       instance_profile_name=os.environ.get("EC2InstanceRoleName")
                                                       )

        CfnOutput(self, "createEC2InstanceRoleName", value=createEC2InstanceRole.role_name)

        # Creating user_data field content
        with open("cdk_templates/user_data.sh") as file:
            user_data = file.read()
            
        file.close()
        
        # Create EC2 Host
        ec2_host = ec2.Instance(self, "ec2_host",
                                instance_type=ec2.InstanceType(
                                    instance_type_identifier=os.environ.get("EC2InstanceType")),
                                instance_name=os.environ.get("EC2InstanceName"),
                                machine_image=ec2.MachineImage.latest_amazon_linux(
                                    generation=ec2.AmazonLinuxGeneration.AMAZON_LINUX_2,
                                    edition=ec2.AmazonLinuxEdition.STANDARD,
                                    virtualization=ec2.AmazonLinuxVirt.HVM,
                                    storage=ec2.AmazonLinuxStorage.GENERAL_PURPOSE
                                ),
                                vpc=Ivpc,
                                key_name=os.environ.get("EC2KeyPairName"),
                                vpc_subnets=ec2.SubnetSelection(
                                    subnets=subnet_object_list),
                                user_data=ec2.UserData.custom(user_data),
                                security_group=ec2.SecurityGroup.from_lookup_by_id(
                                    self, 'DefaultSG', os.environ.get("DefaultSG")),
                                role=createEC2InstanceRole,
                                block_devices=[ec2.BlockDevice(
                                    device_name="/dev/xvda",
                                    volume=ec2.BlockDeviceVolume.ebs(
                                        int(os.environ.get("EC2RootEBSVolume")))
                                )]
                                )

        # Display Commands
        CfnOutput(self, "EC2 Private IP", value=ec2_host.instance_private_ip)
        message = 'Give it 5 mins to spin up before you run : ssh -i "%s.pem" ec2-user@%s' % (os.environ.get("EC2KeyPairName"), ec2_host.instance_private_ip)
        CfnOutput(self, id="Message", value=message)