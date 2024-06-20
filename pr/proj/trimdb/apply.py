#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

'''
apply previous pr.trim_picture_dump.sql to a new pair dump made in 
  proj/pairs/ (pr.pairs_[vh]_dump.sql)
  proj/pairtop (pr.pairtop_[col|nn]_[vh]_dump.sql
'''

import os
import sys


if len(sys.argv) != 3:
    print('Usage: [prog] <trimDir> <pair>_dump.sql')
    quit()

TRIM_DIR = os.path.expanduser(sys.argv[1])
PAIR_DUMP = os.path.expanduser(sys.argv[2])

if not os.path.exists(TRIM_DIR):
    print('Not a dir: ' + TRIM_DIR)
    quit()

TRIM_PIC_DUMP = TRIM_DIR + '/pr.trim_picture_dump.sql'

if not os.path.exists(TRIM_PIC_DUMP):
    print('Not a file: ' + TRIM_PIC_DUMP)
    quit()

if not os.path.exists(PAIR_DUMP):
    print('Not a file: ' + PAIR_DUMP)
    quit()

if PAIR_DUMP.startswith('trim_'):
    print("Don't trim a trim: " + PAIR_DUMP)
    quit()

OUTPUT_DUMP = TRIM_DIR + '/trim_' + os.path.basename(PAIR_DUMP)

TBL_NAME = os.path.basename(PAIR_DUMP).replace('_dump.sql', '')
TTBL = 'trim_' + TBL_NAME

print('\nTable: ' + TBL_NAME + ' -> ' + TTBL)

print('Using:\n\t' + TRIM_PIC_DUMP + \
        '\nto trim: ' + PAIR_DUMP + \
        '\n--> ' + OUTPUT_DUMP + '\n')

if os.path.exists(OUTPUT_DUMP):
    print('Output dump exists: ' + OUTPUT_DUMP)
    exit(1)

if not os.path.exists(TRIM_PIC_DUMP):
    print('No file: ' + TRIM_PIC_DUMP)
    exit(1)

print('Reading ' + TRIM_PIC_DUMP)

pics = set()

with open(TRIM_PIC_DUMP) as fp:
    for line in fp:
        fields = line.split('\t')
        if len(fields) < 30:
            continue
        #print(fields[2])
        pics.add(fields[2])

if len(pics) == 0:
    print('No pics found')
    quit()

print('Total pics: '+ str(len(pics)))

print('Creating ' + OUTPUT_DUMP)

out = open(OUTPUT_DUMP, 'w')

lines = 0
records = 0
rejects = 0

print('Reading pair dump: ' + PAIR_DUMP)

#repl1 = ' ' + TBL_NAME
#repl2 = ' ' + TTBL

field0_ix = 0
field1_ix = 1

if 'approved' in PAIR_DUMP:
    field0_ix = 2
    field1_ix = 3

with open(PAIR_DUMP) as fp:
    for line in fp:
        lines += 1
        fields = line.split('\t')

        if len(fields) < 4:
            if TBL_NAME in line:
                line = line.replace(TBL_NAME, TTBL)
                #print(line) 
            out.write(line)
            continue

        records += 1

        if fields[field0_ix] not in pics or fields[field1_ix] not in pics:
            rejects += 1
            continue

        out.write(line)

out.close()

if records == 0:
    print('\nLines: ' + str(lines) + ' (pass-through / no records)')
else:
    print('\nLines: ' + str(lines) + \
        '\nRecords: ' + str(records) +
        '\nTrimmed records: ' + str(rejects) +
        '\n=> trimmed ' + str(int((100 * rejects)/records)) + '%\n' +
        OUTPUT_DUMP)
