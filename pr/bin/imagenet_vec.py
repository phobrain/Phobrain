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

### tensorflow predict verbosity
# verbose=0: no performance printing
# verbose=1: Shows a progress bar for each epoch.
# verbose=2: Shows one line per epoch.
VERBOSE = 0

DRY_RUN = False
#DRY_RUN = True

# Force fresh start, refuse to tromp old
# CREATE_DIRS = True
CREATE_DIRS = False

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


MODELS = [ 'VGG16', 'VGG19', 'DenseNet121', 'MobileNetV2', 'Xception', \
            'NASNetLarge', 'ResNet152V2', 'InceptionV3', 'InceptionResNetV2', 'EfficientNetV2S' ]

def usage(msg):

    if msg != None:
        print('Usage: ' + msg)

    print('Usage:')
    print('   ' + sys.argv[0] + ' <cpu|gpu[01b]> <model> <-vecs|-classes|-both> -a [<phobrain_archive>]')
    print('   ' + sys.argv[0] + ' <cpu|gpu[01b]> <model> <-vecs|-classes|-both> -r <jpg_dir>')
    print('                         -r copies the tree structure of jpg_dir [TBD/tested]')
    print('')
    print('   <model> one of:\n\t' + str(MODELS) +'\n\tOptionally appending -avg')
    print('')
    print('   -vecs outputs the internal model vector, e.g. 7x7xDIM, or DIM if <model>-avg')
    print('   -classes outputs 1000 weights for Imagenet classes')
    print('')
    print('   Output for -a goes to ./vecs_<model>, ./classes_<model>')
    print('   Output for -r goes to <jpg_dir>/vecs_<model>, <jpg_dir>/classes_<model>')
    print('')

    exit(1)

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

###### check args before tf import muddies output

jpg_dir = None
vec_dir   = None
class_dir   = None

# for TBD vecs->top->class
src_vecs = None

if len(sys.argv) < 4:
    usage('need at least 4 args')

CALC = sys.argv[1]

if CALC == 'cpu':
    GPU = 'CPU'
else:
    if CALC == 'gpu':
        GPU = '0'
    elif CALC == 'gpu0':
        GPU = '0'
    elif CALC == 'gpu1':
        GPU = '1'
    elif CALC == 'gpub':
        GPU = '0,1'
    else:
        usage('Unknown cpu/gpu option: ' + CALC)

print('== GPU: ' + GPU)
if GPU == 'CPU':
    os.environ['CUDA_VISIBLE_DEVICES'] = ''
else:
    os.environ['CUDA_VISIBLE_DEVICES'] = GPU

MODEL_ARG = sys.argv[2]

VEC_POOL = None

IMAGENET_MODEL = MODEL_ARG

if IMAGENET_MODEL.endswith('-avg'):
    VEC_POOL = 'avg'
    IMAGENET_MODEL = IMAGENET_MODEL.removesuffix('-avg')

if IMAGENET_MODEL not in MODELS:
    usage('unknown model: ' + IMAGENET_MODEL)

if len(sys.argv) < 5  or  len(sys.argv) > 7:
    usage('arg count')

do_vecs = False
do_classify = False

CMD = sys.argv[3]

if CMD == '-classes':
    do_classify = True
elif CMD == '-vecs':
    do_vecs = True
elif CMD == '-both':
    do_classify = True
    do_vecs = True
else:
    usage('Expected -vecs or -classes or -both: ' + CMD)

TAG = MODEL_ARG + ' ' + CMD

phobrain_archives = False
select_archive = None

TYPE = sys.argv[4]
NEXTARG = 5

if TYPE == '-r':

    # recursive transform

    if len(sys.argv) != 6:
        usage('-r: expected <jpg_dir>')

    jpg_dir = sys.argv[NEXTARG]

    if do_classify:
        class_dir = jpg_dir + '/classes_' + MODEL_ARG # + '_P' + tag
        if CREATE_DIRS and os.path.isdir(class_dir):
            print('resolve: class dir exists: ' + class_dir)
            exit(1)
    if do_vecs:
        if VEC_POOL is None:
            vec_dir = jpg_dir + '/vecs_' + MODEL_ARG # + '_P' + tag
        else:
            vec_dir = jpg_dir + '/vecs_' + MODEL_ARG

        if CREATE_DIRS and os.path.isdir(vec_dir):
            print('resolve: vec dir exists: ' + vec_dir)
            exit(1)

