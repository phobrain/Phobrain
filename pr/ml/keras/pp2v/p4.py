#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#


## === p4.py - train phobrain nets at different stages
##
##          search MAIN below for main run params


# --------------------- Per-Machine config

TUNER = False
#TUNER = True

import socket
import os

HOST = socket.gethostname()
print('Config for host: ' + HOST)
if HOST == 'phobrain-gpu1':
    BASE = '/mnt/ssd/reep/home'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
    # THREADS for loading the training data
    THREADS = 10
else:
    BASE = '~'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
    # THREADS for loading the training data
    THREADS = 16

BASE = os.path.expanduser(BASE) + '/'

print('-- BASE is ' + BASE)

# --------------------- Per-Job config

## Overrides on DISTRIBUTE:
# RUN_CPU is forced to False if JPG input is involved (JPGV == vectors ok)
RUN_CPU = False
#RUN_CPU = True

if RUN_CPU:
    print('-- RUN_CPU')
    os.environ['CUDA_VISIBLE_DEVICES'] = ''
    DISTRIBUTE = False
else:
    print('-- RUN_GPU')

    ### DISTRIBUTE: use multiple GPUs
    #   False w/ 2080ti's: 29 min/epoch, max batch 250
    #   True: 18 min/epoch, max batch 350, 3x2080ti

    # DISTRIBUTE and optionally set GPU/s
    DISTRIBUTE = True
    os.environ['CUDA_VISIBLE_DEVICES'] = '0,1,2'
    #####
    #DISTRIBUTE = False
    # choose one device:
    #os.environ['CUDA_VISIBLE_DEVICES'] = '0'
    #os.environ['CUDA_VISIBLE_DEVICES'] = '1'
    #os.environ['CUDA_VISIBLE_DEVICES'] = '2'
    #os.environ['CUDA_VISIBLE_DEVICES'] = '1,2'
    ###
    if DISTRIBUTE:
        print('-- DISTRIBUTE: ' + str(DISTRIBUTE) + \
                ' CUDA_VISIBLE_DEVICES: ' + os.environ['CUDA_VISIBLE_DEVICES'])
    


#########################

import tensorflow as tf

print('META tensorflow: ' + tf.__version__)
print('META tensorflow: ' + tf.__file__)

#print('--- Dbug QUIT')
#quit()

#---------------
# misc

# doesn't work yet
permute = False
#permute = True

if permute:
    print('============= PERMUUUUUTING!!!')

LOSS_HISTORY = False

TF_MEM_LEAK = True
#TF_MEM_LEAK = False


########################################################
### MAIN
### Main settings: Horiz, IN_MODE, SIDE_VEC_LEN, OPTIMIZER

### possibly-interesting exptl: FAT, STEM_JOIN='add'

Horiz = True
#Horiz = False

#--- training data

if Horiz:
    #pair_dir = JAVA2ML_BASE + '/pairs/h/2021_10/h_0s' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/h/2021_11/h_bot_pr_neg' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/h/2022_01/h' # PlanF
    pair_dir = JAVA2ML_BASE + '/pairs/h/2022_04/hb' # PlanF
else:
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v2_sel_28322' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v2_sel_27795' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v_w_0' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v_top_33pct_0' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v_low_pos' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_09/v3' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2021_11/v_bot_pr_neg' # PlanF
    #pair_dir = JAVA2ML_BASE + '/pairs/v/2022_03/v' # PlanF
    pair_dir = JAVA2ML_BASE + '/pairs/v/2022_04/bv' # PlanF

pair_dir = os.path.expanduser(pair_dir)

# IN_MODE can contain any/all of ['KWD', 'JPGV', 'JPG', 'HIST'] - or 'VEC'
# As of 2021_03, use is JPGV_HIST ('JVH') generating models
#    which pred.py uses to get vectors for the branches of the Y,
#    then VEC mode trains a simple stem using the two vectors (l and r).

# usually one of JPGV_HIST, VEC
#IN_MODE = 'JPGV_HIST'
IN_MODE = 'VEC'

### ACTIVATION
#
#   so-far duds with VECs/he_normal:
#           gelu # selu # elu # sigmoid, exponential # softplus, softmax 
#   tanh - longer prediction time on CPU ?
#
#  h/JH3, pair conditions: addZeros, useTopNeg true: Lo->Hi, 2022/1
#   DenseNet121 - softsign no good, swish, relu better on negs
#
#  v/JH3/VGG16 - 2022_02
#                   softsign->marginal/extremes
#                   Adam no good w/ relu
#                   tanh no good w/ SGD -> rest canned
#  vb/JH3/VGG16 - 2022_04
#                   softsign->marginal (most negs in 70's)
#  hb/JH2/VGG16 - 2022_04
#                   softsign->interesting
#
# relu, tanh, softsign - at least 1/5 ok for all purposes
#
#ACTIVATION = 'relu'
# softsign no good on vb/JH, good on hb/JH
#ACTIVATION = 'softsign'
# tanh no good on JH
ACTIVATION = 'tanh'
# swish - not ok for VEC input
# swish will be switched to relu for IN_MODE == 'VEC'
#ACTIVATION = 'swish'

# only for further training of imagenet models, 
#normally use V=feature vectors
#IN_MODE = 'JPG_HIST'

# IN_MODEs not tried recently - long before 2021_01
#IN_MODE = 'JPG'
#IN_MODE = 'HIST'
# kwd vecs from java: proj/kvec/
#IN_MODE = 'KWDS'
#IN_MODE = 'KWDS_HIST'

    
IN_HIST_MODE = ''
if 'HIST' in IN_MODE:

    # IN_HIST_MODE can contain any or all of
    #   GREY128, SAT128, RGB12, RGB24, HS24, HS48, SV24, SV48

    IN_HIST_MODE = 'GREY128_SAT128_RGB12'

# for JPGV and JPG
IN_JPG_MODE = ''
if 'JPG' in IN_MODE:

    # IN_JPG_MODE is either
    #   one of VGG16, VGG19, .. with one of 224, ..
    #   plus optionally FFT with either MAG or PHA [long time unused, 2022/1]
    # Baseline is VGG16_224, 
    # DenseNet121_224 somewhat better than VGG16 on v, 
    #   XXXX on h, [vh]_2022_01/JH3/.
    IN_JPG_MODE = 'VGG16_224'
    #IN_JPG_MODE = 'VGG19_224'
    #IN_JPG_MODE = 'DenseNet121_224'
    #IN_JPG_MODE = 'MobileNetV2_224'
    #IN_JPG_MODE = 'DenseNet169_224'
    #IN_JPG_MODE = 'ResNet152V2_224'
    #IN_JPG_MODE = 'NASNetLarge_331'
    #IN_JPG_MODE = 'VGG19_224_FFTPHA'
    #IN_JPG_MODE = 'Xception_299' - big, slow, inaccurate
    #IN_JPG_MODE = 'InceptionV3_299'
    # vecs too big IN_JPG_MODE = 'EfficientNetB7_600'


#---------------

# SIDE_VEC_LEN only applies to training with jpg/hist for nets
#     to create 'side vectors'. It is is the size 
#     of the last layer in the right/left side nets.
#     The idea was to use the vectors as a proxy for
#     jpgs, since they take so long to train, but crunching
#     from 128 or 256 down to 12 to match the transitory 
#     11-dimensional structures formed by neural relationships, 
#     gave 82:82 w/ jpg+kwd+grey+sat+rgb12 off the bat, needs 
#     lots more investigation.
#  if TUNER, tuner handles and this setting is ignored or varied upon
SIDE_VEC_LEN = 2

#---------------


# STEM_JOIN:
#   'concat' - concatenate left/right vectors into stem
#   'add'    - sum vecs into stem, not tried much
STEM_JOIN = 'concat'
#STEM_JOIN = 'add'

distrib_strategy = None

# extra wide layer+ in case it picks up higher dimensional concepts
# see WIDE, from jpg devel line. Triggers SINGLE_EPOCH per model,
# which triggers rotating initializers
FAT = False
#FAT = True


######## Optimization strategy from trial and error

# after 1st round
HARD_SECOND_EPOCH = 2
#HARD_SECOND_EPOCH = None

# keep halving epochs after 1st round; resets with new model
REDUCE_EPOCHS = False
if HARD_SECOND_EPOCH != None:
    print('-- progressively cut epochs->2')
    REDUCE_EPOCHS = True
else:
    print('-- HARD_SECOND_EPOCH: ' + str(HARD_SECOND_EPOCH))

GCTF = True
#GCTF = False

if GCTF:
    import gctf

# OPTIMIZER 
# 'cycle' option:
#CYCLE_LIST = [ 'SGD', 'STEEP_SGD', 'RMSProp']
CYCLE_LIST = [ 'SGD', 'STEEP_SGD', 'RMSProp', 'Adam']

if TUNER:
    #CYCLE_LIST = [ 'RMSProp' ]
    CYCLE_LIST = [ 'Adam' ]

print('--  CYCLE_LIST: ' + str(CYCLE_LIST))

# manual: always try SGD first
# 'cycle' option overrides this setting
OPTIMIZER = 'SGD'
#OPTIMIZER = 'STEEP_SGD'
#OPTIMIZER = 'RMSProp'
#OPTIMIZER = 'Adam'
#OPTIMIZER = 'SWA_SGD' - not maintained
# Nadam, Adagrad w/ defaults gave 100:0
# adadelta fails, RMSProp works on h/grey-only
# adadelta failed on kwd+grey_rgb12+JPG/VGG19
#OPTIMIZER = 'adadelta'
#OPTIMIZER = AdamAccumulate(accum_iters=2) problem w/ siamese

# for STEEP_SGD
STEEP_FAC = 1.4

if OPTIMIZER == 'SWA_SGD':
    if HARD_SECOND_EPOCH != None:
        print('SWA_SGD: unsetting HARD_SECOND_EPOCH')
        HARD_SECOND_EPOCH = None
    if REDUCE_EPOCHS:
        print('SWA_SGD: unsetting REDUCE_EPOCHS')
        REDUCE_EPOCHS = False

    SWA_INIT_LR = 0.02

if FAT:
    if SIDE_VEC_LEN != 12:
        print('-- FAT: overriding SIDE_VEC_LEN of ' + str(SIDE_VEC_LEN) + ' with 12')
        SIDE_VEC_LEN = 12
    if HARD_SECOND_EPOCH != None:
        print('-- FAT: unsetting HARD_SECOND_EPOCH')
        HARD_SECOND_EPOCH = None
    

DEBUG_MODEL = False
#DEBUG_MODEL = True

# TBD https://github.com/jim-meyer/lottery_ticket_pruner
PRUNE = False

if PRUNE:
    from lottery_ticket_pruner import LotteryTicketPruner, PrunerCallback


# ------ get personal

if LOSS_HISTORY:

    class LossHistory(tf.keras.callbacks.Callback):
        def on_train_begin(self, logs={}):
            self.losses = []

        def on_batch_end(self, batch, logs={}):
            self.losses.append(logs.get('loss'))

    def losses(history):
        return ' batch losses [' + str(len(history.losses)) + ']: ' + \
                str(round(history.losses[0], 2)) + ' .. ' + \
                str(round(history.losses[-1], 2))

    history = LossHistory()

# ------ Input data (convention is in order of increasing size)


# DO_JPG was originally only for deriving image_[rl] vectors 
#   and forces narrowness of each side to what may remain the
#   default anyway (SIDE_VEC_LEN = 12, maybe 3 or 2)

DO_HIST = False
NORM_HIST_TO_MAX = False

DO_KWDS = False

DO_JPGV = False
DO_JPG = False
# long-time setting:
DETRAIN_ALL_JPG = True
JPG_224 = False
JPG_299 = False

DO_VEC = False
IN_VEC_MODE = ''

# never worked
DO_DB = False

# screening: relax throw-away criteria if data is weak
# cut0 == 1st iteration
# cut1 == 2nd+
min_pos_cut0 = []
min_pos_cut1 = []
min_neg_cut0 = []
min_neg_cut1 = []

MIN_POS_CUT0 = None
MIN_POS_CUT1 = None
MIN_NEG_CUT0 = None
MIN_NEG_CUT1 = None

def parse_IN_MODE():

    global min_pos_cut0, min_pos_cut1, min_neg_cut0, min_neg_cut1
    global MIN_POS_CUT0, MIN_POS_CUT1, MIN_NEG_CUT0, MIN_NEG_CUT1
    global DO_HIST, NORM_HIST_TO_MAX
    global DO_JPGV, DO_JPG, DETRAIN_ALL_JPG
    global DO_VEC, IN_VEC_MODE

    min_pos_cut0 = []
    min_pos_cut1 = []
    min_neg_cut = []

    print('-- IN_MODE is ' + IN_MODE)

    if 'VEC' == IN_MODE:

        DO_VEC = True

        # IN_VEC_MODE can contain 
        #   NORM - if >1, divide by N  - works well for lots of files
        IN_VEC_MODE = 'NORM'

        min_pos_cut0.append(60)
        min_pos_cut1.append(70)
        min_neg_cut0.append(40)
        min_neg_cut1.append(70)

    if 'KWDS' in IN_MODE:

        DO_KWDS = True

        min_pos_cut0.append(70)
        min_pos_cut1.append(75)
        min_neg_cut0.append(66)
        min_neg_cut1.append(70)

        if Horiz:
            kwdvec_dir = JAVA2ML_BASE + '/kwd_vec/2020_08_new/h'
            kwdvec_dim = 1752
        else:
            kwdvec_dir = JAVA2ML_BASE + '/kwd_vec/2020_08_new/v'
            kwdvec_dim = 1477

        kwdvec_dir = os.path.expanduser(kwdvec_dir)

    if 'HIST' in IN_MODE:

        DO_HIST = True

        min_pos_cut0.append(40)
        min_pos_cut1.append(50)
        min_neg_cut0.append(20)
        min_neg_cut1.append(50)

        # concatted hists divided by sum
        #NORM_HIST = False
        NORM_HIST_TO_MAX = True
        if NORM_HIST_TO_MAX:
            print('-- NORMing histos to max')

    if 'JPGV' in IN_MODE:

        DO_JPGV = True
        DETRAIN_ALL_JPG = True

    elif 'JPG' in IN_MODE:

        DO_JPG = True

        DETRAIN_ALL_JPG = True
        # DETRAIN_ALL_JPG=False == let top 20% train, -> smaller batch size
        #DETRAIN_ALL_JPG = False

    # both JPGV and JPG
    if 'JPG' in IN_MODE:

        min_pos_cut0.append(60)
        min_pos_cut1.append(75)
        min_neg_cut0.append(50)
        min_neg_cut1.append(75)

        if '224' in IN_JPG_MODE:
            JPG_224 = True
        elif '299' in IN_JPG_MODE:
            JPG_299 = True

    if DO_DB:
        # not used
        min_pos_cut0.append(30)
        min_pos_cut1.appen(50)
        min_neg_cut0.append(30)
        min_neg_cut1.append(50)

        dbvec_dir = JAVA2ML_BASE + '/dbvecs'
        dbvec_dim = 27

        dbvec_dir = os.path.expanduser(dbvec_dir)

    if len(min_pos_cut0) == 0:
        print('No IN_MODE option selected')
        quit()

    MIN_POS_CUT0 = max(min_pos_cut0) / 100.0
    MIN_POS_CUT1 = max(min_pos_cut1) / 100.0
    MIN_NEG_CUT0 = max(min_neg_cut0) / 100.0
    MIN_NEG_CUT1 = max(min_neg_cut1) / 100.0

