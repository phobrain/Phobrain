#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === pred.py - batch pair predictions


'''Run model on pair data.

'''
# --------------------- Per-Machine config

import socket
import os

PHOBRAIN_LOCAL = os.environ.get('PHOBRAIN_LOCAL')

if PHOBRAIN_LOCAL is None:
    PHOBRAIN_LOCAL = '~/phobrain_local'
    print('(No env PHOBRAIN_LOCAL: using ' + PHOBRAIN_LOCAL)

PHOBRAIN_LOCAL = os.path.expanduser(PHOBRAIN_LOCAL) + '/'

real_img = PHOBRAIN_LOCAL + '/real_img_orient'

HOST = socket.gethostname()
print('== Config for host: ' + HOST + ' using ' + real_img)

if HOST == 'phobrain-gpu1':
    BASE = '/mnt/ssd/reep/home'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
else:
    BASE = '~'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'

BASE = os.path.expanduser(BASE) + '/'

print('== BASE is ' + BASE)
print('== real_image is ' + real_img)

THREADS = 16

# -----------------------------------------
# Main run config

import sys
import time
import datetime
import string

# force run mode vs tf.keras default
RUN_CPU = False
RUN_GPU = False
GPU = None

IN_MODE = 'UNSET, you heartless animal'
OUT_MODE = 'UNSET, you heartless animal'

if len(sys.argv) < 4:
    print('usage: ' + sys.argv[0] + ' [-cpu|-gpu n] <-v|-h> -ifmt <JvH|JH|V|..> -ofmt <vecs|bin|all|top> <model_file_list|dir>')
    print('      Usually only -ofmt <vecs|bin> is used in production.')
    print('      -bin is then processed by predtool.py.')
    exit(1)

Horiz = None
Both = False

i = 0
while i < len(sys.argv):
    i += 1
    if sys.argv[i][0] != '-':
        print('break parse args on ' + sys.argv[i])
        break

    if sys.argv[i] == '-cpu':
        RUN_CPU = True
    if sys.argv[i] == '-gpu':
        RUN_GPU = True
        i += 1
        if i == len(sys.argv):
            print('usage: expected: -gpu <num>')
            exit(1)
        GPU = sys.argv[i]
    elif sys.argv[i] == '-hb' or sys.argv[i] == '-vb':
        Both = True
    elif sys.argv[i] == '-h':
        Horiz = True
    elif sys.argv[i] == '-v':
        Horiz = False
    elif sys.argv[i] == '-ifmt':
        i += 1
        if i == len(sys.argv):
            print('usage: expected: -ifmt <type>')
            exit(1)

        # JPG_ needs to have '_' to distinguish JPGV

        tryna_mode = sys.argv[i]
        if tryna_mode == 'JvH':
            IN_MODE = 'JPGV_HIST'
        elif tryna_mode == 'JH':
            IN_MODE = 'JPG_HIST'
        elif tryna_mode == 'V':
            IN_MODE = 'VECS'
            i += 1
            if i == len(sys.argv):
                print('usage: expected: -ifmt V <vecs_flist_file>')
                exit(1)
            vec_list_file = sys.argv[i]

            print('== -ifmt V: vec_list_file: ' + vec_list_file)

        elif tryna_mode == 'H':
            print('Legacy IN_MODE? "H" is not Horiz here, V is for VECS')
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
            exit(1)
        tryna_mode = sys.argv[i]
        if tryna_mode == 'vecs':
            OUT_MODE = 'DO_LR_VECS'
        elif tryna_mode == 'bin':
            OUT_MODE = 'DO_BIN'
        elif tryna_mode == 'all':
            print('== Note: -ofmt <all|top> normally done in predtool.py from -ofmt bin')
            OUT_MODE = 'DO_ALL'
        elif tryna_mode == 'top':
            print('== Note: -ofmt <all|top> normally done in predtool.py from -ofmt bin')
            OUT_MODE = 'DO_TOP'
        else:
            print('Legacy OUT_MODE? Trying it verbatim: ' + tryna_mode)
            OUT_MODE = tryna_mode

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
        # superseded in production by predtool.py on DO_BIN files:
        #
        # DO_ALL: output is symmetric pairs floating point
        #         <canonical order of id1,id2, N(N-1)/2 rows>
        #         id1 id2 val_1_2 val_2_1
        # DO_TOP: output is list of pic1,pic2, val floating point
        #         id1 id2 val_1_2
        # DO_TOP_FROM_PAIRS: output is list of pic1,pic2, val floating point
        #         id1 id2 val_1_2

if RUN_CPU and RUN_GPU:
    print('Oops: -cpu incmpatible with -gpu')
    exit(1)

# PRINT_QUEUE 
#  True for [id1 id2 val12 val21] files on DO_ALL
#  False for raw pred in .npy format,
#        which can be easily turned to DO_ALL 
#        or most-importantly averaged first
# 
PRINT_QUEUE = True
if OUT_MODE in ['DO_LR_VECS', 'DO_BIN']:
    PRINT_QUEUE = False

