# Title : run_parquet_exports.py
# Author : Sarthak Vilas Patel
# Description :
# This script gets the S3 bucket and RDS connection details as inputs and iterates over each table from RDS and
# creates a corresponding parquet file at s3://S#_BUCKET_INPUT/rds-parquet-exports
# You can now run a crawler to create Athena tables on top of it.

import pandas as pd
import psycopg2
import sys
from sqlalchemy import create_engine
import logging
import time

"""
MAIN PROGRAM START
"""
if __name__ == "__main__":
    try:
        # Initialise Logger
        format_string = "%(asctime)s %(name)s [%(levelname)s] %(message)s"
        logger = logging.getLogger('run-pq-query')
        handler = logging.StreamHandler()
        logger.setLevel(logging.DEBUG)
        formatter = logging.Formatter(format_string)
        handler.setFormatter(formatter)
        logger.addHandler(handler)

        s3_bucket_name = sys.argv[1]
        s3_path = "s3://%s/rds-parquet-exports" % s3_bucket_name
        pg_host_name = sys.argv[2]
        pg_port = "5432"
        pg_db_name = "ukbb"
        pg_user_name = sys.argv[3]
        pg_password = sys.argv[4]

        # Create a postgres connect using sqlalchemy
        # url = f"postgresql+psycopg2://{pg_user_name}:{pg_password}@{pg_host_name}:{pg_port}/{pg_db_name}"
        url = "postgresql+psycopg2://%s:%s@%s:%s/%s" % (pg_user_name, pg_password, pg_host_name, pg_port, pg_db_name)
        pg_conn = create_engine(url)
        # Connection using psycopg2
        # pg_conn = psycopg2.connect(host=pg_host_name, dbname=pg_db_name, user=pg_user_name, password=pg_password)

        get_table_names_query = "SELECT table_schema, TABLE_NAME FROM information_schema.tables  where table_schema = 'public'"

        # Read data from PostgreSQL database table and load into a DataFrame instance
        df = pd.read_sql(get_table_names_query, pg_conn)
        logger.info(df)
        
        for record in df.to_dict(orient="records"):
            logger.info("Starting : %s " % record)
            start = time.process_time()
            tmp_query = "SELECT * FROM %s" % record['table_name']
            tmp_df = pd.read_sql(tmp_query, pg_conn)
            logger.info("Record Count : %s " % len(tmp_df))
            tmp_df.to_parquet("%s/%s/%s.parquet" % (s3_path, record['table_name'], record['table_name']), index=False)
            logger.info("Time taken : %s" % (time.process_time() - start))


        logger.info("The extraction has completed.")
    except:
        logger.error("the process has failed.", exc_info=True)

