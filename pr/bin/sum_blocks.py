#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later

# used on linux for a java reader for Phobrain
# set False for recursive [-r] opt.. TODO disentangle

import sys
import os
import numpy as np

def usage(msg):
    if msg is not None:
        print(msg)
    print('Usage: ' + sys.argv[0] + ' <dimension> <vec_dir> [archive_num] [-f <fold_size1>,<fold_size2>,...]')
    print('       ' + sys.argv[0] + ' <dimension> <vec_dir> -r [ -f <fold_size1>,<fold_size2>,...]')
    print('    <dimension> is to verify base vector size,')
    print('        e.g. VGG is 1,7,7,512 so dimension of')
    print('        average block would be 512')
    exit(1)

if len(sys.argv) < 3  or  len(sys.argv) > 5:
    usage('arg count')

dimension = int(sys.argv[1])
vec_dir = sys.argv[2]

prefix = None
recurse = None
fold_sizes = None

def get_fold_sizes(ix):

    global fold_sizes

    if ix >= len(sys.argv):
        usage('missing fold_sizes list')

    size_list = sys.argv[ix]
    fold_sizes = list(map(int, size_list.split(',')))
    fold_sizes = sorted(fold_sizes, reverse=True)


    #check
    for size in fold_sizes:

        if dimension % size != 0:

            print('Error: fold_sizes must be factors of dimension')
            exit(1)

next_ix = 3

if len(sys.argv) >= next_ix + 1:

    # handle arg3

    if sys.argv[next_ix] == '-r':

        recurse = True

    elif sys.argv[next_ix] == '-f':

        next_ix += 1
        get_fold_sizes(next_ix) 

    elif isinstance(sys.argv[next_ix], int):

        # archive_num
        prefix = str(int(sys.argv[next_ix])) + ':'

    else:
        usage('Expected <archive> or -r or -f <list>')

    next_ix += 1

if prefix is None and fold_sizes is None and len(sys.argv) >= next_ix:
    if sys.argv[next_ix] == '-f':
        get_fold_sizes(next_ix+1)
    else:
        usage('expected -f, got ' + sys.argv[next_ix])


print('---- in ' + os.getcwd())
print('-- vec_dir ' + vec_dir)
print('-- dimension ' + str(dimension))

if recurse:
    print('-- doing all in tree  ' + vec_dir)
    print()
elif prefix is None:
    print('-- doing all archives')
else:
    print('-- doing archive ' + prefix)

count = 0

KEEP = True

def fold(avg, sz):

    l = len(avg)
    if l % sz != 0:
        usage('2nd size check shuddnt fffail')

    n = int(l / sz)

    acc = np.zeros(sz, dtype='float64')

    for i in range(n-1):

        #print('-- ' + str(i))
        #quit()

        start = i * sz
        end = start + sz

        #quit()
        if end >= len(avg):
            usage('=== OVERFLOW dim ' + \
                        '\nch ' + str(i) + ',' + str(n) + ' - ' + str(start) + ':' + str(end) + ' l ' + str(len(avg)))

        acc += avg[start:end]

    acc /= n
    return acc

def do_dir(this_dir):

    global count

    #print('do_dir: ' + this_dir)

    this_count = 0

    for f in os.listdir(this_dir):

        f = this_dir + '/' + f

        if os.path.isdir(f):
            continue

        vecfile = f  # TODO exclude other types of file

        if prefix is not None:
            if not vecfile.startswith(prefix):
                continue

        if vecfile.endswith('.txt') or 'hist' in vecfile:
            # legacy/check
            continue

        if '_avg_vec' in vecfile:
            # includes folds; overwriting all for now
            continue

        print('\r' + vecfile + '         ', end='')
        sys.stdout.flush()

        acc = None

        # this is skipped just above, for now
        if vecfile.endswith('avg_vec'):

            if fold_sizes is not None:

                # need it - read previous avg file from this method

                acc = np.load(vecfile, allow_pickle=True)
                if len(acc.shape) != 1:
                    print('Unexpected avg vector shape: ' + str(acc.shape) + ' ' + fname)
                    exit(1)
                if acc.shape[0] != dimension:
                    print('avg vector length != dimension: ' + str(acc.shape[0]) + ' ' + fname)
                    exit(1)

            elif KEEP:
                continue


        if acc is None:

            # load 4D array of internet vectors, create fresh avg, (over)write

            fname = vecfile
            fout = fname + '.avg_vec'

            array = np.load(fname, allow_pickle=True)

            if len(array.shape) != 4:
                print('Unexpected imagenet model vector shape: ' + str(array.shape) + ' ' + fname)
                exit(1)

            #print(str(array.shape))
            #quit()
            #for i in range(len(array.shape)):
            #    print(' ' + str(i) + ' ' + str(array.shape[i]))
            #print(str(array.shape))
            #print(str(array.shape[3]))

            if array.shape[0] != 1  or  array.shape[1] != array.shape[2]:
                print('Expected shape = 1, N, N, L')
                print(str(array.shape))
                exit(1)

            if array.shape[3] != dimension:
                print('Expected shape[3] == dimension arg: ' + str(array.shape) + ' fname ' + fname)
                exit(1)

            n = array.shape[2]
            l = array.shape[3]

            acc = np.zeros(dimension, dtype='float64')

            # accumulate/sum all the base blocks and average.

            for i in range(n):
                for j in range(n):
                    acc += array[0][i][j]

            acc /= (n * n)

            # write in float32

            f32_acc = np.float32(acc)

            #print('tofile ' + fout)

            f32_acc.tofile(fout)
            f32_acc.byteswap().tofile(fout + '_bs')

        # we have acc

        this_count += 1

        if fold_sizes is not None:

            for sz in fold_sizes:

                fout = fname + '.' + str(sz) + '_avg_vec'

                #if KEEP and os.path.isfile(fout):
                #    continue

                fx = fold(acc, sz)
                fx = np.float32(fx)

                fx.tofile(fout)
                #fx.byteswap().tofile(fout + '_bs')


        #if this_count % 500 == 0:
        #    print('\r' + vecfile + '         ', end='')
        #    sys.stdout.flush()

        #if this_count > 1000:
        #    break

    if this_count > 0:
        count += this_count
        print(this_dir + '   ' + str(this_count) + '/' + str(count))

if not recurse:

    do_dir(vec_dir)

else:

    for subdir, dirs, files in os.walk(vec_dir):
        do_dir(subdir)

print('\n\nTotal written: ' + str(count))
