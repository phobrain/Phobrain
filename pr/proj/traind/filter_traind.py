#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

import sys

if len(sys.argv) != 3:
    print('Usage: ' + sys.argv[0] + '<file_list> <dir>')
    exit(1)

#pics = set(line.strip() for line in open('vbot28_6kl'))
pics = set(line.strip() for line in open(sys.argv[1]))

print('Pics: ' + str(len(pics)) + ' from ' + sys.argv[1])

indir = sys.argv[2]
outdir = indir + '_sel_' + str(len(pics))

print('In dir: ' + indir + ' output: ' + outdir)

from os import listdir, mkdir
from os.path import isfile, isdir, join
from shutil import rmtree, copy

if isfile(outdir) or isdir(outdir):
    print('exists: ' + outdir)
    print('removing in 5 sec')
    import time
    time.sleep(5)
    rmtree(outdir)

mkdir(outdir)

onlyfiles = [f for f in listdir(indir) if isfile(join(indir, f))]

onlyfiles.sort()

for f in onlyfiles:
    infile = join(indir, f)
    outfile = join(outdir, f)
    if f.startswith('test'):
        print('copying   ' + infile + '  to ' + outfile)
        copy(infile, outfile)
        continue
    print('filtering ' + infile + ' to ' + outfile)

    inr = open(infile, "r")
    outw = open(outfile, "w")

    while True:
        line = inr.readline()
        if not line:
            break
        #print('l ' + line)
        #exit(1)
        fields = line.split(' ')
        #print('fields ' + str(len(fields)))
        if len(fields) != 2:
            print('Error: fields != 2: ' + line)
            exit(1)
        
        fields[1] = fields[1].rstrip('\n')
        #print('fields: ' + fields[0] + ', ' + fields[1])

        if fields[0] not in pics:
            #print('0 not there [' + fields[0] + ']')
            #exit(1)
            continue
        if fields[1] not in pics:
            #print('1 not there [' + fields[1] + ']')
            #exit(1)
            continue
        #print('write ' + line)
        outw.write(line)
        #exit(1)
    outw.close()