elif TYPE == '-a':

    phobrain_archives = True

    if len(sys.argv) == 6:
        # '-a <archive>'
        select_archive = str(int(sys.argv[NEXTARG]))

    # pr/bin/ should be in your path

    IMAGE_DESC_DIR = run_cmd(['phobrain_property.sh',   \
                                'image.desc.dir']).strip()

    print('== IMAGE_DESC_DIR: [' + IMAGE_DESC_DIR + ']')

    if do_classify:
        class_dir = './classes_' + MODEL_ARG
    if do_vecs:
        vec_dir = './vecs_' + MODEL_ARG

else:

    usage('Expected -a or -r, got: ' + TYPE)

if do_classify:
    print('--- classes dir: ' + class_dir)
if do_vecs:
    print('--- vecs dir: ' + vec_dir)

print('=======================')
print('===============')
print('==========')
print('=====')

sys.stdout.flush()

import tensorflow.keras
from tensorflow.keras.models import Model
from tensorflow.keras.preprocessing import image
from tensorflow.python.client import device_lib

import tensorflow as tf
gpu_options = tf.compat.v1.GPUOptions(per_process_gpu_memory_fraction=0.5,allow_growth=True)
tf.compat.v1.Session(config=tf.compat.v1.ConfigProto(gpu_options=gpu_options))


# procId for Phobrain archives

def procId(archive, f):

    if not archive.isdigit():
        print('procId - archive not number: ' + archive)
        exit(1)

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
    try:
        jpg = image.load_img(jpg_fname)
    except Exception as e:
        print(f"skipping {jpg_fname} on load exception: {e}")
        return None

    jpg = image.img_to_array(jpg)
    jpg = np.expand_dims(jpg, axis=0)
    # imagenet preproc
    jpg = preprocess_input(jpg)

    return jpg

def load_img_vec(jpg_fname):

    vec_fname = x
    #print('load ' + vec_fname)

dim_str = ''
done = 0
skipped = 0

def jpg_to(jpg, ascii_vec, bin_vec, classes):

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

    '''
    if classes is not None and os.path.exists(classes):

        vectime = os.path.getmtime(classes)
        if jpgtime < vectime:
            classes = None

    '''
    if classes is None and ascii_vec is None  and  bin_vec is None:
        print('skipping no-calc: ' + jpg)
        skipped = skipped + 1
        return

    '''
    if src_vecs is not None:
        # NOT IMPL classify from pre-saved -vecs
        if not os.path.isfile(bin_vec):
            print('classes: skipping no-vec: ' + bin_vec)
            skipped = skipped + 1
            return
        else:
            vec = load_img_vec(bin_vec)
            vec = vec_model.predict(vec)
    '''

    img = load_img_jpg(jpg)
    if img is None:
        print('jpg_to(): skip')
        return

    # print('LOADED ' + str(img.shape) + '  ' + jpg)

    if done == 0:
        if img.shape[1] != IN_JPG_SIZE or img.shape[2] != IN_JPG_SIZE:
            print('--- Unexpected shape: ' + str(img.shape) + '  ' + jpg)
            print('--- Expected: ' + str(JPG_SHAPE))
            exit(1)

    pred_classes = None

    if do_vecs:
        vec = vec_model.predict(img, verbose=VERBOSE)
    if do_classify:
        #print('-- predict/classify ' + jpg + ' type ' + type(img).__name__ + ', size ' + str(img.nbytes) + ', model is ' + str(classify_model))
        pred_classes = classify_model.predict(img, verbose=VERBOSE)
        #print('-- predict/classify ok')

    if done == 0:

        if do_vecs:
            dim_str = str(vec.shape)
            print('  vec output dimension: ' + dim_str)

            if VEC_POOL is None and vec.shape[1] != vec.shape[2]:

                print('*** -vecs: Expected output dimension of 1, N, N, L')
                print('    (image file: ' + jpg + ')')
                print(' => expecting VEC_POOL != None?')
                exit(1)

        if do_classify and pred_classes.shape[1] != 1000:

            print('*** -classify: Expected output dimension of 1, 1000 for Imagenet classes')
            print('    (image file: ' + jpg + ')')
            exit(1)

    if bin_vec is not None:

        if DRY_RUN:
            print('dry write to ' + bin_vec)
        else:
            with open(bin_vec, 'wb') as fp:
                np.save(fp, vec)

        #print('-- saved ' + binfile)
        #quit()

    if ascii_vec is not None:

        if DRY_RUN:
            print('dry write to ' + ascii_vec)
        else:
            with open(ascii_vec, 'w') as fp:
                vec.tofile(fp, sep='\n')

    if pred_classes is not None:

        if DRY_RUN:
            test_file = '/tmp/xzx.npy'
            print('dry write to ' + test_file + ' vs ' + classes + ' shape ' + str(pred_classes.shape))
            np.save(test_file, pred_classes)
            test = np.load(test_file)
            print('dry read of ' + test_file + ' shape ' + str(test.shape))
            #exit(1)
        else:
            
            with open(classes, 'wb') as fp:
                np.save(fp, pred_classes)
            with open(classes + '_bs', 'wb') as fp:
                pred_classes.byteswap().tofile(fp)

    done = done + 1

