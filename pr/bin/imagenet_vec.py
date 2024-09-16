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

GPU = '0'   # make dynamic ... should use tensorRT?

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


print('== GPU: ' + GPU)
os.environ['CUDA_VISIBLE_DEVICES'] = GPU


MODELS = [ 'VGG16', 'VGG19', 'DenseNet121', 'MobileNetV2', 'Xception', \
            'NASNetLarge', 'ResNet152V2', 'InceptionV3', 'EfficientNetB7' ]

def usage(msg):

    if msg != None:
        print('Usage: ' + msg)

    print('Usage:')
    print('   ' + sys.argv[0] + ' <model> -a [<phobrain_archive>]')
    print('   ' + sys.argv[0] + ' <model> -r <jpg_dir> <vec_dir>')
    print('                  -r copies the tree structure of jpg_dir')
    print('   <model> one of:\n' + str(MODELS))

    exit(1)

###### check args before tf import muddies output

images_dir = None
vec_dir    = None

if len(sys.argv) < 3:
    usage('need at least three args')

IMAGENET_MODEL = sys.argv[1]

if IMAGENET_MODEL not in MODELS:
    usage('unknown model: ' + IMAGENET_MODEL)

if len(sys.argv) < 4  or  len(sys.argv) > 5:
    usage('arg count')

phobrain_archives = False
select_archive = None

if sys.argv[2] == '-r':

    # recursive transform

    if len(sys.argv) != 5:
        usage('-r: expected <jpg_dir> <vec_dir>')

    images_dir = sys.argv[3]
    vec_dir = sys.argv[4]

elif sys.argv[2] == '-a':

    phobrain_archives = True

    if len(sys.argv) == 4:

        select_archive = str(int(sys.argv[3]))

    # this is in pr/bin/ which should be in your path

    #PHOBRAIN_LOCAL = run_cmd(['get_phobrain_local.sh']).strip()
    #print('-- PHOBRAIN_LOCAL: ' + PHOBRAIN_LOCAL)

    IMAGE_DESC_DIR = run_cmd(['phobrain_property.sh',   \
                                'image.desc.dir']).strip()

    print('== IMAGE_DESC_DIR: [' + IMAGE_DESC_DIR + ']')

else:

    usage('Expected -a or -r, got: ' + sys.argv[2])

import tensorflow.keras
from tensorflow.keras.models import Model
from tensorflow.keras.preprocessing import image
from tensorflow.python.client import device_lib

import tensorflow as tf
gpu_options = tf.compat.v1.GPUOptions(per_process_gpu_memory_fraction=0.5,allow_growth=True)
tf.compat.v1.Session(config=tf.compat.v1.ConfigProto(gpu_options=gpu_options))

def run_cmd(cmd):

    proc = subprocess.run(cmd,   \
                            stdout=subprocess.PIPE,   \
                            stderr=subprocess.PIPE,   \
                            universal_newlines=True)

    if proc.returncode != 0:

        print("Can't run " + cmd)
        print('It returned: ' + str(proc.returncode))
        print('     stderr:  ' + str(proc.stderr))
        exit(1)

    return str(proc.stdout)

# procId for Phobrain archives

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

dim_str = None
done = 0
skipped = 0

def jpg_to_vec(jpg, ascii_vec, bin_vec):

    global dim_str, done, skipped

    jpgtime = os.path.getmtime(jpg)

    if ascii_vec is not None  and  os.path.exists(ascii_vec):

        vectime = os.path.getmtime(ascii_vec)
        if jpgtime < vectime:
            ascii_vec = None

    if bin_vec is not None and os.path.exists(bin_vec):

        vectime = os.path.getmtime(bin_vec)
        if jpgtime < vectime:
            bin_vec = None

    if ascii_vec is None  and  bin_vec is None:

        skipped = skipped + 1
        return

    img = load_img_jpg(jpg)
    vec = image_model.predict(img)

    if done == 0:

        dim_str = str(vec.shape)
        print('\n\n  output dimension: ' + dim_str)

        if vec.shape[1] != vec.shape[2]:

            print('*** Expected output dimension of 1, N, N, L')
            print('    (image file: ' + jpg + ')')
            exit(1)

    if bin_vec is not None:

        with open(bin_vec, 'wb') as fp:
            np.save(fp, vec)
        #print('-- saved ' + binfile)
        #quit()

    if ascii_vec is not None:

        with open(ascii_vec, 'w') as fp:
            vec.tofile(fp, sep='\n')

    done = done + 1

