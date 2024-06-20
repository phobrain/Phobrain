#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === sizeDown.sh - use ImageMagick convert to make version
#       of pic that fits size, converting to sRGB color space.

# SETUP - choose colorspace

COLORSPACE=sRGB_v4_ICC_preference.icc

###

SCRIPTPATH="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"

COLORPATH="$SCRIPTPATH"/"$COLORSPACE"

if [ ! -f $1 ] ; then
    echo not a file
    exit 1
fi

BASE=`basename $1`
arrBASE=(${BASE//./ })
#echo $BASE ${arrBASE[0]}

TARGET=${arrBASE[0]}_sm.jpg

if [ -e $TARGET ] ; then
    if [ $1 -nt $TARGET ] ; then
        echo Base $1 is newer - redoing $TARGET
        rm -f $TARGET
    else
        echo $TARGET exists - skipping
        exit 0
    fi
fi

PROFILE=
function setProfile {
    if [ ! -f "$COLORPATH" ] ; then
        echo Missing colorspace file: $COLORPATH
        exit 1
    fi
    PROFILE="-profile $COLORPATH"
}

case $TARGET in 
    _*) setProfile ;;
    DSC*) setProfile ;;
esac

DIM=`"$SCRIPTPATH"/isize.sh $1 | awk '{print $2,$3}'`

#echo $DIM
arrDIM=(${DIM// / })
WIDTH=${arrDIM[0]}
HEIGHT=${arrDIM[1]}
echo width $WIDTH height $HEIGHT

##################
# resize opts
#    https://legacy.imagemagick.org/Usage/resize/

RESIZE_SIZE="-resize 1000x1000"

SIZESPACE=
SIZESPACE="-colorspace LUV"

SHARP=
SHARP="-unsharp 1.5x1+0.7+0.02"


RESIZE_OPTS=
if [ "$WIDTH" -gt 1000 ] || [ "$HEIGHT" -gt 1000 ] ; then
    RESIZE_OPTS="$SIZESPACE $RESIZE_SIZE $SHARP"
fi

TMP_JPG="x.jpg"

echo "== in `pwd`"
CMD="convert $1 $RESIZE_OPTS $TMP_JPG"
echo $CMD
$CMD

CMD="convert $TMP_JPG $PROFILE $TARGET"
echo $CMD
$CMD

/bin/rm $TMP_JPG

