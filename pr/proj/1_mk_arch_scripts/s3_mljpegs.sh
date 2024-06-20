#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# == arg is id# of archive

set -e

if [ $# != 1 ]; then
    echo "Usage: $0 <archnum>"
    exit 1
fi

START=`date`

# run from proj/

IMAGE_DIR=`../bin/phobrain_property.sh image.dir`
IMAGE_DESC_DIR=`../bin/phobrain_property.sh image.desc.dir`

PRUNES="prun224 prun331"

echo $PRUNES | sed -e 's: :\n:g' | while read prun ; do

    PRUNEDIR=$IMAGE_DESC_DIR/3_jpg/$prun

    echo "=== $0"
    echo "    Creating $PRUNEDIR/$1"

    cd $PRUNEDIR

    # mk.sh issues warning/sleeps if necc

    ./mk.sh $1 &

done

echo $PRUNES | sed -e 's: :\n:g' | while read prun ; do
    wait
done

cd $IMAGE_DESC_DIR/3_jpg/

MODELS="VGG16 DenseNet121 NASNetLarge"

echo
echo "=== $0"
echo "    in `pwd`"
echo "    Calcing archive $1 imagenet vectors for: [$MODELS]"
echo

# gpu imagenet_vec.py needs to be serial for memory

echo $MODELS | sed -e 's: :\n:g' | while read model ; do
    echo "=== $0"
    echo "    imagenet_vec.py $model $1"
    imagenet_vec.py $model $1
done

echo
echo "=== $0"
echo "    in `pwd`"
echo "    Summing archive $1 imagenet blocks"
echo "      in parallel for: [$MODELS]"
echo

sum_blocks.py 512 ./prun224/vecs_VGG16 $1 &
sum_blocks.py 1024 ./prun224/vecs_DenseNet121 $1 &
sum_blocks.py 1280 ./prun224/vecs_MobileNetV2 $1 &
sum_blocks.py 4032 ./prun331/vecs_NASNetLarge $1 &

wait ; wait ; wait ; wait

echo
echo
echo "== $0 done"
echo "START $START"
echo "END   `date`"
echo
echo
