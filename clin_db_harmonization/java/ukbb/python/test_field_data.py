'''
Created on May 29, 2020

@author: painter
'''

import csv

if __name__ == '__main__':
    
    path = "/home/painter/workspace/ukbb/src/main/data/"
    infile = path + "101.csv"
    infile = path + "ukb40707-random-100.csv"
    #infile = path + "ukb40707-random-100.csv"
    #infile = path + "ukb40707-random-10.csv"
    reader = csv.reader( open(infile, 'r') , delimiter = "," )
    for fields in reader:
        
        patno = fields[0]
        cancer = []
        startid = 103
        endid = 126
        
        has_data = False
        idx = startid
        while idx < endid:
            
            val = fields[idx]
            if len(val) > 0:
                has_data = True
            cancer.append( fields[idx] )
            idx = idx + 1
            
        #if has_data:
        #    print( "Patient: " + patno + " Cancer: " + str(cancer))
            
            
        operations = []
        # multi-choice Code: 6138
        startid = 11747
        endid = 11751
        
        has_data = True
        idx = startid
        while idx < endid:
            
            val = fields[idx]
            if len(val) > 0:
                has_data = True
            operations.append( fields[idx] )
            idx = idx + 1
            
        if has_data == True:
            print( "Patient: " + patno + " Field results: [" + str(len(operations)) +"] " + str(operations))
                        