parse_IN_MODE()

if not DO_VEC and SIDE_VEC_LEN < 3:
    print('-- only SGD optimization for SIDE_VEC_LEN < 3')
    CYCLE_LIST = [ 'SGD', 'STEEP_SGD' ]

if Horiz:
    print('== H')
else:
    print('== V')

print('Pair dir: ' + pair_dir + '\n' + \
        'IN_MODE: ' + IN_MODE + '\n' + \
        'MODE_OPTS: ' + IN_HIST_MODE + ' ' + IN_JPG_MODE + '\n' + \
        'CUT 0/1+ ' + str(MIN_POS_CUT0) + '/' + str(MIN_POS_CUT1) + '\n' + \
        'THREADS ' + str(THREADS))

#---------------

# INITIALIZER_MODE: 'random' production
#                   'fixed'  production
#                   'rotate' try first round only 
#                            to explore what can get off the ground

kernel_init_i = 0

if DO_VEC:
    kernel_initializers = [ \
                        'glorot_uniform', 'glorot_uniform', 'glorot_uniform', \
                        'glorot_normal', 'glorot_normal', \
                        'lecun_normal', 'lecun_uniform', \
                        'he_normal' ] 
                        # , \ 'he_uniform' ]
    #INITIALIZER_MODE = 'random'
    INITIALIZER_MODE = 'fixed'
    #kernel_init_i = 0
    kernel_init_i = 7

else:
    kernel_initializers = [ 'glorot_normal', 'glorot_uniform', \
                        'lecun_normal', 'lecun_uniform', \
                        'he_normal', 'he_uniform', \
                        'random_normal', 'random_uniform', \
                        'truncated_normal',\
                        'orthogonal', 'zeros', 'ones' ]
                        # 'variance_scaling', 'orthogonal', 'zeros', 'ones' ]
    INITIALIZER_MODE = 'fixed'
    # 0 is the keras default too I think
    kernel_init_i = 0

    if SIDE_VEC_LEN < 3:
        kernel_initializers = [ 'glorot_normal', 'glorot_uniform', \
                        'he_normal', 'he_uniform' ]
        if Horiz:
            INITIALIZER_MODE = 'rotate'
            kernel_init_i = 0
            print('-- lrv<3: H: kernel init ' + INITIALIZER_MODE + ': ' + \
                str(kernel_initializers))
        else:
            INITIALIZER_MODE = 'fixed'
            kernel_init_i = 2
            print('-- lrv<3: V: kernel init ' + INITIALIZER_MODE + ': ' + \
                kernel_initializers[kernel_init_i])

    # softsign/JH: 1 also works, not others
    #INITIALIZER_MODE = 'rotate'

if INITIALIZER_MODE == 'random':
    import random
    kernel_init_i = random.randint(0, len(kernel_initializers)-1)


# SINGLE_EPOCH is good for FAT and exploring initializers
#SINGLE_EPOCH = True
SINGLE_EPOCH = False

if FAT and not SINGLE_EPOCH:
    print('-- FAT: single-epoch')
    SINGLE_EPOCH = True

if SINGLE_EPOCH and INITIALIZER_MODE != 'rotate':
    print('-- SINGLE_EPOCH: rotate initializers [-not done?]')


import sys
print('exec: ' + str(sys.executable))
#sys.path = [ '/project', '/usr/lib/python3.7', '/usr/lib/python3.7/lib-dynload', '/usr/local/lib/python3.7/dist-packages', '/usr/lib/python3/dist-packages']
#print('path: ' + str(sys.path))
#quit()
import numpy as np
import math

from multiprocessing import Pool

import random
import string
import re
import glob
import time
import datetime

#----
#sys.path.append('/usr/local/lib/python2.7/dist-packages/')
#print('xx ' + str(sys.path))
#import plaidml
#import plaidml.keras
#plaidml.keras.install_backend()
#----

from tensorflow.keras.models import Sequential, Model, load_model

#
from tensorflow.keras.preprocessing import image
if DO_JPG:
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

from tensorflow.keras.layers import Input, Flatten, Dense, Dropout, AlphaDropout, GaussianDropout, Concatenate, concatenate, BatchNormalization, Conv2D
from tensorflow.keras.optimizers import Adagrad, RMSprop, Adam, Adamax, Nadam, SGD
from tensorflow.keras import backend as K

#K.set_floatx('float16')
#K.set_epsilon(1e-4)

if OPTIMIZER == 'SWA_SGD':
    # https://github.com/simon-larsson/keras-swa
    from swa.tfkeras import SWA

#sys.path.insert(0, '../')
sys.path.append('..')

# from sharpened_cosine_distance import CosSimConv2D

# custom func from someone on reddit
NATHAN = True
#NATHAN = False

if NATHAN:
    from nathan import Nathan

#run_opts = tf.RunOptions(report_tensor_allocations_upon_oom = True)

from tensorflow.python.client import device_lib
print(device_lib.list_local_devices())

#from kerastuner.tuners import BayesianOptimization
#from keras_spatial_bias import ConcatSpatialCoordinate
#from keras_optim_acc import AdamAccumulate

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from tensorflow import keras
import pickle

# test on training set pos/neg separately as done w/ holdout/test set
TEST_TRAIN_POS_NEG = False

# obsolete: delete and reload model repeatedly for crazy debug
delModel = False

#from ginfty import TDModel, GInftlyLayer, GammaRegularizedBatchNorm, c_l2


# if REFINE_STRATEGY == 
#    'incr_batch_stay' - batch_index increases to end then stay on that
#    'incr_batch_random' - increases to end, then random
#    'random_batch' - always random sel from list
#     else always BATCH_INDEX_START

#REFINE_STRATEGY = 'none'
#REFINE_STRATEGY = 'incr_batch_stay'
REFINE_STRATEGY = 'incr_batch_random'
#REFINE_STRATEGY = 'random_batch'
#REFINE_STRATEGY = 'decr_lr'

print('-- refine strategy: ' + REFINE_STRATEGY)

batches = []

def set_batches():

    global batches, BATCH_INDEX_START

    batches = []

    if DO_VEC:
        # current production 2nd step
        if ACTIVATION == 'relu':
            batches = [ 2048, 2048, 512, 4096, 8192, 512, 10240, 1024, 40480 ]
        else:
            batches = [ 2048, 512, 20840, 40480, 1024, 40480, 2048, 3024 ]

    elif DO_JPGV and DO_HIST:

        batches = [ 100, 150, 200, 150, 100, 250, 100, 150, 200 ]

        # all > 500 => no batchnorm
        # batches = [ 1500, 1500, 2000, 1500, 800, 650, 1800 ]
        #batches = [ 250, 200, 250, 150, 200, 180, 250, 150, 200 ]
        # small regime
        # no/VGG19 batches = [ 100, 80, 150, 100, 80, 50, 150, 100, 80 ]

    elif DO_JPG and DO_HIST:
        # current production 1st step to get vectors to train next nets

        # start w/ the biggest to verify it fits memory

        if DETRAIN_ALL_JPG:

            if 'SGD' in OPTIMIZER:
                if DISTRIBUTE:

                    if 'DenseNet121' in IN_JPG_MODE:
                        B_MAX = 850
                        batches.append(B_MAX)
                        #batches.append(B_MAX-50)
                        #batches.append(B_MAX-100)
                        #batches.append(B_MAX-150)
                        batches.append(300)
                        batches.append(B_MAX)
                        batches.append(int((B_MAX*2)/3))
                        batches.append(100)

                    elif 'DenseNet169' in IN_JPG_MODE:
                        print('-- DenseNet169: 600 max batch on 2080ti?')
                        B_MAX = 600
                        batches.append(B_MAX)
                        batches.append(B_MAX-50)
                        batches.append(B_MAX)
                        batches.append(B_MAX-150)
                        batches.append(300)
                        batches.append(B_MAX)

                    else:
                        batches.append(350)
                        batches.append(300)
                        batches.append(350)
                        batches.append(100)
                        batches.append(350)
                        batches.append(200)
                else:
                    batches.append(250)
                    batches.append(200)
                    batches.append(250)
                    batches.append(100)

            elif 'DenseNet121' in IN_JPG_MODE:
                B_MAX = 850
                batches.append(B_MAX)
                batches.append(int((B_MAX*2)/3))
                #batches.append(B_MAX-100)
                #batches.append(B_MAX-50)
                #batches.append(B_MAX-150)
                batches.append(300)

            else:
                batches.append(200)
                batches.append(150)
                batches.append(150)
                batches.append(100)
                batches.append(150)
                batches.append(150)

        else:

            batches = [ 98, 64, 32, 98, 64 ]

    elif DO_JPGV or DO_JPG:
        # old
        # 32..96 for just VGG19 224x224 on 1080ti (96 w/ adagrad, try SGD/steep
        #batches.append(96)
        if 'SGD' in OPTIMIZER:
            batches.append(100)
            batches.append(150)
            batches.append(200)
            batches.append(64)
            batches.append(150)
            batches.append(64)

            #batches.append(220)
            #batches.append(230)
            #batches.append(240)
        #elif OPTIMIZER = 'adadelta':
        else:
            batches.append(64)
            batches.append(96)
            batches.append(96)
            batches.append(32)
            batches.append(96)

            #batches.append(220)
            #batches.append(230)
            #batches.append(240)
        #elif OPTIMIZER = 'adadelta':
        #else:
        #    batches.append(64)
        #    batches.append(96)
        #    batches.append(96)
        #    batches.append(32)

    else:
        batches = [ 1024, 512, 2048, 512, 4096, 8192 ] #, 10240 ]

    BATCH_INDEX_START = 0

set_batches()

###

START_EPOCHS = 3

if FAT:
    print('-- FAT: START_EPOCHS = 1')
    START_EPOCHS = 1
elif OPTIMIZER == 'SWA_SGD':
    if Horiz:
        START_EPOCHS = 8
    else:
        START_EPOCHS = 12

    print('-- SWA_SGD: epochs: ' + str(START_EPOCHS))

#if not DO_VEC and SIDE_VEC_LEN < 3:
#   START_EPOCHS = 24
#   print('-- SIDE_VEC_LEN=2: epochs: ' + str(START_EPOCHS))

###

if DO_VEC:
    
    # e.g. a .vecs file:
    #  m_h_model_pn_80_87_bEen_200_1_3_3_ki_0_VGG19_224_nathan_lrv_12_SGD.vecs
    # we want a file listing .vecs files with full paths (from pp2 currently).
    #vec_list_file = 'h_2021_05/jh2_nonzero_vecs'
    #vec_list_file = 'v_2021_05_2/jh1_vgg16_76_72_vecs'
    #vec_list_file = 'v_2021_05_2/jh_all_vecs'
    #vec_list_file = 'v_2021_06/vgg16_all_vecs'
    #vec_list_file = 'v_2021_07/jh3_all_vecs'
    #vec_list_file = 'v_2021_07/jh3_relu_vecs'
    #vec_list_file = 'v_2021_07/jh3_swish_vecs'
    #vec_list_file = 'v_2021_07/jh2_all_vecs'
    #vec_list_file = 'h_2021_08/jh_all_vecs'
    #vec_list_file = 'v_2021_09/jh_all_relu_vecs'
    #vec_list_file = 'v_2021_09/jh3_softsign_only_vecs'
    #vec_list_file = 'v_2021_09/jh4_softsign_vecs'
    #vec_list_file = 'h_2021_10/jh4_lim_vecs'
    #vec_list_file = 'h_2021_10/jh3_0_vecs'
    #vec_list_file = 'h_2021_10/jh5_0_vecs'
    #vec_list_file = 'v_2021_11/jh3_sig0_vecs'
    #vec_list_file = 'v_2021_11/jh12_vecs'
    #vec_list_file = 'v_2022_01/jh_all_vecs'
    #vec_list_file = 'h_2022_01/jh3_vecs'
    #vec_list_file = 'hb_2022_04/jh2_vecs'
    #vec_list_file = 'hb_2022_04/jh2_vbhb_vecs'
    #vec_list_file = 'hb_2022_04/jh_hvb_all_vecs'

    #vec_list_file = 'v_2022_02/jh3_lo_pos_vecs'
    #vec_list_file = 'v_2022_02/jh3_vecs'
    #vec_list_file = 'v_2022_02/jh3_2_vecs'
    #vec_list_file = 'v_2022_02/jh_all_vecs'
    #vec_list_file = 'vb_2022_04/jh2_vecs'
    #vec_list_file = 'vb_2022_04/jh3_vecs'
    #vec_list_file = 'vb_2022_04/jh12_vecs'
    vec_list_file = 'vb_2022_04/jh_all_vecs'

    side_vec_files = []
    side_vec_dims = []


    print('-- reading vecs filenames from ' + vec_list_file)

    with open(vec_list_file) as fp:
        for line in fp:
            if line.startswith('#'):
                continue

            fields = line.split('_')
            ix = fields.index('lrv')
            dim = int(fields[ix+1])

            print('-- vecs: ' + line + '  dim: ' + str(dim))

            side_vec_files.append(line.rstrip())
            side_vec_dims.append(dim)
            
    side_vec_dim = sum(side_vec_dims)

    if side_vec_dim == 0:
        print('-- SIDE VEC DIM == 0')
        quit()

    print('-- side_vec_dim: ' + str(side_vec_dim))

    if ACTIVATION == 'swish':
        print('-- DO_VEC: SWITCHING swish -> relu, swish no good @V55_134')
        ACTIVATION = 'relu'

