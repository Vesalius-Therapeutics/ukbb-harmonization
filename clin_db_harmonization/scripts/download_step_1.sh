#!/bin/bash
# Title : download_step_1.sh
# Author : Sarthak Vilas Patel
# Description :
# This script should be executed as the first step after downloading the key and databasket encrypted file to local EC2 Instance.
# Go through parameters below first to understand and update those accordingly before running.
# Below are some timings to note:
# Downloading the databasket file from UKBB would take approx 2 hrs. This is done manually from local machine.
# Unpacking the file would take approx 8 mins
# Creating a CSV file would take 8 hrs
# Creating data_dictionary.html : 6-7 hrs
# Once all the steps are completed, you are now ready to load the data to RDS.
#==============================================================================
UTILS_DIR=/ukbb-harmonization/clin_db_harmonization/utils
CONF_DIR=/ukbb-harmonization/clin_db_harmonization/java/ukbb/conf

S3_Path="" # This is the path where we want to save all files so we dont have to run this process again.
WorkDir="" # Directory where the key and databasket files are present and will be treated as workdir.
Key="" # The Key file to decrypt after download from UKBB. Might have to rename with ext .ukbkey instead of .key
DataBasketFile="" # The .enc file from UKBB Portal.

check_status_of_command(){
    if [ $1 -ne 0 ]
    then
        echo "The process has failed at $2. Please check and resubmit."
        exit 11;
    else
        echo "Completed : $2 ."
    fi
}

cd ${WorkDir}

# First we unpack/decrypt the file. This will create a file of about 18 GBs
# Example : ./ukbunpack ukb669689.enc k55647r669689.key
${UTILS_DIR}/ukbunpack ${DataBasketFile} ${Key}
check_status_of_command $? "Unpacking of encrypted file is completed `date`"

# Now we convert the unpacked encrypyted file to readable format like csv
# Example : ./ukbconv ukb669689.enc_ukb csv
${UTILS_DIR}/ukbconv ${DataBasketFile}_ukb csv
check_status_of_command $? "Converting file to csv is completed `date`"

# Create the data_dictionary.html file needed for harmonization process.
${UTILS_DIR}/ukbconv ${DataBasketFile}_ukb docs
check_status_of_command $? "Creating data_dictionary.html is completed `date`"
# Copy the files to conf files for harmonzation
html_file=`echo ${DataBasketFile} | sed 's/env/html/g'`
cp ${html_file} data_dictionary.html

# Download the field.txt file from Showcase https://biobank.ctsu.ox.ac.uk/crystal/schema.cgi?id=1
wget -nd -O field.txt "biobank.ctsu.ox.ac.uk/ukb/scdown.cgi?fmt=txt&id=1"

# Usually data coding are all covered and might need small changes if additional codes are added.
# Compare the Showcase Data Codes.csv and the last few lines of data_dictionary.html
cp field.txt data_dictionary.html ${CONF_DIR}/

# Make sure you upload the local files 
aws s3 sync . ${S3_Path}/