def handle_phobrain_archives():

    global done, skipped  # reset per-archive

    all_done = 0
    all_skipped = 0

    for archive in os.listdir(images_dir):

        if not archive.isdigit():
            continue

        if select_archive is not None and archive != select_archive:
            continue

        arch_dir = images_dir + '/' + archive
        if not os.path.isdir(arch_dir):
            continue

        print('-- arch ' + arch_dir)

        done = 0
        skipped = 0

        for file in os.listdir(arch_dir):

            if not (file.endswith('.jpg') or file.endswith('.JPG')):
                continue

            id = procId(archive, file)
            jpg = arch_dir + '/' + file

            bin_vec = vec_dir + '/' + id
            asc_vec = None  # dev/test: = bin_vec + '.txt'  # (triples space used)

            jpg_to_vec(jpg, asc_vec, bin_vec)


        print('--   did ' + str(done) + ' skipped ' + str(skipped))
        if len(dim_str) > 0:
            print('--      dimension: ' + dim_str)

        all_done = all_done + done
        all_skipped = all_skipped + skipped

    print('-- total done ' + str(all_done) + ' total skipped ' + str(all_skipped))

def handle_recursive():

    # -r <images_dir> <vec_dir>:
    #       recursively descend and put vecs
    #       in matching tree.

    new_dirs = 0
    new_vecs = 0

    for cur, _dirs, _files in os.walk(images_dir):

        dir = cur.removeprefix(images_dir)

        new = vec_dir + dir
        if not os.path.isdir(new):
            os.makedirs(new)
            new_dirs += 1
    '''
    if new_dirs == 0:
        print('destination tree structure checked, no new dirs')
    else:
        print('destination tree structure checked / created ' + \
                                        str(new_dirs) + ' dirs')
    '''
    for cur, _dirs, files in os.walk(images_dir):

        newdir = vec_dir + cur.removeprefix(images_dir)

        for f in files:

            if not f.endswith('.jpg'):
                continue

            jpg = cur + '/' + f

            bin_vec = newdir + '/' + f.removesuffix('.jpg')
            asc_vec = None  # dev/test: = bin_vec + '.txt'  # (triples space used)

            jpg_to_vec(jpg, asc_vec, bin_vec)

    print('-- recursively  did ' + str(done) + ' skipped ' + str(skipped))


### take stock

print('------- devices ---->')
print(device_lib.list_local_devices())

IN_JPG_SIZE = 224

if IMAGENET_MODEL in [ 'Xception', 'InceptionV3' ]:
    IN_JPG_SIZE = 299
elif IMAGENET_MODEL == 'NASNetLarge':
    IN_JPG_SIZE = 331
elif IMAGENET_MODEL == 'EfficientNetB7':
    IN_JPG_SIZE = 600

jpg_shape = (IN_JPG_SIZE, IN_JPG_SIZE, 3)

if phobrain_archives:

    if images_dir is not None  or  vec_dir is not None:
        print('internal error')
        exit(1)

    if IN_JPG_SIZE == 224:
        images_dir = IMAGE_DESC_DIR + '/3_jpg/prun224'
    elif IN_JPG_SIZE == 299:
        images_dir = IMAGE_DESC_DIR + '/3_jpg/prun299'
    elif IN_JPG_SIZE == 331:
        images_dir = IMAGE_DESC_DIR + '/3_jpg/prun331'
    elif IN_JPG_SIZE == 600:
        images_dir = IMAGE_DESC_DIR + '/3_jpg/prun600'
    else:
        print('Honky, what jpeg size?')
        exit(1)

    vec_dir = images_dir + '/vecs_' + IMAGENET_MODEL # + '_P' + tag


#---------------

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
        exit(1)
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
else:
    print('likely error - no model imported')
    exit(1)


print('JPG:   ' + images_dir)
print('JPG shape:   ' + str(jpg_shape))
print('Out Vecs:  ' + vec_dir)

if phobrain_archives:

    if select_archive is None:
        print('Doing all archives')
    else:
        print('Archive: ' + select_archive);

print('Loading ' + IMAGENET_MODEL)

image_model = vars()[IMAGENET_MODEL](include_top=False, weights='imagenet')

top = image_model.output

model = Model(image_model.input, [top])

print('-- loaded ' + IMAGENET_MODEL + ',  starting on ' + images_dir)

created_dir = False

if not os.path.exists(vec_dir):

    print('Creating ' + vec_dir)
    os.mkdir(vec_dir)
    created_dir = True

if phobrain_archives:

    handle_phobrain_archives()

else:

    handle_recursive()


#os.system('/home/epqe/bin/funwait.sh')

