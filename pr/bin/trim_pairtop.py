#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === trim_pairtop.py - reduce the base count of top pics per pic.

import sys

if len(sys.argv) != 3:
    print('usage: ' + sys.argv[0] + ' <trim_sz> <in_file>')
    quit()

N  = int(sys.argv[1])
IN = sys.argv[2]

N_IN = 0
ID0 = None
N_ID = 0
COUNT_ID = 0

COUNT_OUT = 0
TOTAL_OUT = 0

with open(IN) as f:
    for line in f:

        N_IN += 1

        fields = line.split(' ')
        if len(fields) != 3:
            print(IN + ': bad line at ' + str(N_IN) + ': ' + line)
            quit()

        if ID0 == None:
            ID0 = fields[0]
            COUNT_ID += 1
        elif N_ID == 0:
            if ID0 != fields[0]:
                N_ID = N_IN - 1
                print('Input N: '+ str(N_ID), file=sys.stderr)
            else:
                COUNT_ID += 1
        else:
            COUNT_ID += 1

        if COUNT_OUT < N:
            sys.stdout.write(line)
            COUNT_OUT += 1
            TOTAL_OUT += 1

        if COUNT_ID == N_ID:
            #print('reset ct n ' + str(N_ID))
            COUNT_ID = 0
            COUNT_OUT = 0

print('N_IN ' + str(N_IN) + ' ->OUT: ' + str(TOTAL_OUT) + '\n', file=sys.stderr)
