Code to generate database schema files for UKBB clinical related data

# Concepts

The Java application (under `java/ukbb`) is able to take a UK
BioBank-formatted CSV file and turn it into a database schema and
input data for use in a PostgreSQL-compatible database.

The scripts under `scripts` are responsible for coordinating the
parallel loading of a large CSV file into an AWS RDS database
instance.

* `load-all.sh` takes the full CSV file, splits it up, calls
  `load-schema.sh` to prepare the database schema, and then
  parallelizes `load-one.sh` over the CSV chunks.
  
* `load-schema.sh` takes a CSV file, splits off the first two lines,
  and calls the Java app with `CREATE_SCHEMA=true` enabled to generate
  the schema data. Then, it loads the schema data into the database.
  
* `load-one.sh` takes a CSV file, calls the Java app, transfers the
  resulting database CSV files to S3, and then calls the `load-sql.sh`
  script to load the data into the database.
  
* `load-sql.sh` takes a file containing SQL statements and sends them
  to the database.
  
* `common.sh` is not meant to be executed directly; it has shell
  functions that are common across the various scripts listed above.
  
Each of the scripts above (except `common.sh`) takes an argument
(usually a path to a CSV file) and a path to a config file that has
various options in it. Some example config files can be found under
`scripts/test-runs`. These config files contain all of the Java
application configuration options, as well as a set of additional
options that are specific to the scripts. These are:

```
# Path to existing directory to hold temporary data
TEMP_DIR=/mnt/ephem/clindb-temp
#
# Full path to Java tool JAR
UKBB_JAR=/home/ec2-user/ukbb-clinical-database/java/ukbb/target/ukbb-0.0.1-SNAPSHOT-jar-with-dependencies.jar
#
# Database hostname (from CloudFormation output)
AWS_DB_HOSTNAME=ukbb-clindb-test-12-db-1k1pzcrfx5w3u.cluster-cbguxfooj5z0.us-east-1.rds.amazonaws.com
#
# Database secret ARN (from CloudFormation output)
AWS_DB_SECRET_ARN=arn:aws:secretsmanager:us-east-1:533739284350:secret:DBSecret-Hl8eI6Z1VGFq-S3vDCT
```

# Setup

## EC2 Instance

You should launch an EC2 instance that has at least 64 GB RAM and
16-32 cores, as well as sufficient local scratch space (4x the size of
the input data or approximately 150 GB). Initial testing has been done
with a `c6gd.8xlarge` instance type. The requirements installation
instructions below assume the use of Amazon Linux 2, but other
distributions should have comparable tools.

The instance must also have assigned an IAM role that grants it access
to the relevant S3 bucket as well as access to the database via a
managed policy that is created by the database CloudFormation template
(see the steps below for details). You can use `ukbb-s3-access` for
this.

## External Requirements

```
sudo amazon-linux-extras enable postgresql10 epel
sudo yum -y install epel-release
sudo yum -y install maven postgresql jq parallel pv

# ephemeral disk mount for c6gd instance type
sudo mkfs.xfs /dev/nvme1n1
sudo mkdir /mnt/ephem
sudo mount /dev/nvme1n1 /mnt/ephem
sudo chmod 1777 /mnt/ephem
```

## Java Build

```
cd java/ukbb/
mvn clean compile assembly:single
```


# Batch Load

1. Launch a new PostgreSQL database instance

    a. Open the AWS console and navigate to the CloudFormation console
    b. Create a stack with new resources
    c. Upload a template file with the file from `cfn/postgres-db.yml`
    d. Specify the stack name as `ukbb-clindb-VER` where VER is a
       short identifier of the database version
    e. Enter the name of the EC2 instance role from the setup step
    f. Adjust the other parameters as needed (defaults should be fine)
    g. Finish the stack launch wizard and wait for the stack to complete

2. Create a configuration file

    a. Start with an existing configuration file
    b. Make sure the `AWS_DB_HOSTNAME` and `AWS_DB_SECRET_ARN`
       parameters are set from the CloudFormation template outputs
    
3. Start the load job

    a. `cd scripts`
    b. `./load-all.sh /PATH/TO/ALL-PATIENTS.csv /PATH/TO/run.props`
