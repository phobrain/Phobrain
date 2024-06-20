#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === get_pic_times.py <-list|-dict> <dir>
#       Make json for jpegs in dir by date taken==exif:DateTime 

OUTPUT = 'pics_sorted_by_date.js'

import sys
import os
from datetime import datetime
from PIL import Image, ExifTags
import json



top = '.'
OPT = '-list'
if len(sys.argv) == 1:
    # print('Using .')
    top = '.'
elif len(sys.argv) == 2:
    top = sys.argv[1]
elif len(sys.argv) == 3:
    OPT = sys.argv[1]
    top = sys.argv[2]
else:
    print('Usage: figger it out yerself')
    exit(1)

if OPT != '-list' and OPT != '-dict':
    print('usage: ' + sys.argv[0] + '[-list|-dict] <dir>')
    exit(1)

if OPT == '-dict':
    OUTPUT = 'pics_dict_date.js'

#print('Top ' + top)

OUTPUT = top + '/' + OUTPUT

DATETIME_TAG = 'DateTime'
DATETIME_ID = 306

CAMERAMODEL_TAG = 'Model'
CAMERAMODEL_ID = 272

# not in my pics
LENSMODEL_TAG = 'LensSpecification'
LENSMODEL_ID =  42034

# check tags

if ExifTags.TAGS[DATETIME_ID] != DATETIME_TAG:
    print('Error: expected exiftags[' + str(DATETIME_ID) + '] to be ' + DATETIME_TAG)
    print('exiftags[' + str(DATETIME_ID) + '] is ' + str(ExifTags.TAGS[DATETIME_ID]))
    exit(1)

if ExifTags.TAGS[CAMERAMODEL_ID] != CAMERAMODEL_TAG:
    print('Error: expected exiftags[' + str(CAMERAMODEL_ID) + '] to be ' + CAMERAMODEL_TAG)
    print('exiftags[' + str(CAMERAMODEL_ID) + '] is ' + str(ExifTags.TAGS[CAMERAMODEL_ID]))
    exit(1)

if ExifTags.TAGS[LENSMODEL_ID] != LENSMODEL_TAG:
    print('Error: expected exiftags[' + str(LENSMODEL_ID) + '] to be ' + LENSMODEL_TAG)
    print('exiftags[' + str(LENSMODEL_ID) + '] is ' + str(ExifTags.TAGS[LENSMODEL_ID]))
    exit(1)

def getExif(path):

    img = Image.open(path)
    img_exif = img.getexif()
    #print(type(img_exif))
    if img_exif is None:
        print('No exif data: ' + path)
    return img_exif

def getExifInfo(ex, id):

    i = ex.get(id)

    if i is None:
        print('No exif.xxx in ' + str(ex) + '\nExiting. Now.')
        import time
        time.sleep(2)

    return i

def getExifTimestamp(ex):

    d = ex.get(DATETIME_ID)

    if d is None:
        print('No exif.DateTime in ' + str(ex) + '\nExiting. Now.')
        import time
        time.sleep(2)
        exit(1)
    # "2008:11:15 19:36:24"
    dt = datetime.strptime(d, '%Y:%m:%d %H:%M:%S')
    #print(ex + ' -> ' + str(dt))
    ts = datetime.timestamp(dt)
    #if '33/_K7A6946' in path:
    #    print('path ' + path + ' str: ' + str(ex) + ' ts ' + str(ts))
    return ts

#ex = '_K7A3750.JPG'
#x = getExifDate(ex)
#print('got ' + str(x))


def getPicsList(top):

    print('getPicsList: counting .jpg in tree: ' + top)
    i = 0
    for dirpath, dirnames, fnames in os.walk(top):
        for f in fnames:
            if f.endswith('jpg'):
                i += 1

    if i == 0:
        print('No .jpg in ' + top)
        return None

    print('Processing ' + str(i) + ' .jpg,  progress: . == 500')

    pics = []

    for dirpath, dirnames, fnames in os.walk(top):
        for f in fnames:
            if f.endswith('jpg'):

                path = dirpath + '/' + f
                ex = getExif(path)
                ts = getExifTimestamp(ex)
                cam = getExifInfo(ex, CAMERAMODEL_ID)
                #lens = getExifInfo(ex, LENSMODEL_ID)
                #print('-- ' + path + ' ' + str(dt))
                #quit()
                pics.append((ts, path, cam))
                if len(pics) % 500 == 0:
                    print('.', end='', flush=True)
                    if len(pics) % 35000 == 0:
                        print('')

    print('\nPics found in ' + top + ': ' + str(len(pics)))

    #print('Sorting by timestamp..')
    l = sorted(pics, key=lambda t: (t[0], t[1]))
    #print('Done - start/end')
    #print(str(l[0]))
    #print(str(l[len(l)-1]))

    # keep ts
    return l

    '''
    pics = []
    for ts, path in l:
        pics.append(path)
    return pics
    '''

def getPicsDict(top):

    print('getPicsDict: finding .jpg in ' + top)
    i = 0
    for dirpath, dirnames, fnames in os.walk(top):
        for f in fnames:
            if f.endswith('jpg'):
                i += 1
    if i == 0:
        print('No .jpg in ' + top)
        exit(1)

    print('Processing ' + str(i) + ' .jpg in ' + top + ',  . == 500')

    pics = {}

    for dirpath, dirnames, fnames in os.walk(top):
        for f in fnames:
            if f.endswith('jpg'):
                path = dirpath + '/' + f
                ex = getExif(path)
                ts = getExifTimestamp(ex)
                cam = getExifInfo(ex, CAMERAMODEL_ID)
                #lens = getExifInfo(ex, LENSMODEL_ID)
                #print('-- ' + path + ' ' + str(ts))
                #quit()
                # list of pics at time
                if ts not in pics:
                    l = []
                    pics[ts] = l
                else:
                    l = pics[ts]
                l.append((path, cam))
                if len(pics) % 500 == 0:
                    print('.', end='', flush=True)
                    if len(pics) % 35000 == 0:
                        print('')

    print('\nPics found in ' + top + ': ' + str(len(pics)))
    #print('Sorting by date..')
    #l = sorted(pics, key=lambda t: t[0], t[1])
    #print('Done - start/end')
    #print(str(l[0]))
    #print(str(l[len(l)-1]))

    # keep dt
    return pics

if OPT == '-list':
    ret = getPicsList(top)
elif OPT == '-dict':
    ret = getPicsDict(top)
    if ret is not None:
        for k in sorted(ret.keys()):
            print('k,v ' + str(k) + ',' + str(ret[k]))
            print('TODO..')
            exit(1)
else:
    print('Unknown option: ' + OPT)
    exit(1)

if ret is None:
    print('quitting')
    exit(1)

json_object = json.dumps(ret, indent = 1)

with open(OUTPUT, 'w') as fp:
    fp.write(json_object)

if OPT == '-list':
    print('Json [date, file, camera] list sorted by date in file: ' + OUTPUT)
else:
    print('Json [date1: [file1,camera1], date2: [file2,camera2], ...] dict in file: ' + OUTPUT)