def handle_phobrain_archives():

    global done, skipped  # reset per-archive

    if do_vecs and vec_dir == None:
        prin('-- internal: do_vecs w/ vec_dir none')
        exit(1)

    all_done = 0
    all_skipped = 0
    t_start_all = time.perf_counter() # datetime.datetime.now()

    dirs = {}

    for archive in os.listdir(jpg_dir):

        if not archive.isdigit():
            continue
        if select_archive is not None and archive != select_archive:
            continue
        if not os.path.isdir(jpg_dir + '/' + archive):
            continue

        arch_dir = jpg_dir + '/' + archive
        if not os.path.isdir(arch_dir):
            continue

        count = len(os.listdir(arch_dir))
        if count > 0:
            dirs[count] = arch_dir

    # iterate dirs small -> large
    sorted_dirs = sorted(dirs.items())
    sorted_dirs = [value for key, value in sorted_dirs]
    print('-- number-only (archive) dirs: ' + str(sorted_dirs))

    for arch_dir in sorted_dirs:

        archive = os.path.basename(arch_dir)

        if DRY_RUN:
            print('-- dry skip over archive ' + archive + ' dir: ' + arch_dir)
        #    continue

        if do_classify:
            print('-- ' + MODEL_ARG + ' classes, arch ' + arch_dir)
        if do_vecs:
            print('-- ' + MODEL_ARG + ' vecs, arch ' + arch_dir)

        done = 0
        skipped = 0
        t_start = time.perf_counter() # datetime.datetime.now()

        for file in os.listdir(arch_dir):

            if not (file.endswith('.jpg') or file.endswith('.JPG')):
                continue

            id = procId(archive, file)
            jpg = arch_dir + '/' + file
            #print('f->id ' + archive + '//' + file + ' ' + id)

            # output files
            bin_vec = None
            asc_vec = None
            classes = None
            if do_vecs:
                bin_vec = vec_dir + '/' + id
                asc_vec = None  # dev/test: = bin_vec + '.txt'  # (triples space used)
            if do_classify:
                classes = class_dir + '/' + id

            jpg_to(jpg, asc_vec, bin_vec, classes)


        print('--   did ' + str(done) + ' skipped ' + str(skipped))

        if done > 0:
            now = time.perf_counter() # datetime.datetime.now()
            dt = now - t_start
            pp = dt / done
            print(f"--   sec/pic: {pp:.4f} total {dt:.0f}s on gpu {GPU}  {TAG}")

        if len(dim_str) > 0:
            print('--      dimension: ' + dim_str)

        all_done = all_done + done
        all_skipped = all_skipped + skipped

    print('-- DONE total ' + str(all_done) + ' skipped ' + str(all_skipped))
    if all_done > 0:
        now = time.perf_counter() # datetime.datetime.now()
        dt = now - t_start_all
        pp = dt / all_done
        print(f"--   sec/pic: {pp:.4f} total {dt:.2f} on gpu {GPU}  {TAG}")

def handle_recursive():

    # -r <jpg_dir>
    #       recursively descend non-{vecs_|classes_}
    #       and put vecs in matching tree under jpg_dir.

    new_dirs = 0
    new_vecs = 0
    new_classes = 0

    # make tree

    for cur, _dirs, _files in os.walk(jpg_dir):

        dir = cur.removeprefix(jpg_dir)

        if 'vecs_' in dir or 'classes_' in dir:
            continue

        if do_vecs:
            new = vec_dir + dir
            if not os.path.isdir(new):
                os.makedirs(new)
                new_dirs += 1
        if do_classify:
            new = class_dir + dir
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

    # populate tree

    for cur, _dirs, files in os.walk(jpg_dir):

        if do_vecs:
            newdir = vec_dir + cur.removeprefix(jpg_dir)

        for f in files:

            if not f.endswith('.jpg'):
                continue

            jpg = cur + '/' + f
            base = f.removesuffix('.jpg')

            bin_vec = None
            asc_vec = None
            classes = None

            if do_vecs:
                bin_vec = newdir + '/' + base
                #asc_vec = bin_vec + '.txt'  # (triples space used)

            if do_classify:
                classes = class_dir + '/' + base

            jpg_to(jpg, asc_vec, bin_vec, classes)

    print('-- recursively  did ' + str(done) + ' skipped ' + str(skipped))


### take stock

print('------- devices ---->')
print(device_lib.list_local_devices())

IN_JPG_SIZE = 224

