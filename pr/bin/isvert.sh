#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === isvert.sh - print 'fname <t|f>' using ImageMagick dims.

if [ -d $1 ] ; then
  echo ====== $1
  \ls $1 | while read i ; do 
    if [ -d $1/$i ] ; then
      echo skipping dir: $1/$i
    else
      ~/bin/isize.sh $1/$i
    fi
  done
  exit 0
fi

DIM=`convert $1 -print "%w %h\n" /dev/null`
WIDTH=`echo $DIM | awk '{print $1}'`
HEIGHT=`echo $DIM | awk '{print $2}'`
isvert="t"
if [ $WIDTH -gt $HEIGHT ] ; then
    isvert="f"
fi
echo $1 ${isvert}
