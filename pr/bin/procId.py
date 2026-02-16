#!/usr/bin/env python3
# /usr/bin/env works with anaconda
#
#  SPDX-FileCopyrightText: 2025 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#
# procId -- extract :xx part of id from dilename
#           for Phobrain archives
#            - arbitrary/historical

import sys
import os

#print(str(sys.argv))
#exit

if len(sys.argv) != 2:
    print('Usage(n): ' + sys.argv[0] + ' <file>')
    exit(1)

def fileId(jpg):

    #print('fileId: ' + jpg)

    f = os.path.basename(jpg)

    tag = f.replace('.hist','').replace('.jpg','')
    tag = tag.replace('img', '').replace('IMG', '').replace('_MG', '')
    tag = tag.replace('DSC', '')
    tag = tag.replace('DK7A', '').replace('_K7A', '')
    tag = tag.replace('_sm', '').replace('_srgb', '')
    tag = tag.lstrip('_').lstrip('0')

    return tag

if os.path.isdir(sys.argv[1]):
    if not sys.argv[1].isdigit():
        print('-- just doing int dirs for archives')
        exit(1)
    archive = sys.argv[1]
    #print('-- archive ' + archive)
    files = os.listdir(archive)
    for f in files:
        #print('--- ' + f)
        if f.endswith('.jpg'):
            print(archive + ':' + fileId(f))

else:
    if not sys.argv[1].endswith('.jpg'):
        print('-- Expected number dir or .jpg')
        exit(1)

    print(fileId(sys.argv[1]))
