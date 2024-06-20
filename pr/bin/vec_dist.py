#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === vec_dist.py - Use vec-vec distances to make a
#                   *symmetrical* .top (like histogram dists).

import sys
import os
import subprocess
import socket
import time
import math
import numpy as np
from multiprocessing import Process

#from scipy.spatial import distance
#from scipy.spatial.distance import cdist

pylib = os.path.dirname(os.path.realpath(__file__)) + '/pylib'
#print('-- pylib ' + pylib)
sys.path.append(pylib)

TOP_N = 50

############################ args

def usage():
    print('Usage: <file.vecs> [func]')
    print('   func in [poinca, cosine]')
    print('   default is both')
    exit(1)

if len(sys.argv) < 2 or len(sys.argv) > 3:
    usage()

VECS = sys.argv[1]

if not VECS.endswith('.vecs'):
    usage()

if not os.path.isfile(VECS):
    print('Not a file: ' + VECS)
    usage()

FUNCS = ['cos', 'poi']

if len(sys.argv) == 3:
    FUNC = sys.argv[2]

    if FUNC == 'cosine':
        FUNCS = ['cos']
    elif FUNC == 'poinca':
        FUNCS = ['poi']
    else:
        print('Unknown function: ' + FUNC)
        usage()

start = VECS.find('_lrv_') + len('_lrv_')
end = VECS.find('_', start)
print('x ' + str(start) + ' ' + str(end))

DIM = int(VECS[start:end])

print('== VECS ' + VECS + '  DIM (lrv_X) ' + str(DIM))

############### pylib this

