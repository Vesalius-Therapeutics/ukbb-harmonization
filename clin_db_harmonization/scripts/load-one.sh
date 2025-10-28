#!/bin/bash
#
# Load a single patient CSV file into the database
#
# Usage: load-one.sh /path/to/small.csv /path/to/conf.props
set -x

echo -e "load-one RUN\n\n\n\n"

INPUT_SUBSET=$1
CONF=$2

BASEDIR=$(readlink -f `dirname $0`)
COMMON=$BASEDIR/common.sh
. $COMMON

# load config
. $CONF

# make temporary directory
# TMPD=$(mktemp -d -p $TEMP_DIR)
TMPD=$(readlink -f $TEMP_DIR)
echo Temporary directory: $TMPD

# prep java ukbb conf
SUB_BASE=$(basename $INPUT_SUBSET)
echo ${SUB_BASE}
OUTPUT_DIR=$OUTPUT_DIR/${SUB_BASE}
mkdir -p $OUTPUT_DIR
echo ${OUTPUT_DIR}

cd ${OUTPUT_DIR}
cat $CONF - <<EOF > data.props
PATIENT_IN_FILE=$INPUT_SUBSET
AWS_S3_PATH=$SUB_BASE
EOF

# Update data.props file with new Output Dir
sed -i "/OUTPUT_DIR/c\OUTPUT_DIR=$OUTPUT_DIR" data.props

java -jar $UKBB_JAR -c data.props

if [[ "$CSV_OUTPUT" -eq "true" ]]; then
    # s3 sync data up to the bucket
    if [[ $COMPRESS_OUTPUT = "true" ]]; then
        aws s3 sync $OUTPUT_DIR s3://$AWS_S3_BUCKET/${SUB_BASE}/ --content-encoding gzip
    else
        aws s3 sync $OUTPUT_DIR s3://$AWS_S3_BUCKET/${SUB_BASE}/
    fi

    #echo A brief pause...
    #sleep 30
    
    # run postgresql code
    sem --fg \
        $BASEDIR/load-sql.sh $OUTPUT_DIR/ukbb_copy_csv_to_tables.sql \
        data.props 20

else
    # run postgresql code
    $BASEDIR/load-sql.sh $OUTPUT_DIR/$PATIENT_OUT_FILE data.props
    
fi

echo `date` Complete

# clean up
#cd / && rm -rf $TMPD
