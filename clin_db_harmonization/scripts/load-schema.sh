#!/bin/bash
#
# Load the schema generated from the provided CSV file into the database
#
# Usage: load-schema.sh /path/to/subset.csv /path/to/conf.props

echo -e "load-schema RUN\n\n\n\n"

INPUT=$1
CONF=$2

BASEDIR=$(readlink -f `dirname $0`)
COMMON=${BASEDIR}/common.sh
. $COMMON

# load config
. $CONF

# make temporary directory
# TMPD=$(mktemp -d -p $TEMP_DIR)
TMPD=$TEMP_DIR
TMPD=$(readlink -f $TMPD)
echo Temporary directory: $TMPD

# get just one patient
INPUT_SUBSET=$TMPD/one-patient.csv
# head -2 $INPUT > $INPUT_SUBSET # Already one patient record.

# prep java ukbb conf
SUB_BASE=$(basename $INPUT_SUBSET)
cat <(grep -v CREATE_SCHEMA $CONF) - <<EOF > $TMPD/data.props
PATIENT_IN_FILE=$INPUT_SUBSET
AWS_S3_PATH=$SUB_BASE
CREATE_SCHEMA=true
EOF

# run java ukbb tool
cd $TMPD
echo $OUTPUT_DIR
mkdir -p $OUTPUT_DIR
echo $UKBB_JAR
java -jar $UKBB_JAR -c data.props

# run postgresql code
aws-psql -c 'CREATE EXTENSION IF NOT EXISTS aws_s3 CASCADE;' || true

echo "aws-psql -f $OUTPUT_DIR/$SCHEMA_FILE | pv-sql $OUTPUT_DIR/$SCHEMA_FILE > log.txt"

aws-psql -f $OUTPUT_DIR/$SCHEMA_FILE \
    | pv-sql $OUTPUT_DIR/$SCHEMA_FILE > log.txt

aws-psql -f ${BASEDIR}/phy_hearing_test_schema.sql >> log.txt

echo Complete!

# clean up
#cd / && rm -rf $TMPD