if DO_HIST:

    hist_dirs = []
    hist_load_dims = []
    hist_dense_sizes = []

    hist_alg = ''

    # in order of size

    if 'SAT48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/s48_hist')
        hist_load_dims.append(48)
        hist_dense_sizes.append(48)  # s48
        hist_alg += 's48_'

    if 'GREY128' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/grey_hist')
        hist_load_dims.append(128)
        hist_dense_sizes.append(128)  # greyscale
        hist_alg += 'g128_'

    if 'SAT128' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/s128_hist')
        hist_load_dims.append(128)
        hist_dense_sizes.append(128)  # s128
        hist_alg += 's128_'

    if 'HS24' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/hs24_hist')
        hist_load_dims.append(576)
        hist_dense_sizes.append(256)  # hs24
        hist_alg += 'h24_'

    if 'RGB12' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb12_hist')
        hist_load_dims.append(1728)
        hist_dense_sizes.append(728)  # rgb12
        hist_alg += 'r12_'

    if 'HS48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/hs48_hist')
        hist_load_dims.append(2304)
        hist_dense_sizes.append(812)  # hs48
        hist_alg += 'h48_'

    if 'SV48' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/sv48_hist')
        hist_load_dims.append(2304)
        hist_dense_sizes.append(812)  # hs48
        hist_alg += 'sv48_'

    if 'RGB24' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb24_hist')
        hist_load_dims.append(13824)
        hist_dense_sizes.append(2048) #  rgb24
        hist_alg += 'r24_'

    if 'RGB32' in IN_HIST_MODE:
        hist_dirs.append(BASE + '/image_desc/2_hist/pics/rgb32_hist')
        hist_load_dims.append(32768)
        hist_dense_sizes.append(4096) # rgb32
        hist_alg += 'r32_'

    hist_input_dim = sum(hist_load_dims)
    hist_dense_size = sum(hist_dense_sizes)

    DENSE_FAC = 1.0  # grey..hs48
    #DENSE_FAC = 0.5  # grey..hs48, grey+rgb12
    #DENSE_FAC = 0.5   # grey..rgb24
    if 'HIST' in IN_MODE and hist_dense_size < 512 and 'KWDS' not in IN_MODE:
        DENSE_FAC = 3.0  # grey+s128
    print('histo DENSE_FAC * ' + str(DENSE_FAC))
    hist_dense_size = int(hist_dense_size * DENSE_FAC)

    hist_input_shape = (hist_input_dim, )

    print('HISTS: ' + str(len(hist_dirs)))
    #print('INP:   ' + str(input_shape))
    print('hist_input_shape ' + str(hist_input_shape))
    print('hist_dense_size ' + str(hist_dense_size))

tot_input_dim = 0

# POOLING - None gives a nice Flatten strategy. Others not found useful.
POOLING = None
#POOLING = 'avg'
#POOLING = 'max'

if DO_JPGV and POOLING is not None:
    print('-- changing POOLING from ' + POOLING + ' to None for JPGV')
    POOLING = None

if DO_JPGV or DO_JPG:

    #if OPTIMIZER == 'STEEP_SGD'  and  START_EPOCHS != 20:
    #    print('-- jpg: increasing START_EPOCHS ' + str(START_EPOCHS) + 
    #            ' -> 20 for STEEP_SGD')
    #    START_EPOCHS = 20

    if 'FFTMAG' in IN_JPG_MODE: # HACK: force dims for model
        jpg_dir = BASE + '/images/fft/mag'
        jpg_shape = (224, 224, 3)
        tot_input_dim += 224 * 224 * 3
    elif 'FFTPHA' in IN_JPG_MODE:
        jpg_dir = BASE + '/images/fft/pha'
        jpg_shape = (224, 224, 3)
        tot_input_dim += 224 * 224 * 3
    elif '224' in IN_JPG_MODE:
        jpg_dir = BASE + '/image_desc/3_jpg/prun224'
        jpg_shape = (224, 224, 3)
        tot_input_dim += 224 * 224 * 3
    elif '299' in IN_JPG_MODE:
        jpg_dir = BASE + '/image_desc/3_jpg/prun299'
        jpg_shape = (299, 299, 3)
        tot_input_dim += 299 * 299 * 3
    elif '331' in IN_JPG_MODE:
        jpg_dir = BASE + '/image_desc/3_jpg/prun331'
        jpg_shape = (331, 331, 3)
        tot_input_dim += 331 * 331 * 3
    elif '600' in IN_JPG_MODE:
        jpg_dir = BASE + '/image_desc/3_jpg/prun600'
        jpg_shape = (600, 600, 3)
        tot_input_dim += 600 * 600 * 3
    else:
        print('Honky, what jpeg type?')
        quit()

    if DO_JPGV:
        x = IN_JPG_MODE.split('_')
        img_vec_dir = jpg_dir + '/vecs_' + x[0]

    if DO_JPG:
        print('JPG:   ' + jpg_dir)
        print('JPG shape:   ' + str(jpg_shape))
    else:
        print('JPG vecs:   ' + img_vec_dir)

##### pick 'potential energy function' params

if DO_VEC:
    tot_input_dim += side_vec_dim

if DO_DB:
    tot_input_dim += dbvec_dim

if DO_HIST:
    tot_input_dim += hist_input_dim

if DO_KWDS:
    tot_input_dim += kwdvec_dim

CHECK_DATA = True
CHECK_DATA2 = True
#--------------------------

#def procId(archive, f):
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

def vecIdFile(fname):
    id = procId(fname)
    return img_vec_dir + '/' + id

def load_text_nums(model, fname, is_histogram):
    global hist_sum
    if is_histogram:
        target_size = hist_load_dims[model]
    elif model == 0:
        target_size = kwdvec_dim
    else:
        target_size = dbvec_dim

    ct = 0
    data = np.loadtxt(fname)
    if len(data) != target_size:
        print('Dim mismatch: ' + fname + ' exp/actual ' + 
                str(target_size) + '/' + str(len(data)))
        quit()

    if CHECK_DATA:
        x = np.sum(data)
        if np.isnan(x):
            print('Got NaN: ' + fname)
            quit()
        if np.isinf(x):
            print('Got Inf: ' + fname)
            quit()

    #if not is_histogram:
    #    data *= 0.5;
    #    t = np.sum(data)
    #    if t > 1:
    #        data /= t

    return data
    #with open(fname) as fp:
        #for line in fp:
            #ct += 1

side_vec_map = {}

def load_vecs():
    print('Loading vecs; getting dims from file names:')
    for i in range(len(side_vec_files)):
        print('\t' + str(side_vec_dims[i]) + '\t' + side_vec_files[i])

    for i in range(len(side_vec_files)):
        f = side_vec_files[i]
        dim =  side_vec_dims[i]
        print('Loading vecs: ' + f + ' dim ' + str(dim))
        vec_len = 0
        with open(f) as fp:

            ct = 0

            for line in fp:

                if line.startswith('#'):
                    continue

                # id <l|r> vals...

                fields = line.split()
                v_len = len(fields) - 2

                if v_len < 1:
                    print('fields < 3: ' + line)
                    exit(1)

                if vec_len == 0:
                    if fields[0] == '1:1a':
                        print('-- 1:1a - ' + str(v_len))
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

                id = fields[0]
                side = fields[1]

                side_id = id + '_' + side

                tvec = np.array(fields[2:],  dtype=np.float32)

                vec = side_vec_map.get(side_id)
                if not isinstance(vec, type(None)):
                    tvec = np.concatenate((tvec, vec), axis=0)   

                side_vec_map[side_id] = tvec
             
            #if ct != n_pics * 2:
            #    print("Load: bad pic count: ' + str(ct) + ' expected ' + \
            #                                    str(n_pics * 2))
            #    quit()

    if 'NORM' in IN_VEC_MODE and len(side_vec_dims) > 1:
        print('-- Norm side vecs by count: ' + str(len(side_vec_dims)))
        factor = 1.0 / len(side_vec_dims)
        for k in side_vec_map:
            side_vec_map[k] = side_vec_map[k] * factor

    sumdims = sum(side_vec_dims)
    print('-- check dims.. tot ' + str(sumdims))
    for k in side_vec_map:
        if len(side_vec_map[k]) != sumdims:
            print('Error: got ' + str(len(side_vec_map[k])) + ' for ' + k)
            exit(1)

    print('Loaded vecs: ' + str(len(side_vec_files)) + ' tot_dim ' + \
                        str(sum(side_vec_dims)))
#QQQ

dbvec_map = {}

def load_dbvec(arg):
    model, pid, fname = arg
    return pid, load_text_nums(model, fname, False)

def load_dbvecs():

    global dbvec_dir

    if not dbvec_dir.endswith('/'):
        dbvec_dir += '/'

    print('Read dbvecs: ' + dbvec_dir)

    start = time.time()

    #for x in glob.glob(os.path.join(kwdvec_dir, '*:*')):
    #    print('- ' + str(x))
    files = glob.glob(os.path.join(dbvec_dir, '*:*'))
    if len(files) == 0:
        print('NO FILES IN ' + dbvec_dir + ' FOR DB LOAD')
        quit()

    print('dbvecs: ' + str(len(files)))

    pids = [w.replace(dbvec_dir, '') for w in list(files)]

    # nonzeros used for 'model' 
    nonzeros = [-1] * len(pids)
    tmp = zip(nonzeros, pids, files)
    print('Load dbvecs, threads: ' + str(THREADS))
    pool = Pool(processes=THREADS)
    for pid, dbvec in pool.map(load_dbvec, tmp):
        #print('dbv pid ' + pid)
        dbvec_map[pid] = dbvec
    pool.close()
    pool.terminate()
    pool.join()
    end = time.time()
    print('loaded ' + str(len(dbvec_map)) + ' in ' + str(datetime.timedelta(0, end-start)))
    if len(dbvec_map) == 0:
        print('NO DB LOAD')
        quit()


kwdvec_map = {}

def load_kwdvec(arg):
    model, pid, fname = arg
    return pid, load_text_nums(model, fname, False)

def load_kwdvecs():

    global kwdvec_dir

    if not kwdvec_dir.endswith('/'):
        kwdvec_dir += '/'

    print('Read kwdvecs: ' + kwdvec_dir)

    start = time.time()

    #for x in glob.glob(os.path.join(kwdvec_dir, '*:*')):
    #    print('- ' + str(x))
    files = glob.glob(os.path.join(kwdvec_dir, '*:*'))
    if len(files) == 0:
        print('NO FILES IN ' + kwdvec_dir + ' FOR KWD LOAD')
        quit()

    pids = [w.replace(kwdvec_dir, '') for w in list(files)]
    print('kwdvecs: ' + str(len(files)))

    zeros = [0] * len(pids)
    tmp = zip(zeros, pids, files)
    #for pid, fname in zip(pids, files):
    #    print('pid ' + pid + ' file ' + fname)
    #    kwdvec_map[pid] = load_text_nums(fname, False)
    print('Load kwdvecs, threads: ' + str(THREADS))
    pool = Pool(processes=THREADS)
    for pid, kwdvec in pool.map(load_kwdvec, tmp):
        #print('kv pid ' + pid)
        kwdvec_map[pid] = kwdvec
    pool.close()
    pool.terminate()
    pool.join()
    end = time.time()
    print('loaded ' + str(len(kwdvec_map)) + ' in ' + str(datetime.timedelta(0, end-start)))
    if len(kwdvec_map) == 0:
        print('NO KWD LOAD')
        quit()
 
hist_file_maps = []
hist_final_map = {}
jpg_final_map = {}

if 'HIST' in IN_MODE:
    for i in range(len(hist_dirs)):
        hist_file_maps.append({})

def load_it_hist(arg):
    map_fname = arg 
    #print('load ' + map_fname)
    hist_fname = hist_file_maps[0][map_fname]
    x = load_text_nums(0, hist_fname, True)
    #print('Sum ' + str(sum(x)) + ' ' + map_fname + '->' + hist_fname)
    for hist_model in range(1, len(hist_dirs)):
        hist_fname = hist_file_maps[hist_model][map_fname]
        x1 = load_text_nums(hist_model, hist_fname, True)
        #print('Sum x1 ' + str(sum(x1)))
        x = np.concatenate((x, x1), axis=0)   
    #print('Final Sum ' + str(sum(x)))
    if NORM_HIST_TO_MAX:
        x /= max(x)
    elif len(hist_dirs) > 1:
        x /= len(hist_dirs)
        #print('div types ' + str(sum(x)))
    #print('QUIT')
    #quit()
    #if len(x) != hist_input_dim:
    #    print('Bad ' + str(len(x)))
    #    quit()

    return map_fname, x
    # load_text_nums(model, hist_fname, True)
    #doneq.put([map_fname, load_hist(hist_fname)])

def norm_it(arg):
    map_fname, hist = arg
    return map_fname, hist - hist_sum


img_vec_shape = None

def load_it_jpg(map_fname):

    #print('load ' + map_fname)
    if DO_JPGV:
        # imagenet feature vecs
        img_vec_file = vecIdFile(map_fname)
        #print('-- load imgvec ' + img_vec_file)
        #quit()
        jpg = np.load(img_vec_file)
        #print('vec ' + str(jpg))
        #quit()
    else:
        jpg_fname = jpg_map[map_fname]
        jpg = image.load_img(jpg_fname)
        jpg = image.img_to_array(jpg)
        jpg = np.expand_dims(jpg, axis=0)
        # imagenet preproc
        jpg = preprocess_input(jpg)

    return map_fname, jpg