def run_cmd(cmd):

    proc = subprocess.run([cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    if proc.returncode != 0:
        print("Can't run " + cmd)
        print('It returned: ' + str(proc.returncode))
        print('     stderr:  ' + str(proc.stderr))
        exit(1)

    return str(proc.stdout)

def proc_id(fname):
    archive, name = fname.split('/')
    tag = name.replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')
    #print('xx ' + archive + ':' + str(seq))
    return archive + '/' + tag

portrait = []
landscape = []

def read_pics(real_img):

    global portrait, landscape

    lines = 0

    try:

        with open(real_img, "r", encoding="utf-8") as fpx:
            for linex in fpx:
                lines += 1
                if linex[0] == '#':
                    continue
                #print('DO: '+ line)
                fname, torient = linex.split()
                id = proc_id(fname)
                if torient == 't':
                    portrait.append(id)
                elif torient == 'f':
                    landscape.append(id)
                else:
                    print('Error: unexpected boolean: ' + linex)
                    exit(1)
    except Exception as inst:
        print('Exception: ' + type(inst) + ' reading ' + real_img)
        exit(1)

    print('== portrait(v): ' + str(len(portrait)) + '  landscape(h): ' + str(len(landscape)))

# cosine and poincare-ready vecs
cvecs = {}
pvecs = {}

def read_vecs(vecfile):

    global vecs

    lines = 0

    try:

        with open(vecfile, "r", encoding="utf-8") as fpx:
            for linex in fpx:
                lines += 1
                if linex[0] == '#':
                    continue
                #print('DO: '+ linex)

                vline = linex.split()
                #id = vline[0]
                #side = vline[1]
                id = vline[0].replace(":", "/")

                key = id + '-' + vline[1]

                vec = np.array(vline[2:])
                vec = vec.astype(float)

                if 'cos' in FUNCS:

                    if key in cvecs:
                        print('Error: already loaded: ' + key + ' in ' + linex)
                        exit(1)

                    norm = np.linalg.norm(vec)
                    if np.isnan(norm):
                        print('-- Error: cos: norm is nan for key: ' + \
                                            key + ' vec: ' + str(vec) + \
                                            ' Line: ' + linex)
                        exit(1)
                    if norm < 0.0000001:
                        cvecs[key] = vec
                    else:
                        cvecs[key] = vec / norm

                    '''
                    try:
                        cvecs[key] = vec / norm
                    except:
                        print('- key: ' + key + ' norm: ' + str(norm) + \
                                ' vec: ' + str(vec) + ' => ' + str(cvecs[key]))
                    '''

                if 'poi' in FUNCS:

                    if key in pvecs:
                        print('Error: already loaded: ' + key + ' in ' + linex)
                        exit(1)

                    order = 2
                    axis = -1
                    l2 = np.atleast_1d(np.linalg.norm(vec, order, axis))
                    l2[l2==0] = 1

                    pvecs[key] = vec / np.expand_dims(l2, axis)[0]

                    #print('-- normalized vec ' + str(vec))
                    #exit(1)
                    #norm1 = vec / np.linalg.norm(vec)
                    #norm2 = normalize(vec[:,np.newaxis], axis=0).ravel()
                    #vec = np.all(norm1 == norm2)


    except Exception as inst:
        print('Exception: ' + type(inst) + ' reading ' + vecfile)
        exit(1)
    '''
    if len(left) != len(right):
        print('Error: left/right imbalance in ' + vecfile + ': ' + \
                    str(len(left)) + '/' + str(len(right)))
        exit(1)
    '''
    if 'cos' in FUNCS:
        if len(cvecs) == 0:
            print('Error: no vecs in ' + vecfile)
            exit(1)
        print('== cvecs: ' + str(len(cvecs)))

    if 'poi' in FUNCS:
        if len(pvecs) == 0:
            print('Error: no vecs in ' + vecfile)
            exit(1)

        print('== pvecs: ' + str(len(pvecs)))

#################### config

# TODO - have a config object loaded by a lib instead of piecemeal

# get_phobrain_local.sh is in pr/bin/, which should be in your path

PHOBRAIN_LOCAL = run_cmd('get_phobrain_local.sh').strip()

#print('-- PHOBRAIN_LOCAL: ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

if not os.path.isdir(PHOBRAIN_LOCAL):
    print('-- Error: PHOBRAIN_LOCAL: Not a directory: ' + PHOBRAIN_LOCAL)
    exit(1)

real_img = PHOBRAIN_LOCAL + '/real_img_orient'

if not os.path.exists(real_img):
    print('-- Error: not a file: ' + real_img)
    sys.exit(1)

HOST = socket.gethostname()

print('== HOST ' + HOST + '  PHOBRAIN_LOCAL ' + PHOBRAIN_LOCAL)
print('== real_image is ' + real_img)


read_pics(real_img)
read_vecs(VECS)

if 'cos' in FUNCS:

    if len(cvecs) != 2 * (len(portrait) + len(landscape)):
        print('Error: cvecs not matching real_img, len=' + str(len(cvecs)))
        exit(1)

if 'poi' in FUNCS:

    if len(pvecs) != 2 * (len(portrait) + len(landscape)):
        print('Error: pvecs not matching real_img, len=' + str(len(pvecs)))
        exit(1)

# per-id counts of other ids paired via TOP_N
#   patterns unexplored
id_counts = {}

def count_ids(id1, id2):
    global id_counts

    if id1 in id_counts:
        id_counts[id1] += 1
    else:
        id_counts[id1] = 1

    if id2 in id_counts:
        id_counts[id2] += 1
    else:
        id_counts[id2] = 1

# https://stackoverflow.com/questions/43493235/cosine-distance-computation-between-two-arrays-python
'''
just do it in C
#def cosine_vectorized_v3(array1, array2):
def cosine_sim(array1, array2):
    print('-- ' + str(array1) + '/' + str(array2))

    sumyy = np.einsum('ij,ij->i',array2,array2)
    sumxx = np.einsum('ij,ij->i',array1,array1)[:,None]
    sumxy = array1.dot(array2.T)
    sqrt_sumxx = ne.evaluate('sqrt(sumxx)')
    sqrt_sumyy = ne.evaluate('sqrt(sumyy)')
    return ne.evaluate('(sumxy/sqrt_sumxx)/sqrt_sumyy')
def cosine_sim(array1, array2):
    #array1 = array1.reshape(,3)
    #array2 = array2.reshape(,3)
    print('-- ' + str(array1.shape) + '/' + str(array2.shape))
    sumyy = (array2**2).sum(1)
    sumxx = (array1**2).sum(1, keepdims=1)
    sumxy = array1.dot(array2.T)
    return (sumxy/np.sqrt(sumxx))/np.sqrt(sumyy)
'''

if DIM == 2:

    COS_TRIM = 0.9999999
    COS_MULT = 100000000000

elif DIM == 3:

    COS_TRIM = 0.999
    COS_MULT = 100000000

elif DIM < 6:

    COS_TRIM = 0.99
    COS_MULT = 10000000

else:

    COS_TRIM = 0.9
    COS_MULT = 1000000

if 'cos' in FUNCS:
    print('-- COS_TRIM: ' + str(COS_TRIM) + '  COS_MULT: ' + str(COS_MULT))

def print_cosine(out, id1, id2_dict):

    # hi -> lo
    keys = sorted(id2_dict, key=id2_dict.get, reverse=True)

    got = len(keys)

    if got > TOP_N:
        got = TOP_N 

    tot = 0
    used = 0
    failed_cut = 0

    for k in range(got):

        id2 = keys[k]
        d2 = id2_dict[id2]

        #print('d2 ' + str(d2))


        if d2 < COS_TRIM:

            failed_cut += 1
            continue

        elif COS_TRIM > 0:

            d2 -= COS_TRIM
                        
        d2 = int(COS_MULT * d2)

        #print(fname + ' d2 ' + str(d2))

        tot += d2
        used += 1

        #print('-- id1 ' + id1 + '  ' + id2 + ': ' + str(d2))
        out.write(id1 + ' ' + id2 + ' ' + str(d2) + '\n')
        #count_ids(id1, id2)

        if d2 > 2147483647:
            print('-- bzzt: id1 ' + id1 + ' d2 > maxint (2147483647): ' + str(d2))

    return got, tot, used, failed_cut


# merge w/ COS_ above

if DIM == 2:

   POI_TRIM = 10000
   POI_MULT = 10000

elif DIM == 3:

   POI_TRIM = 10000
   POI_MULT = 100000

elif DIM == 4:

   POI_TRIM = 10000
   POI_MULT = 100000

elif DIM == 5:

   POI_TRIM = 40000
   POI_MULT = 10000

elif DIM == 12:

   POI_TRIM = 10000
   POI_MULT = 1000

else:

   POI_TRIM = 10000
   POI_MULT = 100000

if 'poi' in FUNCS:
    print('-- POI_TRIM: ' + str(POI_TRIM) + '  POI_MULT: ' + str(POI_MULT))

def print_poincare(out, id1, id2_dict, s_orient):

    # lo -> hi
    keys = sorted(id2_dict, key=id2_dict.get)

    got = len(keys)

    if got > TOP_N:
        got = TOP_N 

    tot = 0
    used = 0
    failed_cut = 0

    for k in range(got):

        id2 = keys[k]
        d2 = id2_dict[id2]


        if d2 > POI_TRIM:
            failed_cut += 1
            continue

        d2 = POI_TRIM - d2
        d2 = int(d2 * POI_MULT)

        #print('d2 ' + str(d2))
        tot += d2
        used += 1

        #print('-- id1 ' + id1 + '  ' + id2 + ': ' + str(d2))
        out.write(id1 + ' ' + id2 + ' ' + str(d2) + '\n')
        #count_ids(id1, id2)

    if used < got / 2:

        print('-- poincare dim ' + str(DIM) + \
                    ' id1 ' + id1 + \
                    ' ' + s_orient +
                    ' used ' + str(used) + \
                    ' < got/2  got ' + str(got))
        #exit(1)


    return got, tot, used, failed_cut

def write_top(fname, s_orient, l_orient, side, target):

    global id_counts

    start_time = time.time()

    id_counts = {}

    N = len(l_orient)

    fnames = []

    if target == 'cos':

        cos_fname = 'cosine_' + fname
        fnames.append(cos_fname)
        cosine_out = open(cos_fname, "wt", encoding="utf-8")

        c_nzero = 0
        c_neg = 0
        c_nnan = 0
        c_maxd2 = 0.0
        c_mind2 = 9.9e9
        c_incomplete = 0
        c_failed_cut = 0


    if target == 'poi':

        poi_fname = 'poincare_' + fname
        fnames.append(poi_fname)
        poincare_out = open(poi_fname, "wt", encoding="utf-8")
        
        p_nzero = 0
        p_neg = 0
        p_nnan = 0
        p_maxd2 = 0.0
        p_mind2 = 9.9e9
        p_incomplete = 0
        p_failed_cut = 0

    print('== Writing ' + str(fnames) + ' pics ' + str(N))

    # these are all on either right or left, so symmetry
    # canonical order ensures lookup w/ triangular matrix

    for i in range(N-1):

        id1 = l_orient[i]
        key1 = id1 + '-' + side

        if target == 'cos':

            cvec1 = cvecs[key1]
            #print('cvec1 ' + str(cvec1))
            c_id2_dict = {}

        if target == 'poi':

            pvec1 = pvecs[key1]
            #print('pvec1 ' + str(pvec1))
            p_id2_dict = {}


        ct = 0

        for j in range(i+1, N):

            id2 = l_orient[j]
            key2 = id2 + '-' + side

                
            if target == 'cos':

                cvec2 = cvecs[key2]

                d2 = np.dot(cvec1, cvec2) # / (normvec1 * normvec2)  # vecs normalized
                if np.isnan(d2):
                    c_nnan += 1
                    d2 = 1.0
                    print('-- nan on ' + str(cvec1) + ' ' + str(cvec2) + '  - ' + id1 + ' ' + id2)

                c_id2_dict[id2] = d2

                if d2 > -0.00000001 and d2 < 0.00000001:
                    c_nzero += 1
                elif d2 < 0:
                    c_neg += 1

                if d2 > c_maxd2:
                    c_maxd2 = d2
                if d2 < c_mind2:
                    c_mind2 = d2

                #d2 = int(d2 * 100000)
                #print('-- cosine(' + str(cvec1) + ', ' + str(cvec2) + ') = ' + str(d2))

            if target == 'poi':

                pvec2 = pvecs[key2]

                epsilon = 1e-7
                sqdist = np.sum((pvec1-pvec2) ** 2)
                v1norm = 1
                v2norm = 1
                x = 1 + 2 * sqdist / epsilon
                z = np.sqrt(x ** 2 - 1)
                d2 = np.sqrt(x + z)

                #print('-- v1 ' + str(pvec1) + ' v2 ' + str(pvec2))
                #print('-- distsq ' + str(sqdist) + ' v1sq ' + str(v1norm) + ' v2sq ' + str(v2norm))
                #exit(1)

                p_id2_dict[id2] = d2

                if d2 > -0.00000001 and d2 < 0.00000001:
                    p_nzero += 1
                elif d2 < 0:
                    p_neg += 1

                if d2 > p_maxd2:
                    p_maxd2 = d2
                if d2 < p_mind2:
                    p_mind2 = d2


            ct += 1

        # id1 done, proc id2_dict

        if ct == 0:
            print('-- SKIP: ' + target + '-' + s_orient + \
                        ' id2 ct is 0 for id1 ' + id1 + \
                        ' i=' + str(i))

            if target == 'cos':
                del c_id2_dict

            if target == 'poi':
                del p_id2_dict

            continue

        if target == 'cos':

            got, tot, used, failed_cut = print_cosine(cosine_out, id1, c_id2_dict)

            del c_id2_dict

            if used == 0:

                c_incomplete += 1

            if i == 0:

                if used == 0:
                    avg = 'n/a'
                else:
                    avg = str(int(tot / used))

                print(cos_fname + ': First pic initial/final/target/used count: ' + \
                            str(ct) + '/' + str(got) + '/' + str(TOP_N) + '/' + str(used) + \
                            '  failed_cut ' + str(failed_cut) + \
                            '  avgd2 ' + avg + \
                            '  maxd2: ' + str(int(c_maxd2)) + \
                            '  mind2: ' + str(int(c_mind2)) + \
                            '  zeroish: ' + str(c_nzero) + \
                            '  neg: ' + str(c_neg) + \
                            '  nan: ' + str(c_nnan))
            #print('Kill me if you dare')
            #exit(1)

        if target == 'poi':

            got, tot, used, failed_cut = print_poincare(poincare_out, id1, p_id2_dict, s_orient)

            del p_id2_dict

            if used == 0:

                p_incomplete += 1

            if i == 0:

                if used == 0:
                    avg = 'n/a'
                else:
                    avg = str(int(tot / used))

                print(poi_fname + ': First pic initial/final/target/used count: ' + \
                            str(ct) + '/' + str(got) + '/' + str(TOP_N) + '/' + str(used) + \
                            '  failed_cut ' + str(failed_cut) + \
                            '  avgd2 ' + avg + \
                            '  maxd2: ' + str(int(p_maxd2)) + \
                            '  mind2: ' + str(int(p_mind2)) + \
                            '  zeroish: ' + str(p_nzero) + \
                            '  neg: ' + str(p_neg) + \
                            '  nan: ' + str(p_nnan))
    '''
    keys = sorted(id_counts, key=id_counts.get)
    for k in range(-1, -5, -1):
        print('-- ' + keys[k] + ': ' + str(id_counts[keys[k]]))
    print('->')
    for k in range(5):
        print('-- ' + keys[k] + ': ' + str(id_counts[keys[k]]))
    '''

    dt = (time.time() - start_time) / 60

    if target == 'cos':

        cosine_out.close()

        print('== written ' + cos_fname + ' in ' + \
                        str(int(dt)) + ' minutes. Pct pics incomplete ' + str(int(100 * c_incomplete/N)) + \
                        ', failed_cut=' + str(c_failed_cut) + \
                        ', maxd2=' + str(int(c_maxd2)) + \
                        ', mind2=' + str(int(c_mind2)) + \
                        ', nzero=' + str(c_nzero) + \
                        ', neg=' + str(c_neg) + \
                        ', nan=' + str(c_nnan) + '\n')

    if target == 'poi':

        poincare_out.close()

        print('== written ' + poi_fname + ' in ' + \
                        str(int(dt)) + ' minutes. Pct pics incomplete ' + str(int(100 * p_incomplete/N)) + \
                        ', failed_cut=' + str(p_failed_cut) + \
                        ', maxd2=' + str(int(p_maxd2)) + \
                        ', mind2=' + str(int(p_mind2)) + \
                        ', nzero=' + str(p_nzero) + \
                        ', neg=' + str(p_neg) + \
                        ', nan=' + str(p_nnan) + '\n')

print('-- generating .top files')

vdict = { 'v': portrait, 'h': landscape }

procs = []
ix = 0

for s_orient in 'v', 'h':

    l_orient = vdict[s_orient]

    for side in 'l', 'r':

        fname = str(s_orient) + '_' + \
                    side + '_' + \
                    str(DIM) + '.top'

        for func in FUNCS:

            procs.append(Process(target=write_top, args=(fname, s_orient, l_orient, side, func,)))
            procs[ix].start()
            ix += 1

        #write_top(fname, s_orient, l_orient, side, func)

print('== started ' + str(ix) + ' procs')

for p in procs:
    p.join()

print('== all ' + str(ix) + ' procs done')
