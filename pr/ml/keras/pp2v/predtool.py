#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === predtool.py - operations on .pairs_bin final predictions.

# add, find top, format-ascii
#   for numpy arrays of nums for pairs, .pairs_bin

import os
import sys
import socket
import time
#from datetime import datetime, timedelta
import numpy as np
import glob

NDARRAY_SUFFIX = '.pairs_bin'

def usage():
    print('Usage: -<v|h> -<avg|avgbin|per|top N M> -o <outdir|same>  <.pairs_bin files...>')
    print('              (-top is per-file, min N per pic/side, M per pic/side/archive)')
    exit(1)

if len(sys.argv) < 6:
    print('args < 6: ' + str(sys.argv))
    usage()

## arg 1

if sys.argv[1] == '-v':
    ORIENT = 'v'
elif sys.argv[1] == '-h':
    ORIENT = 'h'
else:
    print('Expected -v|-h: ' + argv[1])
    usage()

## arg 2

TOP = False
TOP_N = 50
TOP_M = 5  # min per archive

AVG = False
BIN_OUT = False

nextarg = 3

if sys.argv[2] == '-avg':
    AVG = True
elif sys.argv[2] == '-avgbin':
    AVG = True
    BIN_OUT = True
elif sys.argv[2] == '-per':
    AVG = False
elif sys.argv[2] == '-top':
    AVG = False
    TOP = True
    TOP_N = int(sys.argv[3])
    TOP_M = int(sys.argv[4])
    nextarg = 5
else:
    print('Expected -avg|-per|-top n: ' + argv[1])
    usage()

## arg 

if sys.argv[nextarg] != '-o':
    print('== Expected -o <outdir>')
    usage()

nextarg = nextarg + 1

OUT_DIR = sys.argv[nextarg]
if OUT_DIR == 'same':
    if not TOP:
        print('== -o same  is only for .top for now')
        exit(1)
elif not os.path.isdir(OUT_DIR):
    print('== -o: need same w/ -top or a dir: -o <outdir>: ' + OUT_DIR)
    usage()

nextarg = nextarg + 1

IN_FILES = []

for i in range(nextarg, len(sys.argv)):
    path = sys.argv[i]

    #print('-- path ' + path)

    if os.path.isdir(path):

        print('== ' + sys.argv[0] + ' - got a dir: ' + path)

        dirlist = glob.glob(path + '/*' + NDARRAY_SUFFIX)
        if len(dirlist) == 0:
            print(' No ' + NDARRAY_SUFFIX + ' files in ' + path)
        else:
            for f in dirlist:
                IN_FILES.append(f)

    elif sys.argv[i].endswith(NDARRAY_SUFFIX):
        IN_FILES.append(sys.argv[i])
    else:
        print('Not a dir or ' + NDARRAY_SUFFIX + ' file: ' + sys.argv[i])
        usage()

if len(IN_FILES) == 0:
    print('== ' + sys.argv[0] + ' - No files with suffix ' + NDARRAY_SUFFIX)
    usage()

# -------------------------- basics

PHOBRAIN_LOCAL = os.environ.get('PHOBRAIN_LOCAL')

