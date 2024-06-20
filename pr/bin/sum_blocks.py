#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later

import sys
import os
import numpy as np

if len(sys.argv) < 3:
    print('Usage: ' + sys.argv[0] + ' <dimension> <vec_dir> [archive_num]')
    exit(1)

dimension = int(sys.argv[1])
vec_dir = sys.argv[2]

prefix = None

if len(sys.argv) > 3:
    prefix = str(int(sys.argv[3])) + ':'

print('---- in ' + os.getcwd())
print('-- vec_dir ' + vec_dir)
print('-- dimension ' + str(dimension))

if prefix is None:
    print('-- doing all archives')
else:
    print('-- doing archive ' + prefix)

count = 0

for vecfile in os.listdir(vec_dir):

    if prefix is not None:
        if not vecfile.startswith(prefix):
            continue

    if vecfile.endswith('.txt'):
        continue
    if vecfile.endswith('_bs.npy'):
        # leftover; delete TODO
        continue
    if vecfile.endswith('.hist_bin'):
        # will overwrite tho
        continue

    fname = vec_dir + '/' + vecfile
    fout = fname + '.hist_bin' # not really a hist, but bin

    array = np.load(fname)

    if len(array.shape) != 4:
        print('Unexpected shape: ' + str(array.shape) + ' ' + fname)
        exit(1)

    #print(str(array.shape))
    #quit()
    #for i in range(len(array.shape)):
    #    print(' ' + str(i) + ' ' + str(array.shape[i]))
    #print(str(array.shape))
    #print(str(array.shape[3]))

    if array.shape[1] != array.shape[2]:
        print('Expected shape = 1, N, N, L')
        print(str(array.shape))
        exit(1)

    n = array.shape[2]
    l = array.shape[3]

    if l != dimension:
        print('Unexpected shape ' + str(array.shape) + ' fname ' + fname)
        exit(1)

    acc = np.zeros(dimension, dtype='float64')

    for i in range(n):
        for j in range(n):
            acc += array[0][i][j]

    acc /= (n * n)

    acc = np.float32(acc)

    acc.byteswap().tofile(fout)

    count += 1

    if count % 500 == 0:
        print(vecfile + '         ', end='\r')
        sys.stdout.flush()

    '''
    if count % 777 == 0:
        print('.', end='')
        if count % (777 * 70) == 0:
            print('!')
    '''

    #if count > 1000:
    #    break

print('Read ' + str(count))