size_report = 'Sizes: '

def load_preproc():

    global hist_sum
    global img_vec_shape

    print('Pre-loading files... ' + str(datetime.datetime.now()))
    start = time.time()

    if DO_VEC:
        load_vecs()

    if DO_DB:
        load_dbvecs()

    if DO_KWDS:
        load_kwdvecs()

    if DO_JPGV or DO_JPG:

        print('Scanning jpg file names to map..')
        scan_file_jpgs_to_map(pair_dir, 'train.pos')
        scan_file_jpgs_to_map(pair_dir, 'train.neg')
        scan_file_jpgs_to_map(pair_dir, 'test.pos')
        scan_file_jpgs_to_map(pair_dir, 'test.neg')

        if DO_JPG:
            print('Load jpgs, threads: ' + str(THREADS))
        else:
            print('Load jpg vecs, threads: ' + str(THREADS))

        pool = Pool(processes=THREADS)

        for map_fname, jpg in pool.map(load_it_jpg, jpg_map.keys()):
            jpg_final_map[map_fname] = jpg
            #hist_file_maps[model][map_fname] = hist
        pool.close()
        pool.terminate()
        pool.join()
        end = time.time()
        print('loaded in ' + str(datetime.timedelta(0, end-start)))
        print('jpg_final_map size: ' + str(len(jpg_final_map)))

        if DO_JPGV:
            vec = next(iter(jpg_final_map.values()))
            img_vec_shape = vec.shape
            print('-- img vector shape: ' + str(img_vec_shape))

    if DO_HIST:
        for model in range(len(hist_dirs)):
            print('Make hist list from ' + pair_dir + \
                  '\n                for ' + hist_dirs[model])
            scan_file_hists(model, pair_dir, 'train.pos')
            scan_file_hists(model, pair_dir, 'train.neg')
            scan_file_hists(model, pair_dir, 'test.pos')
            scan_file_hists(model, pair_dir, 'test.neg')

        pool = Pool(processes=THREADS)
        print('Load hists, threads: ' + str(THREADS))
        l = []
        for map_fname, hist in hist_file_maps[0].items():
            l.append((map_fname))
        for map_fname, hist in pool.map(load_it_hist, l):
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
            quit()

def jpg_name(fname):
    fname = jpg_dir + '/' + fname
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
        quit()
    return fname

def hist_name(model, fname):
    fname = fname.replace('jpg', 'hist')
    fname = hist_dirs[model] + '/' + fname
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
    fname = os.path.expanduser(fname)
    if not os.path.isfile(fname):
        print('No file like: ' + fname)
        quit()
    return fname

jpg_map = {}

def scan_file_jpgs_to_map(direc, fname):
    global size_report
    real_fname = direc + '/' + fname
    print('  Scan file: ' + real_fname)
    ct0 = 0
    ct = 0
    ct2 = 0
    with open(real_fname) as fp:
        for line in fp:
            ct0 += 1
            map_fname1, map_fname2 = line.split()  # names are .jpg
            if map_fname1 not in jpg_map:
                ct += 1
                fname1 = jpg_name(map_fname1)
                #x = load_hist(fname1)
                jpg_map[map_fname1] = fname1
            else:
                ct2 += 1

            if map_fname2 not in jpg_map:
                ct += 1
                fname2 = jpg_name(map_fname2)
                #x = load_hist(fname2)
                jpg_map[map_fname2] = fname2
            else:
                ct2 += 1

    print('      pairs: ' + str(ct0) + ', new pics: ' + str(ct) + ', repeat pics: ' + str(ct2) + ', Total pics: ' + str(len(jpg_map)))
    size_report += fname + ': ' + str(ct0) + ' '

def scan_file_hists(model, direc, fname):
    global size_report
    real_fname = direc + '/' + fname
    print('  Scan file: ' + real_fname)
    ct0 = 0
    ct = 0
    ct2 = 0
    with open(real_fname) as fp:
        for line in fp:
            ct0 += 1
            map_fname1, map_fname2 = line.split()  # names are .jpg
            if map_fname1 in hist_file_maps[model]:
                ct2 += 1
            else:
                ct += 1
                fname1 = hist_name(model, map_fname1)
                #x = load_hist(fname1)
                hist_file_maps[model][map_fname1] = fname1

            if map_fname2 in hist_file_maps[model]:
                ct2 += 1
            else:
                ct += 1
                fname2 = hist_name(model, map_fname2)
                #x = load_hist(fname2)
                hist_file_maps[model][map_fname2] = fname2

    print('      pairs: ' + str(ct0) + ', new pics: ' + str(ct) + ', repeat pics: ' + str(ct2) + ', Total pics: ' + str(len(hist_file_maps[model])))
    size_report += fname + ': ' + str(ct0) + ' '

def create_cases(pr_type, which, interleave):
    print('create_cases: ' + pr_type + ' which=' + str(which))
    pairs = []
    labels = []
    if which == None:
        if interleave:
            # altenate pos/neg
            p1 = []
            l1 = []
            read_file(pair_dir + '/' + pr_type + '.pos', p1, l1, 0.)

            #print('Shuffling ' + pr_type + '.pos')
            #tmp = list(zip(p1, l1))
            #random.shuffle(tmp)
            #p1, l1 = zip(*tmp)

            p2 = []
            l2 = []
            read_file(pair_dir + '/' + pr_type + '.neg', p2, l2, 1.)

            print('Shuffling ' + pr_type + '.neg')
            tmp = list(zip(p2, l2))
            random.shuffle(tmp)
            p2, l2 = zip(*tmp)

            print('Interleaving pos/neg cases: ' + str(len(p1)) + '/' + 
                                               str(len(p2)))
            end = len(p1)
            if end > len(p2):
                end = len(p2)
                print('WARN: Ignoring extra pos: ' + str(len(p1)-end))
            elif len(p2) > end:
                print('WARN: Ignoring extra neg: ' + str(len(p2)-end))
                #quit()
            for i in range(0, end, 1):
                pairs.append(p1[i])
                labels.append(l1[i])
                pairs.append(p2[i])
                labels.append(l2[i])
        else:
            read_file(pair_dir + '/' + pr_type + '.pos', pairs, labels, 0.)
            read_file(pair_dir + '/' + pr_type + '.neg', pairs, labels, 1.)

    elif which == True:
        read_file(pair_dir + '/' + pr_type + '.pos', pairs, labels, 0.)
    else:
        read_file(pair_dir + '/' + pr_type + '.neg', pairs, labels, 1.)
    # TODO interleave?
    #return np.array(pairs), np.array(labels)
    return pairs, labels

def read_file(fname, pairs, labels, label):
    with open(fname) as fp:
        for line in fp:
            fname1, fname2 = line.split()
            pairs += [[fname1, fname2]]
            #x = file_map[fname1]
            #y = file_map[fname2]
            #pairs += [[x, y]]
            labels += [label]





SHUF_MASTER = True # True for normal shuffle
#SHUF_MASTER = False

# myGen will be called 1x more than # epochs it seems

rotate = False
ROT = 1

def myGen(tag, pairs, labels, batch_size, shuffle):

    global rotate, ROT

    data_size = len(labels)
    print('myGen: ' + tag + ' ' + str(len(pairs)) + ' ' + str(data_size) +
                ' batch ' + str(batch_size) + ' shuf ' + str(shuffle))
    if CHECK_DATA2:
        x = np.sum(labels)
        if np.isnan(x):
            print('lbls: Got NaN: ' + fname)
            quit()
        if np.isinf(x):
            print('lbls: Got Inf: ' + fname)

    if batch_size > data_size:
        print('myGen ' + tag + ': batch_size ' + str(batch_size) + 
                    ' > data ' + str(data_size) + ': quitting!')
        quit()

    if shuffle and not SHUF_MASTER:
        shuffle=False
        print('DEBUG - no shuffle on ' + tag)

    abs_batch = abs(batch_size)

    if DO_VEC:
        vecdims = [abs_batch, side_vec_dim]
        vec1 = np.zeros((vecdims))
        vec2 = np.zeros((vecdims))

    if DO_DB:
        dbdims = [abs_batch, dbvec_dim]
        dbv1 = np.zeros((dbdims))
        dbv2 = np.zeros((dbdims))

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
        if JPG_224:
            jdims = [abs_batch, 224, 224, 3]
        elif JPG_299:
            jdims = [abs_batch, 299, 299, 3]
        else:
            jdims = [abs_batch, 331, 331, 3]

        jpg1 = np.zeros((jdims))
        jpg2 = np.zeros((jdims))

    batch_labels = np.zeros(abs_batch)

    if batch_size < 0:

        while 1:

            #batch_size *= -1
            start = data_size + batch_size  # subtracts
           
            ix = 0
            for j in range(start, data_size, 1):
                # ix = j % data_size
                fname1, fname2 = pairs[j]
                id1 = procId(fname1)
                id2 = procId(fname2)

                if DO_VEC:
                    vec1[ix] = side_vec_map[id1 + '_l']
                    vec2[ix] = side_vec_map[id2 + '_r']
                if DO_DB:
                    dbv1[ix] = dbvec_map[id1]
                    dbv2[ix] = dbvec_map[id2]
                if DO_KWDS:
                    kv1[ix] = kwdvec_map[id1]
                    kv2[ix] = kwdvec_map[id2]
                if DO_HIST:
                    hv1[ix] = hist_final_map[fname1]
                    hv2[ix] = hist_final_map[fname2]
                    #print('XXX ' + str(len(hv1[ix])))
                    #quit()
                if DO_JPGV or DO_JPG:
                    jpg1[ix] = jpg_final_map[fname1]
                    jpg2[ix] = jpg_final_map[fname2]

                #if ix == 0:
                #    print('SIZES  ' + str(len(jpg1[0])) + ' ' + \
                #                     str(len(kv1[0])) + ' ' + \
                #                     str(len(hv1[0])))
                batch_labels[ix] = labels[j]
                ix += 1

                #print('-- ' + str(j) + ' ' + fname1 + ' ' + fname2 + ' ' + str(labels[j]))

            #print('yield ' + str(len(t)) + ' ' + str(len(tt)))
            #yield  [t[:, 0], t[:, 1]], tt 
            list1 = []
            list2 = []
            if DO_VEC:
                list1.append(vec1)
                list2.append(vec2)
            if DO_DB:
                list1.append(dbv1)
                list2.append(dbv2)
            if DO_KWDS:
                list1.append(kv1)
                list2.append(kv2)
            if DO_HIST:
                list1.append(hv1)
                list2.append(hv2)
            if DO_JPGV or DO_JPG:
                list1.append(jpg1)
                list2.append(jpg2)
            #list1 = list1 + list2
            #print('isnull l1 ' + str(np.isnan(np.sum(list1))) + ' l2 ' +str(np.isnan(np.sum(list2))) + ' lbl ' + str(np.isnan(np.sum(batch_labels))))
            yield  list1 + list2, batch_labels

            if DO_VEC:
                vec1 = np.zeros((vecdims)) 
                vec2 = np.zeros((vecdims)) 

            if DO_DB:
                dbv1 = np.zeros((dbdims)) 
                dbv2 = np.zeros((dbdims)) 

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

            batch_labels = np.zeros(abs_batch)

            #yield  [jpg1, jpg2], batch_labels
  
    totpred = 0

    yieldct = 0

    if tag == 'train_rot':
        # leave set for test phase
        rotate = True # bool(random.getrandbits(1))
        a_key = next(iter(side_vec_map))
        l = len(side_vec_map[a_key])
        ROT = random.randint(0, l)
        print('\n-- permute/train: rotate: ' + str(rotate) + ' rot ' + str(ROT))

    while 1:

        if shuffle:
            print('\nshuffle ' + tag)
            tmp = list(zip(pairs, labels))
            random.shuffle(tmp)
            pairs, labels = zip(*tmp)

        #print('gen/go')
        #print('loop ' + tag + ' size ' + str(data_size))
        for i in range(0, data_size, batch_size):
            end = i + batch_size

            #if tag == 'valtrain':
            #    print('\nyield ' + str(yieldct) + ' i ' + str(i) + 
            #            ' end ' + str(end))
            if end > data_size:
                if shuffle is False:  # straight 1-time eval
                    print(tag + ': no shuffle but going off end (dropping/wrapping): ' + str(data_size) + ' ' + str(end))
                    #quit()
                end = data_size
                break
            ix = 0
            for j in range(i, end):
                # ix = j % data_size
                fname1, fname2 = pairs[j]
                id1 = procId(fname1) 
                id2 = procId(fname2)

                if DO_VEC:
                    #print('-- ids ' + id1 + ' ' + id2 + ' sz1 ' + str(len(side_vec_map[id1 + '_l'])))
                    if rotate and tag == 'train_rot':
                        vec1[ix] = np.roll(side_vec_map[id1 + '_l'], ROT)
                        vec2[ix] = np.roll(side_vec_map[id2 + '_r'], ROT)
                    else:
                        vec1[ix] = side_vec_map[id1 + '_l']
                        vec2[ix] = side_vec_map[id2 + '_r']

                if DO_DB:
                    dbv1[ix] = dbvec_map[id1]
                    dbv2[ix] = dbvec_map[id2]

                if DO_KWDS:
                    kv1[ix] = kwdvec_map[id1]
                    kv2[ix] = kwdvec_map[id2]

                if DO_HIST:
                    hv1[ix] = hist_final_map[fname1]
                    hv2[ix] = hist_final_map[fname2]

                if DO_JPGV or DO_JPG:
                    jpg1[ix] = jpg_final_map[fname1]
                    jpg2[ix] = jpg_final_map[fname2]
                    #print(tag + '_YYY ' + fname1 + ' => ' + str(len(hv1[ix])) + '\n' + str(sum(hv1[ix])))
                    #print(tag + '_YY2 ' + fname2 + ' => ' + str(len(hv2[ix])) + '\n' + str(sum(hv2[ix])))
                    #quit()

                #if ix == 0:
                #    print('SIZES  ' + str(len(jpg1[0])) + ' ' + \
                #                     str(len(kv1[0])) + ' ' + \
                #                     str(len(hv1[0])))
                batch_labels[ix] = labels[j]
                ix += 1

                #print('-- ' + str(j) + ' ' + fname1 + ' ' + fname2 + ' ' + str(labels[j]))
            #totpred += len(batch_labels)
            #if end == data_size:
            #    print('end: ' + str(totpred))
            #if len(batch_hists) > 0:

            # tmp = list(zip(batch_hists, batch_labels))
            # random.shuffle(tmp)
            # batch_hists, batch_labels = zip(*tmp)

            #print('yield ' + str(len(t)) + ' ' + str(len(tt)))
            yieldct += 1
            #if tag == 'valtrain':
            #print('\nyield ' + str(yieldct) + 
            #            ' end ' + str(end) + ' size ' + str(len(t)))
            #yield  [t[:, 0], t[:, 1]], tt

            list1 = []
            list2 = []
            if DO_VEC:
                list1.append(vec1)
                list2.append(vec2)
            if DO_DB:
                list1.append(dbv1)
                list2.append(dbv2)
            if DO_KWDS:
                list1.append(kv1)
                list2.append(kv2)
            if DO_HIST:
                list1.append(hv1)
                list2.append(hv2)
            if DO_JPGV or DO_JPG:
                list1.append(jpg1)
                list2.append(jpg2)

            #print('--list1,2[0] len: ' + str(len(list1[0])) + ', ' + str(len(list2[0])))

            #print('isnull2 l1 ' + str(np.isnan(np.sum(list1))) + ' l2 ' +str(np.isnan(np.sum(list2))) + ' lbl ' + str(np.isnan(np.sum(batch_labels))))
            yield  list1 + list2, batch_labels
            #yield  [jpg1, jpg2], batch_labels

            if DO_VEC:
                vec1 = np.zeros((batch_size, side_vec_dim)) 
                vec2 = np.zeros((batch_size, side_vec_dim)) 

            if DO_DB:
                dbv1 = np.zeros((batch_size, dbvec_dim)) 
                dbv2 = np.zeros((batch_size, dbvec_dim)) 

            if DO_KWDS:
                kv1 = np.zeros((batch_size, kwdvec_dim)) 
                kv2 = np.zeros((batch_size, kwdvec_dim)) 

            if DO_HIST:
                hv1 = np.zeros((batch_size, hist_input_dim)) 
                hv2 = np.zeros((batch_size, hist_input_dim)) 

            if DO_JPGV:
                jpg1 = np.zeros((jvdims))
                jpg2 = np.zeros((jvdims))

            elif DO_JPG:
                jpg1 = np.zeros((jdims))
                jpg2 = np.zeros((jdims))

            batch_labels = np.zeros(batch_size)

        #if shuffle:
        #    print('\nshuffle ' + tag)
        #    tmp = list(zip(pairs, labels))
        #    random.shuffle(tmp)
        #    pairs, labels = zip(*tmp)