if PHOBRAIN_LOCAL is None:
    PHOBRAIN_LOCAL = '~/phobrain_local'
    print('(No env PHOBRAIN_LOCAL: using ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

real_img = PHOBRAIN_LOCAL + '/real_img_orient'

HOST = socket.gethostname()
print('== Config for host: ' + HOST + ' Using ' + real_img)

if HOST == 'phobrain-gpu1':
    BASE = '/mnt/ssd/reep/home'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
else:
    BASE = '~'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'

BASE = os.path.expanduser(BASE) + '/'

print('== BASE is ' + BASE)

# ------------------------\

fnames = []
ids = []
#id_map = {}
n_pics = 0

def procId(fname):
    archive, f = fname.split('/')
    tag = f.replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')
    #print('xx ' + archive + ':' + str(seq))
    return archive + ':' + tag

def load_pic_list():

    global fnames, ids, n_pics

    print('Read pic list/' + ORIENT + ': ' + real_img)

    OR_V = 't'
    if ORIENT == 'h':
        OR_V = 'f'

    lines = 0
    with open(real_img) as fp:
        for line in fp:
            lines += 1
            if line[0] == '#':
                continue
            #print('DO: '+ line)
            fname, torient = line.split()
            #torient = torient.strip()
            '''
            if fname =='1/img03a.jpg':
                print('F: ' + line + ' [' + torient + ']' + \
                      str(torient == 'false') + ' ' + str(torient == 'f'))
            '''
            if torient == OR_V:
                fnames += [ fname ]
                ids += [ procId(fname) ]
                
                #if fname =='1/img03a.jpg':
                #   print('mapped ' + fname)

    n_pics = len(fnames)
    print('=== Pics: ' + str(n_pics))
    if n_pics == 0:
        print('NO PICS, ORIENT=' + ORIENT)
        exit(1)

'''
with open(sys.argv[1]) as fp:
    ids = fp.readlines()
ids = [x.strip() for x in ids]

print('ids: ' + str(len(ids)) + ' from ' + sys.argv[1])

'''


def writeFile(outfile, pred):

    path = OUT_DIR + '/' + outfile

    if os.path.isfile(path):
        print('??? already written ' + path)
        return

    end = len(ids)
    print('Writing: ' + path + ' using N ' + str(len(pred)) + \
                        ' pics ' + str(end) + ' pics sqd ' + str(end * end))

    #start = datetime.now()

    if outfile.endswith('_bin'):
        with open(path, 'wb') as fp:
            np.save(fp, pred[0:end*end])
        return

    ix_ct = 0
    ct = 0
    eq_sum = 0.0
    i_index = 0

    try:
        with open(path, 'w') as out:
            for i in range(end):

                i_index = i * end

                #if i == 1:
                #    print('===== i_index i==1 ' + str(i_index) + \
                #                    ' ids ' + str(len(ids)))

                id1 = ids[i]

                #if i == 1:
                #    print('===== id1 ' + id1)

                eq_sum += pred[i_index + i]

                # these need to be in canonical order, for parallel ops
                # also floating point, since they'll be added
                for j in range(i+1, end):

                    # i->j

                    id2 = ids[j]
                    val1 = pred[i_index + j][0]

                    # j->i
                        
                    j_index = j * end

                    val2 = pred[j_index + i][0]

                    out.write(id1 + ' ' + id2 + ' ' + str(val1)
                                                  + ' ' + str(val2) + '\n')

        print('Written: ' + outfile)
    except Exception as inst:
        print('exception writing ' + path + ': ' + \
                                str(type(inst)) + ' ' + str(inst.args))
        print('i_index ' + str(i_index))
        exit(1)

    #endt = datetime.now()
    print('\nWritten: ' + path)
            #  + '  in ' + str(timedelta(start, endt)))

    avg_aa = eq_sum / end
    avg_all = np.mean(pred)
    if avg_all == 0.0:
        print('--== WROTE 0s')
    else:
        print('--- ' + path + ' Avg of AA pairs, all pairs, %: ' + \
                str(avg_aa) + ' ' + \
                str(avg_all) + ' ' + \
                str((100 * avg_aa)/avg_all))

def writeTopFile(path, pred):

    #path = OUT_DIR + '/' + outfile

    if os.path.isfile(path):
        print('??? already written ' + path)
        return

    end = len(ids)
    print('Writing: ' + path + ' using N ' + str(len(pred)) + \
                        ' pics ' + str(end) + ' pairs_per_pic ' + str(TOP_N) + \
                        ' per pic/arch ' + str(TOP_M))

    #start = datetime.now()
    ct = 0
    eq_sum = 0.0
    try:
        with open(path, 'w') as out:
            for i in range(end):

                i_index = i * end

                id1 = ids[i]

                eq_sum += pred[i_index + i]

                # get TOP_N best A's and TOP_N B's; 
                # pairtop wrap will sort -u for overlap

                # i -> j (A's)

                # sort

                picDict_r = {}  # for fname1 on left: i, j
                picDict_l = {}  # for fname1 on right: j, i

                for j in range(end):

                    if i == j:
                        continue

                    id2 = ids[j]

                    picDict_r[id2] = pred[i_index + j][0]

                    #print('xxx ' + id1 + ' ' + id2 + ' ' + str(val))

                for j in range(end):
                    if i == j:
                        continue

                    other_id = ids[j]
                    other_index = j * end

                    picDict_l[other_id] = pred[other_index + i][0]

                # print in sorted order, [id1 X, val] then [X id1 val]

                #?get_the_others = False

                ix = id1.index(':') + 1

                arch_prefix = id1[:ix]
                #print('-ARCH [' + arch_prefix + ']')
                #quit()

                pic_ct = 0
                arch_ct = 0

                for item in sorted(picDict_r.items(), key=lambda x: x[1]):
                    pic_ct += 1
                    idX = item[0]
                    in_arch = idX.startswith(arch_prefix)
                    if in_arch:
                        arch_ct += 1
                    if pic_ct > TOP_N:
                        if arch_ct > TOP_M:
                            break
                        if not in_arch:
                            continue
                    out.write(id1 + ' ' + item[0] + ' ' + 
                                    str(item[1]) + '\n')
                                    #str(int(item[1] * MAG)) + '\n')
                    ct += 1
            
                pic_ct = 0
                arch_ct = 0
                for item in sorted(picDict_l.items(), key=lambda x: x[1]):
                    pic_ct += 1
                    idX = item[0]
                    in_arch = idX.startswith(arch_prefix)
                    if in_arch:
                        arch_ct += 1
                    if pic_ct > TOP_N:
                        if arch_ct > TOP_M:
                            break
                        if not in_arch:
                            continue
                    out.write(item[0]+ ' ' + id1 + ' ' + 
                                    str(item[1]) + '\n')
                                    #str(int(item[1] * MAG)) + '\n')
                    ct += 1

        print('Written: ' + str(ct) + ' lines to ' + path)

    except Exception as inst:

        print('exception writing ' + str(type(inst)) + ' ' + str(inst.args))
        quit()

    #endt = datetime.now()
    print('\nWritten: ' + path + ' (' + str(ct) + ')')
                #  in ' + str(timedelta(start, endt)))
    avg_aa = eq_sum / end
    avg_all = np.mean(pred)
    print('--- ' + path + ' Avg of AA pairs, all pairs, %: ' + \
                str(avg_aa) + ' ' + str(avg_all) + ' ' + \
                str((100 * avg_aa)/avg_all))

def removesuffix(self: str, suffix: str, /) -> str:

    # suffix='' should not call self[:-0].
    if suffix and self.endswith(suffix):
        return self[:-len(suffix)]
    else:
        return self[:]

load_pic_list()

ids_sq = len(ids) * len(ids)

if AVG:
    #start_add = datetime.now()
    predsum = np.zeros((ids_sq,1))

skipped = []

skipped_fsizes = set()

correct_fsize = -1

ok_files = 0

for i in range(len(IN_FILES)):

    size = os.path.getsize(IN_FILES[i])

    if correct_fsize > -1  and  size != correct_fsize:
        print('=== SKIP/fsize ' + IN_FILES[i] + ' is ' + str(size) + \
                                                ' not ' + str(correct_fsize))
        skipped_fsizes.add(size)
        skipped.append(IN_FILES[i])
        continue

    if size in skipped_fsizes:

        skipped.append(IN_FILES[i])

        if correct_fsize > 0:
            print('=== SKIP/fsize ' + IN_FILES[i] + \
                                    ' is ' + str(size) + \
                                    ' not ' + str(correct_fsize))
        else:
            print('=== SKIP/fsize ' + IN_FILES[i] + ' is ' + str(size))
            
        continue

    try:
        pred = np.load(IN_FILES[i])
    except OSError as err:
        print('=== SKIP/OSError: ' + IN_FILES[i] + ' {0}'.format(err))
        skipped.append(IN_FILES[i])
        continue
    except:
        print('=== SKIP: Problem file: ' + IN_FILES[i])
        skipped.append(IN_FILES[i])
        continue

    if len(pred) != ids_sq:

        skipped_fsizes.add(size)

        print('=== SKIP: Pred ' + IN_FILES[i] + ': got ' + str(len(pred)) + \
                                        ' expected ' + str(ids_sq))
        skipped.append(IN_FILES[i])
        del pred
        continue

    if correct_fsize == -1:
        correct_fsize = size

    #print('SH ' + str(predsum.shape) + ' ' + str(pred.shape))

    if TOP:
        ok_files = ok_files + 1

        print('--- ' + IN_FILES[i] + ' pred: ' + \
                str(np.mean(pred)) + ' / ' + str(np.std(pred)))

        # put files adjacent
        outpath = removesuffix(IN_FILES[i], '.pairs_bin') + '.top'
        writeTopFile(outpath, pred)

        del pred
        continue

    if AVG:

        mean = np.mean(pred)

        print('--- ' + IN_FILES[i] + ' avg/stdev: ' + \
                str(mean) + ' / ' + str(np.std(pred)))

        #print('== cumulative: ' + str(timedelta(start_add, datetime.now())))
        if mean > 90.0:
            print('--- avg: skipping for mean > 90.0: ' + IN_FILES[i])
            skipped.append(IN_FILES[i])

            del pred
            continue

        ok_files = ok_files + 1
        predsum = np.add(predsum, pred)

        del pred
        continue

    #quit()

    #print('read len ' + str(len(pred)))
    #print('dim ' + str(pred.ndim))
    #print('shape ' + str(pred.shape))

    ok_files = ok_files + 1

    # in is .pairs_bin
    outfile = removesuffix(os.path.basename(IN_FILES[i]), '_bin')

    writeFile(outfile, pred)

    del pred

if AVG:

    if ok_files == 0:
        print('=== No ok files out of ' + str(len(IN_FILES)))
        exit(1)

    #end_add = datetime.now()

    #print('\n== added in: ' + str(timedelta(start_add, end_add)))
    predsum = np.divide(predsum, ok_files)
    #print('== divided in: ' + str(timedelta(end_add, datetime.now())))


    outfile = ORIENT + '_avg_' + str(ok_files) + '.pairs'
    bin_outfile = outfile + '_bin'

    print('== writing average .pairs_bin, .pairs for ' + str(ok_files) + \
                                    ' ok files out of ' + str(len(IN_FILES)) + \
                                    ' to ' + outfile)

    writeFile(bin_outfile, predsum)
    writeFile(outfile, predsum)

if len(skipped) == 0:
    print('--- all input files ok')
else:
    print('=== SKIPPED: ' + str(len(skipped)))
    print(str(skipped))
    print('sizes skipped: ' + str(skipped_fsizes))

