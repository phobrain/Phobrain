#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === pickle_training_data.py - put files in redis

IN_MODES = ['HIST_JPGV']

HIST_TYPE = '.hist'
HIST_TYPE = '.hist_bin'


# --- parse args 

import sys
import socket
import os
from os.path import exists
import subprocess

import datetime
import time
import string

import numpy as np
import math

import multiprocessing
from multiprocessing import Pool, Process, Queue
from threading import Thread

import re
import glob

import dill

print('== ' + sys.argv[0])

def usage():
    print('usage: ' + sys.argv[0] + ' <in_mode>')
    print('in_modes:')
    print('\t' + str(IN_MODES))
    exit(1)

import math

suffixes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']

def human_size(nbytes):
    if nbytes == 0:
        return '0B'

    human = nbytes
    rank = 0
    if nbytes != 0:
        rank = int((math.log10(nbytes)) / 3)
        rank = min(rank, len(suffixes) - 1)
        human = nbytes / (1024.0 ** rank)
    f = ('%.2f' % human).rstrip('0').rstrip('.')
    return '%s %s' % (f, suffixes[rank])

if len(sys.argv) != 2:
    usage()

IN_MODE = sys.argv[1]

if IN_MODE not in IN_MODES:
    print('unknown in_type: ' + IN_MODE)
    usage()

if IN_MODE == 'HIST_JPGV':
    IN_MODE = IN_MODE + '_VGG16'

# --------------------- Per-Machine config

# return string from cmd's stdout or die

def run_cmd(cmd):

    print('run_cmd: ' + str(cmd))

    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    if proc.returncode != 0:
        print("Can't run " + str(cmd))
        print('It returned: ' + str(proc.returncode))
        print('     stderr:  ' + str(proc.stderr))
        exit(1)

    return str(proc.stdout).strip()

# get_phobrain_local.sh is in pr/bin/, which should be in your path

PHOBRAIN_LOCAL = run_cmd(['get_phobrain_local.sh'])

