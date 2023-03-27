#!/bin/bash
#
# Execute a SQL file on the database
#
# Usage: load-sql.sh /path/to/input.sql /path/to/conf.props [wait-time]
#
# * [wait-time] is optional, if specified, the input file must be at
#   least wait-time seconds old before it is executed. The script will
#   wait for that condition to be true before proceeding.
set -x 

echo -e "load-sql RUN\n\n\n\n"

INPUT_SQL=$1
CONF=$2
WAIT_TIME=$3

BASEDIR=$(readlink -f `dirname $0`)
COMMON=$BASEDIR/common.sh
. $COMMON

# load config
. $CONF

# if the file is less than 20 seconds old, wait
if [[ -n $WAIT_TIME ]]; then
    AGE_SEC=$(($(date +%s) - $(date +%s -r $INPUT_SQL)))
    if [[ $AGE_SEC -lt $WAIT_TIME ]]; then
        SLEEP_TIME=$(($WAIT_TIME - $AGE_SEC))
        echo `date` "Waiting $SLEEP_TIME sec ..."
        sleep $SLEEP_TIME
    fi
fi

# run sql
set -o pipefail # exit if aws-psql has an error

# Removing tables from ukbb_copy_csv_to_tables.sql where csv is empty
array=($(find ${OUTPUT_DIR} -name "*.csv"))
for i in "${array[@]}"
do
    row_count=`cat ${i} | wc -l`
    if [[ "$row_count" -eq "1" ]];   
    then
        file_name=`basename $i | sed "s/.csv//g"`
        echo "Removing insert statement for ${file_name} from ${OUTPUT_DIR}/ukbb_copy_csv_to_tables.sql as no records are present in csv."
        sed -i "/$file_name/d" ${OUTPUT_DIR}/ukbb_copy_csv_to_tables.sql
    fi
done

# Failed , need to check later
# sed -i "/UKBB_PATIENT/d" ${OUTPUT_DIR}/ukbb_copy_csv_to_tables.sql
# sed -i "/PHYSICAL_MEASURES_HEARING_TEST/d" ${OUTPUT_DIR}/ukbb_copy_csv_to_tables.sql

( aws-psql --single-transaction -f $INPUT_SQL || (
      echo Failed once, waiting 5 sec and retrying 1>&2
      sleep 5
      aws-psql --single-transaction -f $INPUT_SQL ) ) 2> sql-err.$$.txt \
    | pv-sql $INPUT_SQL > sql-log.$$.txt
