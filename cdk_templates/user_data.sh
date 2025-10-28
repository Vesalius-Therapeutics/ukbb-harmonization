#!/bin/bash
yum -y update
amazon-linux-extras enable postgresql10 epel
yum -y install epel-release
yum -y install maven postgresql jq parallel pv git

# Install python packages
python3 -m pip install awscli botocore==1.27.59 boto3 toolz fsspec==2022.11.0 pandas dask pyarrow s3fs==2022.11.0 requests psycopg2-binary packaging numpy sqlalchemy==1.4.41

# Download Github Repo
#git clone https://github.com/Vesalius-Therapeutics/ukbb-harmonization.git
git clone https://github.com/sarthak1287/ukbb-harmonization.git

# build jar file
cd /ukbb-harmonization/clin_db_harmonization/java/ukbb
mvn clean compile assembly:single


chmod -R 777 /ukbb-harmonization