if IMAGENET_MODEL in [ 'Xception', 'InceptionV3', 'InceptionResNetV2' ]:
    IN_JPG_SIZE = 299
elif IMAGENET_MODEL == 'NASNetLarge':
    IN_JPG_SIZE = 331
elif IMAGENET_MODEL == 'EfficientNetV2S':
    IN_JPG_SIZE = 384

jpg_shape = (IN_JPG_SIZE, IN_JPG_SIZE, 3)
print('--- input jpg shape: ' + str(jpg_shape))

if phobrain_archives:

    if IN_JPG_SIZE == 224:
        jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun224'
    elif IN_JPG_SIZE == 299:
        jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun299'
    elif IN_JPG_SIZE == 331:
        jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun331'
    elif IN_JPG_SIZE == 384:
        jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun384'
    elif IN_JPG_SIZE == 400:
        jpg_dir = IMAGE_DESC_DIR + '/3_jpg/prun400'
    else:
        print('Honky, what jpeg size?')
        exit(1)



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
elif 'InceptionResNetV2' in IMAGENET_MODEL:
    from tensorflow.keras.applications.inception_resnet_v2 import InceptionResNetV2
    from tensorflow.keras.applications.inception_resnet_v2 import preprocess_input
elif 'EfficientNetV2S' in IMAGENET_MODEL:
    from tensorflow.keras.applications import EfficientNetV2S
    def preprocess_input(img):
        return img
    #from tensorflow.keras.applications.efficientnet import preprocess_input
else:
    print('likely error - no model imported')
    exit(1)

print('==========================================:...')
print('===')
print('=== JPG:   ' + jpg_dir)
print('=== JPG shape:   ' + str(jpg_shape))
if vec_dir is not None:
    print('=== Out Vecs:    ' + vec_dir)
if class_dir is not None:
    print('=== Out Classes: ' + class_dir)

if vec_dir is None and class_dir is None:
    print('==> No output specified, should be a no-write run.. if it works.')

if phobrain_archives:

    if select_archive is None:
        print('=== Doing all Phobrain archives')
    else:
        print('=== Phobrain archive: ' + select_archive);

else:
    print('=== Recursion on ' + jpg_dir)

print('===')
print('==========================================:...')
print('Loading ' + TAG + ' classify=' + str(do_classify) + ' vecs=' + str(do_vecs))
print('==========================================:...')
print('===========')
print('======')
print('===')

# TODO - get classes and vecs from same model
classify_model = None
vec_model = None

if do_vecs:
    if IMAGENET_MODEL == 'EfficientNetV2S':
        vec_model = vars()[IMAGENET_MODEL](include_top=False, weights='imagenet',
                                pooling=VEC_POOL,
                                input_shape=jpg_shape, include_preprocessing=True)
    else:
        print('-- making vecs: ' + IMAGENET_MODEL)
        sys.stdout.flush()
        vec_model = vars()[IMAGENET_MODEL](include_top=False, pooling=VEC_POOL, weights='imagenet')

    top = vec_model.output

    vec_model = Model(vec_model.input, [top])

if do_classify:

    if IMAGENET_MODEL == 'EfficientNetV2S':
        classify_model = vars()[IMAGENET_MODEL](include_top=True, weights='imagenet',
                                classifier_activation=None,
                                include_preprocessing=True)
    else:
        print('-- making classify: ' + IMAGENET_MODEL)
        sys.stdout.flush()

        classify_model = vars()[IMAGENET_MODEL](include_top=True, weights='imagenet')
        #classify_model = NASNetLarge(include_top=True, weights='imagenet')
        print('-- made classify: ' + IMAGENET_MODEL)
        sys.stdout.flush()

    '''
    if src_vecs is not None:
        base_model = vars()[IMAGENET_MODEL](include_top=True, weights='imagenet')
        base_model2 = vars()[IMAGENET_MODEL](include_top=False, weights='imagenet')
        top_layer0 = 1 + len(base_model2.layers) - len(base_model.layers) # negative
        classify_model = Model(inputs=base_model.layers[top_layer0], outputs=base_model.output)
        ## TODO: make Input
    else:
    '''


'''
#if do_classify:
#    class_model = vars()[IMAGENET_MODEL](include_top=True, weights='imagenet')
'''

print('-- loaded ' + TAG + ',  starting on ' + jpg_dir)

created_dir = False

if vec_dir != None and not os.path.exists(vec_dir):

    print('Creating ' + vec_dir)
    os.mkdir(vec_dir)
    created_dir = True

if class_dir != None and not os.path.exists(class_dir):

    print('Creating ' + class_dir)
    os.mkdir(class_dir)
    created_dir = True

if phobrain_archives:

    handle_phobrain_archives()

else:

    handle_recursive()


#os.system('funwait.sh')

