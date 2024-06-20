#!/usr/bin/python3
#
#  SPDX-FileCopyrightText: 2022 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#


# THIS IS v2, not verified, see pr/bin/ version.

# --------------------- Per-Machine config

import socket
import os

HOST = socket.gethostname()
if HOST == 'phobrain-gpu1':
    BASE = '/mnt/ssd/reep/home'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
else:
    BASE = '~'
    JAVA2ML_BASE = BASE + '/ml/java2ml/'
    #os.environ['CUDA_VISIBLE_DEVICES'] = '0'

BASE = os.path.expanduser(BASE) + '/'

# ------------ end Per-Machine config

# Needs a training phase
#POOLING = None
#POOLING = 'avg'
#POOLING = 'max'

#IMAGENET_MODEL = 'VGG16'
IMAGENET_MODEL = 'VGG19'
#IMAGENET_MODEL = 'DenseNet121'
#IMAGENET_MODEL = 'Xception'
#IMAGENET_MODEL = 'NASNetLarge'
#IMAGENET_MODEL = 'ResNet152V2'
#IMAGENET_MODEL = 'MobileNetV2'
#IMAGENET_MODEL = 'InceptionV3'

IN_JPG_SIZE = 224

if IMAGENET_MODEL in [ 'Xception', 'InceptionV3' ]:
    IN_JPG_SIZE = 299
elif IMAGENET_MODEL == 'NASNetLarge':
    IN_JPG_SIZE = 331

jpg_dir = None

if IN_JPG_SIZE == 224:
    jpg_dir = BASE + '/images/prun224'
    jpg_shape = (224, 224, 3)
elif IN_JPG_SIZE == 299:
    jpg_dir = BASE + '/images/prun299'
    jpg_shape = (299, 299, 3)
elif IN_JPG_SIZE == 331:
    jpg_dir = BASE + '/images/prun331'
    jpg_shape = (331, 331, 3)
else:
    print('Honky, what jpeg size?')
    quit()

#if POOLING == None:
#    tag = 'None'
#else:
#    tag = POOLING

out_vec_dir = jpg_dir + '/vecs_' + IMAGENET_MODEL # + '_P' + tag

from tensorflow.keras.layers import Input, Flatten, Dense, Dropout, AlphaDropout, GaussianDropout, Concatenate, concatenate, BatchNormalization, Conv2D
from tensorflow.keras.models import Sequential, Model

#---------------

import sys
#print('exec: ' + str(sys.executable))
#sys.path = [ '/project', '/usr/lib/python3.7', '/usr/lib/python3.7/lib-dynload', '/usr/local/lib/python3.7/dist-packages', '/usr/lib/python3/dist-packages']
#print('path: ' + str(sys.path))
#quit()

import numpy as np
#import math

from multiprocessing import Process, Pool, Queue, Manager
from contextlib import closing

#import string
#import re
#import glob
import time
import datetime

import tensorflow as tf

os.environ['CUDA_VISIBLE_DEVICES'] = ''

#
from tensorflow.keras.preprocessing import image
if 'VGG16' in IMAGENET_MODEL:
    from tensorflow.keras.applications.vgg16 import preprocess_input
    from tensorflow.keras.applications.vgg16 import VGG16
elif 'VGG19' in IMAGENET_MODEL:
    from tensorflow.keras.applications.vgg19 import preprocess_input
    from tensorflow.keras.applications.vgg19 import VGG19
elif 'DenseNet' in IMAGENET_MODEL:
    from tensorflow.keras.applications.densenet import preprocess_input
    if '121' in IMAGENET_MODEL:
        from tensorflow.keras.applications import DenseNet121
    elif '169' in IMAGENET_MODEL:
        from tensorflow.keras.applications import DenseNet169
    else:
        print('-- Densenet type??')
        quit()
elif 'Xception' in IMAGENET_MODEL:
    from tensorflow.keras.applications.xception import Xception
    from tensorflow.keras.applications.xception import preprocess_input
elif 'NASNetLarge' in IMAGENET_MODEL:
    from tensorflow.keras.applications.nasnet import NASNetLarge
    from tensorflow.keras.applications.nasnet import preprocess_input
elif 'ResNet152V2' in IMAGENET_MODEL:
    from tensorflow.keras.applications.resnet_v2 import ResNet152V2
    from tensorflow.keras.applications.resnet_v2 import preprocess_input
elif 'MobileNetV2' in IMAGENET_MODEL:
    from tensorflow.keras.applications.mobilenet_v2 import MobileNetV2
    from tensorflow.keras.applications.mobilenet_v2 import preprocess_input
elif 'InceptionV3' in IMAGENET_MODEL:
    from tensorflow.keras.applications.inception_v3 import InceptionV3
    from tensorflow.keras.applications.inception_v3 import preprocess_input

#from tensorflow.keras import backend as K
#K.set_floatx('float16')
#K.set_epsilon(1e-4)

