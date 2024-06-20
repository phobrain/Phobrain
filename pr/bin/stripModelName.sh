#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === stripModelName.sh - transmute model file names.

PREFIX="   "

strip() {

    STR=`echo $1 | sed -e \
            's:m_::g' -e \
            's:model_::g' -e \
            's:ki_0_Hmx_g128_s128_r12_1984_VGG16_224v:JvH:g' -e \
            's:_nathan::g'`
    echo "${PREFIX} $STR"
}

if [ -d $1 ] ; then
    echo "$PREFIX DIR $1"
    \ls $1/ | grep h5 | while read i ; do
        strip $i
    done
else
    strip $1
fi
