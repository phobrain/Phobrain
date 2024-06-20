#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#
# === compare_pairtops.py
#
#   pairtop files are made in pp2 and folded by 
#   pr/proj/pairtop into pr.pairtop_nn_[hv] db table dumps.
#

import sys
import os
import glob
from pathlib import Path

VERBOSE = False

N_FILES_A = 0
N_FILES_B = 0
N_IN_A = 0
N_IN_B = 0

# return set, nfile, nlines

def pairtop_set(pairtop, outset):

    if outset is None:
        outset = set()

    nlines = 0

    with open(pairtop) as f:
        for line in f:

            nlines += 1

            fields = line.split(' ')
            if len(fields) != 3:
                print(IN + ': bad line at ' + str(line_n) + ': ' + line)
                quit()

            outset.add(fields[0] + ' ' + fields[1])

    if VERBOSE:
        print('    -- lines: ' + str(nlines) + '  ' + str(pairtop))

    return outset, 1, nlines

# return set, nfiles, nlines

def setify(X):

    if not os.path.exists(X):

        print('-- Error: does not exist: ' + X)
        exit(1)

    if os.path.isfile(X):

        print('    files: 1 ' + str(X))

        return pairtop_set(X, None)

    if os.path.isdir(X):

        nfiles = 0
        nlines = 0

        setX = set()
        for file in Path(X).glob('**/*.top'):
            #if file.endswith('.top'):
            _, _, nl = pairtop_set(file, setX)
            nfiles += 1
            nlines += nl

        print('    files: ' + str(nfiles) + '  in  ' + str(X))

        return setX, nfiles, nlines

    print('-- Error: not file or dir: ' + str(X))
    exit(1)

if len(sys.argv) != 3:
    print('usage: ' + sys.argv[0] + ' <A.pairs> <B.pairs>')
    print('       A,B: both files or dirs/recursive')
    exit(1)

A = sys.argv[1]
B = sys.argv[2]

if not os.path.exists(A):
    print('-- Error: does not exist: ' + A)
    exit(1)
if not os.path.exists(B):
    print('-- Error: does not exist: ' + B)
    exit(1)

setA, nFilesA, nLinesA = setify(A)
setB, nFilesB, nLinesB = setify(B)

if nLinesA == 0 or nLinesB == 0:
    print('-- Error: nothing read from one or both')
    exit(1)

avgN_A = int(nLinesA / nFilesA)
avgN_B = int(nLinesB / nFilesB)

if avgN_A == avgN_B:
    print('    avg lines A==B: ' + str(avgN_B))
    useAvg = avgN_B
else:
    useAvg = int((nLinesA + nLinesB) / (nFilesA + nFilesB))

    print('    avg lines: ' + str(avgN_A) + ' ' + str(avgN_B))
    
setI = setA.intersection(setB)
setU = setA.union(setB)

lenA = len(setA)
lenB = len(setB)
lenI = len(setI)
lenU = len(setU)

print('    sets: ' + str(len(setA)) + ' ' + str(len(setB)) + '\n    ' + \
            'intersection ' + str(len(setI)) + ' union ' + str(len(setU)))

print('    intersection as % of avg file: ' + \
            str(int(100 * lenI / useAvg)) + '%')
print('    intersection/union: ' + \
            str(int(100 * lenI / lenU)) + '%')

if lenI < 20:
    print('overlap: ' + str(lenI))