#sys.path.insert(0, '../')
#sys.path.append('..')

#run_opts = tf.RunOptions(report_tensor_allocations_upon_oom = True)

from tensorflow.python.client import device_lib
print(device_lib.list_local_devices())

#from kerastuner.tuners import BayesianOptimization

#import matplotlib
#matplotlib.use('Agg')
#import matplotlib.pyplot as plt

import tensorflow.keras
import pickle

CHECK_DATA = True

def procId(archive, f):
    tag = f.replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')
    #print('xx ' + archive + ':' + str(seq))
    return archive + ':' + tag

def load_img_jpg(jpg_fname):
    #print('load ' + jpg_fname)
    jpg = image.load_img(jpg_fname)
    jpg = image.img_to_array(jpg)
    jpg = np.expand_dims(jpg, axis=0)
    # imagenet preproc
    jpg = preprocess_input(jpg)

    return jpg

print('-- Config for host: ' + HOST)
print('-- BASE is ' + BASE)

print('JPG:   ' + jpg_dir)
print('JPG shape:   ' + str(jpg_shape))
print('Out Vecs:  ' + out_vec_dir)

print('Loading ' + IMAGENET_MODEL)

image_model = vars()[IMAGENET_MODEL](include_top=False, weights='imagenet')

'''
top = image_model.output

if POOLING is not None:

    # Pooling

    top =  Dense(1024, activation='relu')(top)
    top =  Dense(512, activation='relu')(top)

else:

    # non-Pooling

    conv1x1 = Conv2D(512, (1, 1), activation='relu')(top)
    top =  Flatten()(conv1x1)


model = Model(image_model.input, [top])

#print(model.summary())
#quit()
'''

print('-- loaded ' + IMAGENET_MODEL + '  starting on ' + jpg_dir)

created_dir = False

if not os.path.exists(out_vec_dir):
    print('Creating ' + out_vec_dir)
    os.mkdir(out_vec_dir)
    created_dir = True

# make list

jpgs = []

all_added = 0
all_skipped = 0

for archive in os.listdir(jpg_dir):

    if not archive.isdigit():
        continue

    arch_dir = jpg_dir + '/' + archive
    if not os.path.isdir(arch_dir):
        continue

    print('-- arch ' + arch_dir)
    
    skipped = 0
    added = 0

    for file in os.listdir(arch_dir):
        if not (file.endswith('.jpg') or file.endswith('.JPG')):
            continue

        id = procId(archive, file)
        path = arch_dir + '/' + file
        outfile = out_vec_dir + '/' + id

        if not created_dir and os.path.exists(outfile):
            jpgtime = os.path.getmtime(path)
            vectime = os.path.getmtime(outfile)
            if jpgtime < vectime:
                skipped = skipped + 1
                continue

        #print('-- ' + outfile)

        jpgs.append( [ id, path, outfile ] )

        added = added + 1

    print('--   added ' + str(added) + ' skipped ' + str(skipped))
    all_added = all_added + added
    all_skipped = all_skipped + skipped

print('-- total added ' + str(all_added) + ' total skipped ' + str(all_skipped))

total = len(jpgs)

done = []

for i in range(total):
    id, path, outfile = jpgs[i]
    img = load_img_jpg(path)
    vecs = image_model.predict(img)
    with open(outfile, 'wb') as fp:
        np.save(fp, img)
    print('-- saved ' + outfile)
    quit()

    #print('-- ' + path + '  ->  ' + outfile)
    '''
    done.append(id)
    if len(done) > 5:
        print(str(done))
        done = []
    '''

print('-- jpgs: ' + str(total))

quit()

# read and predict batches TODO

BATCH_SIZE = 1

start = 0

done = 0

print('-- batch size ' + str(BATCH_SIZE))

while True:

    end = start + BATCH_SIZE
    if end > total:
        end = total
    imgs = np.empty((end-start, 224, 224, 3))
    ids = []
    outs = []
    print('-- batch-load ' + str(start) + '..' + str(end))
    for i in range(start, end):
        id, path, outfile = jpgs[i]
        img = load_img_jpg(path)
        np.append(imgs, img)
        ids.append(id)
        outs.append(outfile)

    print ('-- batched ' + str(end-start))

    vecs = image_model.predict_on_batch(imgs)

    print ('-- preds ' + str(len(vecs)))

    if len(vecs) > 1  and  vecs[0].all() == vecs[1].all():
        print('-- same vec repeated: ' + str(vecs[0]))
        quit()

    for i in range(len(ids)):
        vec = vecs[i]
        with open(outs[i], 'wb') as fp:
            np.save(fp, vec)
        #print('-- saved ' + outs[i])
        #quit()

        done = done + 1

    del vecs

    if end == total:
        break

    start = end

print('-- done ' + str(done))

os.system('/home/epqe/bin/funwait.sh')

