#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === isize.sh - get image dims using ImageMagick convert.


if [ -d $1 ] ; then

  echo ====== $1

  \ls $1 | while read i ; do 
    if [ -d $1/$i ] ; then
      echo skipping subdir: $1/$i
      continue
    fi

    if [[ "$i" == *".jpg" ]] || [[ "$i" == *".JPG" ]] ; then
      "$0" ${1%/}/$i
    fi
  done
  exit 0
else
    #echo === $1

    if [[ "$1" != *".jpg" ]] && [[ "$1" != *".JPG" ]] ; then
        echo skipping non-jpg: $1
    else
        DIM=`convert $1 -print "%w %h\n" /dev/null`
        WIDTH=`echo $DIM | awk '{print $1}'`
        HEIGHT=`echo $DIM | awk '{print $2}'`
        AREA=$(($WIDTH*$HEIGHT))
        echo $1 ${WIDTH} $HEIGHT $AREA
    fi
fi
