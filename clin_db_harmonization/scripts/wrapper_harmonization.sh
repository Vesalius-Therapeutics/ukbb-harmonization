#!/bin/bash
# Title : wrapper_harmonization.sh
# Author : Sarthak Vilas Patel
# Description :
# This script will be used to run harmonization on the data basket file after its brought in readable format.
# Below are the steps this code will run.
# 1. Prepare the data.props file in conf folder using the secrets for RDS instance, IAM user.
# 2. Start loading using load-all.sh
#   a. First go through the input file and just use 1 record/patient.
#   b. Create the RDS schema file and execute it so all tables are created.
#   c. Load the data for this 1 patient.
#   d. Once successful, split the patients in the counts of 1000's as mentioned in load-all.sh as "split_count"
#   e. Iterate over the chunks as per the cores available and load those.
#   f. Once loaded, the files are also uploaded to s3.
#   g. We then run exports from RDS to S3 in parquet format for the Data Lake (Athena) tables.
#==============================================================================
source /ukbb-harmonization/configs/deploy_config.env prod

export input_data_file=$1
export DATA_PROPS_FILE=/ukbb-harmonization/clin_db_harmonization/java/ukbb/conf/data.props
export TEMP_DIR=/ukbb-harmonization/clin_db_harmonization/temp
export UKBB_JAR=/ukbb-harmonization/clin_db_harmonization/java/ukbb/target/ukbb-0.0.1-SNAPSHOT-jar-with-dependencies.jar

# Copy the initial parent file so that any variable changes would be taken care of
cp ${DATA_PROPS_FILE}_initial_file ${DATA_PROPS_FILE}

# # Update the tfvars file with the correct passwords.
get_ukbb_user_key_from_sm=`aws secretsmanager get-secret-value --secret-id ${IAMUserCredentialsSecretName} | jq -r '.SecretString'`
access_key=`echo $get_ukbb_user_key_from_sm | jq ".access_key" | cut -d '"' -f2`
secret_access_key=`echo $get_ukbb_user_key_from_sm | jq ".secret_access_key" | cut -d '"' -f2`

assume_role_details=`AWS_ACCESS_KEY_ID=$access_key AWS_SECRET_ACCESS_KEY=$secret_access_key aws sts assume-role --role-arn "arn:aws:iam::${AccountId}:role/${EC2InstanceRoleName}" --role-session-name "ukbb" --duration-seconds 43100`
AccessKeyId=`echo $assume_role_details | jq -r '.Credentials' | jq ".AccessKeyId" | cut -d '"' -f2`
SecretAccessKey=`echo $assume_role_details | jq -r '.Credentials' | jq ".SecretAccessKey" | cut -d '"' -f2`
SessionToken=`echo $assume_role_details | jq -r '.Credentials' | jq ".SessionToken" | cut -d '"' -f2`

# Update the code with the correct values as per config file.
array=( AccessKeyId SecretAccessKey SessionToken S3BasketBucketName AwsRegion )
for i_var in "${array[@]}"
do
	i_val=${!i_var}
	sed -i "s~${i_var}~${i_val}~g" ${DATA_PROPS_FILE}
done

# Get RDS Connection Details from Secret Manager
get_ukbb_rds_creds_from_sm=`aws secretsmanager get-secret-value --secret-id ${UKBBRdsSecretName} | jq -r '.SecretString'`
export AWS_DB_HOSTNAME=`echo $get_ukbb_rds_creds_from_sm | jq ".host" | cut -d '"' -f2`
export username=`echo $get_ukbb_rds_creds_from_sm | jq ".username" | cut -d '"' -f2`
export password=`echo $get_ukbb_rds_creds_from_sm | jq ".password" | cut -d '"' -f2`

# Start loading of input files to RDS
./load-all.sh ${input_data_file} ${DATA_PROPS_FILE}
if [ $? -ne 0 ]
then
    echo "The process has failed. Please check and resubmit."
    exit 11;
else
    echo "Completed loading data to RDS. Proceeding with extraction in parquet format."
    python3 run_parquet_exports.py ${S3BasketBucketName} ${AWS_DB_HOSTNAME} ${username} ${password}
    aws glue start-crawler --name "glue_crawler_ukbb_basket_tables"
    echo "The AWS Glue crawler glue_crawler_ukbb_basket_tables was executed and the Athena tables should be available in few mins."

fi