print('-- PHOBRAIN_LOCAL: ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

if not os.path.isdir(PHOBRAIN_LOCAL):
    print('-- Error: PHOBRAIN_LOCAL: Not a directory: ' + PHOBRAIN_LOCAL)
    exit(1)

real_img = PHOBRAIN_LOCAL + '/real_img_orient'

if not exists(real_img):
    print('-- Error: not a file: ' + real_img)
    exit(1)

JAVA2ML_BASE = run_cmd(['phobrain_property.sh', 'java2ml.dir'])

if not os.path.isdir(JAVA2ML_BASE):
    print('-- Error: JAVA2ML_BASE: Not a directory: ' + JAVA2ML_BASE)
    exit(1)

IMAGE_DESC_DIR = run_cmd(['phobrain_property.sh', 'image.desc.dir'])

if not os.path.isdir(IMAGE_DESC_DIR):
    print('-- Error: IMAGE_DESC_DIR: Not a directory: ' + IMAGE_DESC_DIR)
    exit(1)

# HOST is for the record, possible future use (was for PHOBRAIN_LOCAL/some_dir.sh function).

HOST = socket.gethostname()

print('== HOST ' + HOST)
print('== JAVA2ML_BASE is ' + JAVA2ML_BASE)
print('== IMAGE_DESC_DIR is ' + IMAGE_DESC_DIR)
print('== real_image is ' + real_img)
print('== IN_MODE ' + IN_MODE)

# transitory, so overkill in a way.
LOAD = 0.8
TOT_THREADS = multiprocessing.cpu_count()
THREADS = int(LOAD * TOT_THREADS)
if THREADS < 1:
    THREADS = 1
# effective limit is vaguely nvme-based
if THREADS > 10:
    THREADS = 10

print('== THREADS: using ' + str(THREADS) + \
                ' of total ' + str(TOT_THREADS) + \
                '  starting ' + str(datetime.datetime.now()))

# -----------------------------------------
# Main run config

# phase 2
DO_SIDE_VECS = False

DO_HIST = False
IN_HIST_MODE = ''

if 'HIST' in IN_MODE:

    DO_HIST = True

    # IN_HIST_MODE can contain any or all of
    #   GREY128, SAT128, RGB12, RGB24, HS24, HS48, SV24, SV48

    # ML pre-concats previous IN_HIST_MODE = 'GREY128_SAT128_RGB12'
    #       judged-sufficient+minimal 

    IN_HIST_MODE = 'ML'

    # concatted hists divided by sum
    #NORM_HIST_TO_MAX = False
    NORM_HIST_TO_MAX = True
    if NORM_HIST_TO_MAX:
        print('== NORMing histos to max')

    hist_dirs = []
    hist_load_dims = []

    if 'ML' in IN_HIST_MODE:

        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/ml_concat')
        hist_load_dims.append(1984)

    # rest in order of size

    if 'SAT48' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/s48_hist')
        hist_load_dims.append(48)

    if 'GREY128' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/grey_hist')
        hist_load_dims.append(128)

    if 'SAT128' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/s128_hist')
        hist_load_dims.append(128)

    if 'HS24' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/hs24_hist')
        hist_load_dims.append(576)

    if 'RGB12' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/rgb12_hist')
        hist_load_dims.append(1728)

    if 'HS48' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/hs48_hist')
        hist_load_dims.append(2304)

    if 'SV48' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/sv48_hist')
        hist_load_dims.append(2304)

    if 'RGB24' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/rgb24_hist')
        hist_load_dims.append(13824)

    if 'RGB32' in IN_HIST_MODE:
        hist_dirs.append(IMAGE_DESC_DIR + '/2_hist/pics/rgb32_hist')
        hist_load_dims.append(32768)

    hist_input_dim = sum(hist_load_dims)

    print('IN_HISTS: ' + str(IN_HIST_MODE))
    print('HISTS: ' + str(len(hist_dirs)))
    print('       DIM\tDIR')
    for i in range(len(hist_dirs)):
        print('       ' + str(hist_load_dims[i]) + '\t' + hist_dirs[i])
    print('Total dim: ' + str(hist_input_dim))

    #quit()
    #checkHists()

DO_JPGV = False
if 'JPGV_' in IN_MODE:
    DO_JPGV = True

DO_JPG = False
if 'JPG_' in IN_MODE:
    DO_JPG = True

IN_JPG_MODE = None

if DO_JPGV:

    if 'VGG16' in IN_MODE:
        IN_JPG_MODE = 'VGG16_224'
    elif 'VGG19' in IN_MODE:
        IN_JPG_MODE = 'VGG19_224'
    elif 'DenseNet121' in IN_MODE:
        IN_JPG_MODE = 'DenseNet121_224'
    elif 'MobileNetV2' in IN_MODE:
        IN_JPG_MODE = 'MobileNetV2_224'
    elif 'EfficientNetB7' in IN_MODE:
        IN_JPG_MODE = 'EfficientNetB7_600'
    else:
        print('-- IN_MODE/JPG: expected one of <VGG19|VGG16|DenseNet121|EfficientNetB7> in path:')
        print('\t' + IN_MODE)
        exit(1)


'''
may revive kwds?
DO_KWDS = False

if 'KWDS' in IN_MODE:

    DO_KWDS = True

    if Horiz:
        kwdvec_dir = JAVA2ML_BASE + '/kwd_vec/2020_08_new/h'
        kwdvec_dim = 1752
    else:
        kwdvec_dir = JAVA2ML_BASE + '/kwd_vec/2020_08_new/v'
        kwdvec_dim = 1477

    kwdvec_dir = os.path.expanduser(kwdvec_dir)
    if not kwdvec_dir.endswith('/'):
        kwdvec_dir += '/'

KWD_VECS = False
KWD_VECS_AVG = False
end kwds
'''

# parse IN_JPG_MODE

JPG_DIM = None

if DO_JPGV or DO_JPG:

    if '224' in IN_JPG_MODE:
        JPG_DIM = 224
    elif '299' in IN_JPG_MODE:
        JPG_DIM = 299
    elif '331' in IN_JPG_MODE:
        JPG_DIM = 331
    elif '600' in IN_JPG_MODE:
        JPG_DIM = 600
    else:
        print('Honky, what jpeg type?')
        exit(1)

    jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun' + str(JPG_DIM)
    jpg_shape = (JPG_DIM, JPG_DIM, 3)

    if DO_JPGV:
        x = IN_JPG_MODE.split('_')
        img_vec_dir = jpg_dir + '/vecs_' + x[0]

    if DO_JPG:
        print('JPG:   ' + jpg_dir)
        print('JPG shape:   ' + str(jpg_shape))
    else:
        print('JPG vecs:   ' + img_vec_dir)


# -----------------------------------------


print('exec: ' + str(sys.executable))
#sys.path = [ '/project', '/usr/lib/python3.7', '/usr/lib/python3.7/lib-dynload', '/usr/local/lib/python3.7/dist-packages', '/usr/lib/python3/dist-packages']
print('path: ' + str(sys.path))

fnames = []
v_fnames = []
h_fnames = []
id_map = {}
n_pics = 0

Both = True

def load_pic_list():

    global fnames, v_fnames, h_fnames, id_map, n_pics

    print('Read pic list: ' + real_img)

    lines = 0
    with open(real_img) as fp:
        for line in fp:
            lines += 1
            if line[0] == '#':
                continue
            #print('DO: '+ line)
            fname, torient = line.split()

            #torient = torient.strip()

            fnames += [ fname ]
            if torient == 'f':
                h_fnames += [ fname ]
            else:
                v_fnames += [ fname ]

            id_map[fname] = procId(fname)
                
            #if fname =='1/img03a.jpg':
            #   print('mapped ' + fname)

    n_pics = len(fnames)

    print('Pics: ' + str(n_pics) + \
                ' V ' + str(len(v_fnames)) + \
                ' H ' + str(len(h_fnames)))

    if n_pics == 0:
        print('NO PICS from ' + real_img)
        exit(1)

def vecIdFile(fname):

    id = procId(fname)

    return img_vec_dir + '/' + id

def checkHists():
    print('Checking hists for fnames from real_img: ' + str(n_pics))
    error = False
    for i in range(n_pics):
        fname = fnames[i]
        try:
            hist = hist_final_map[fname]
        except KeyError:
            print('-- ' + fname)
            error = True

    if error:
        print('bye!')
        exit(1)

# TODO - remove per-camera dependency (DK7A here)

def procId(fname):
    archive, f = fname.split('/')
    tag = f.replace('.hist_bin','').replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')
    #print('xx ' + archive + ':' + str(seq))
    return archive + ':' + tag

# old option - convolution didn't help on histos
#               in original attempts, just slower
histogram_convolute = False

def load_nums(fname, target_size, is_histogram):
    global hist_sum

    ct = 0
    if is_histogram:
        if HIST_TYPE == '.hist':
            data = np.loadtxt(fname)
        else:
            t = '>f'
            #t = np.float32
            data = np.fromfile(fname, dtype=t, count=-1, sep='')
    else:
        data = np.loadtxt(fname)

    if len(data) != target_size:

        print('Dim mismatch: ' + fname + '\n\texpected/actual: ' + 
                str(target_size) + '/' + str(len(data)))
        exit(1)

    # print('-- ' + fname + ' data=' + str(data))
    # quit()

    #if not is_histogram:
    #    data *= 0.5;
    #    t = np.sum(data)
    #    if t > 1:
    #        data /= t

    if is_histogram:
        #hist_sum += data
        if histogram_convolute:
            if input_axes == 2:
                data.shape = (-1, input_dim, 1)
            else:
                data.shape = (-1, input_dim, input_dim, 1)

    return data
    #with open(fname) as fp:
        #for line in fp:
            #ct += 1


side_vec_map = {}

def load_side_vecs():

    print('Loading vecs; dims and files:')
    for i in range(len(side_vec_files)):
        print('\t' + str(side_vec_dims[i]) + '\t' + side_vec_files[i])

    i = 0

    for f in side_vec_files:
        print('Loading .vecs file: ' + f)
        vec_len = 0
        with open(f) as fp:

            ct = 0

            for line in fp:

                if line.startswith('#'):
                    continue

                # id <l|r> vals...
                fields = line.split()
                if len(fields) < 3:
                    print('fields < 3: ' + line)
                    exit(1)

                id = fields[0]

                v_len = len(fields) - 2
                if vec_len == 0:
                    vec_len = v_len
                    if vec_len != side_vec_dims[i]:
                        print('Config err: bad dim[' + str(i) + ': ' + \
                                            str(vec_len))
                        exit(1)

                elif v_len != vec_len:
                    print('Load err: wrong vec_len: ' + str(vec_len) + ' ' + \
                                str(v_len))
                    exit(1)

                ct = ct + 1

                side = fields[1]
                side_id = id + '_' + side

                tvec = np.array(fields[2:],  dtype=np.float32)

                vec = side_vec_map.get(side_id)
                if not isinstance(vec, type(None)):
                    tvec = np.concatenate((tvec, vec), axis=0)   
                    #print('len tvec ' + str(len(tvec)))
                    #quit()
                side_vec_map[side_id] = tvec
             
        i = i + 1

    if len(side_vec_dims) > 1:
        print('-- Norm side vecs by count')
        factor = 1.0 / len(side_vec_dims)
        for k in side_vec_map:
            side_vec_map[k] = side_vec_map[k] * factor

    print('Loaded vecs: ' + str(len(side_vec_files)) + ' tot_dim ' + \
                        str(sum(side_vec_dims)) + ' checking..')
    ok = 0
    vlen = -1
    for item in side_vec_map.items():
        if vlen == -1:
            vlen = len(item[-1])
        elif len(item[-1]) != vlen:
            print('Got bad vec, key=' + item[0] + \
                    ' len=' + str(len(item[-1])) + \
                    ' expected: ' + str(vlen) + \
                    ' ok map items so far ' + str(ok))
            exit(1)
        ok += 1


'''
may revive kwds?
kwdvec_map = {}

def load_kwdvec(arg):
    pid, fname = arg
    #fname = kwdvec_dir + fname
    #print('load_kv ' + fname + ' ' + pid)
    #quit()
    return pid, load_nums(fname, kwdvec_dim, False)

def load_kwdvecs():

    print('Read kwdvecs: ' + kwdvec_dir)

    start = time.time()

    #for x in glob.glob(os.path.join(kwdvec_dir, '*:*')):
    #    print('- ' + str(x))
    files = glob.glob(os.path.join(kwdvec_dir, '*:*'))
    if len(files) == 0:
        print('No kwd vecs in ' + kwdvec_dir)
        exit(1)
    print('all kwdvecs: ' + str(len(files)))

    all_pids = [w.replace(kwdvec_dir, '') for w in list(files)]

    tmp = zip(all_pids, files)

    #for pid, fname in zip(pids, files):
    #    print('pid ' + pid + ' file ' + fname)
    #    kwdvec_map[pid] = load_nums(fname, False)
    print('Load kwdvecs, threads: ' + str(THREADS))
    pool = Pool(processes=THREADS)
    for pid, kwdvec in pool.map(load_kwdvec, tmp):
        #if '1:3a' in pid or '1/3a' in pid:
        #    print('PID! [' + pid + ']')
        #    quit()
        kwdvec_map[pid] = kwdvec
        #print('loaded ' + str(pid))
    pool.close()
    pool.terminate()
    pool.join()
    end = time.time()
    print('kwds: loaded ' + str(len(kwdvec_map)) + ' in ' + \
           str(datetime.timedelta(0, end-start)))
end kwds
'''

hist_final_map = {}
jpg_final_map = {}

def load_it_hist(arg):
    hist_fname = arg 
    #print('load ' + hist_fname + ' x ' + str(len(hist_dirs)))
    x = load_nums(hist_dirs[0] + '/' + hist_fname, hist_load_dims[0], True)
    #print('Sum ' + str(sum(x)) + ' ' + map_fname + '->' + hist_fname)
    for hist_model in range(1, len(hist_dirs)):
        #hist_fname = hist_file_maps[hist_model][map_fname]
        x1 = load_nums(hist_dirs[hist_model] + '/' + hist_fname, 
                        hist_load_dims[hist_model], True)
        #print('Sum x1 ' + str(sum(x1)))
        x = np.concatenate((x, x1), axis=0)   
    #print('Final Sum ' + str(sum(x)))
    if NORM_HIST_TO_MAX:
        x /= max(x)
    elif len(hist_dirs) > 1:
        x /= len(hist_dirs)

    #print('QUIT')
    #quit()
    #if len(x) != hist_input_dim:
    #    print('Bad ' + str(len(x)))
    #    quit()

    map_fname = hist_fname.replace(HIST_TYPE, '.jpg')

    return map_fname, x


img_vec_shape = None

def load_it_jpg(map_fname):

    #print('load ' + map_fname + ' jpg_dir ' + jpg_dir)
    #exit(1)
    if DO_JPGV:
        # imagenet feature vecs
        img_vec_file = vecIdFile(map_fname)
        #print('-- load imgvec ' + img_vec_file)
        jpg = np.load(img_vec_file)
        #print('vec ' + str(jpg))
    else:
        jpg_fname = jpg_dir + '/' + map_fname
        jpg = image.load_img(jpg_fname)
        jpg = image.img_to_array(jpg)
        jpg = np.expand_dims(jpg, axis=0)
        # imagenet preproc
        jpg = preprocess_input(jpg)

    return map_fname, jpg


def load_preproc():

    global img_vec_shape

    start = time.time()

    if DO_SIDE_VECS:
        load_side_vecs()

    #if DO_KWDS:
    #    load_kwdvecs()

    if DO_HIST:
        # use 1st dir to derive the file list
        files = [y for x in os.walk(hist_dirs[0]) \
                   for y in glob.glob(os.path.join(x[0], '*' + HIST_TYPE))]
        #print('ff ' + files[1])
        all_files = len(files)
        print('All files in 1st [' + hist_dirs[0] + ']: ' + str(all_files))

        # delete the 1st dir-specific part, and turn into jpg-keys
        delstr = hist_dirs[0] + '/'
        trim_files = [w.replace(delstr, '') for w in list(files)]
        keys = [w.replace(HIST_TYPE, '.jpg') for w in trim_files]
        print('intersect keys=' + str(len(keys)) + ' fnames=' + str(len(fnames)))
        #print('k0 ' + keys[0] + ' fnames0 ' + fnames[0])

        keys = set(keys).intersection(set(fnames))

        if len(keys) < all_files:
            print('Error: Expected: ' + str(all_files) + ' got ' + str(len(keys)))
            exit(1)

        trim_files = [w.replace('.jpg', HIST_TYPE) for w in list(keys)]

        if len(trim_files) == 0:
            print('No histogram files: ' + HIST_TYPE)
            exit(1)

        print('Load hists, threads: ' + str(THREADS) + \
                        ' trimmed ' + HIST_TYPE + ' files: ' + \
                        str(len(trim_files)))

        pool = Pool(processes=THREADS)

        for map_fname, hist in pool.map(load_it_hist, trim_files):

            #for map_fname, hist in pool.map(load_it_hist, l):
            hist_final_map[map_fname] = hist
            #hist_file_maps[model][map_fname] = hist

        pool.close()
        pool.terminate()
        pool.join()
        end = time.time()
        print('loaded ' + str(len(hist_dirs)) + ' hist types in ' + str(datetime.timedelta(0, end-start)))
        print('hist_final_map size: ' + str(len(hist_final_map)))
        if len(hist_final_map) == 0:
            print('Histograms: nothing loaded to hist_final_map.')
            exit(1)
        #---

    if DO_JPGV or DO_JPG:

        n = str(len(fnames))

        if DO_JPG:
            print('Load ' + n + ' jpgs from ' + jpg_dir + ', threads: ' + str(THREADS))
        if DO_JPGV:
            print('Load ' + n + ' jpg vecs/features from ' + img_vec_dir + ', threads: ' + str(THREADS))

        pool = Pool(processes=THREADS)

        for map_fname, jpg in pool.map(load_it_jpg, fnames):
            jpg_final_map[map_fname] = jpg
        pool.close()
        pool.terminate()
        pool.join()
        endt = time.time()
        if DO_JPGV:
            vec = next(iter(jpg_final_map.values()))
            img_vec_shape = vec.shape
            print('-- img vector shape: ' + str(img_vec_shape))

        print('loaded ' + str(len(jpg_final_map)) + ' in ' + \
                          str(datetime.timedelta(0, endt-start)))

def hist_name(fname):
    fname = string.replace(fname, 'jpg', HIST_TYPE)
    fname = hist_dir + '/' + fname
    if not os.path.isfile(fname):
        tries = ['a.', 'b.', 'c.', 'd.', 'e.', 'f.', 'g.', 'h.', 'i.', 'j.', 'k.', 'l.', 'm.', 'n.', 'a_sm.', 'b_sm.', 'c_sm.', 'd_sm.', 'e_sm.', 'f_sm.', 'g_sm.', 'h_sm.', 'i_sm.', 'j_sm.', 'k_sm.', 'l_sm.', 'm_sm.', 'n_sm.'  ]
        for x in tries:
            fname2 = fname.replace(x, '*.')
            #print('Try ' + fname + '->' + fname2 + ': ' + str(glob.glob(fname2)))
            txt = glob.glob(fname2)
            if len(txt) == 0:
                continue
            fname2 = txt[0]
            if os.path.isfile(fname2):
                fname = fname2
                break
    if not os.path.isfile(fname):
        print('No file like: ' + fname)
        exit(1)
    return fname

# load all IN_MODE input data: imagenet, histograms, maybe even kwd vecs someday
load_pic_list()
load_preproc()

print('-- fnames/v/h ' + str(len(fnames)) + '/' + str(len(v_fnames)) + '/' +
                str(len(h_fnames)))

data = [ fnames, v_fnames, h_fnames, id_map, hist_final_map, jpg_final_map ]

fo = IMAGE_DESC_DIR + '/' + IN_MODE + '.dil'

print('-- writing ' + fo)

dill.dump(data, open(fo, 'wb'))

#import pickle
#pickle.dump(data, open(fo, 'wb'))

fstats = os.stat(fo)

print('=== Done: ' + fo + '   ' + human_size(fstats.st_size) + '  ' + str(datetime.datetime.now()))

'''
t0 = datetime.datetime.now()
unpacked_object = pickle.load(open('d.p', 'rb'))
f2, v2, h2, i2, hi2, j2 = unpacked_object
print('-- unpack: ' + str(datetime.datetime.now()-t0))

if f2 != fnames:
    print('-- fnames - no')
    exit(1)
if v2 != v_fnames:
    print('-- v_fnames - no')
    exit(1)
if h2 != h_fnames:
    print('-- h_fnames - no')
    exit(1)
if i2 != id_map:
    print('-- id_map - no')
    exit(1)
np.testing.assert_equal(hi2, hist_final_map)
np.testing.assert_equal(j2, jpg_final_map)
print('ok')
'''
