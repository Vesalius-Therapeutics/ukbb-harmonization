'''
Created on Jan 28, 2021

Extract mother/father disease history from raw ukbb CSV and store in db table

@author: painter
'''

import csv

if __name__ == '__main__':
    
    path = "/home/painter/workspace/ukbb/data/"
    infile = path + "101.csv"
    infile = path + "ukb40707-first-100.csv"
    
    outfile = "/home/painter/load_patient_family_history.sql"
    output = open(outfile, 'w')

    # fam hx disease codes found here: https://biobank.ndph.ox.ac.uk/showcase/coding.cgi?id=1010
    diseases = {}
    diseases[1] = "Heart disease"
    diseases[2] = "Stroke"
    diseases[3] = "Lung cancer"
    diseases[4] = "Bowel cancer"
    diseases[5] = "Breast cancer"
    diseases[6] = "Chronic bronchitis/emphysema"
    diseases[8] = "High blood pressure"
    diseases[9] = "Diabetes"
    diseases[10] = "Alzheimer's disease/dementia"
    diseases[11] = "Parkinson's disease"
    diseases[12] = "Severe depression"
    diseases[13] = "Prostate cancer"

    header = True
    line = 0
        
    with open(infile,'r') as file:
        csvreader = csv.reader(file, delimiter=',')
        for fields in csvreader:
            
            if header == True:
                header = False
            else:
                
                # Progress indicator
                line = line + 1
                if (line % 100000 == 0 ):
                    print(" >> " + str(line) )
                
                
                # find mother/father PD fields        
                patno = fields[0]
    
                # Extract mother and father diseases
                father_diseases = {}
                mother_diseases = {}
    
                # Test for father                
                startid = 8625
                endid = 8664
                 
                idx = startid
                while idx <= endid:
                    if len(fields[idx]) > 0:
                        val = int(fields[idx])
                        if val in diseases.keys():
                            father_diseases[val] = True
                            # Create table entry
                            sql = "INSERT INTO serq_family_history_dx ( eid, parent, disease_id ) VALUES ("
                            sql = sql + patno + ", 'F', " + str(val)
                            sql = sql + ");\n"
                            output.write(sql)
                            print(sql)
                            
                            
                    idx = idx + 1
                                
                # Test for mother                
                startid = 8720
                endid = 8763
                idx = startid
                while idx <= endid:
                    if len(fields[idx]) > 0:
                        val = int(fields[idx])
                        if val in diseases.keys():
                            mother_diseases[val] = True
                            
                            # Create table entry
                            sql = "INSERT INTO serq_family_history_dx ( eid, parent, disease_id ) VALUES ("
                            sql = sql + patno + ", 'M', " + str(val)
                            sql = sql + ");\n"
                            output.write(sql)
                            print(sql)
    
                    idx = idx + 1
                    
                                                      
    output.flush()
    output.close()