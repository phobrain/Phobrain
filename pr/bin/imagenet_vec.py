#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === get imagenet vectors for each pic
# input images are e.g. 224x224's made earlier in the process of
#   adding pics to the archive, sized for the pretrained standard
#   imagenet model.

import socket
import os
import sys

import time
import datetime

import subprocess
from multiprocessing import Process, Pool, Queue, Manager
from contextlib import closing

import numpy as np

import pickle

# return string from cmd's stdout or die

def run_cmd(cmd):

    proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)

    if proc.returncode != 0:
        print("Can't run " + cmd)
        print('It returned: ' + str(proc.returncode))
        print('     stderr:  ' + str(proc.stderr))
        exit(1)

    return str(proc.stdout)

# this is in pr/bin/ which should be in your path

#PHOBRAIN_LOCAL = run_cmd(['get_phobrain_local.sh']).strip()
#print('-- PHOBRAIN_LOCAL: ' + PHOBRAIN_LOCAL)

IMAGE_DESC_DIR = run_cmd(['phobrain_property.sh', 'image.desc.dir']).strip()

print('== IMAGE_DESC_DIR: [' + IMAGE_DESC_DIR + ']')

print('------- devices ---->')
import tensorflow.keras
from tensorflow.keras.models import Model
from tensorflow.keras.preprocessing import image
from tensorflow.python.client import device_lib
print(device_lib.list_local_devices())

# Needs a training phase
#POOLING = None
#POOLING = 'avg'
#POOLING = 'max'

MODELS = [ 'VGG16', 'VGG19', 'DenseNet121', 'MobileNetV2', 'Xception', \
            'NASNetLarge', 'ResNet152V2', 'InceptionV3', 'EfficientNetB7' ]

def usage(msg):
    if msg != None:
        print('Usage: ' + msg)
    print('arg: one of:\n' + str(MODELS))
    exit(1)

## args

if len(sys.argv) < 2:
    usage('need at least one arg')

IMAGENET_MODEL = sys.argv[1]

if IMAGENET_MODEL not in MODELS:
    usage('unknown model: ' + IMAGENET_MODEL)

select_archive = None

if len(sys.argv) == 3:
    select_archive = str(int(sys.argv[2]))

'''
print('ok')
quit()
IMAGENET_MODEL = 'VGG16'
#IMAGENET_MODEL = 'VGG19'
#IMAGENET_MODEL = 'DenseNet121'
#IMAGENET_MODEL = 'MobileNetV2'
#IMAGENET_MODEL = 'Xception'
#IMAGENET_MODEL = 'NASNetLarge'
#IMAGENET_MODEL = 'ResNet152V2'
#IMAGENET_MODEL = 'InceptionV3'
'''

IN_JPG_SIZE = 224

if IMAGENET_MODEL in [ 'Xception', 'InceptionV3' ]:
    IN_JPG_SIZE = 299
elif IMAGENET_MODEL == 'NASNetLarge':
    IN_JPG_SIZE = 331
elif IMAGENET_MODEL == 'EfficientNetB7':
    IN_JPG_SIZE = 600

jpg_shape = (IN_JPG_SIZE, IN_JPG_SIZE, 3)

jpg_dir = None

if IN_JPG_SIZE == 224:
    jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun224'
elif IN_JPG_SIZE == 299:
    jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun299'
elif IN_JPG_SIZE == 331:
    jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun331'
elif IN_JPG_SIZE == 600:
    jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun600'
else:
    print('Honky, what jpeg size?')
    quit()

out_vec_dir = jpg_dir + '/vecs_' + IMAGENET_MODEL # + '_P' + tag


#---------------

#print('exec: ' + str(sys.executable))
#sys.path = [ '/project', '/usr/lib/python3.7', '/usr/lib/python3.7/lib-dynload', '/usr/local/lib/python3.7/dist-packages', '/usr/lib/python3/dist-packages']
#print('path: ' + str(sys.path))
#quit()

#
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
elif 'EfficientNetB7' in IMAGENET_MODEL:
    from tensorflow.keras.applications.efficientnet import EfficientNetB7
    from tensorflow.keras.applications.efficientnet import preprocess_input

#from tensorflow.keras.applications.nasnet import NASNetLarge, NASNetMobile

#from tensorflow.keras import backend as K
#K.set_floatx('float16')
#K.set_epsilon(1e-4)

#sys.path.insert(0, '../')
#sys.path.append('..')

#run_opts = tf.RunOptions(report_tensor_allocations_upon_oom = True)

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

print('JPG:   ' + jpg_dir)
print('JPG shape:   ' + str(jpg_shape))
print('Out Vecs:  ' + out_vec_dir)
if select_archive is None:
    print('Doing all archives')
else:
    print('Archive: ' + select_archive);

print('Loading ' + IMAGENET_MODEL)

image_model = vars()[IMAGENET_MODEL](include_top=False, weights='imagenet')

top = image_model.output

model = Model(image_model.input, [top])


print('-- loaded ' + IMAGENET_MODEL + '  starting on ' + jpg_dir)

created_dir = False

if not os.path.exists(out_vec_dir):
    print('Creating ' + out_vec_dir)
    os.mkdir(out_vec_dir)
    created_dir = True

all_done = 0
all_skipped = 0

for archive in os.listdir(jpg_dir):

    if not archive.isdigit():
        continue

    if select_archive is not None and archive != select_archive:
        continue

    arch_dir = jpg_dir + '/' + archive
    if not os.path.isdir(arch_dir):
        continue

    print('-- arch ' + arch_dir)
    
    skipped = 0
    done = 0

    dim_str = ''

    for file in os.listdir(arch_dir):

        if not (file.endswith('.jpg') or file.endswith('.JPG')):
            continue

        id = procId(archive, file)
        path = arch_dir + '/' + file

        binfile = out_vec_dir + '/' + id
        ascfile = binfile + '.txt'

        doBin = True

        doAscii = True
        #doAscii = False
        #print('-- doAscii: ' + str(doAscii))

        if not created_dir:

            jpgtime = os.path.getmtime(path)

            if os.path.exists(binfile):
                vectime = os.path.getmtime(binfile)
                if jpgtime < vectime:
                    doBin = False

            if os.path.exists(ascfile):
                vectime = os.path.getmtime(ascfile)
                if jpgtime < vectime:
                    doAscii = False

        #print('-- ' + binfile)
        #print('-- ' + path + '  ->  ' + binfile)
        #quit()

        if not (doAscii or doBin):
            skipped = skipped + 1
            continue

        img = load_img_jpg(path)
        vec = image_model.predict(img)

        if done == 0:
            dim_str = str(vec.shape)
            print(' output dimension: ' + dim_str)

        if doBin:
            with open(binfile, 'wb') as fp:
                np.save(fp, vec)
            #print('-- saved ' + binfile)
            #quit()

        if doAscii:
            with open(ascfile, 'w') as fp:
                vec.tofile(fp, sep='\n')

        done = done + 1

    print('--   did ' + str(done) + ' skipped ' + str(skipped))
    if len(dim_str) > 0:
        print('--      dimension: ' + dim_str)

    all_done = all_done + done
    all_skipped = all_skipped + skipped

print('-- total done ' + str(all_done) + ' total skipped ' + str(all_skipped))

#os.system('/home/epqe/bin/funwait.sh')