STABILITY = 0.00001

# need?
def contrastive_loss(y_true, y_pred):
    '''Contrastive loss from Hadsell-et-al.'06
    http://yann.lecun.com/exdb/publis/pdf/hadsell-chopra-lecun-06.pdf
    '''
    margin = 1
    return K.mean(y_true * K.square(y_pred) +
                  (1 - y_true) * K.square(K.maximum(margin - y_pred, 0)))

def f1_score(y_true, y_preds, pred_threshold=0.5, beta=1):
    y_preds = np.array([(y_pred > pred_threshold) for y_pred in y_preds])
    TP=[]; TN=[]; FP=[]; FN=[]
    for (y_t, y_p) in zip(y_true, y_preds):
        TP.append(1 if (y_t == 1) and (y_p == 1) else 0)
        TN.append(1 if (y_t == 0) and (y_p == 0) else 0)
        FP.append(1 if (y_t == 0) and (y_p == 1) else 0)
        FN.append(1 if (y_t == 1) and (y_p == 0) else 0)
    TP, TN, FP, FN = np.sum(TP), np.sum(TN), np.sum(FP), np.sum(FN)
       
    precision   = TP / (TP + FP) if not (TP == 0 and FP == 0) else 0
    recall      = TP / (TP + FN) if not (TP == 0 and FN == 0) else 0
    specificity = TN / (TN + FN) if not (TN == 0 and FN == 0) else 0
   
    if not (precision==0 and recall==0):
        return (1 + beta)*precision*recall/(beta*precision + recall)
    else:
        if np.sum(y_true) != 0:
            return 0           # '1' labels present, none guessed
        else:
            return specificity # '1' labels absent,  return '0' class accuracy

def compute_accuracy(predictions, labels):
    '''Compute classification accuracy with a fixed threshold on distances.
    '''
    #pred = np.concatenate(predictions)
    if len(predictions) != len(labels):
        print('Lennn')
        quit()
    n = len(labels)
    if n == 0:
        print('compute_accuracy: no labels');
        quit()
    n_ok_pos = 0
    n_ok_neg = 0
    expected_match = 0
    for i in range(0, n, 1):
        match = labels[i]
        if match == 0.0:
            expected_match = expected_match + 1
        elif match != 1.0:
            print('not 0/1. ' + str(match) + ' ' + match.shape)
            quit()
        if predictions[i] < 0.5: # match
            if match == 0.0:
                n_ok_pos += 1
        else:
            if match == 1.0:
                n_ok_neg += 1

    n_ok = n_ok_pos + n_ok_neg
    #labels = np.array(labels)
    #print('pred shape ' + str(pred.shape))
    #print('labels shape ' + str(labels.shape))
    #print('PR ' + str(predictions))
    #x = labels[predictions.ravel() < 0.5]
    #print('-> ' + str(x))
    #print('Labels: ' + str(labels))
    #print('Pos acc: ' + str(int(100 * n_ok_pos/expected_match))) # not good for 0
    #print('Neg acc: ' + str(int(100 * n_ok_neg/(n-expected_match))))
    #return labels[predictions.ravel() < 0.5].mean()
    acc = n_ok / float(n)
    print('Net acc: ' + str(n_ok) + '/' + str(n) + ' ' + str(int(100 * acc)))
    return acc


print('-- kernel INITIALIZER_MODE: ' + INITIALIZER_MODE + \
            ' init: ' + str(kernel_init_i) + \
            '=' + kernel_initializers[kernel_init_i] + \
            ' List: ' + str(kernel_initializers))

#BxIAS_INIT = ''
SNN = False
#SNN = True
if SNN:
    print('=== Selu/AlphaDropout')
    ACTIVATION = 'selu'
    BxIAS_INIT=", bias_initializer='zeros'"
SEED = None
#SEED = 1234567

CONV_DROPOUT = 0.1
WIDE = False

if TUNER:
    #import keras_tuner as kt
    from keras_tuner import RandomSearch, Hyperband, BayesianOptimization

REGPEN=1e-4

alg_notes = ''