if PRINT_QUEUE:
    print('== Warning: using ' + OUT_MODE + ' causes lots more mem use ' +
                ' due to using a print queue, with no savings in time,' +
                ' only convenience.')

sub = 1
if sys.argv[len(sys.argv)-1] == '&':
    sub = 2

arglen = len(sys.argv) - sub

if i != arglen:
    print(sys.argv[0] + ': Usage: after args, need 1 dir or file to proc')
    print('debug: args: ' + str(len(sys.argv)) + '  i: ' + str(i))
    print('sys.argv: ' + str(sys.argv))
    exit(1)

MATTER = sys.argv[i]

# --- args parsed

DO_JPGV = False
if 'JPGV_' in IN_MODE:
    DO_JPGV = True

DO_JPG = False
if 'JPG_' in IN_MODE:
    DO_JPG = True

IN_JPG_MODE = ''

if DO_JPGV:

    if not os.path.isdir(MATTER):
        print('DO_JPGV: Not a dir: ' + MATTER)
        print('    - need dir with models using same feature vecs')
        exit(1)

    if 'VGG19' in MATTER:
        IN_JPG_MODE = 'VGG19_224'
    elif 'VGG16' in MATTER:
        IN_JPG_MODE = 'VGG16_224'
    elif 'DenseNet121' in MATTER:
        IN_JPG_MODE = 'DenseNet121_224'
    elif 'MobileNetV2' in MATTER:
        IN_JPG_MODE = 'MobileNetV2_224'
    elif 'EfficientNetB7' in MATTER:
        IN_JPG_MODE = 'EfficientNetB7_600'
    else:
        print('Dir: expected one of <VGG19|VGG16|DenseNet121|EfficientNetB7> in path:')
        print('\t' + MATTER)
        exit(1)

if DO_JPG:
    IN_JPG_MODE = 'VGG19_224'
 
print('============ START ' + str(datetime.datetime.now()))

print('== REAL_IMG ' + real_img)
print('== IN_MODE: ' + IN_MODE)
print('== OUT_MODE: ' + OUT_MODE)
print('== MATTER: ' + MATTER)
if Both:
    print('== BOTH h, v')
else:
    print('== Horiz ' + str(Horiz))
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

elif OUT_MODE == 'DO_ALL':

    print('Calcing all pairs')

elif OUT_MODE == 'DO_TOP':

    PAIR_LIMIT = 25
    print('Calcing top ' + str(PAIR_LIMIT))

elif OUT_MODE == 'DO_TOP_FROM_PAIRS':

    # how many pairs per pic per side
    PAIR_LIMIT = 50
    print('Calcing top ' + str(PAIR_LIMIT) + ' from pairlist')

else:
    print('OUT_MODE needs fix: ' + OUT_MODE)
    exit(1)

from tensorflow.keras import backend as K

if RUN_CPU:

    os.environ['CUDA_VISIBLE_DEVICES'] = ''
    '''
    import tensorflow as tf
    config = tf.ConfigProto(intra_op_parallelism_threads=14, 
                        inter_op_parallelism_threads=14, 
                        allow_soft_placement=True)

    session = tf.Session(config=config)
    K.set_session(K.tf.Session(config=K.tf.ConfigProto(intra_op_parallelism_threads=14, inter_op_parallelism_threads=14))) 
    File "./pred.py", line 275, in <module>
    K.set_session(K.tf.Session(config=K.tf.ConfigProto(intra_op_parallelism_threads=14, inter_op_parallelism_threads=14))) 
AttributeError: module 'tensorflow.keras.backend' has no attribute 'set_session'
    import tensorflow as tf
    tf.set_session(tf.Session(config=tf.ConfigProto( \
                    intra_op_parallelism_threads=10)))
    '''

elif RUN_GPU:

    print('== GPU: ' + GPU)
    os.environ['CUDA_VISIBLE_DEVICES'] = GPU
    #os.environ['TF_GPU_ALLOCATOR'] = 'cuda_malloc_async'
    #os.environ["TF_CPP_VMODULE"]="gpu_process_state=10,gpu_cudamallocasync_allocator=10"

    import tensorflow as tf
    gpu_options = tf.compat.v1.GPUOptions(per_process_gpu_memory_fraction=0.2,allow_growth=True)
    tf.compat.v1.Session(config=tf.compat.v1.ConfigProto(gpu_options=gpu_options))

# inputs models trained with

DO_SIDE_VECS = False

if 'VECS' == IN_MODE:

    DO_SIDE_VECS = True

    side_vec_files = []
    side_vec_dims = []

    # e.g. 
    #  m_h_model_pn_80_87_bEen_200_1_3_3_ki_0_VGG19_224_nathan_lrv_12_SGD.vecs

    print('Reading vec list file: ' + vec_list_file)

    with open(vec_list_file) as fp:
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

DO_HIST = False

