#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === ppred.py - parallel/forked batch pair predictions

import sys

import subprocess
from multiprocessing import Pool

import time
import datetime
#import string
import glob

import threading
import queue

import socket
import os
from os.path import exists

import math
import random
import numpy as np
import numpy.lib.format

import pynvml
import psutil

import faulthandler
import signal
faulthandler.register(signal.SIGUSR1.value)
# To see stack when hung: $ kill -s SIGUSR1 <pid>

# --------------------- Per-Machine config

# return string from cmd's stdout or die

def run_cmd(cmd):

    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    if proc.returncode != 0:
        print("Can't run " + cmd)
        print('It returned: ' + str(proc.returncode))
        print('     stderr:  ' + str(proc.stderr))
        exit(1)

    return str(proc.stdout).strip()

PHOBRAIN_LOCAL = os.environ.get('PHOBRAIN_LOCAL')

if PHOBRAIN_LOCAL is None:
    PHOBRAIN_LOCAL = '~/phobrain_local'
    print('(ppred.py: No env PHOBRAIN_LOCAL: using ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

if not os.path.isdir(PHOBRAIN_LOCAL):
    print('-- Error: PHOBRAIN_LOCAL: Not a directory: ' + PHOBRAIN_LOCAL)
    sys.exit(1)

real_img = PHOBRAIN_LOCAL + '/real_img_orient'

if not exists(real_img):
    print('-- Error: not a file: ' + real_img)
    sys.exit(1)

IMAGE_DESC_DIR = run_cmd( ['phobrain_property.sh', 'image.desc.dir'] )

JAVA2ML_BASE = run_cmd( ['phobrain_property.sh', 'java2ml.dir'] )

HOST = socket.gethostname()

print('== HOST ' + HOST + '  PHOBRAIN_LOCAL ' + PHOBRAIN_LOCAL)
print('== real_image is ' + real_img)
print('== JAVA2ML_BASE is ' + JAVA2ML_BASE)
print('== IMAGE_DESC_DIR is ' + IMAGE_DESC_DIR)

THREADS = 8

# -----------------------------------------
# Main run config

HORIZ = None
BOTH = False

IN_MODE = 'UNSET, you heartless animal'
OUT_MODE = 'UNSET, you heartless animal'

# force run mode vs tf.keras default
RUN_CPU = False
RUN_GPU = False
GPU = None

VEC_LIST_FILE = None
MATTER = None

def usage():

    print('usage: ' + sys.argv[0] +
            ' [-cpu|-gpu n] <-v|-h|-b> ' +
            '-ifmt <JvH|JH|V|..> ' +
            '-ofmt <vecs|bin|all|top> ' +
            ' <model_file_list|dir>')
    print('      Usually only -ofmt <vecs|bin> is used in production.')
    print('      -bin is then processed by predtool.py.')
    sys.exit(1)

def parse_args():

    global HORIZ, BOTH
    global IN_MODE, OUT_MODE
    global RUN_CPU, RUN_GPU, GPU
    global VEC_LIST_FILE, MATTER

    if len(sys.argv) < 4:
        usage()

    i = 0
    while i < len(sys.argv):
        i += 1
        if sys.argv[i][0] != '-':
            print('done with parse args on ' + sys.argv[i])
            break

        if sys.argv[i] == '-cpu':
            RUN_CPU = True
        if sys.argv[i] == '-gpu':
            RUN_GPU = True
            i += 1
            if i == len(sys.argv):
                print('usage: expected: -gpu <num>')
                sys.exit(1)
            GPU = sys.argv[i]
        elif sys.argv[i] == '-b':
            BOTH = True
        elif sys.argv[i] == '-h':
            HORIZ = True
        elif sys.argv[i] == '-v':
            HORIZ = False
        elif sys.argv[i] == '-ifmt':
            i += 1
            if i == len(sys.argv):
                print('usage: expected: -ifmt <type>')
                sys.exit(1)

            # JPG_ needs to have '_' to distinguish JPGV

            tryna_mode = sys.argv[i]
            if tryna_mode == 'JvH':
                IN_MODE = 'HIST_JPGV'
            elif tryna_mode == 'JH':
                IN_MODE = 'HIST_JPG'
            elif tryna_mode == 'V':
                IN_MODE = 'VECS'
                i += 1
                if i == len(sys.argv):
                    print('usage: expected: -ifmt V <vecs_flist_file>')
                    sys.exit(1)
                VEC_LIST_FILE = sys.argv[i]

                print('== -ifmt V: VEC_LIST_FILE: ' + VEC_LIST_FILE)

            elif tryna_mode == 'H':
                print('Legacy IN_MODE? "H" is not HORIZ here, V is for VECS')
            else:
                print('Legacy IN_MODE? Trying it verbatim: ' + tryna_mode)
                IN_MODE = tryna_mode
                # Legacy: last kwds in arch10
                # IN_MODE can contain any/all of
                #       [ 'KWD', 'JPG', 'HIST' ]
                #    - or 'VECS'
                #IN_MODE = 'JPG'
                #IN_MODE = 'KWDS'
                #IN_MODE = 'HIST'
                #IN_MODE = 'KWDS_HIST'

        elif sys.argv[i] == '-ofmt':
            i += 1
            if i == len(sys.argv):
                print('usage: expected: -ofmt <type>')
                sys.exit(1)

            tryna_mode = sys.argv[i]
            if tryna_mode == 'vecs':
                OUT_MODE = 'DO_LR_VECS'
            elif tryna_mode == 'bin':
                OUT_MODE = 'DO_BIN'
            else:
                print('Unknown OUT_MODE, see predtool.py: ' + tryna_mode)
                usage()

            # -----------------------
            # -- OUT_MODE
            #
            # DO_LR_VECS: output is a vecs_ file with latent space vector
            #   representations of pics on left, right.
            #   For each id idX in v or h:
            #         idX l <val_list>
            #         idX r <val_list>
            # DO_BIN: just output preds in binary form.
            #

    if RUN_CPU and RUN_GPU:
        print('Oops: -cpu incmpatible with -gpu')
        sys.exit(1)

    arg_i = len(sys.argv) - 1
    if sys.argv[arg_i] == '&':
        arg_i -= 1

    if i != arg_i:
        print(sys.argv[0] + ': Usage: after args, need 1 dir or file to proc')
        print('debug: args: ' + str(len(sys.argv)) + '  i: ' + str(i))
        print('sys.argv: ' + str(sys.argv))
        sys.exit(1)

    MATTER = sys.argv[i]

parse_args()

def out_file_name(model_file):

    if OUT_MODE == 'DO_LR_VECS':
        # exception - .vecs goes next to .h5
        return model_file.replace('.h5', '.vecs')

    if OUT_MODE == 'DO_BIN':
        # main calc; swap bytes to .pairs and delete when done
        return model_file.replace('.h5', '.pairs')

    # nothing matches:
    print('Fix OUT_MODE outfile: ' + OUT_MODE)
    usage()
    sys.exit(1) # for lint

MODEL_LIST = []
DONE_LIST = []

def read_list_file():

    # MATTER is a file listing .h5's

    global MODEL_LIST, MODEL_LIST

    if len(MODEL_LIST) > 0:
        print('-- (read_list_file: already read ' + MATTER + ')')
        return

    fields = MATTER.split('_')

    with open(MATTER, 'r', encoding='utf-8') as fp:
        for line in fp:
            if line[0] == '#':
                print('Commented: ' + line)
                continue

            line = line.rstrip()

            #print('-- LINE ' + line)

            if line.endswith('.h5'):

                MODEL_LIST.append(line)

                outfile = out_file_name(line)

                if os.path.isfile(outfile):
                    DONE_LIST.append(outfile)

            elif len(line) > 0:

                print('Ignoring line: ' + line)

DO_JPGV = False
DO_JPG = False
if '_JPGV' in IN_MODE:
    DO_JPGV = True
elif '_JPG' in IN_MODE:
    DO_JPG = True

def jpg_size(str):
    if 'VGG16' in str:
        return 'VGG16_224'
    if 'VGG19' in str:
        return 'VGG19_224'
    if 'DenseNet121' in str:
        return 'DenseNet121_224'
    if 'MobileNetV2' in str:
        return 'MobileNetV2_224'
    if 'EfficientNetB7' in str:
        return 'EfficientNetB7_600'
    print('Fatal model->size: Expected one of <VGG19|VGG16|DenseNet121|EfficientNetB7> in model/path:')
    print('\t' + str)
    sys.exit(1)

IN_JPG_MODE = ''

if DO_JPGV:

    if os.path.isdir(MATTER):
        IN_JPG_MODE = jpg_size(MATTER)
    else:
        read_list_file()
        IN_JPG_MODE = jpg_size(MODEL_LIST[0])

    print('-- DO_JPGV: ' + IN_JPG_MODE)
        
if DO_JPG:
    # unused
    IN_JPG_MODE = 'VGG19_224'

print('============ START ' + str(datetime.datetime.now()))

print('== REAL_IMG ' + real_img)
print('== IN_MODE: ' + IN_MODE)
print('== OUT_MODE: ' + OUT_MODE)
print('== MATTER: ' + MATTER)
if BOTH:
    print('== BOTH h, v')
else:
    print('== HORIZ ' + str(HORIZ))
print('== CPU: ' +  str(RUN_CPU))
if not RUN_CPU:
    print('== designated GPU: ' +  str(GPU))

print('== THREADS: ' + str(THREADS))

IN_HIST_MODE = ''
if 'HIST' in IN_MODE:

    # IN_HIST_MODE can contain any or all of
    #   GREY128, SAT128, RGB12, RGB24, HS24, HS48, SV24, SV48

    IN_HIST_MODE = 'GREY128_SAT128_RGB12'

PAIR_LIMIT=-1

#print('OUT_MODE is ' + OUT_MODE + ' ==pvecs: ' +
#        str(OUT_MODE == 'DO_LR_VECS'))

if OUT_MODE == 'DO_LR_VECS':

    print('Calcing left,right pic vecs')

elif OUT_MODE == 'DO_BIN':

    print('Calcing binary pred array')

else:
    print('OUT_MODE needs fix: ' + OUT_MODE)
    sys.exit(1)

# inputs models trained with

DO_SIDE_VECS = False

if 'VECS' == IN_MODE:

    DO_SIDE_VECS = True

    side_vec_files = []
    side_vec_dims = []

    # e.g.
    #  m_h_model_pn_80_87_bEen_200_1_3_3_ki_0_VGG19_224_nathan_lrv_12_SGD.vecs

    print('Reading vec list file: ' + VEC_LIST_FILE)

    with open(VEC_LIST_FILE, 'r', encoding='utf-8') as fp:
        for line in fp:
            if line.startswith('#'):
                continue

            fields = line.split('_')
            ix = fields.index('lrv')
            dim = int(fields[ix+1])

            side_vec_files.append(line.rstrip())
            side_vec_dims.append(dim)

    side_vec_dim = sum(side_vec_dims)

    print('Side vecs: ' + str(len(side_vec_files)) + ' dim: ' +str(side_vec_dim))


DO_KWDS = False

if 'KWDS' in IN_MODE:

    DO_KWDS = True

    if HORIZ:
        KWDVEC_DIR = JAVA2ML_BASE + '/kwd_vec/2020_08_new/h'
        KWDVEC_DIM = 1752
    else:
        KWDVEC_DIR = JAVA2ML_BASE + '/kwd_vec/2020_08_new/v'
        KWDVEC_DIM = 1477

    KWDVEC_DIR = os.path.expanduser(KWDVEC_DIR)
    if not KWDVEC_DIR.endswith('/'):
        KWDVEC_DIR += '/'

KWD_VECS = False
KWD_VECS_AVG = False

DO_HIST = False
if 'HIST' in IN_MODE:
    DO_HIST = True

NATHAN = True
#NATHAN = False

# -----------------------------------------

if NATHAN:
    pylib = os.path.dirname(os.path.realpath(__file__)) + '/pylib'
    sys.path.append(pylib)
    from nathan import Nathan

print('exec: ' + str(sys.executable))
print('path: ' + str(sys.path))


#MAG = 1.0e10


# how many to chunk for each pass of the engine

# default for vector-input predictions
# 3xV7->21: 150000 is best
BATCH_SIZE = 150000

N_BATCHES = 0

if DO_JPG:
    BATCH_SIZE = 64
elif DO_JPGV:
    # i9,128G,2x2080ti 
    #       2 procs/gpu: 500,666->15min; 300->11min; 200->10min; 100->11min
    #       3            200->8min; 100->9min
    #       4            100->9min; 50->9min
    BATCH_SIZE = 200

FNAMES = []
ID_MAP = {}
N_PICS = 0

def load_pic_list():

    global FNAMES, N_PICS

    if BOTH:
        print('Read pic list/H+V: ' + real_img)
    elif HORIZ:
        print('Read pic list/H: ' + real_img)
    else:
        print('Read pic list/V: ' + real_img)

    lines = 0
    with open(real_img, "r", encoding="utf-8") as fpx:
        for linex in fpx:
            lines += 1
            if linex[0] == '#':
                continue
            #print('DO: '+ line)
            fname, torient = linex.split()
            #torient = torient.strip()

            if BOTH or HORIZ == (torient == 'f'):
                FNAMES += [ fname ]
                ID_MAP[fname] = proc_id(fname)

                #if fname =='1/img03a.jpg':
                #   print('mapped ' + fname)

    N_PICS = len(FNAMES)
    print('Pics: ' + str(N_PICS) + ' from ' + real_img)
    if N_PICS == 0:
        print('NO PICS, Horz=' +str(HORIZ))
        sys.exit(1)

def vec_id_file(fname):

    pic_id = proc_id(fname)

    return img_vec_dir + '/' + pic_id

def check_hists():
    end = len(FNAMES)
    print('Checking hists for FNAMES from real_img: ' + str(end))
    error = False
    for fname in FNAMES:
        try:
            _ = HIST_FINAL_MAP[fname]
        except KeyError:
            print('-- ' + fname)
            error = True

    if error:
        print('bye!')
        sys.exit(1)




def proc_id(fname):
    archive, name = fname.split('/')
    tag = name.replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')
    #print('xx ' + archive + ':' + str(seq))
    return archive + ':' + tag

# old histogram-only
CONVOLUTE = False

def load_nums(fname, target_size, is_histogram):

    data = np.loadtxt(fname)
    if len(data) != target_size:
        print('Dim mismatch: ' + fname + 'exp/actual ' +
                str(target_size) + '/' + str(len(data)))
        sys.exit(1)

    #if not is_histogram:
    #    data *= 0.5;
    #    t = np.sum(data)
    #    if t > 1:
    #        data /= t

    #if is_histogram:
    #    #HIST_SUM += data
    #    if CONVOLUTE:
    #        if input_axes == 2:
    #            data.shape = (-1, input_dim, 1)
    #        else:
    #            data.shape = (-1, input_dim, input_dim, 1)

    return data


side_vec_map = {}

def load_side_vecs():

    print('Loading vecs; dims and files:')
    for i in range(len(side_vec_files)):
        print('\t' + str(side_vec_dims[i]) + '\t' + side_vec_files[i])

    i = 0

    for fname in side_vec_files:
        print('Loading .vecs file: ' + fname)
        vec_len = 0
        with open(fname, "r", encoding="utf-8") as fpx:

            count = 0

            for linex in fpx:

                if linex.startswith('#'):
                    continue

                # id <l|r> vals...
                fieldsx = linex.split()
                if len(fieldsx) < 3:
                    print('fields < 3: ' + linex)
                    sys.exit(1)

                pic_id = fieldsx[0]

                #if pic_id not in targets:
                #    continue

                v_len = len(fieldsx) - 2
                if vec_len == 0:
                    vec_len = v_len
                    if vec_len != side_vec_dims[i]:
                        print('Config err: bad dim[' + str(i) + ': ' + \
                                            str(vec_len))
                        sys.exit(1)

                elif v_len != vec_len:
                    print('Load err: wrong vec_len: ' + str(vec_len) + ' ' + \
                                str(v_len))
                    sys.exit(1)

                count += 1

                side = fieldsx[1]
                side_id = pic_id + '_' + side

                tvec = np.array(fieldsx[2:],  dtype=np.float32)

                vec = side_vec_map.get(side_id)
                if not isinstance(vec, type(None)):
                    tvec = np.concatenate((tvec, vec), axis=0)
                    #print('len tvec ' + str(len(tvec)))
                    #quit()
                side_vec_map[side_id] = tvec

            '''
            if count != N_PICS * 2:
                print("Load: bad pic count: ' + str(count) + ' expected ' + \
                                                str(N_PICS * 2))
                quit()
            '''
        i = i + 1

    if len(side_vec_dims) > 1:
        print('-- Norm side vecs by count')
        factor = 1.0 / len(side_vec_dims)
        for k in side_vec_map:
            side_vec_map[k] = side_vec_map[k] * factor

    print('Loaded vecs: ' + str(len(side_vec_files)) + ' tot_dim ' + \
                        str(sum(side_vec_dims)) + ' checking..')
    ok_items = 0
    vlen = -1
    for item in side_vec_map.items():
        if vlen == -1:
            vlen = len(item[-1])
        elif len(item[-1]) != vlen:
            print('Got bad vec, key=' + item[0] + \
                    ' len=' + str(len(item[-1])) + \
                    ' expected: ' + str(vlen) + \
                    ' ok map items so far ' + str(ok_items))
            sys.exit(1)
        ok_items += 1

kwdvec_map = {}

def load_kwdvec(arg):
    pid, fname = arg
    #fname = KWDVEC_DIR + fname
    #print('load_kv ' + fname + ' ' + pid)
    #quit()
    return pid, load_nums(fname, KWDVEC_DIM, False)

def load_kwdvecs():

    print('Read kwdvecs: ' + KWDVEC_DIR)

    startx = time.time()

    #for x in glob.glob(os.path.join(KWDVEC_DIR, '*:*')):
    #    print('- ' + str(x))
    files = glob.glob(os.path.join(KWDVEC_DIR, '*:*'))
    if len(files) == 0:
        print('No kwd vecs in ' + KWDVEC_DIR)
        sys.exit(1)
    print('all kwdvecs: ' + str(len(files)))

    all_pids = [w.replace(KWDVEC_DIR, '') for w in list(files)]

    tmp = zip(all_pids, files)

    #for pid, fname in zip(pids, files):
    #    print('pid ' + pid + ' file ' + fname)
    #    kwdvec_map[pid] = load_nums(fname, False)
    print('Load kwdvecs, threads: ' + str(THREADS))
    with Pool(THREADS) as pool:
        for pid, kwdvec in pool.map(load_kwdvec, tmp):
            #if '1:3a' in pid or '1/3a' in pid:
            #    print('PID! [' + pid + ']')
            #    quit()
            kwdvec_map[pid] = kwdvec
            #print('loaded ' + str(pid))
    end = time.time()
    print('kwds: loaded ' + str(len(kwdvec_map)) + ' in ' + \
           str(datetime.timedelta(0, end-startx)))

HIST_FINAL_MAP = {}
JPG_FINAL_MAP = {}

#for i in range(len(hist_dirs)):
#    hist_file_maps.append({})

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
    #if len(x) != HIST_VEC_SHAPE
    #    print('Bad ' + str(len(x)))
    #    quit()

    map_fname = hist_fname.replace('.hist', '.jpg')

    return map_fname, x
    # load_nums(model, hist_fname, True)
    #doneq.put([map_fname, load_hist(hist_fname)])


IMG_VEC_SHAPE = None
HIST_VEC_SHAPE = None

def load_it_jpg(map_fname):

    #print('load ' + map_fname + ' JPG_DIR ' + JPG_DIR)
    #sys.exit(1)
    if DO_JPGV:
        # imagenet feature vecs
        img_vec_file = vec_id_file(map_fname)
        #print('-- load imgvec ' + img_vec_file)
        jpg = np.load(img_vec_file)
        #print('vec ' + str(jpg))
    else:
        jpg_fname = JPG_DIR + '/' + map_fname
        jpg = image.load_img(jpg_fname)
        jpg = image.img_to_array(jpg)
        jpg = np.expand_dims(jpg, axis=0)
        # imagenet preproc
        jpg = preprocess_input(jpg)

    return map_fname, jpg

#HIST_SUM = np.zeros(load_dim)
#def norm_it(arg):
#    map_fname, hist = arg
#    return map_fname, hist - HIST_SUM


def load_preproc():

    #load_config()

    global IMG_VEC_SHAPE, HIST_VEC_SHAPE

    start = time.time()

    if DO_SIDE_VECS:
        load_side_vecs()

    if DO_KWDS:
        load_kwdvecs()

    if DO_HIST:

        #------ beginning of untested-since-moved-from-main section
        HIST_LOAD_DIR = IMAGE_DESC_DIR + '/2_hist/pics'

        # concatted hists divided by max val
        #NORM_HIST_TO_MAX = False
        NORM_HIST_TO_MAX = True
        if NORM_HIST_TO_MAX:
            print('== NORMing histos to max')

        hist_dirs = []
        hist_load_dims = []

        # in order of size

        if 'SAT48' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/s48_hist')
            hist_load_dims.append(48)

        if 'GREY128' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/grey_hist')
            hist_load_dims.append(128)

        if 'SAT128' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/s128_hist')
            hist_load_dims.append(128)

        if 'HS24' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/hs24_hist')
            hist_load_dims.append(576)

        if 'RGB12' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/rgb12_hist')
            hist_load_dims.append(1728)

        if 'HS48' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/hs48_hist')
            hist_load_dims.append(2304)

        if 'SV48' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/sv48_hist')
            hist_load_dims.append(2304)

        if 'RGB24' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/rgb24_hist')
            hist_load_dims.append(13824)

        if 'RGB32' in IN_HIST_MODE:
            hist_dirs.append(HIST_LOAD_DIR + '/rgb32_hist')
            hist_load_dims.append(32768)

        HIST_VEC_SHAPE = sum(hist_load_dims)

        print('IN_HISTS: ' + str(IN_HIST_MODE))
        print('HISTS: ' + str(len(hist_dirs)))
        print('       DIM\tDIR')

        for i in range(len(hist_dirs)):
            print('       ' + str(hist_load_dims[i]) + '\t' + hist_dirs[i])

        print('Total dim: ' + str(HIST_VEC_SHAPE))

        #quit()
        #check_hists()
        #------ end of moved-from-main section

        # use 1st dir to derive the file list
        files = [y for x in os.walk(hist_dirs[0]) \
                   for y in glob.glob(os.path.join(x[0], '*.hist'))]
        #print('ff ' + files[1])
        all_files = len(files)
        print('All files in 1st [' + hist_dirs[0] + ']: ' + str(all_files))

        # delete the 1st dir-specific part, and turn into jpg-keys
        delstr = hist_dirs[0] + '/'
        trim_files = [w.replace(delstr, '') for w in list(files)]
        keys = [w.replace('hist', 'jpg') for w in trim_files]
        print('intersect keys=' + str(len(keys)) + ' FNAMES=' + str(len(FNAMES)))
        #print('k0 ' + keys[0] + ' FNAMES0 ' + FNAMES[0])

        keys = set(keys).intersection(set(FNAMES))
        if not BOTH and len(keys) == all_files:
            print('No filter: all files')
            sys.exit(1)
        if len(keys) == 0:
            print('Wrong filter: no files')
            sys.exit(1)
        trim_files = [w.replace('jpg', 'hist') for w in list(keys)]
        print('trimmed files: ' + str(len(trim_files)))

        print('Load hists, threads: ' + str(THREADS))
        with Pool(THREADS) as pool:
            for map_fname, hist in pool.map(load_it_hist, trim_files):
                #for map_fname, hist in pool.map(load_it_hist, l):
                HIST_FINAL_MAP[map_fname] = hist
                #hist_file_maps[model][map_fname] = hist
        end = time.time()
        print('loaded ' + str(len(hist_dirs)) + ' hist types in ' +
                str(datetime.timedelta(0, end-start)))
        print('HIST_FINAL_MAP size: ' + str(len(HIST_FINAL_MAP)))
        if len(HIST_FINAL_MAP) == 0:
            print('Histograms: nothing loaded to HIST_FINAL_MAP.')
            sys.exit(1)

    if DO_JPGV or DO_JPG:

        #---- moved from main/untested
        # parse IN_JPG_MODE

        JPG_DIM = None

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
            sys.exit(1)

        JPG_DIR = IMAGE_DESC_DIR + '/3_jpg/prun' + str(JPG_DIM)
        jpg_shape = (JPG_DIM, JPG_DIM, 3)

        if DO_JPGV:
            x = IN_JPG_MODE.split('_')
            img_vec_dir = JPG_DIR + '/vecs_' + x[0]

        if DO_JPG:
            print('JPG:   ' + JPG_DIR)
            print('JPG shape:   ' + str(jpg_shape))
        else:
            print('JPG vecs:   ' + img_vec_dir)

        #------------ end of moved-from-main/untested block

        n = str(len(FNAMES))

        if DO_JPG:
            print('Load ' + n + ' jpgs from ' + JPG_DIR +
                    ', threads: ' + str(THREADS))
        if DO_JPGV:
            print('Load ' + n + ' jpg vecs/features from ' +
                     img_vec_dir + ', threads: ' + str(THREADS))

        with Pool(THREADS) as pool:
            for map_fname, jpg in pool.map(load_it_jpg, FNAMES):
                JPG_FINAL_MAP[map_fname] = jpg
        endt = time.time()
        if DO_JPGV:
            vec = next(iter(JPG_FINAL_MAP.values()))
            IMG_VEC_SHAPE = vec.shape
            print('-- img vector shape: ' + str(IMG_VEC_SHAPE))

        print('loaded ' + str(len(JPG_FINAL_MAP)) + ' in ' + \
                          str(datetime.timedelta(0, endt-start)))

tries = ['a.', 'b.', 'c.', 'd.', 'e.', 'f.', 'g.', 'h.', 'i.',
            'j.', 'k.', 'l.', 'm.', 'n.', 'a_sm.', 'b_sm.',
            'c_sm.', 'd_sm.', 'e_sm.', 'f_sm.', 'g_sm.',
            'h_sm.', 'i_sm.', 'j_sm.', 'k_sm.', 'l_sm.',
            'm_sm.', 'n_sm.'  ]

padding = 0

def set_batch():

    global BATCH_SIZE, N_BATCHES, padding

    n_pairs = (N_PICS * N_PICS)
    print('Pairs: ' + str(n_pairs))
    if n_pairs < 1000:
        if n_pairs % 2 == 0:
            print('Adjusting BATCH_SIZE down to 2')
            BATCH_SIZE = 2
        else:
            print('Adjusting BATCH_SIZE down to 1')
            BATCH_SIZE = 1

    print('batch size: ' + str(BATCH_SIZE))

    if OUT_MODE == 'DO_LR_VECS':
        n_pairs = N_PICS

    if n_pairs < 1000:
        if n_pairs % 2 == 0:
            print('Adjusting BATCH_SIZE down to 2')
            BATCH_SIZE = 2
        else:
            print('Adjusting BATCH_SIZE down to 1')
            BATCH_SIZE = 1

    expected_size = n_pairs
    overflow = n_pairs % BATCH_SIZE

    if overflow > 0:
        padding = BATCH_SIZE - overflow
        expected_size += padding
        #print('Overflow: ' + str(overflow) +
        # ' but NO pad ' + str(padding) +
        # ' STILL expected: ' + str(expected_size))


    N_BATCHES = math.ceil(expected_size / BATCH_SIZE)

    print('real_img Pics: ' + str(len(FNAMES)) + \
            ' pairs/expected: ' + str(n_pairs) + '/' + str(expected_size)  +
            ' BATCH_SIZE: ' + str(BATCH_SIZE) +
            ' batches: ' + str(N_BATCHES))
            #' padding: ' + str(padding) )

q_in = queue.Queue(maxsize=3)
q_out = queue.SimpleQueue()

def feed_input():

    print('feed_input pics ' + str(N_PICS))
    # + ' start/end ' + str(start_ix) + '/' + str(end_ix) + ' batch ' + str(BATCH_SIZE))

    abs_batch = BATCH_SIZE

    if DO_SIDE_VECS:
        vecdims = [abs_batch, side_vec_dim]
        vec1 = np.zeros((vecdims))
        vec2 = np.zeros((vecdims))

    '''
    if DO_DB:
        dbdims = [abs_batch, dbvec_dim]
        dbv1 = np.zeros((dbdims))
        dbv2 = np.zeros((dbdims))
    '''

    if DO_KWDS:
        kvdims = [abs_batch, KWDVEC_DIM]
        kv1 = np.zeros((kvdims))
        kv2 = np.zeros((kvdims))

    if DO_HIST:
        hvdims = [abs_batch]
        hvdims.extend(HIST_VEC_SHAPE)
        hv1 = np.zeros((hvdims))
        hv2 = np.zeros((hvdims))

    if DO_JPGV:
        jvdims = [abs_batch]
        jvdims.extend(IMG_VEC_SHAPE)
        #print('-- img vec dims ' + str(jvdims))
        jpg1 = np.zeros((jvdims))
        jpg2 = np.zeros((jvdims))

    elif DO_JPG:
        #jdims = [abs_batch, list(jpg_shape)]
        jdims = [abs_batch, JPG_DIM, JPG_DIM, 3]
        jpg1 = np.zeros((jdims))
        jpg2 = np.zeros((jdims))

    #labels = np.zeros(BATCH_SIZE) # used but ignored

    total = 0
    batch_tot = 0

    count = 0
    batches = 0
    missing = {}

    for i in range(N_PICS):

        #if i % 1000 == 0:
        #    print('--- i ' + str(i))
        fname1 = FNAMES[i]
        id1 = ID_MAP[fname1]
        #print('fname1 ' + str(i) + ': ' + FNAMES[i] + ' ->id ' + id1)

        if DO_SIDE_VECS:
            sv_l = side_vec_map[id1 + '_l']

        try:

            if DO_KWDS:
                kwd1 = kwdvec_map[id1]
            if DO_HIST:
                #print('XX ' + str(HIST_FINAL_MAP))
                hist1 = HIST_FINAL_MAP[fname1]
            if DO_JPGV or DO_JPG:
                jpgm1 = JPG_FINAL_MAP[fname1]

        except:
            missing.add(id1)
            continue

        if OUT_MODE == 'DO_LR_VECS':

            if DO_KWDS:
                kv1[count] = kwd1
                kv2[count] = kwd1
            if DO_HIST:
                hv1[count] = hist1
                hv2[count] = hist1
            if DO_JPGV or DO_JPG:
                jpg1[count] = jpgm1
                jpg2[count] = jpgm1

            count += 1
            total += 1

            if count == BATCH_SIZE:
                #print('HEWERERE')
                #sys.exit(1)
                #quit()
                batch_tot += 1
                list1 = []
                list2 = []
                if DO_KWDS:
                    list1.append(kv1)
                    list2.append(kv1)
                if DO_HIST:
                    list1.append(hv1)
                    list2.append(hv2)
                if DO_JPGV or DO_JPG:
                    list1.append(jpg1)
                    list2.append(jpg2)
                #print('yield1')
                #yield  list1 + list2, labels
                #print('-- DDDDDDDDDDDDDDDDDDDDDD ' + str(len(list1)) + '/' + str(len(list2)))
                #sys.exit(1)
                #quit()
                q_in.put(list1 + list2)
                batches += 1

                count = 0

                if DO_SIDE_VECS:
                    vec1 = np.zeros((vecdims))
                    vec2 = np.zeros((vecdims))

                '''
                if DO_DB:
                    dbv1 = np.zeros((dbdims))
                    dbv2 = np.zeros((dbdims))
                '''

                if DO_KWDS:
                    kv1 = np.zeros((kvdims))
                    kv2 = np.zeros((kvdims))

                if DO_HIST:
                    hv1 = np.zeros((hvdims))
                    hv2 = np.zeros((hvdims) )

                if DO_JPGV:
                    jpg1 = np.zeros((jvdims))
                    jpg2 = np.zeros((jvdims))
                elif DO_JPG:
                    jpg1 = np.zeros((jdims))
                    jpg2 = np.zeros((jdims))

        else:

            for j in range(N_PICS):
                # include i==j
                fname2 = FNAMES[j]
                id2 = ID_MAP[fname2]
                #print('---  ' + str(i) + ' j ' + str(j) + ' ' + fname2 + ' ' + id2)

                if DO_SIDE_VECS:
                    vec1[count] = sv_l
                    vec2[count] = side_vec_map[id2 + '_r']

                try:

                    if DO_KWDS:
                        kv1[count] = kwd1
                        kv2[count] = kwdvec_map[id2]
                    if DO_HIST:
                        hv1[count] = hist1
                        hv2[count] = HIST_FINAL_MAP[fname2]
                    if DO_JPGV or DO_JPG:
                        jpg1[count] = jpgm1
                        jpg2[count] = JPG_FINAL_MAP[fname2]

                except:

                    missing.add(id2)
                    continue

                count += 1
                total += 1

                if count == BATCH_SIZE:

                    batch_tot += 1
                    list1 = []
                    list2 = []

                    if DO_SIDE_VECS:

                        list1.append(vec1)
                        list2.append(vec2)

                    if DO_KWDS:
                        list1.append(kv1)
                        list2.append(kv2)
                    if DO_HIST:
                        list1.append(hv1)
                        list2.append(hv2)
                    if DO_JPGV or DO_JPG:
                        list1.append(jpg1)
                        list2.append(jpg2)
                    #print('yield1')
                    #yield  list1 + list2, labels
                    q_in.put(list1 + list2)
                    batches += 1

                    count = 0

                    if DO_SIDE_VECS:
                        vec1 = np.zeros((vecdims))
                        vec2 = np.zeros((vecdims))

                    '''
                    if DO_DB:
                        dbv1 = np.zeros((dbdims))
                        dbv2 = np.zeros((dbdims))
                    '''

                    if DO_KWDS:
                        kv1 = np.zeros((kvdims))
                        kv2 = np.zeros((kvdims))

                    if DO_HIST:
                        hv1 = np.zeros((hvdims))
                        hv2 = np.zeros((hvdims) )

                    if DO_JPGV:
                        jpg1 = np.zeros((jvdims))
                        jpg2 = np.zeros((jvdims))
                    elif DO_JPG:
                        jpg1 = np.zeros((jdims))
                        jpg2 = np.zeros((jdims))

    # round off last batch with repeats

    if count != 0  and  count < BATCH_SIZE:

        print('cases left over after batch ' + str(batch_tot) + \
                    ' got ' + str(count) + '/' + str(BATCH_SIZE) + \
                    ' totseen ' + str(total) +  \
                    ' -> Adding padding: ' + str(BATCH_SIZE-count))
                    #'\n' + str(pairs[0]))

        for i in range(count, BATCH_SIZE, 1):

            if DO_SIDE_VECS:
                vec1[i] = vec1[count-1]
                vec2[i] = vec2[count-1]

            if DO_KWDS:
                kv1[i] = kv1[count-1]
                kv2[i] = kv2[count-1]
            if DO_HIST:
                hv1[i] = hv1[count-1]
                hv2[i] = hv2[count-1]
            if DO_JPGV or DO_JPG:
                jpg1[i] = jpg1[count-1]
                jpg2[i] = jpg2[count-1]
        list1 = []
        list2 = []
        if DO_SIDE_VECS:
            list1.append(vec1)
            list2.append(vec2)

        if DO_KWDS:
            list1.append(kv1)
            list2.append(kv2)
        if DO_HIST:
            list1.append(hv1)
            list2.append(hv2)
        if DO_JPGV or DO_JPG:
            list1.append(jpg1)
            list2.append(jpg2)

        #print('Final batch cases: ' + str(len(list1[0])))
        #yield  list1 + list2
        q_in.put(list1 + list2)
        batches += 1

    #print('feed_input: cases ' + str(total) + ' batches ' + str(batches) + ' last ' + str(count))

    print('\nfeed_input loop i,j done final total/batches: ' \
                + str(total) + '/' + str(batches))
    if len(missing) > 0:
        print('\nMISSING:')
        print(missing)

def predict_batch(model):

    '''
    print('-- predict_batch wait..')
    while q_in.qsize() < 2:
        print('.', end='')
        time.sleep(1)
    print('-- predict_batch start')
    '''

    cases = 0

    for itct in range(N_BATCHES):

        try:
            item = q_in.get(block=True)
        except queue.Empty:
            print(f'-- predict_batch: inq done, cases {cases}')
            break
        if len(item) == 0:
            print('predict_batch: len is 0')
            sys.exit(1)
        #print('-- got ' + str(len(item)))

        itct += 1

        try:
            r = model.predict_on_batch(item)
        except Exception as e:
            print('predict_batch: unexpected Exception: ' + str(e))
            # TODO - kill all, exit hangs now.. maybe delete queue?
            sys.exit(1)

        if len(r) == 2:
            #print(' len/r0 ' + str(len(r[0])) + ' r0 ' + str(r[0]) + ' r1 ' + str(r[1]))
            l = len(r[0])
        else:
            l = len(r)

        cases += l

        if l != BATCH_SIZE:
            print('non-batch len: ' + str(l))
        if l != len(item[0]):
            print('preds != input: ' + str(l) + '/' + str(len(item[0])))

        q_out.put(r)

        q_in.task_done()


        '''
        if itct % 6000 == 0:
            print('.', flush=True)
        elif itct % 60 == 0:
            print('.', end='', flush=True)

        '''

    print('-- predict_batch done, did ' + str(itct) + ' cases ' + str(cases))


# concats/returns ndarray

def predict_vecs(model, outfile):

    feed = threading.Thread(target=feed_input)
    work = threading.Thread(target=predict_batch, args=(model,))

    feed.start()
    work.start()

    print('-- started feed and work')

    cases = 0

    preds = []

    twopct = math.ceil(N_BATCHES/50)
    print('[progress: .==2%]')

    for itct in range(N_BATCHES):
        try:
            item = q_out.get(block=True)
        except queue.Empty:
            #print(f'-- outq done')
            break

        preds.append(item)

        cases += len(item)

        if itct % twopct == 0:
            print('.', end='', flush=True)
        #print(f'-- outq got')
        #sys.exit(1)

    if itct == 0:
        print('predictit: None')
        return None

    #print('\n-- vecs[0][0] shape ' + str(preds[0][0].shape))
    x = np.concatenate(preds, axis=1)

    print('\n-- predict_vecs(model): ' \
            + ' iterations: ' + str(itct) \
            + ' len/preds ' + str(len(preds)) \
            + ' len/preds[0] ' + str(len(preds[0])) \
            + ' len/all/x ' + str(len(x)) \
            + ' cases ' + str(cases))

    preds.clear()
    del preds

    feed.join()
    work.join()

    write_lr_pic_vecs(outfile, x)
    del x 

def predict_write_bin(model, outfile):

    feed = threading.Thread(target=feed_input)
    work = threading.Thread(target=predict_batch, args=(model,))

    feed.start()
    work.start()

    print('-- started feed and work')

    cases = 0

    #preds = []

    npoutfile = outfile + '.np'
    if os.path.isfile(npoutfile):
        print('-- removing .np version')
        os.remove(npoutfile)

    twopct = math.ceil(N_BATCHES/50)
    print('[progress: .==2%]')

    #f32swap = np.dtype('float32').newbyteorder('S')
    # w/ swap here, would need to skip numpy header in java. TODO translate file now?
    marray = numpy.lib.format.open_memmap(npoutfile, mode='w+', shape=(N_PICS*N_PICS,), \
                dtype='float32')

    for itct in range(N_BATCHES):
        try:
            item = q_out.get(block=True)
        except queue.Empty:
            #print(f'-- outq done')
            break

        item = item.reshape((-1))

        if itct == N_BATCHES-1:
            item = np.resize(item, (len(item)-padding,))
 
        start = cases       
        cases += len(item)

        #print('-- shape ' + str(np.shape(item)))
        #exit(1)

        #item.tofile(fp)
        marray[start:cases] = item

        if itct % twopct == 0:
            print('.', end='', flush=True)
        #print(f'-- outq got')
        #sys.exit(1)

    print('-- endian-flip float32 to  ' + outfile)

    marray.byteswap().tofile(outfile)

    del marray

    print('-- remove original .np')
    os.remove(npoutfile)

    if itct == 0:
        print('predict_write_bin: itct==0')
        exit(1)

    print('\n-- predictit(model): ' \
            + ' iterations: ' + str(itct) \
            + ' cases ' + str(cases))

    feed.join()
    work.join()


def write_lr_pic_vecs(outfile, pred):

    print('-- write_lr_pic_vecs: ' + outfile)

    if OUT_MODE != 'DO_LR_VECS':
        print('writeRLPicVecs err: fix')
        sys.exit(1)

    if os.path.isfile(outfile):
        print('??? already written ' + outfile)
        return

    n_pics = len(FNAMES)

    if len(pred[0]) < n_pics: # might [?] be padded
        print('ERR: FNAMES: ' + str(n_pics) + ' pred: ' + str(len(pred[0])))
        sys.exit(1)

    print('Writing: ' + outfile + ' pics: ' + str(n_pics))

    start = time.time()
    ct = 0
    try:
        with open(outfile, 'w', encoding='utf-8') as out:

            for i in range(n_pics):

                fname = FNAMES[i]
                idx = ID_MAP[fname]

                out.write(idx + ' l ')
                for vval in pred[0][i]:
                    out.write(str(vval) + ' ')
                out.write('\n')
                out.write(idx + ' r ')
                for vval in pred[1][i]:
                    out.write(str(vval) + ' ')
                out.write('\n')

        print('Written: ' + outfile)
    except OSError as inst:
        print('OSError writing ' + outfile + ': ' +
                str(type(inst)) + ' ' + str(inst.args))
        sys.exit(1)

    endt = time.time()
    print('\nWritten: ' + outfile + ' (' + str(ct) + ') in ' + \
                        str(datetime.timedelta(0, endt-start)))
    #avg_aa = eq_sum / end
    #avg_all = np.mean(pred)
    #print('Avg of AA pairs, all pairs, %: ' + str(avg_aa) + ' ' +
    #            str(avg_all) + ' ' + str((100 * avg_aa)/avg_all))

#----~~~~~~----------__~~~~~_--- START


load_pic_list()

if os.path.isdir(MATTER):

    fields = MATTER.split('_')

    #dirList = sorted(os.listdir(DIR))
    mglob = MATTER + '/*.h5'
    print('Models: ' + mglob + ' BATCH_SIZE: ' + str(BATCH_SIZE))
    MODEL_LIST = sorted(glob.glob(mglob))
    # output is one file per model
    if OUT_MODE == 'DO_BIN':
        DONE_LIST = glob.glob(MATTER + '/m_*.pairs')
    elif OUT_MODE == 'DO_LR_VECS':
        DONE_LIST = glob.glob(MATTER + '/*.vecs')
    else:
        print('HOW to decide DONE_LIST for dir for ' + OUT_MODE + '?')
        sys.exit(1)

else:

    # MATTER is a file

    if MATTER.endswith('.h5'):

        MODEL_LIST.append(MATTER)
        if OUT_MODE == 'DO_BIN':
            DONE_LIST = glob.glob(MATTER.replace('h5', 'pairs'))
        elif OUT_MODE == 'DO_LR_VECS':
            DONE_LIST = glob.glob(MATTER.replace('h5', 'vecs'))
        else:
            print('HOW to decide DONE_LIST for ' + OUT_MODE + '?')
            sys.exit(1)
    else:

        read_list_file()


print('Models: ' + str(len(MODEL_LIST)) + ' done: ' + str(len(DONE_LIST)))
if len(MODEL_LIST) == len(DONE_LIST):
    print('All already done: ' + str(DONE_LIST))
    sys.exit(0)

if len(DONE_LIST) > 0:
    MODEL_LIST = [x for x in MODEL_LIST if x not in DONE_LIST]

TOT_SZ = 0
MAX_SZ = 0
for model_file in MODEL_LIST:
    sz = os.path.getsize(model_file)
    TOT_SZ = TOT_SZ + sz
    if sz > MAX_SZ:
        MAX_SZ = sz

print('-- model sizes tot/max/avg MB: ' + \
                str(int(TOT_SZ/(1024*1024))) + '/' + \
                str(int(MAX_SZ/(1024*1024))) + '/' + \
                str(int(TOT_SZ/(1024*1024*len(MODEL_LIST)))))

#sys.exit(1)

def load_cached_data():

    global IMG_VEC_SHAPE, HIST_VEC_SHAPE, ID_MAP, HIST_FINAL_MAP, JPG_FINAL_MAP

    pick = IMAGE_DESC_DIR + '/HIST_JPGV_VGG16.dil'

    print('-- load_cached_data: ' + pick)

    if not os.path.exists(pick):
        print('-- Nope on load_cached_data: ' + pick)
        return False

    print('-- load ' + pick)
    print(f'   ({int(os.stat(pick).st_size / (1024 * 1024 * 1024))} GB)')

    print('   expecting N_PICS ' + str(N_PICS) + \
                ' mtime is ' +
                 str(datetime.date.fromtimestamp(os.path.getmtime(pick))))


    #pick = 'HIST_JPGV_VGG16.pkl'
    #import pickle
    #FNAMES2, v_fnames, h_fnames, ID_MAP, HIST_FINAL_MAP, JPG_FINAL_MAP =
    #       pickle.load(open(pick, 'rb'))

    import dill
    # fnames are for checking
    with open(pick, 'rb') as inp:
        fnames2, v_fnames, h_fnames, ID_MAP, HIST_FINAL_MAP, JPG_FINAL_MAP = \
                dill.load(inp)
    if BOTH:
        if FNAMES != fnames2:
            print('-- ' + str(len(fnames2)) + ' pics in ' + pick)
            print('-- Exiting: BOTH v/h, and FNAMES != (' + str(len(FNAMES)) + '/' + str(len(fnames2)) + ')')
            sys.exit(1)
    elif HORIZ:
        if FNAMES != h_fnames:
            print('-- Exiting: HORIZ, and FNAMES !=')
            sys.exit(1)
    elif not HORIZ:
        if FNAMES != v_fnames:
            print('-- Exiting: Vertical, and FNAMES !=')
            sys.exit(1)

    print('-- loaded ' + pick + '  FNAMES: ' + str(N_PICS))

    vecx = next(iter(JPG_FINAL_MAP.values()))
    IMG_VEC_SHAPE = vecx.shape
    print('-- img vector shape: ' + str(IMG_VEC_SHAPE))

    vecx = next(iter(HIST_FINAL_MAP.values()))
    HIST_VEC_SHAPE = vecx.shape
    print('-- hist vector shape: ' + str(HIST_VEC_SHAPE))

    return True

def load_data():

    print('-- load data, IN_MODE ' + IN_MODE)

    loaded = False

    if IN_MODE == 'HIST_JPGV':
        print('-- HIST_JPGV: ' + IN_JPG_MODE)
        if 'VGG16' in IN_JPG_MODE:
            print('-- load data: got VGG16 so try cached')
            loaded = load_cached_data()

    if not loaded:
        # load all input data: histograms, maybe kwd vecs
        print('-- load data: from scratch')
        load_preproc()

load_data()
#exit(1)

set_batch()

if len(MODEL_LIST) == 0  or  MODEL_LIST[0].endswith('*.h5'):
    if len(MODEL_LIST) == 1:
        print('*** No models match: ' + MODEL_LIST[0])
    else:
        print('*** No models!')
    sys.exit(1)

# FORK and split the model list

def get_memory_free_B(gpu_index):
    pynvml.nvmlInit()
    handle = pynvml.nvmlDeviceGetHandleByIndex(int(gpu_index))
    mem_info = pynvml.nvmlDeviceGetMemoryInfo(handle)
    return mem_info.free * 1024 * 1024

#for i in range(3):
#    print('-- ' + str(get_memory_free_B(i)))

# allow for parallel on gpu
MY_MODELS = MODEL_LIST

if RUN_GPU:

    print('-- GPU ' + str(GPU))

    GPU_SZ = get_memory_free_B(GPU)
    GPU_MB = GPU_SZ / (1024*1024)
    # MAX_MODELS_GPU limited by main mem for N^2 output array
    MAX_MODELS_GPU = 8

    print('-- GPU_SZ ' + str(GPU_MB) + ' MB,  MAX_MODELS_GPU: ' + str(MAX_MODELS_GPU))

    print('-- model sizes tot/max/avg MB: ' + \
                str(int(TOT_SZ/(1024*1024))) + '/' + \
                str(int(MAX_SZ/(1024*1024))) + '/' + \
                str(int(TOT_SZ/(1024*1024*len(MODEL_LIST)))))

    if IN_MODE == 'HIST_JPGV':
        # 11G GPU.. TODO need shared mem for lots-of-pics .dil
        #           so 1 for now? or consider dil size.
        NPROC = 1
    elif TOT_SZ < GPU_SZ  and  len(MODEL_LIST) < MAX_MODELS_GPU:
        print('-- Fits in 1 round!')
        NPROC = len(MODEL_LIST)
    else:
        NPROC = int(GPU_SZ / MAX_SZ)
        if NPROC == 0:
            NPROC = 1
        elif NPROC > MAX_MODELS_GPU:
            NPROC = MAX_MODELS_GPU

    mem_pct = psutil.virtual_memory().percent
    print('-- system virtual memory ' + str(mem_pct) + '% - NPROC ' + str(NPROC))
    if NPROC > 2:
        if mem_pct > 90:
            print('-- mem>80% ' + str(mem_pct) + '% - NPROC ' + str(NPROC) + ' -> 2')
            NPROC = 2


    print('-- forking ' + str(NPROC-1) + ' processes')
    me = 0
    for i in range(NPROC-1):
        if me == 0:
            if os.fork() == 0:
                me = i + 1

    MY_MODELS = []

    if me == 0:
        print('-- handing out models to ourselves: ' + str(len(MODEL_LIST)))

    for i in range(0, len(MODEL_LIST) ):
        if i % NPROC == me:
            MY_MODELS.append(MODEL_LIST[i])  # NPROC1/evens

    print('-- proc' + str(me) + '/' + str(os.getpid()) + ': ' + str(MY_MODELS))


#####  1 = INFO messages are not printed
#####  2 = INFO and WARNING messages are not printed
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '1'

from tensorflow.keras import backend as K
from tensorflow.keras.models import Model, load_model

if RUN_CPU:

    os.environ['CUDA_VISIBLE_DEVICES'] = ''

elif RUN_GPU:

    print('== GPU: ' + GPU)
    os.environ['CUDA_VISIBLE_DEVICES'] = GPU
    #os.environ['TF_GPU_ALLOCATOR'] = 'cuda_malloc_async'
    #os.environ["TF_CPP_VMODULE"]="gpu_process_state=10,gpu_cudamallocasync_allocator=10"

    import tensorflow as tf
    gpu_options = tf.compat.v1.GPUOptions(per_process_gpu_memory_fraction=0.2,allow_growth=True)
    tf.compat.v1.Session(config=tf.compat.v1.ConfigProto(gpu_options=gpu_options))

if DO_JPG:

    from tensorflow.keras.preprocessing import image

    if 'VGG16' in IN_JPG_MODE:
        from tensorflow.keras.applications.vgg16 import preprocess_input
        from tensorflow.keras.applications.vgg16 import VGG16
    elif 'VGG19' in IN_JPG_MODE:
        from tensorflow.keras.applications.vgg19 import preprocess_input
        from tensorflow.keras.applications.vgg19 import VGG19
    elif 'DenseNet' in IN_JPG_MODE:
        from tensorflow.keras.applications.densenet import preprocess_input
        from tensorflow.keras.applications import DenseNet121
        from tensorflow.keras.applications import DenseNet169
    elif 'EfficientNetB7' in IN_JPG_MODE:
        from tensorflow.keras.applications.efficientnet import preprocess_input
        from tensorflow.keras.applications.efficient import EfficientNetB7


for model_file in MY_MODELS:

    outfile = out_file_name(model_file)

    #print('-- checking outfile: ' + outfile)

    x = glob.glob(outfile.replace('/m_', '/*m_'))
    if len(x) > 0:
        print('-- Already done/skipping: ' + outfile + ' ' + str(x))
        continue

    print('-- Loading model [' + model_file + ']')
    if 'nathan' in model_file:
        model = load_model(model_file, custom_objects={'Nathan': Nathan})
    else:
        model = load_model(model_file)

    #model.summary()

    if OUT_MODE == 'DO_LR_VECS':
        print('Making internal model w/ l,r vec outputs')
        int_model = Model(model.input,
                [model.get_layer('side_left').output,
                model.get_layer('side_right').output])
        #int_model.summary()
        model = int_model

    #model.summary()

    #half_ix = int(N_PICS/2)
    #print('half ' + str(half_ix) + ' N_PICS ' + str(N_PICS))

    print('predict: ' + model_file)
    start = time.time()

    if OUT_MODE == 'DO_LR_VECS':
        predict_vecs(model, outfile)
    elif OUT_MODE == 'DO_BIN':
        predict_write_bin(model, outfile)
    else:
        print('ERROR: This is an empty hole: OUT_MODE=' + OUT_MODE)
        sys.exit(1)

    endt = time.time()
    print('\nCalced ' + model_file + ' in ' +
                str(datetime.timedelta(0, endt-start)) +
                ' batch_size ' + str(BATCH_SIZE))

    K.clear_session()
    del model

print('=== ppred.py Done: GPU=' + str(GPU) + ': ' + str(datetime.datetime.now()))
sys.exit(0)