# inp = (jpg, histograms)
def create_siamese_net():

    global alg_notes

    if DO_VEC:
        print('-- vecs not in siamese')
        quit()

    kinit = kernel_initializers[kernel_init_i]

    ins = []
    output_list = []

    if DO_DB:

        alg_notes += 'D' + str(dbvec_dim) + '_'

        dbvec_in = Input(shape=(dbvec_dim,))
        ins.append(dbvec_in)

        db = Dense(dbvec_dim, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(dbvec_in)

        for i in range(1):
            db = Dense(32, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(db)
        for i in range(1):
            db = Dense(16, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(db)
            db = Dense(8, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(db)
            db = Dense(16, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(db)
        for i in range(1):
            db = Dense(32, activation=ACTIVATION, \
                        kernel_initializer=kinit,
                        bias_initializer='zeros')(db)

        output_list.append(db)

    if DO_KWDS:

        alg_notes += 'K' + str(kwdvec_dim) + '_'

        kvec_in = Input(shape=(kwdvec_dim,))
        ins.append(kvec_in)

        kwd = Dense(kwdvec_dim, activation=ACTIVATION, \
                        kernel_initializer=kinit, \
                        bias_initializer='zeros')(kvec_in)
        kwd = Dense(128, activation=ACTIVATION, \
                        kernel_initializer=kinit, \
                        bias_initializer='zeros')(kwd)
        kwd = Dense(256, activation=ACTIVATION, \
                        kernel_initializer=kinit, \
                        bias_initializer='zeros')(kwd)

        output_list.append(kwd)

    if DO_HIST:

        if NORM_HIST_TO_MAX:
            alg_notes += 'Hmx_' + hist_alg + str(hist_input_dim) + '_'
        else:
            alg_notes += 'H_' + hist_alg + str(hist_input_dim) + '_'

        hist_in = Input(shape=hist_input_shape)
        ins.append(hist_in)

        hist_d = Dense(hist_dense_size, 
                        activation=ACTIVATION, 
                        kernel_initializer=kinit)(hist_in)
        hist_d = Dropout(0.1)(hist_d)
        hist_d = Dense((hist_dense_size*2)/3, 
                        activation=ACTIVATION,
                        kernel_initializer=kinit)(hist_d)
        hist_d = Dense(hist_dense_size/4, 
                        activation=ACTIVATION,
                        kernel_initializer=kinit)(hist_d)
        hist_d = Dense((hist_dense_size*2)/3, 
                        activation=ACTIVATION,
                        kernel_initializer=kinit)(hist_d)
        hist_d = Dense(hist_dense_size/6, 
                        activation=ACTIVATION,
                        kernel_initializer=kinit)(hist_d)
        hists = GaussianDropout(0.1)(hist_d)
        for i in range(4):
            hists = Dense(256, activation=ACTIVATION,
                        kernel_initializer=kinit)(hists)
        #for i in range(3):
        #    hists = Dense(128, activation='relu', 
        #                kernel_initializer=kinit)(hists)
        output_list.append(hists)

    if DO_JPGV:

        #print('-- img vec shape ' + str(img_vec_shape))
        #quit()
        jpgv_in = Input(shape=img_vec_shape)
        ins.append(jpgv_in)

        top = jpgv_in

        alg_notes += IN_JPG_MODE + 'v'

    elif DO_JPG:

        jpg_in = Input(shape=jpg_shape)
        ins.append(jpg_in)

        alg_notes += IN_JPG_MODE

        # spatialized = ConcatSpatialCoordinate()(jpg_in)

        # 224x224
        if 'VGG16' in IN_JPG_MODE:
            image_model = VGG16(
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')

        # 224x224
        elif 'VGG19' in IN_JPG_MODE:
            image_model = VGG19(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')

        # 224x224
        elif 'DenseNet121' in IN_JPG_MODE:
            image_model = DenseNet121(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')
        elif 'DenseNet169' in IN_JPG_MODE:
            image_model = DenseNet169(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')
        elif 'ResNet152V2' in IN_JPG_MODE:
            image_model = ResNet152V2(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')

        elif 'Xception' in IN_JPG_MODE:
            # 299x299
            image_model = Xception(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')

        elif 'NASNetLarge' in IN_JPG_MODE:
            # 331x331
            alg_notes += 'NASNetL'
            image_model = NASNetLarge(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')

        elif 'EfficientNetB7' in IN_JPG_MODE:
            # 600x600
            alg_notes += 'EffNetB7'
            image_model = EfficientNetB7(#input_shape=jpg_shape,
                        pooling=POOLING,
                        input_tensor=jpg_in,
                        include_top=False, 
                        weights='imagenet')
        else:
            print('No image model set')
            quit()

        if POOLING is not None:
            alg_notes += 'P_' + POOLING + '_'

        image_model_layers = len(image_model.layers)

        if not SINGLE_EPOCH:

            if PRUNE:
                detrain_layers = 0      # no effect!
            else:
                if DETRAIN_ALL_JPG:
                    detrain_layers = image_model_layers
                    de_tag = 'all_' + str(image_model_layers)
                else:
                    detrain_layers = int(image_model_layers * 0.8)
                    de_tag = str(detrain_layers) + '_' + str(image_model_layers)

                alg_notes += '_de_' + de_tag + '_'
            
                #print('== DETRAINING IMAGE NET, layers ' + \
                #       str(detrain_layers) + '/' + str(nlayers))
                for i in range(detrain_layers):
                    image_model.layers[i].trainable = False

        # 'top'
        top = image_model.output

    # first after this thru siam_img_name will maybe be used
    #   to shrink imagenet vectors before saving.
    top._name = 'img_top'

    if DO_JPG or DO_JPGV:

        if POOLING is not None:

            # Pooling

            top =  Dense(1024, activation='relu')(top)
            top =  Dense(512, activation='relu')(top)

        else:

            # non-Pooling

            conv1x1 = Conv2D(512, (1, 1), activation=ACTIVATION)(top)
            top =  Flatten()(conv1x1)

        flat_size = K.int_shape(top)[1]
        print('JPG FLAT ' + str(flat_size))

        # layers from 'img_top' to this will be extracted on a possible
        #  long term basis, if so, maybe save them separately when saving
        #  model.h5's for faster reloading over many uses.
        siam_img_name = 'siam_img_top'
        if WIDE:
            # tinkered briefly
            top = Dense(128, activation=ACTIVATION, name=siam_img_name)(top)
        else:
            top = Dense(128, activation=ACTIVATION, name=siam_img_name)(top)

        output_list.append(top)

    if len(output_list) > 1:
        merged = concatenate(output_list)
    else:
        merged = output_list[0]

    if FAT: # more weight, no improvement, STEEP trains ok
        fat_dim = int(tot_input_dim * 0.5)
        print('FAT dim ' + str(fat_dim))
        merged = Dense(fat_dim, activation=ACTIVATION,
                kernel_initializer=kinit,
                bias_initializer='zeros')(merged)
        '''
        merged = Dense(int(tot_input_dim/2), activation=ACTIVATION,
                kernel_initializer=kinit,
                bias_initializer='zeros')(merged)
        '''

    if alg_notes[-1] == '_':
        alg_notes = alg_notes[:-1]

    print('Siamese: ' + alg_notes)
    print('Merged/output size: ' + str(merged.get_shape()))

    # trying batchnorm alternative: big batches
    # if not ((DO_JPG or DO_JPGV) and min(batches) > 500):
    merged = BatchNormalization()(merged)

    merged = Dense(512, activation=ACTIVATION, 
                    kernel_initializer=kinit,
                    bias_initializer='zeros')(merged)
    if SNN:
        print('SNN->AlphaDropout')
        merged = AlphaDropout(0.15)(merged)
    else:
        merged = Dropout(0.2)(merged)

    #print('Final merged/output size: ' + str(len(merged)))
    print('Final Merged/output size: ' + str(merged.get_shape()))

    model = Model(ins, [merged])

    return model

# feed vecs from side nets trained with histo/kwd/jpg data
#  simple in==out if one vec set, 
#  else 1+ Dense to get to SIDE_VEC_LEN

# not used
def create_side_net_feed(side, inp):

    if side_vec_dim == 0:
        print('create_side_net_feed: no side vecs loaded')
        quit()

    print('side_vec_dim: ' + str(side_vec_dim))

    kinit = kernel_initializers[kernel_init_i]

    x = inp

    if len(side_vec_dims) == 1:
        if 'STEM' in IN_VEC_MODE:
            print('-- VEC_MODE.STEM: only one vec, not stemming.')
    else:

        if 'STEM' in IN_VEC_MODE:

            x = Dense(side_vec_dim/2, activation=ACTIVATION,
                    kernel_initializer=kinit)(x)
            x = Dense(side_vec_dim/2, activation=ACTIVATION,
                    kernel_initializer=kinit)(x)

        x = Dense(SIDE_VEC_LEN, activation=ACTIVATION,
                kernel_initializer=kinit)(x)
    
    # might be just inp->inp

    model = Model(inp, [x], name='side_' + side)

    return model

#QQQ

# inp = Inputs: jpg, kwd_vec, hists, side_vec_len
def create_side_net(side, siamese, inp, side_vec_len):
    siamout = siamese(inp)

    # this is to keep track of what goes on what side

    side_dim = 1024

    kinit = kernel_initializers[kernel_init_i]

    side_d1 = Dense(side_dim, activation=ACTIVATION, 
                kernel_initializer=kinit,
                bias_initializer='zeros')(siamout)

    side_d2 = Dense(256, activation=ACTIVATION, 
                kernel_initializer=kinit,
                bias_initializer='zeros')(side_d1)
    side_d3 = Dense(128, activation=ACTIVATION, 
                kernel_initializer=kinit,
                bias_initializer='zeros')(side_d2)
    print('-- Side ' + side + ': side_vec_len: squash side net to ' + \
            str(side_vec_len))

    side_hook = Dense(side_vec_len, activation=ACTIVATION, 
                name='side_' + side,
                kernel_initializer=kinit)(side_d3)

    model = Model(inp, [side_hook], name='side_' + side)

    return model

def stem_sigmoid(left, right, side_vec_len, hp):

    if STEM_JOIN == 'concat':
        merged = concatenate([left, right])
    else:
        merged = left + right

    #merged = Dense(1024, ... no good

    kinit = kernel_initializers[kernel_init_i]

    if NATHAN:
        #TODO: retry, trying disabled for narrow vec
        #if not DO_JPG:
        #    merged = Dense(256)(merged)
        # trying batchnorm alternative: big batches
        # if not ((DO_JPG or DO_JPGV) and min(batches) > 500):
        merged = BatchNormalization()(merged)
        merged = Nathan()(merged)
        #Dropout ~.4 makes pos > neg, e.g. 84:81
        #merged = Dropout(0.2)(merged)
    else:
        if not DO_JPGV and not DO_JPG:
            merged = Dense(256, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
    #merged = Dense(512, activation=ACTIVATION,  
    #                 kernel_initializer=kinit)(merged)
    n_sig = 12
    if TUNER:
        if Horiz:
            # explore n_sig in 2..20
            n_sig = hp.Int("nsig", min_value=2, max_value=20, step=1)
        else:
            # explore n_sig in 4..30
            n_sig = hp.Int("nsig", min_value=2, max_value=20, step=1)

        print('--- TUNE: stdepth is ' + str(n_sig))

    #elif not DO_VEC and SIDE_VEC_LEN < 3:
    #    n_sig = 30
    #    print('--- SID_VEC_LEN<3: stdepth is ' + str(n_sig))

    elif DO_HIST and not FAT:
        if Horiz:
            if side_vec_len > 4:
                # tested for side_vec_len==12
                n_sig = 2
            elif side_vec_len == 4:
                # untested
                n_sig = 4
            elif side_vec_len == 3:
                # untested
                n_sig = 8
            else:
                # untested
                n_sig = 16
        else:
            if side_vec_len > 4:
                # tested for side_vec_len==12
                n_sig = 2
            elif side_vec_len == 4:
                # 8 works, 12 is too many => 10 for now
                n_sig = 10
            elif side_vec_len == 3:
                n_sig = 3
            else:
                # untested
                n_sig = 20

        #n_sig = 8
        #print('CHOP final Dense.128s 12->2 for HIST w/o FAT: ' + str(n_sig))

    elif DO_VEC:
        if STEM_JOIN == 'add':
            # no good w/ width 3, placeholder
            n_sig = 8
        else:
            n_sig = 36
        print('-- Stem: using depth for imported vecs: ' + str(n_sig))

    if STEM_JOIN == 'concat':
        TS2 = 2 * side_vec_len
    else:
        TS2 = side_vec_len

    #if n_sig == 1:
    #    print('-- expt TS2==side for nsig==1')
    #    TS2 = side_vec_len

    #print('-- expt nsig=0')
    #n_sig = 0

    print('-- n_sig ' + str(n_sig) + ' TS2 ' + str(TS2))
    for i in range(n_sig):
       merged = Dense(TS2, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
    '''
    if DO_VEC:
        merged = Dense( 2 * TS2, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
        merged = Dense( 15 * TS2, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
        merged = Dense( 2 * TS2, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
        for i in range(n_sig):
            merged = Dense(TS2, activation=ACTIVATION,
                     kernel_initializer=kinit,
                     bias_initializer='zeros') (merged)
    '''

    sigmoid = Dense(1, activation='sigmoid')(merged)
    return sigmoid


if (DO_JPGV or DO_JPG) and OPTIMIZER == 'adadelta' and batches[BATCH_INDEX_START]> 96:
    print('Recommend for jpg+adadelta drop batch ' + str(batches[BATCH_INDEX_START]) + '->96')
    #batch_size = 96

print('-- OPT=' + OPTIMIZER);

if OPTIMIZER == 'AdamW':
    sys.path.append('../keras-adamw/keras-adamw')
    from keras_adamw.optimizers import AdamW
    from keras_adamw.utils import get_weight_decays, fill_dict_in_order


# inX = list of Inputs: jpg, hists
def create_network(in1, in2, hp):

    global alg_notes
    global kernel_init_i
    global prev_history

    prev_history = None

    alg_notes = 'ki_' + kernel_initializers[kernel_init_i] + '_'

    if IN_MODE == 'VEC':

        # LR_VECS from prev single-pass predictions
        # initially just a pass-through, 1+ size 2+, 
        #   if > 1 concat and maybe normalized at the 
        #   load phase as with histos but with left/right-specific
        #   versions. 
        #
        #   TODO: allow this to be composed with raw data.
        
        alg_notes += 'V' + str(len(side_vec_dims)) + '_' + \
                                str(side_vec_dim) + '_'

        stem = stem_sigmoid(in1[0], in2[0], side_vec_dim, None)

    else: 

        # 'RAW' data unprocessed by neural nets

        m_siamese = create_siamese_net()

        side_vec_len = SIDE_VEC_LEN
        if TUNER:
            # explore SIDE_VEC_LEN in 2..12
            side_vec_len = hp.Int("svl", min_value=2, max_value=12, step=1)
            print('--- TUNE: svl is ' + str(side_vec_len))

        m_left = create_side_net('left', m_siamese, in1, side_vec_len)

        m_right = create_side_net('right', m_siamese, in2, side_vec_len)

        stem = stem_sigmoid(m_left.output, m_right.output, side_vec_len, hp)

    # QQQ
    model = Model(in1 + in2, [stem])

    if PRUNE:
        pruner = LotteryTicketPruner(model)


    if GCTF:
        
        if 'SGD' in OPTIMIZER:
            if not DO_VEC and SIDE_VEC_LEN < 3:
                print('-- gctf.SGD on lrv=2: orig lr=0.01, momentum=0.7, nesterov=True')
                opt = gctf.optimizers.sgd(learning_rate=0.01, momentum=0.7, nesterov=True)
            else:
                opt = gctf.optimizers.sgd(learning_rate=0.02, momentum=0.9, nesterov=False)
            print('-- SGD: gctf.optimizers.sgd')
        elif OPTIMIZER == 'RMSProp':
            if not DO_VEC and SIDE_VEC_LEN < 3:
                # won't train for vb+he_normal w/ 1e-3,1e-5,1e-7, 1e-9
                opt = gctf.optimizers.rmsprop(learning_rate = 1e-4)
                print('-- RMSProp lrv<3: gctf.optimizers.rmsprop 1e-5')
            else:
                opt = gctf.optimizers.rmsprop(learning_rate = 1e-4)
                print('-- RMSProp: gctf.optimizers.rmsprop')
        elif OPTIMIZER == 'Adam':
            print('-- Adam: gctf.optimizers.adam')
            opt = gctf.optimizers.adam()
        else:
            print('-- todo.gtcf: ' + opt)
            quit()

    else:  # non-gctf

        if OPTIMIZER == 'AdamW':
            wd_dict = get_weight_decays(model)
            weight_decays = fill_dict_in_order(wd_dict, [4e-4, 1e-4])
            lr_multipliers = {'dense': 0.5}
            opt = AdamW(lr=1e-4, weight_decays=weight_decays, 
                    lr_multipliers=lr_multipliers,
                    use_cosine_annealing=True, total_iterations=24)
        elif OPTIMIZER == 'SWA_SGD':
            #START_EPOCHS=8
            # SGD from gen search:
            # https://github.com/snf/keras-fractalnet/blob/master/src/cifar100_fractal.py
            print('SWA_SGD: lr=0.02, momentum=0.9, nesterov=True')
            opt = SGD(lr=SWA_INIT_LR, momentum=0.9, nesterov=True)
        elif 'SGD' in OPTIMIZER:
            if not DO_VEC and SIDE_VEC_LEN < 3:
                print('-- SGD on lrv=2: orig lr=0.01, momentum=0.7, nesterov=False')
                opt = SGD(learning_rate=0.01, momentum=0.7, nesterov=False)
            else:
                print('-- SGD: orig lr=0.02, momentum=0.9, nesterov=False')
                opt = SGD(learning_rate=0.02, momentum=0.9, nesterov=False)
        else:
            opt = OPTIMIZER


    if TUNER:
        model.compile(optimizer=opt,
                        loss='binary_crossentropy', metrics=['binary_accuracy'])

    else:
        model.compile(optimizer=opt,
            loss='binary_crossentropy'
            #loss='kullback_leibler_divergence',
            #loss='mean_squared_error',
            #loss='binary_crossentropy', metrics=['binary_accuracy']
            #options=run_opts
        )

    return model

tr_rounds_list = []
tr_epochs_list = []
tr_batch_list = []

tr_optimizer_list = []

tr_acc_list = []
tr_pos_acc_list = []
tr_neg_acc_list = []

te_acc_list = []
te_pos_acc_list = []
te_neg_acc_list = []
te_sum_acc_list = []

tr_te_net_mins = []

te_run_pos_acc_list = []
te_run_neg_acc_list = []
te_run_sum_acc_list = []

model_names = []

def printSum():
    N = len(te_run_pos_acc_list)
    if N > 0:
        print('\n**** Summary N=' + str(N))
        #print('Train %: ' + 
        #        " ".join(["{:.2f}".format(100*x) for x in tr_acc_list] ))
        #print('Test  %: ' + 
        #        " ".join(["{:.2f}".format(100*x) for x in te_acc_list] ))

        max_pos = max(te_run_pos_acc_list)
        max_pos_ix = te_run_pos_acc_list.index(max_pos)
        print('Run Pos Test max/avg:  %0.2f' % 
                    (100*max_pos) +
                ' (%0.2f)' % 
                    (100*te_run_neg_acc_list[max_pos_ix]) +
                '  %0.2f' % 
                    ((100*sum(te_run_pos_acc_list))/N) +
                '    '  + str(N))
        max_neg = max(te_run_neg_acc_list)
        max_neg_ix = te_run_neg_acc_list.index(max_neg)
        print('Run Neg Test max/avg:  %0.2f' % 
                    (100*max_neg) +
                ' (%0.2f)' % 
                    (100*te_run_pos_acc_list[max_neg_ix]) +
                '  %0.2f' % 
                    ((100*sum(te_run_neg_acc_list))/N) +
                '    ' + str(N))
        print('Run Sum Test max/avg:  %0.2f' % 
                    (100*max(te_run_sum_acc_list)) +
                '  %0.2f' % 
                    ((100*sum(te_run_sum_acc_list))/N) +
                '    ' + str(N))
        max_sum = max(te_run_sum_acc_list)
        max_sum_ix = te_run_sum_acc_list.index(max_sum)
        print('Run Top pos/neg for top sum:  %0.2f' %
                    (100*te_pos_acc_list[max_sum_ix]) +
                '  %0.2f' % 
                    (100*te_neg_acc_list[max_sum_ix]) +
                '    ' + str(max_sum_ix) + '/' + str(N))

        N = len(te_pos_acc_list)

        max_pos = max(te_pos_acc_list)
        max_pos_ix = te_pos_acc_list.index(max_pos)
        print('All: Pos Test max/avg:  %0.2f' % 
                    (100*max_pos) +
                ' (%0.2f)' % 
                    (100*te_neg_acc_list[max_pos_ix]) +
                '  %0.2f' % 
                    ((100*sum(te_pos_acc_list))/N) +
                '    '  + str(N))
        max_neg = max(te_neg_acc_list)
        max_neg_ix = te_neg_acc_list.index(max_neg)
        print('All Neg Test max/avg:  %0.2f' % 
                    (100*max_neg) +
                ' (%0.2f)' % 
                    (100*te_pos_acc_list[max_neg_ix]) +
                '  %0.2f' % 
                    ((100*sum(te_neg_acc_list))/N) +
                '    ' + str(N))
        max_sum = max(te_sum_acc_list)
        max_sum_ix = te_sum_acc_list.index(max_sum)
        print('All Top pos/neg for top sum:  %0.2f' %
                    (100*te_pos_acc_list[max_sum_ix]) +
                '  %0.2f' % 
                    (100*te_neg_acc_list[max_sum_ix]) +
                '    ' + str(max_sum_ix) + '/' + str(N))

        print('All Sum Test max/avg:  %0.2f' % 
                    (100*max(te_sum_acc_list)) +
                '  %0.2f' % 
                    ((100*sum(te_sum_acc_list))/N) +
                '    ' + str(N))

        print('Training Rounds: ' + \
                " ".join(["{:5d}".format(x) for x in tr_rounds_list] ))
        print('Optimizer:       ' + \
                " ".join(["{:>5}".format(x) for x in tr_optimizer_list] ))
        print('Epochs/Round:    ' + \
                " ".join(["{:5d}".format(x) for x in tr_epochs_list] ))
        print('Batch/Round:     ' + \
                " ".join(["{:5d}".format(x) for x in tr_batch_list] ))
        print('Positive Test %: ' + \
                " ".join(["{:.2f}".format(100*x) for x in te_pos_acc_list] ))
        print('Negative Test %: ' + \
                " ".join(["{:.2f}".format(100*x) for x in te_neg_acc_list] ))
        print('Net Time/mins:   ' + \
                " ".join(["{:5d}".format(x) for x in tr_te_net_mins ] ))

        #        '\tTrain: ' + " ".join(["{:.2f}".format(100*x) for x in tr_pos_acc_list] ) + 
        #        '\n\tTest:  ' + " ".join(["{:.2f}".format(100*x) for x in te_pos_acc_list] ))
        #print('Negative %:\n' + 
        #        '\tTrain: ' + " ".join(["{:.2f}".format(100*x) for x in tr_neg_acc_list] ) +
        #        '\n\tTest:  ' + " ".join(["{:.2f}".format(100*x) for x in te_neg_acc_list] ))


        #            (100*sum(tr_acc_list)/len(tr_acc_list)) +
        #        ' Test: %0.2f%%' % (100*sum(te_acc_list)/len(te_acc_list)))
        #print('Positive:  %0.2f%%' %
        #            (100*(sum(tr_pos_acc_list)+sum(te_pos_acc_list)) / (len(tr_pos_acc_list) + len(te_pos_acc_list))) +
        #            '  negative:  %0.2f%%' %
        #            (100*(sum(tr_neg_acc_list)+sum(te_neg_acc_list))
        #            / (len(tr_neg_acc_list) + len(te_neg_acc_list))))
        print('****')

def check_pred(tag, pred):

    if len(pred) == 0:
        print('Error: ' + tag + ': pred is 0-len')
        quit()

    val = pred[0]
    if val != pred[len(pred)-1]:
        return False

    for v in pred:
        if v != val:
            return False
    return True


notes = None
round_ct = 0
round_start_time = None
batch_index = 0
epochs = 0


def new_model(hp):

    global alg_notes
    global notes
    global round_ct
    global round_start_time
    global batch_index
    global epochs
    global notes
    global kernel_init_i
    global te_run_pos_acc_list
    global te_run_neg_acc_list
    global te_run_sum_acc_list
    global HARD_SECOND_EPOCH
    global REDUCE_EPOCHS

    print('--- creating new model')

    if OPTIMIZER == 'STEEP_SGD':
        if HARD_SECOND_EPOCH != 2:
            print('-- STEEP_SGD: setting HARD_SECOND_EPOCH=2')
            HARD_SECOND_EPOCH = 2
        if REDUCE_EPOCHS:
            print('-- STEEP_SGD: unsetting REDUCE_EPOCHS')
            REDUCE_EPOCHS = False

    in1 = []
    in2 = []
    if DO_VEC:
        in1.append(Input(shape=(side_vec_dim,)))
        in2.append(Input(shape=(side_vec_dim,)))
    if DO_DB:
        in1.append(Input(shape=(dbvec_dim,)))
        in2.append(Input(shape=(dbvec_dim,)))
    if DO_KWDS:
        in1.append(Input(shape=(kwdvec_dim,)))
        in2.append(Input(shape=(kwdvec_dim,)))
    if DO_HIST:
        in1.append(Input(shape=hist_input_shape))
        in2.append(Input(shape=hist_input_shape))
    if DO_JPGV:
        print('-- shape ' + str(img_vec_shape))
        in1.append(Input(shape=img_vec_shape))
        in2.append(Input(shape=img_vec_shape))
    elif DO_JPG:
        in1.append(Input(shape=jpg_shape))
        in2.append(Input(shape=jpg_shape))

    model = create_network(in1, in2, hp)

    if not DO_VEC:
        alg_notes += '_lrv_' + str(SIDE_VEC_LEN)

    if NATHAN:
        alg_notes += '_nathan'

    while alg_notes[-1] == '_':
        alg_notes = alg_notes[:-1]

    alg_notes = alg_notes + '_' + ACTIVATION

    if ' ' in str(OPTIMIZER):
        notes = alg_notes + '_' + type(OPTIMIZER).__name__
    else:
        notes = alg_notes + '_' + str(OPTIMIZER)

    print('-- Model: ' + notes)

    model.summary()

    te_run_pos_acc_list = []
    te_run_neg_acc_list = []
    te_run_sum_acc_list = []

    round_ct = 0
    round_start_time = time.time() # seconds+frac
    batch_index = BATCH_INDEX_START
    epochs = START_EPOCHS

    if DO_VEC and side_vec_dim < 16:
        if ACTIVATION == 'tanh':
            # likely makes no sense w/o more epochs
            print('-- side_vec_dim < 16 w/ tanh: random_batch')
            REFINE_STRATEGY = 'random_batch'
        else:
            print('-- side_vec_dim < 16: epochs=30 and random_batch')
            epochs = 30
            REFINE_STRATEGY = 'random_batch'


    if INITIALIZER_MODE == 'rotate':
        kernel_init_i += 1
        if kernel_init_i >= len(kernel_initializers):
            kernel_init_i = 0
        print('-- Rotate initializer: ' + kernel_initializers[kernel_init_i])
    elif INITIALIZER_MODE == 'random':
        kernel_init_i = random.randint(0, len(kernel_initializers)-1)
        print('-- Random initializer: ' + kernel_initializers[kernel_init_i])

    return model


modname = None

def load_prev_model():

    global modname

    if modname == None:
        print('=== Error: no model name')
        quit()

    print('-- loading ' + modname)

    if 'nathan' in modname:
        model = load_model(modname, custom_objects={'Nathan': Nathan})
    else:
        model = load_model(modname)

    print('-- loaded w/ lr ' + str(K.get_value(model.optimizer.learning_rate)))

    if 'STEEP' in modname:
        if GCTF:
            K.set_value(model.optimizer.learning_rate, \
                    K.get_value(model.optimizer.learning_rate) * STEEP_FAC)
            print('-- STEEPened lr: ' + str(K.get_value(model.optimizer.learning_rate)) + \
                        '  with STEEP_FAC ' + str(STEEP_FAC))
        else:
            K.set_value(model.optimizer.learning_rate, \
                    K.get_value(model.optimizer.learning_rate) * STEEP_FAC)
            print('-- STEEPened lr: ' + str(K.get_value(model.optimizer.learning_rate)) + \
                        '  with STEEP_FAC ' + str(STEEP_FAC))

    return model

def cycle_optimizers():

    global OPTIMIZER

    for OPTIMIZER in CYCLE_LIST: 

        print('------------ cycle: ' + OPTIMIZER + '   =======>>>')

        if OPTIMIZER == 'STEEP_SGD' and modname != None:
            if 'SGD' in modname and 'STEEP' not in modname:
                print('-- starting STEEP  from modname ' + modname)
                # roll it over if original lr
                model = load_prev_model()
                lr = K.get_value(model.optimizer.learning_rate)
                del model
                if lr == 0.02:
                    doit(False)
                else:
                    # True = new model, epochs: 3 init, 2 per lr bump
                    doit(True) 

        else:
            doit(True)

        for i in range(9):
            doit(modname == None)

def doit(new):

    print('== DOIT')

    #print('-- STEEP_SGD: factor is ' + str(STEEP_FAC))
    if DISTRIBUTE:
        # Open a distrib_strategy scope.
        distrib_strategy = tf.distribute.MirroredStrategy()
        print('-- DISTRIBUTE: Number of devices: {}'.format(distrib_strategy.num_replicas_in_sync))
        with distrib_strategy.scope():
            orig_doit(new)

    else:
        orig_doit(new)

    if TF_MEM_LEAK:
        print('-- clear_session for mem leak')
        K.clear_session()

### Unused for a while
# more-kosher test eval, slower, maybe optimizable?
DO_F1 = False
# more-bulky?
SAVE_JSON = False
###

prev_history = None

def orig_doit(new):

    # train/test one round; if good, note result and save model 

    global alg_notes
    global round_ct
    global batch_index
    global epochs
    global modname
    global kernel_init_i
    global te_run_pos_acc_list
    global te_run_neg_acc_list
    global te_run_sum_acc_list
    global REFINE_STRATEGY
    global prev_history

    if TUNER:
        if REFINE_STRATEGY == 'random_batch':
            batch_size = random.choice(batches)
            print('-- random batch: ' + str(batch_size) + 
                    ' from ' + str(len(batches)))
        else:
            batch_size = batches[batch_index]

        print('--- TUNER doit with batch ' + str(batch_size))
        '''
        tuner = RandomSearch(
                new_model,
                #objective="binary_accuracy",
                objective="loss",
                max_trials=20,
                executions_per_trial=5,
                overwrite=False,
                directory="tuning",
                project_name="dual_rand_1",
                distribution_strategy=tf.distribute.MirroredStrategy(),
            )
        tuner = Hyperband(
                hypermodel=new_model,
                #objective="binary_accuracy",
                objective="loss",
                max_epochs=3,
                factor=3,
                hyperband_iterations=10,
                distribution_strategy=tf.distribute.MirroredStrategy(),
                directory="tuning_hyper",
                project_name="hyper_2fac_loss_1v",
                overwrite=False,
            )
        '''
        tuner = BayesianOptimization(
                hypermodel=new_model,
                objective="loss",
                #objective="binary_accuracy",
                max_trials=5,
                num_initial_points=3,
                alpha=0.0001,
                beta=2.6,
                seed=None,
                #hyperparameters=None,
                tune_new_entries=True,
                allow_new_entries=True,
                directory="tuning",
                project_name="bayes_loss_4v",
                overwrite=False,
                distribution_strategy=tf.distribute.MirroredStrategy(),
            )
                

        tuner.search_space_summary()

        epochs = 3

        tuner.search(
                x=myGen('tune_train', tr_data, tr_y, batch_size, True),
                y=None, 
                batch_size=batch_size, epochs=epochs,
                validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
                steps_per_epoch=int((len(tr_data)-1) / batch_size), 
                validation_steps=1,
                max_queue_size=6,
                workers=1,
                verbose=1)
        tuner.results_summary()
        print('--- quick tuner expt done')
        quit()

    if new:
        model = new_model(None)
    else:
        model = load_prev_model()

    round_ct += 1

    if round_ct > 1:

        if SINGLE_EPOCH:
            print('-- break for SINGLE_EPOCH (TODO redo)')
            quit()

        # lr

        if OPTIMIZER == 'SWA_SGD':
            K.set_value(model.optimizer.learning_rate, SWA_INIT_LR)
        elif REFINE_STRATEGY == 'decr_lr' and \
                round_ct > 5 and round_ct < 10:
            print('lowering lr by 1.3')
            K.set_value(model.optimizer.learning_rate, \
                        K.get_value(model.optimizer.learning_rate) / 1.333)

        if round_ct == 2  and HARD_SECOND_EPOCH != None:
            epochs = HARD_SECOND_EPOCH
            print('-- HARD_SECOND_EPOCH: epochs -> ' + str(epochs))
        elif REDUCE_EPOCHS and epochs > 1:
            epochs = int(round(epochs/2))
            print('-- REDUCE_EPOCHS: halve epochs -> ' + str(epochs))

    train_tag = 'train'
    if permute and round_ct > 5: 
        epochs = 10
        train_tag = 'train_rot'

    # batch_index

    if REFINE_STRATEGY == 'random_batch':
        batch_size = random.choice(batches)
        print('-- random batch: ' + str(batch_size) + 
                    ' from ' + str(len(batches)))
    else:
        batch_size = batches[batch_index]

    print('-- train round ' + str(round_ct) + \
              ' opt ' + str(K.get_value(model.optimizer.__class__.__name__)) + \
              ' lr ' + str(K.get_value(model.optimizer.learning_rate))  + \
              '  ' + alg_notes + \
              ' e=' + str(epochs) + \
              ' b=' + str(batch_size) + \
              '   distribute=' + str(DISTRIBUTE))

    t_start = datetime.datetime.now()

    if OPTIMIZER == 'SWA_SGD':

        '''
        if round_ct == 1:
            start_epoch = 3 #int(epochs * 0.3)
        else:
            start_epoch = 3 
        '''
        start_epoch = int(epochs * 0.5)

        if Horiz:
            swa_lr = K.get_value(model.optimizer.learning_rate) * 0.97
            swa_lr2 = K.get_value(model.optimizer.learning_rate) * 0.97
            swa = SWA(start_epoch=start_epoch, 
                    lr_schedule='manual',
                    # lr_schedule='constant',
                    #lr_schedule='cyclic', swa_freq=2,
                    swa_lr=swa_lr, swa_lr2=swa_lr2, verbose=1)
        else:

            CYCLIC = False
            #CYCLIC = True
            if DO_HIST:
                CYCLIC = True

            if CYCLIC:
                swa_lr = K.get_value(model.optimizer.learning_rate) * 0.87
                swa_lr2 = K.get_value(model.optimizer.learning_rate) * 0.97

                swa = SWA(start_epoch=start_epoch, 
                        lr_schedule='cyclic', swa_freq=2, 
                        swa_lr=swa_lr, swa_lr2=swa_lr2, verbose=1)
            else:
                swa_lr = K.get_value(model.optimizer.learning_rate) * 0.97
                swa_lr2 = K.get_value(model.optimizer.learning_rate) * 0.1

                swa = SWA(start_epoch=start_epoch, 
                        lr_schedule='manual',
                        #lr_schedule='constant',
                        swa_lr=swa_lr, swa_lr2=swa_lr2, verbose=1)

            
        if LOSS_HISTORY:
            model.fit(
                x=myGen(train_tag, tr_data, tr_y, batch_size, True),
                y=None, 
                batch_size=batch_size, epochs=epochs,
                validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
                steps_per_epoch=int((len(tr_data)-1) / batch_size), 
                validation_steps=1,
                max_queue_size=6,
                workers=1,
                verbose=1, callbacks=[swa, history])
        else:
            model.fit(
                x=myGen(train_tag, tr_data, tr_y, batch_size, True),
                y=None, 
                batch_size=batch_size, epochs=epochs,
                validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
                steps_per_epoch=int((len(tr_data)-1) / batch_size), 
                validation_steps=1,
                max_queue_size=6,
                workers=1,
                verbose=1, callbacks=[swa])
        # end SWA_SGD

    else:
        if LOSS_HISTORY:
            model.fit(
                x=myGen(train_tag, tr_data, tr_y, batch_size, True),
                y=None, 
                batch_size=batch_size, epochs=epochs,
                validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
                steps_per_epoch=int((len(tr_data)-1) / batch_size), 
                validation_steps=1,
                max_queue_size=2,
                workers=1, callbacks=[history])
        else:
            # normal case, others long-unused
            term = tf.keras.callbacks.TerminateOnNaN()
            new_history = model.fit(
                x=myGen(train_tag, tr_data, tr_y, batch_size, True),
                y=None, 
                batch_size=batch_size, epochs=epochs,
                validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
                steps_per_epoch=int((len(tr_data)-1) / batch_size), 
                validation_steps=1,
                max_queue_size=2,
                callbacks=[term],
                workers=1)
            print('-- history: loss: ' + str(new_history.history['loss']))
            print('-- history: val_loss: ' + str(new_history.history['val_loss']))
            if prev_history != None:
                print('-- prev_history: loss: ' + str(prev_history.history['loss']))
                print('-- prev_history: val_loss: ' + str(prev_history.history['val_loss']))
                if new_history.history['loss'] > prev_history.history['loss']:
                    print('--- LOSS INCREASE')
            prev_history = new_history
    if PRUNE:
        pruner.set_pretrained_weights(model)
        model.set_weights(initial_weights)
        pruner.calc_prune_mask(model, 0.5, 'large_final')
        untrained_loss, untrained_accuracy = model.evaluate(x_test, y_test)
        # TBD
        '''
        model.fit(
            myGen(train_tag, tr_data, tr_y, batch_size, True),
            int((len(tr_data)-1) / batch_size), # steps per epoch
            epochs=epochs,
            validation_data=
                    myGen('valid', te_data, te_y, batch_size, True),
            validation_steps=1,
            max_queue_size=2,
            workers=1)
        '''

    if LOSS_HISTORY:
        print('-- trained round ' + str(round_ct) + ': ' + \
                  str(datetime.datetime.now()-t_start) + \
                  losses(history))
    else:
        print('-- trained round ' + str(round_ct) + ': ' + \
                  str(datetime.datetime.now()-t_start))

    # test predictions
    pred = model.predict_generator( 
            myGen('predict_te_pos', te_pos_data, te_p0, batch_size, False),
            int(len(te_p0)/batch_size),
            max_queue_size=1,
            workers=1, verbose=1)
    
    residue = len(te_p0) % batch_size
    if residue > 0:
        print('\nadd2 ' + str(residue))
        pred2 = model.predict_generator(
            myGen('predict_te_pos2', te_pos_data, te_p0, -residue, False), 1,
            max_queue_size=1,
            workers=1, verbose=1)
        p = np.concatenate([pred, pred2])
        del pred
        del pred2
        pred = p

    if check_pred('te_pos', pred):
        print('-- SKIPPING FURTHER EVAL ON POS TEST SAME ' + str(pred[0]))
        del pred
        del model
        modname = None
        return

    te_pos_acc = compute_accuracy(pred, te_p0)
    print('-- te_pos round ' + str(round_ct) + ': ' + \
                  str(te_pos_acc) + '   ' + str(datetime.datetime.now()))

    del pred

    # minimal reqt at any stage

    if te_pos_acc < MIN_POS_CUT0:
        print('-- SKIPPING FURTHER EVAL ON POS TEST ACC ' + \
                    str(te_pos_acc) + ' on min_pos_cut0: ' + str(MIN_POS_CUT0))
        del model
        modname = None
        return

    if not permute and round_ct > 1  and  te_pos_acc < MIN_POS_CUT1 \
                        and  (0.04 + te_pos_acc) < \
                                te_run_pos_acc_list[round_ct-2]:
        print('-- SKIPPING FURTHER EVAL ON POS TEST ACC DECREASE ' + \
                           str(te_run_pos_acc_list[round_ct-2]) + '->' + \
                           str(te_pos_acc))

        del model
        modname = None
        return

    if not permute and round_ct > 3  and  te_pos_acc < MIN_POS_CUT1:
        print('-- SKIPPING FURTHER EVAL ON POS TEST ACC < ' + \
                            str(MIN_POS_CUT1))
        del model
        modname = None
        return

    pred = model.predict_generator( 
            myGen('Predict_te_neg', te_neg_data, te_p1, batch_size, False),
            int(len(te_p1)/batch_size),
            max_queue_size=1,
            workers=1, verbose=1)
    
    residue = len(te_p1) % batch_size
    if residue > 0:
        print('\nadd2 ' + str(residue))
        pred2 = model.predict_generator(
            myGen('Predict_te_neg2', te_neg_data, te_p1, -residue, False), 1,
            max_queue_size=1,
            workers=1, verbose=1)
        p = np.concatenate([pred, pred2])
        del pred
        del pred2
        pred = p

    if check_pred('te_neg', pred):
        print('-- SKIPPING FURTHER EVAL ON NEG TEST SAME ' + str(pred[0]))
        #open('SKIP_same_neg_' + str(pred[0]) + '_' + notes, 'a').close()
        del pred
        del model
        modname = None
        return

    te_neg_acc = compute_accuracy(pred, te_p1)
    print('pred ' + str(pred.ravel()))
    del pred

    # bare minimum reqt

    if not permute and te_neg_acc < MIN_NEG_CUT0:
        print('-- SKIPPING FURTHER EVAL ON NEG TEST <' + \
                    str(MIN_NEG_CUT0) + ': ' + \
                    str(te_neg_acc))
        #open('SKIP_neg_lt_66_' + str(pred[0]) + '_' + notes, 'a').close()
        del model
        modname = None
        return

    if not permute and round_ct > 3  and  te_neg_acc < MIN_NEG_CUT1:
        print('-- SKIPPING FURTHER EVAL ON NEG TEST <' + \
                    str(MIN_NEG_CUT1) + ': ' + \
                    str(te_neg_acc))
        del model
        modname = None
        return

    if round_ct > 1:
        sum_te_acc = te_pos_acc + te_neg_acc 
        sum_te_acc_prev = te_run_pos_acc_list[round_ct-2] + \
                              te_run_neg_acc_list[round_ct-2]
        if not permute and te_pos_acc < te_run_pos_acc_list[round_ct-2] and \
                te_neg_acc < te_run_neg_acc_list[round_ct-2]:
            print('-- SKIPPING FURTHER EVAL ON BOTH POS, NEG ACC DECREASE ' + \
                           str(sum_te_acc_prev) + '->' + \
                           str(sum_te_acc))
            #open('SKIP_posneg_' + str(sum_te_acc_prev) + '_' + \
                    #               str(sum_te_acc) + '_' + notes, 'a').close()

            del model
            modname = None
            return

    if REFINE_STRATEGY.startswith('incr_batch'):

        if REFINE_STRATEGY == 'incr_batch_random' and \
                        batch_index == len(batches)-1:

            # kicks in at the next init
            REFINE_STRATEGY = 'random_batch'

        elif batch_index < len(batches)-1:
            # and abs(te_pos_acc - te_neg_acc) < .02:
            #print('REFINE?')
            #if round_ct > 3:  # sum_te_acc - sum_te_acc_prev > 0.3:

            batch_index += 1
            print('-- ' + REFINE_STRATEGY + 
                    ': bump batch_index, new batch ' + 
                    str(batches[batch_index]))
        elif round_ct > 8 and batch_index > 0 and \
                abs(te_pos_acc - te_neg_acc) > .05:

            batch_index -= 1
            print('-- ' + REFINE_STRATEGY +
                    ': REDUCE batch_index, new batch ' + 
                    str(batches[batch_index]))

    tr_rounds_list.append(round_ct)
    tr_epochs_list.append(epochs)
    tr_batch_list.append(batch_size)

    tr_optimizer_list.append(OPTIMIZER)

    te_pos_acc_list.append(te_pos_acc)
    te_neg_acc_list.append(te_neg_acc)
    te_sum_acc_list.append(te_pos_acc + te_neg_acc)

    net_minutes = int( (time.time()-round_start_time)/60 )

    tr_te_net_mins.append(net_minutes)

    te_run_pos_acc_list.append(te_pos_acc)
    te_run_neg_acc_list.append(te_neg_acc)
    te_run_sum_acc_list.append(te_pos_acc + te_neg_acc)

    # make modname

    orient = 'v'
    if Horiz:
        orient = 'h'

    modname = 'm_' + orient + '_model_pn_' + \
                        str(int(100*te_pos_acc)) + '_' + \
                        str(int(100*te_neg_acc)) + \
                    '_reb_' + \
                        str(round_ct) + '_' + \
                        str(epochs) + '_' + \
                        str(batch_size) + '_' + \
                    '_min_' + \
                        str(net_minutes) + '_' + \
                    notes + '.h5'

    print('Saving model: ' + modname)
    model.save(modname)

    if SAVE_JSON:
        with open(modname + '_json', 'w') as json:
            json.write(model.to_json())
        model.save_weights(modname + '_weights.h5')
    
    del model

    model_names.append(modname)
    if LOSS_HISTORY:
        print('-- T: ' + str(datetime.datetime.now()-t_start) +
            '  Model saved: ' + modname + losses(history))
    else:
        print('-- T: ' + str(datetime.datetime.now()-t_start) +
            '  Model saved: ' + modname)
    printSum()



#~~~~~-----~~~~~~~------*******---~~~~~~@@@@@~~888
# debug model w/out data load:
if DEBUG_MODEL:
    print('Debug model.. doit()')
    doit(True)
    quit()

if len(sys.argv) > 2:
    print('Too amny args')
    quit()

if len(sys.argv) == 2:
    run = sys.argv[1]
else:
    print('Pairs: ' + pair_dir)
    if DO_HIST:
        print('Hists: ' + str(hist_dirs))
        print('hist_input_shape: ' + str(hist_input_shape))
        print('hist_dense_size: ' + str(hist_dense_size))
    if DO_JPGV:
        print('JPG vec:   ' + jpg_vec_dir)
        print('JPG vec shape:   ' + str(img_vec_shape))
    elif DO_JPG:
        print('JPG:   ' + jpg_dir)
        print('JPG shape:   ' + str(jpg_shape))
    print('Batch/epochs: ' + str(batch_size) + '/' + str(epochs))
    run = raw_input('Run [N|ask|cycle]: ')

if run not in ['ask', 'cycle', 'iv_cycle']:
    try:
        n = int(run)
        if n <= 0:
            print('Going to ask mode')
            run = 'ask';
    except ValueError:
        print('Expected: N or ask or cycle: ' + run)
        quit()

# create training+test positive and negative pairs
load_preproc()

tr_data, tr_y = create_cases('train', None, True) # all
te_data, te_y = create_cases('test', None, False)  # all

if TEST_TRAIN_POS_NEG:
    tr_pos_data, tr_p0 = create_cases('train', True, False)
    if len(tr_p0) == 0:
        print('Nothing for tr_pos')
        quit()
    tr_neg_data, tr_p1 = create_cases('train', False, False)

te_pos_data, te_p0 = create_cases('test', True, False)
te_neg_data, te_p1 = create_cases('test', False, False)

#print(' TR_P ' + str(tr_data.shape))
#print(' TE_P ' + str(te_pairs.shape))

if run == 'ask':

    doit(True)

    while 1:
        if modname == None:
            print('New model: ' + alg_notes)
            again = input('Make again? [Y/n]: ')
            if again == 'n':
                break
            doit(True)
        else:
            print('Model: ' + modname)
            again = input('Train another round? [Y/n]: ')
            if again == 'n':
                modname = None
                continue
            doit(False)

elif run == 'iv_cycle':

    IN_MODE = 'JPGV_HIST'
    parse_IN_MODE()

    IMAGENET_LIST = ['VGG16_224', 'VGG19_224', 'DenseNet121_224' ]

    for IN_JPG_MODE in IMAGENET_LIST:

        print('------------ IMAGENET cycle: ' + IN_JPG_MODE + '   =======>>>')

        parse_IN_MODE()

        cycle_optimizers()

elif run == 'cycle':

    cycle_optimizers()

else:
    n = int(run)
    doit(True)
    for i in range(0, n, 1):
        doit(modname == None)

print('Models:\n' + "\n".join([x for x in model_names] ))

#printSum()
#print('Models:\n' + "\n".join([x for x in model_names] ))