if 'HIST' in IN_MODE:

    DO_HIST = True

    # concatted hists divided by sum
    #NORM_HIST_TO_MAX = False
    NORM_HIST_TO_MAX = True
    if NORM_HIST_TO_MAX:
        print('== NORMing histos to max')

    hist_dirs = []
    hist_load_dims = []

    # in order of size

    if 'SAT48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/s48_hist')
        hist_load_dims.append(48)

    if 'GREY128' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/grey_hist')
        hist_load_dims.append(128)

    if 'SAT128' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/s128_hist')
        hist_load_dims.append(128)

    if 'HS24' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/hs24_hist')
        hist_load_dims.append(576)

    if 'RGB12' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb12_hist')
        hist_load_dims.append(1728)

    if 'HS48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/hs48_hist')
        hist_load_dims.append(2304)

    if 'SV48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/sv48_hist')
        hist_load_dims.append(2304)

    if 'RGB24' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb24_hist')
        hist_load_dims.append(13824)

    if 'RGB32' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb32_hist')
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

    jpg_dir = BASE + '/image_desc/3_jpg/prun' + str(JPG_DIM)
    jpg_shape = (JPG_DIM, JPG_DIM, 3)

    if DO_JPGV:
        x = IN_JPG_MODE.split('_')
        img_vec_dir = jpg_dir + '/vecs_' + x[0]

    if DO_JPG:
        print('JPG:   ' + jpg_dir)
        print('JPG shape:   ' + str(jpg_shape))
    else:
        print('JPG vecs:   ' + img_vec_dir)

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


NATHAN = True
#NATHAN = False

# -----------------------------------------


import numpy as np
import math

from multiprocessing import Pool, Process, Queue
from threading import Thread

import os
import re
import glob

if NATHAN:
    sys.path.append('..')
    from nathan import Nathan

print('exec: ' + str(sys.executable))
#sys.path = [ '/project', '/usr/lib/python3.7', '/usr/lib/python3.7/lib-dynload', '/usr/local/lib/python3.7/dist-packages', '/usr/lib/python3/dist-packages']
print('path: ' + str(sys.path))


from tensorflow.keras.models import Model, load_model

#MAG = 1.0e10


# how many to chunk for each pass of the engine
#batch_size = 64
#batch_size = 512
#batch_size = 1024
#batch_size = 2048
# VEC
# 3xV7->21: 150000 is best
batch_size = 150000
#batch_size = 8192
#batch_size = 81920

if DO_JPG:
    batch_size = 64
elif DO_JPGV:
    batch_size = 666

#print('TODO - batch_size forced to 64')
#batch_size = 64

fnames = []
id_map = {}
n_pics = 0

def load_pic_list():

    global fnames, id_map, n_pics

    if Both:
        print('Read pic list/H+V: ' + real_img)
    elif Horiz:
        print('Read pic list/H: ' + real_img)
    else:
        print('Read pic list/V: ' + real_img)

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
            if Both or Horiz == (torient == 'f'):
                fnames += [ fname ]
                id_map[fname] = procId(fname)
                
                #if fname =='1/img03a.jpg':
                #   print('mapped ' + fname)

    n_pics = len(fnames)
    print('Pics: ' + str(n_pics))
    if n_pics == 0:
        print('NO PICS, Horz=' +str(Horiz))
        exit(1)

def vecIdFile(fname):

    id = procId(fname)

    return img_vec_dir + '/' + id

def checkHists():
    end = len(fnames)
    print('Checking hists for fnames from real_img: ' + str(end))
    error = False
    for i in range(end):
        fname = fnames[i]
        try:
            hist = hist_final_map[fname]
        except KeyError:
            print('-- ' + fname)
            error = True

    if error:
        print('bye!')
        exit(1)




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

convolute = False

