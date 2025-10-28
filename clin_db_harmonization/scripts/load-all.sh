#!/bin/bash
set -x
#
# Load the full patient CSV file into the database and its schema
#
# Usage: load-all.sh /path/to/full.csv /path/to/conf.props

INPUT_FILE=$(readlink -f $1)
CONF=$(readlink -f $2)

BASEDIR=$(readlink -f `dirname $0`)

echo $INPUT_FILE
echo $CONF
echo $BASEDIR

COMMON=$BASEDIR/common.sh
. $COMMON

# load config
. $CONF

# make temporary directory
TMPD=$(mkdir -p $TEMP_DIR)
echo ${TMPD}
#TMPD=$(mktemp -d $TEMP_DIR)   # removing -p for macos
TMPD=$(readlink -f $TEMP_DIR)
echo Temporary directory: $TMPD

cd $TMPD

# Get stub files
head -1 $INPUT_FILE > $TMPD/header.csv
head -2 $INPUT_FILE > $TMPD/one-patient.csv

# load schema
$BASEDIR/load-schema.sh $TMPD/one-patient.csv $CONF

# split main file into 500-line subsets (with headers)
echo "Splitting $INPUT_FILE ..."
split_count=1000
tail -n +2 $INPUT_FILE | split -l ${split_count} -a 4 -d --verbose \
      --filter='cat '$TMPD/header.csv' - > $FILE' \
      - $TMPD/split-patients.

# parallelize load-one.sh for each sub file
find $TMPD -name 'split-patients.*' \
    | parallel -P 12 --delay 15 --joblog joblog.txt --progress --eta \
               --halt soon,fail=1 --results jobout \
               $BASEDIR/load-one.sh '{}' $CONF

# TODO: also try adding parallel options --memfree 5G --retries 3 and
# increasing -P to 12