def load_nums(fname, target_size, is_histogram):
    global hist_sum

    ct = 0
    data = np.loadtxt(fname)
    if len(data) != target_size:
        print('Dim mismatch: ' + fname + 'exp/actual ' + 
                str(target_size) + '/' + str(len(data)))
        exit(1)

    #if not is_histogram:
    #    data *= 0.5;
    #    t = np.sum(data)
    #    if t > 1:
    #        data /= t

    if is_histogram:
        #hist_sum += data
        if convolute:
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

                '''
                if id not in targets:
                    continue
                '''

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
             
            '''   
            if ct != n_pics * 2:
                print("Load: bad pic count: ' + str(ct) + ' expected ' + \
                                                str(n_pics * 2))
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

#jpg_file_map = {}
#hist_file_map = {}

#hist_file_maps = []
hist_final_map = {}
jpg_final_map = {}

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
    #if len(x) != hist_input_dim:
    #    print('Bad ' + str(len(x)))
    #    quit()

    map_fname = hist_fname.replace('.hist', '.jpg')

    return map_fname, x
    # load_nums(model, hist_fname, True)
    #doneq.put([map_fname, load_hist(hist_fname)])


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

#hist_sum = np.zeros(load_dim)
#def norm_it(arg):
#    map_fname, hist = arg
#    return map_fname, hist - hist_sum


def load_preproc():

    global img_vec_shape

    start = time.time()

    if DO_SIDE_VECS:
        load_side_vecs()

    if DO_KWDS:
        load_kwdvecs()

    if DO_HIST:
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
        print('intersect keys=' + str(len(keys)) + ' fnames=' + str(len(fnames)))
        #print('k0 ' + keys[0] + ' fnames0 ' + fnames[0])

        keys = set(keys).intersection(set(fnames))
        if not Both and len(keys) == all_files:
            print('No filter: all files')
            exit(1)
        if len(keys) == 0:
            print('Wrong filter: no files')
            exit(1)
        trim_files = [w.replace('jpg', 'hist') for w in list(keys)]
        print('trimmed files: ' + str(len(trim_files)))

        '''
        #print('KK ' + keys[0])
        #for key in keys:
        #    #print('k ' + key + ' hfn ' + histfname)
        #    id_map[key] = procId(key)
        #print('Val ' + str(hist_file_map))
        it = dict(zip(trim_files, keys))
        pool = Pool(processes=THREADS)
        print('Load hists, threads: ' + str(THREADS))
        for map_fname, hist in pool.map(load_hist, it.iteritems()):
            hist_file_map[map_fname] = hist
        pool.close()
        pool.terminate()
        pool.join()
        endt = time.time()
        print('loaded ' + str(len(hist_file_map)) + ' in ' + \
                          str(datetime.timedelta(0, endt-start)))
        '''

        #new---
        pool = Pool(processes=THREADS)
        print('Load hists, threads: ' + str(THREADS))
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
        print('JPG Files: ' + str(len(fnames)) + ' in ' + jpg_dir)
        n = str(len(fnames))
        if DO_JPGV:
            print('Load ' + n + ' jpg features, threads: ' + str(THREADS))
        else:
            print('Load ' + n + ' jpgs, threads: ' + str(THREADS))

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
    fname = string.replace(fname, 'jpg', 'hist')
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

import threading, queue

q_in = queue.Queue(maxsize=3)
q_out = queue.SimpleQueue()

def feedInput():

    print('feedInput pics ' + str(n_pics))
    # + ' start/end ' + str(start_ix) + '/' + str(end_ix) + ' batch ' + str(batch_size))

    abs_batch = batch_size

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
        kvdims = [abs_batch, kwdvec_dim]
        kv1 = np.zeros((kvdims)) 
        kv2 = np.zeros((kvdims)) 

    if DO_HIST:
        hvdims = [abs_batch, hist_input_dim]
        hv1 = np.zeros((hvdims)) 
        hv2 = np.zeros((hvdims)) 

    if DO_JPGV:
        jvdims = [abs_batch]
        jvdims.extend(img_vec_shape)
        #print('-- img vec dims ' + str(jvdims))
        jpg1 = np.zeros((jvdims))
        jpg2 = np.zeros((jvdims))

    elif DO_JPG:
        #jdims = [abs_batch, list(jpg_shape)]
        jdims = [abs_batch, JPG_DIM, JPG_DIM, 3]
        jpg1 = np.zeros((jdims))
        jpg2 = np.zeros((jdims))

    #labels = np.zeros(batch_size) # used but ignored

    total = 0
    batch_tot = 0

    pred = None

    ct = 0
    batches = 0

    for i in range(n_pics):
        #if i % 1000 == 0:
        #    print('--- i ' + str(i))
        fname1 = fnames[i]
        id1 = id_map[fname1]
        #print('fname ' + str(i) + ': ' + fnames[i] + ' ->id ' + id1)

        if DO_SIDE_VECS:
            sv_l = side_vec_map[id1 + '_l']

        if DO_KWDS:
            kwd1 = kwdvec_map[id1]
        if DO_HIST:
            #print('XX ' + str(hist_final_map))
            hist1 = hist_final_map[fname1]
        if DO_JPGV or DO_JPG:
            jpgm1 = jpg_final_map[fname1]

        if OUT_MODE == 'DO_LR_VECS':

            if DO_KWDS:
                kv1[ct] = kwd1
                kv2[ct] = kwd1
            if DO_HIST:
                hv1[ct] = hist1
                hv2[ct] = hist1
            if DO_JPGV or DO_JPG:
                jpg1[ct] = jpgm1
                jpg2[ct] = jpgm1

            ct += 1
            total += 1

            if ct == batch_size:
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
                q_in.put(list1 + list2)
                batches += 1

                ct = 0

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

            for j in range(n_pics):
                # include i==j
                #print('---  ' + str(i) + ' j ' + str(j))
                fname2 = fnames[j]
                id2 = id_map[fname2]

                if DO_SIDE_VECS:
                    vec1[ct] = sv_l
                    vec2[ct] = side_vec_map[id2 + '_r']

                if DO_KWDS:
                    kv1[ct] = kwd1
                    kv2[ct] = kwdvec_map[id2]
                if DO_HIST:
                    hv1[ct] = hist1
                    hv2[ct] = hist_final_map[fname2]
                if DO_JPGV or DO_JPG:
                    jpg1[ct] = jpgm1
                    jpg2[ct] = jpg_final_map[fname2]

                ct += 1
                total += 1

                if ct == batch_size:
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

                    ct = 0

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
    if ct != 0  and  ct < batch_size:
        print('cases left over after batch ' + str(batch_tot) + \
                    ' got ' + str(ct) + '/' + str(batch_size) + \
                    ' totseen ' + str(total) +  \
                    ' -> Adding padding: ' + str(batch_size-ct))
                    #'\n' + str(pairs[0]))
        for i in range(ct, batch_size, 1):

            if DO_SIDE_VECS:
                vec1[i] = vec1[ct-1]
                vec2[i] = vec2[ct-1]

            if DO_KWDS:
                kv1[i] = kv1[ct-1]
                kv2[i] = kv2[ct-1]
            if DO_HIST:
                hv1[i] = hv1[ct-1]
                hv2[i] = hv2[ct-1]
            if DO_JPGV or DO_JPG:
                jpg1[i] = jpg1[ct-1]
                jpg2[i] = jpg2[ct-1]
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

    #print('feedInput: cases ' + str(total) + ' batches ' + str(batches) + ' last ' + str(ct))

    print('\nfeedInput loop i,j done final total/batches: ' \
                + str(total) + '/' + str(batches))

def predictBatch(model):

    '''
    print('-- predictBatch wait..')
    while q_in.qsize() < 2:
        print('.', end='')
        time.sleep(1)
    print('-- predictBatch start')
    '''

    cases = 0

    for itct in range(n_batches):

        try:
            item = q_in.get(block=True)
        except queue.Empty:
            print(f'-- predictBatch: inq done, cases {cases}')
            break
        if len(item) == 0:
            print('predictBatch: len is 0')
            exit(1)
        #print('-- got ' + str(len(item)))

        itct += 1

        r = model.predict_on_batch(item)
        if len(r) == 2:
            #print(' len/r0 ' + str(len(r[0])) + ' r0 ' + str(r[0]) + ' r1 ' + str(r[1]))
            l = len(r[0])
        else:
            l = len(r)

        cases += l

        if l != batch_size:
            print('non-batch len: ' + str(l))
        if l != len(item[0]):
            print('preds != input: ' + str(l) + '/' + str(len(item[0])))

        q_out.put(r)

        q_in.task_done()


        '''
        if itct % 6000 == 0:
            print('.')
            sys.stdout.flush()
        elif itct % 60 == 0:
            print('.', end='')
            sys.stdout.flush()

        '''

    print('-- predictBatch done, did ' + str(itct) + ' cases ' + str(cases))


def predictit(model):

    feed = threading.Thread(target=feedInput)
    work = threading.Thread(target=predictBatch, args=(model,))

    feed.start()
    work.start()

    cases = 0

    preds = []

    twopct = math.ceil(n_batches/50)

    for itct in range(n_batches):
        try:
            item = q_out.get(block=True)
        except queue.Empty:
            #print(f'-- outq done')
            break

        preds.append(item)
        # not for SimpleQueue: q_out.task_done()

        cases += len(item)

        if itct == 0:
            print('[progress: .==2%]')

        if itct % twopct == 0:
            print('.', end='')
            sys.stdout.flush()
        #print(f'-- outq got')
        #exit(1)

    if itct == 0:
        print('predictit: None')
        return None

    if OUT_MODE == 'DO_LR_VECS':
        #print('\n-- vecs[0][0] shape ' + str(preds[0][0].shape))
        x = np.concatenate(preds, axis=1)
    else:
        # 1-D
        x = np.concatenate(preds, axis=0)

    print('\n-- predictit(model): ' \
            + ' iterations: ' + str(itct) \
            + ' len/preds ' + str(len(preds)) \
            + ' len/preds[0] ' + str(len(preds[0])) \
            + ' len/all/x ' + str(len(x)) \
            + ' cases ' + str(cases))

    preds.clear()
    del preds

    feed.join()
    work.join()

    return x


def outFileName(modelFile):

    if OUT_MODE == 'DO_LR_VECS':
        # exception - .vecs goes next to .h5
        return modelFile.replace('.h5', '.vecs')

    #modname = os.path.basename(modelFile)

    if OUT_MODE == 'DO_BIN':
        return modelFile.replace('.h5', '.pairs_bin')
    if OUT_MODE == 'DO_ALL':
        return modelFile.replace('.h5', '.pairs')
    if 'TOP' in OUT_MODE:
        return modelFile.replace('.h5', '.pairs_top')
    # nothing matches:
    print('Fix OUT_MODE outfile: ' + OUT_MODE)
    usage()


def writeTopFileFromAll(outfile, pred):

    if OUT_MODE != 'DO_TOP':
        print('ERROR: writeTopFileFromAll: ??? expected OUT_MODE=DO_TOP')
        exit(1)

    if os.path.isfile(outfile):
        print('??? already written ' + outfile)
        return

    end = len(fnames)
    print('Filtering/writing: ' + outfile + ' N ' + str(len(pred)) + ' pics ' + str(end) + ' pics sqd ' + str(end * end))

    start = time.time()
    ix_ct = 0
    ct = 0
    eq_sum = 0.0
    try:
        with open(outfile, 'w') as out:
            for i in range(end):

                i_index = i * end

                fname1 = fnames[i]
                id1 = id_map[fname1]

                eq_sum += pred[i_index + i]

                # get PAIR_LIMIT best A's and PAIR_LIMIT B's; 
                # pairtop wrap will sort -u for overlap

                # i -> j (A's)

                # sort

                picDict_r = {}  # for fname1 on left: i, j
                picDict_l = {}  # for fname1 on right: j, i

                for j in range(end):

                    if i == j:
                        continue

                    fname2 = fnames[j]
                    id2 = id_map[fname2]

                    picDict_r[id2] = pred[i_index + j][0]

                    #print('xxx ' + id1 + ' ' + id2 + ' ' + str(val))

                for j in range(end):
                    if i == j:
                        continue

                    other_fname = fnames[j]
                    other_id = id_map[other_fname]
                    other_index = j * end

                    picDict_l[other_id] = pred[other_index + i][0]

                # print in sorted order, [id1 X, val] then [X id1 val]

                #?get_the_others = False
                pic_ct = 0
                for item in sorted(picDict_r.items(), key=lambda x: x[1]):
                    pic_ct += 1
                    if pic_ct > PAIR_LIMIT:
                        break
                    out.write(id1 + ' ' + item[0] + ' ' + 
                                    str(item[1]) + '\n')
                                    #str(int(item[1] * MAG)) + '\n')
                    ct += 1
            
                pic_ct = 0
                for item in sorted(picDict_l.items(), key=lambda x: x[1]):
                    pic_ct += 1
                    if pic_ct > PAIR_LIMIT:
                        break
                    out.write(item[0]+ ' ' + id1 + ' ' + 
                                    str(item[1]) + '\n')
                                    #str(int(item[1] * MAG)) + '\n')
                    ct += 1

        print('Written: ' + str(ct) + ' lines to ' + outfile)
    except Exception as inst:
        print('exception writing ' + str(type(inst)) + ' ' + str(inst.args))
        exit(1)

    endt = time.time()
    print('\nWritten: ' + outfile + ' (' + str(ct) + ') in ' + str(datetime.timedelta(0, endt-start)))
    avg_aa = eq_sum / end
    avg_all = np.mean(pred)
    print('Avg of AA pairs, all pairs, %: ' + str(avg_aa) + ' ' + 
                str(avg_all) + ' ' + str((100 * avg_aa)/avg_all))

def writeTopFileFromPairs(outfile, pred):

    if OUT_MODE != 'DO_TOP_FROM_PAIRS':
        print('ERROR: writeTopFileFromPairs: ??? expected OUT_MODE=DO_TOP_FROM_PAIRS')
        exit(1)

    if os.path.isfile(outfile):
        print('??? already written ' + outfile)
        return

    start = time.time()

    ct = 0
    try:
        with open(outfile, 'w') as out:
            for i in range(end):

                #print('ids ' + id1 + ' ' + id2 + ' pred ix ' + str(ix))
                #val = pred[ix][0]
                #print('ids ' + id1 + ' ' + id2 + ' val ' + str(val))

                out.write(id1 + ' ' + id2 + ' ' + str(val) + '\n')
                ct += 1

    except Exception as inst:
        print('exception writing ' + str(type(inst)) + ' ' + str(inst.args))
        exit(1)

    endt = time.time()
    print('\nWritten: ' + outfile + ' (' + str(ct) + ') in ' + \
            str(datetime.timedelta(0, endt-start)))
 
def writeFile(outfile, pred):

    if OUT_MODE != 'DO_ALL':
        print('writeFile err: fix')
        exit(1)

    if os.path.isfile(outfile):
        print('??? already written ' + outfile)
        return

    end = len(fnames)
    print('Writing: ' + outfile + ' N ' + str(len(pred)) + ' pics ' + \
            str(end) + ' pics sqd ' + str(end * end))

    start = time.time()
    ix_ct = 0
    ct = 0
    eq_sum = 0.0
    try:
        with open(outfile, 'w') as out:
            for i in range(end):

                i_index = i * end

                fname1 = fnames[i]
                id1 = id_map[fname1]

                eq_sum += pred[i_index + i]

                # these need to be in canonical order, for parallel ops
                # also floating point, since they'll be added
                for j in range(i+1, end):

                    # i->j

                    fname2 = fnames[j]
                    id2 = id_map[fname2]
                    val1 = pred[i_index + j][0]

                    # j->i
                        
                    j_index = j * end
                    # tfname1 = fnames[j]
                    # tid1 = id_map[tfname1]
                    val2 = pred[j_index + i][0]

                    out.write(id1 + ' ' + id2 + ' ' + str(val1)
                                                  + ' ' + str(val2) + '\n')

        print('Written: ' + outfile)
    except Exception as inst:
        print('exception writing ' + str(type(inst)) + ' ' + str(inst.args))
        exit(1)

    endt = time.time()
    print('\nWritten: ' + outfile + '  in ' + \
                                    str(datetime.timedelta(0, endt-start)))
    avg_aa = eq_sum / end
    avg_all = np.mean(pred)
    if avg_all == 0.0:
        print('--== WROTE 0s')
    else:
        print('Avg of AA pairs, all pairs, %: ' + str(avg_aa) + ' ' + 
                str(avg_all) + ' ' + str((100 * avg_aa)/avg_all))

def writeLRPicVecs(outfile, pred):
    
    print('-- writeLRPicVecs: ' + outfile)

    if OUT_MODE != 'DO_LR_VECS':
        print('writeRLPicVecs err: fix')
        exit(1)

    if os.path.isfile(outfile):
        print('??? already written ' + outfile)
        return

    n_pics = len(fnames)

    if len(pred[0]) < n_pics: # might [?] be padded
        print('ERR: fnames: ' + str(n_pics) + ' pred: ' + str(len(pred[0])))
        exit(1)

    print('Writing: ' + outfile + ' pics: ' + str(n_pics))

    start = time.time()
    ct = 0
    try:
        with open(outfile, 'w') as out:

            for i in range(n_pics):

                fname = fnames[i]
                id = id_map[fname]

                out.write(id + ' l ')
                for vval in pred[0][i]:
                    out.write(str(vval) + ' ')
                out.write('\n');
                out.write(id + ' r ')
                for vval in pred[1][i]:
                    out.write(str(vval) + ' ')
                out.write('\n');
                
        print('Written: ' + outfile)
    except Exception as inst:
        print('Exception writing ' + outfile + ': ' +
                str(type(inst)) + ' ' + str(inst.args))
        exit(1)

    endt = time.time()
    print('\nWritten: ' + outfile + ' (' + str(ct) + ') in ' + \
                        str(datetime.timedelta(0, endt-start)))
    #avg_aa = eq_sum / end
    #avg_all = np.mean(pred)
    #print('Avg of AA pairs, all pairs, %: ' + str(avg_aa) + ' ' + 
    #            str(avg_all) + ' ' + str((100 * avg_aa)/avg_all))

def printWorker(q):
    while True:
        outfile, pred = q.get()
        if outfile == 'stop':
            break
        if OUT_MODE == 'DO_ALL':
            writeFile(outfile, pred)
        elif OUT_MODE == 'DO_TOP':
            writeTopFileFromAll(outfile, pred)
        elif OUT_MODE == 'DO_TOP_FROM_PAIRS':
            writeTopFileFromPairs(outfile, pred)
        elif OUT_MODE == 'DO_LR_VECS':
            writeLRPicVecs(outfile, pred)
        else:
            print('Unknown OUT_MODE: ' + OUT_MODE)
            exit(1)
 
#----~~~~~~----------__~~~~~_--- START


if OUT_MODE == 'DO_TOP_FROM_PAIRLIST':
    load_pairlist()
else:
    load_pic_list()

modelList = []
doneList = []

if os.path.isdir(MATTER):

    if OUT_MODE == 'DO_ALL':
        print('DO_ALL needs work for dir')
        exit(1)
    # MATTER is a dir
    fields = MATTER.split('_')

    #dirList = sorted(os.listdir(DIR))
    mglob = MATTER + '/*.h5'
    print('Models: ' + mglob + ' batch_size: ' + str(batch_size))
    modelList = sorted(glob.glob(mglob))
    # output is one file per model
    if OUT_MODE == 'DO_BIN':
        doneList = glob.glob(MATTER + '/*.pairs_bin')
    elif OUT_MODE == 'DO_LR_VECS':
        doneList = glob.glob(MATTER + '/*.vecs')
    else:
        print('HOW to decide doneList for dir for ' + OUT_MODE + '?')
        exit(1)

else:

    # MATTER is a file

    if MATTER.endswith('.h5'):

        modelList.append(MATTER)
        if OUT_MODE == 'DO_BIN':
            doneList = glob.glob(MATTER.replace('h5', 'pairs_bin'))
        elif OUT_MODE == 'DO_LR_VECS':
            doneList = glob.glob(MATTER.replace('h5', 'vecs'))
        else:
            print('HOW to decide doneList for ' + OUT_MODE + '?')
            exit(1)
    else:

        # MATTER is a file listing .h5's

        fields = MATTER.split('_')

        with open(MATTER) as fp:
            for line in fp:
                if line[0] == '#':
                    print('Commented: ' + line)
                    continue

                line = line.rstrip()

                if line.endswith('.h5'):

                    modelList.append(line)

                    outfile = outFileName(line)

                    if os.path.isfile(outfile):
                        doneList.append(outfile)

                elif len(line) > 0:

                    print('Ignoring line: ' + line)

print('Models: ' + str(len(modelList)) + ' done: ' + str(len(doneList)))
if len(modelList) == len(doneList):
    print('All already done: ' + str(doneList))
    quit()

# load all input data: histograms, maybe kwd vecs
load_preproc()

# manual cleanup for now - versions changed

n_pairs = (n_pics * n_pics) 
print('Pairs: ' + str(n_pairs))
if n_pairs < 1000:
    if n_pairs % 2 == 0:
        print('Adjusting batch_size down to 2')
        batch_size = 2
    else:
        print('Adjusting batch_size down to 1')
        batch_size = 1

print('batch size: ' + str(batch_size))

if PRINT_QUEUE:
    print_q = Queue()
    N_PRINT = 1
    pWorkers = []
    for i in range(N_PRINT):
        pWorker = Process(target=printWorker, args=(print_q,))
        pWorker.daemon = True
        pWorkers.append(pWorker)
        pWorker.start()

if OUT_MODE == 'DO_LR_VECS':
    n_pairs = n_pics

if n_pairs < 1000:
    if n_pairs % 2 == 0:
        print('Adjusting batch_size down to 2')
        batch_size = 2
    else:
        print('Adjusting batch_size down to 1')
        batch_size = 1

padding = 0
expected_size = n_pairs
overflow = n_pairs % batch_size

if overflow > 0:
    padding = batch_size - overflow
    expected_size += padding
    #print('Overflow: ' + str(overflow) + ' but NO pad ' + str(padding) + ' STILL expected: ' + str(expected_size))


n_batches = math.ceil(expected_size / batch_size)

print('real_img Pics: ' + str(len(fnames)) + \
            ' pairs/expected: ' + str(n_pairs) + '/' + str(expected_size)  +
            ' batch_size: ' + str(batch_size) + 
            ' batches: ' + str(n_batches))
            #' padding: ' + str(padding) )

if len(modelList) == 0  or  modelList[0].endswith('*.h5'):
    if len(modelList) == 1:
        print('*** No models match: ' + modelList[0])
    else:
        print('*** No models!')
    exit(1)

for modelFile in modelList:

    outfile = outFileName(modelFile)

    #print('-- checking outfile: ' + outfile)

    x = glob.glob(outfile.replace('/m_', '/*m_'))
    if len(x) > 0:
        print('-- Already done/skipping: ' + outfile + ' ' + str(x))
        continue

    print('-- Loading model [' + modelFile + ']')
    if 'nathan' in modelFile:
        model = load_model(modelFile, custom_objects={'Nathan': Nathan})
    else:
        model = load_model(modelFile)

    #model.summary()

    if OUT_MODE == 'DO_LR_VECS':
        print('Making internal model w/ l,r vec outputs')
        int_model = Model(model.input,
                [model.get_layer('side_left').output,
                model.get_layer('side_right').output])
        #int_model.summary()
        model = int_model

    #model.summary()

    #half_ix = int(n_pics/2)
    #print('half ' + str(half_ix) + ' n_pics ' + str(n_pics))

    print('predict: ' + modelFile)
    start = time.time()
    pred = predictit(model)
    if pred is None:
        print('\n!!!pred==None on ' + modelFile)
        exit(1)
        continue

    #print('exit!')
    #exit(1)

    endt = time.time()
    print('\nCalced ' + modelFile + ' in ' + 
            str(datetime.timedelta(0, endt-start)) + 
            ' preds: ' + str(len(pred)) + 
            ' mean/dev: ' +  str(np.mean(pred)) + ' / ' + str(np.std(pred)))
    '''
    if len(pred) == 2:
        if len(pred[0]) != expected_size:
            print('ERROR: len(pred)==2: expected len(pred[0]): ' + str(expected_size) + \
                    ' got ' + str(len(pred[0])) + \
                    ' model ' + modelFile)
            exit(1)

    elif len(pred) != expected_size:
        print('ERROR: expected: ' + str(expected_size) + ' got ' + str(len(pred)) + ' model ' + modelFile + ' mode ' + OUT_MODE)
        exit(1)
    '''
    if OUT_MODE == 'DO_LR_VECS':
        writeLRPicVecs(outfile, pred)
        del pred
    elif OUT_MODE == 'DO_BIN':
        t1 = datetime.datetime.now()
        with open(outfile, 'wb') as fp:
            np.save(fp, pred[0:n_pics*n_pics]) # (pred[] may be padded beyond)
        dt = datetime.datetime.now() - t1
        print('== wrote binary ' + outfile + ' in ' + \
                                str(int(dt.total_seconds())) + ' sec')
        del pred
    elif PRINT_QUEUE:
        print_q.put((outfile, pred))
    else:
        print('ERROR: This is an empty hole.')
        exit(1)

    K.clear_session()
    del model

if PRINT_QUEUE:
    for i in range(N_PRINT):
        print_q.put(('stop', None))
    print('---- DONE - wait/join/printers')
    for pWorker in pWorkers:
        pWorker.join()

print('=== Done: ' + str(datetime.datetime.now()))
