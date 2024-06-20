#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === s5_ml_pair_vecs.sh
#
#        If there are models trained with user-labeled photo pairs,
#           Run model predictions of latent pair vecs from 
#                               ml histos and imagenet vecs.
#           Update pr.picture with vectors.
#
#           Selected models are used for vector matching in pr.picture,
#           defined in PHOBRAIN_LOCAL/picture_vectors.
#           All pair vectors for ml (vs. vector-match) training
#           and prediction are calced by 2_all_ml_pair_vecs.sh.
#
#       For now, runs v models to gen vecs for both v,h

echo "=== $0"
echo "    Regenerating pair vecs used in pr.picture"
echo "    ONLY applies if user has labeled pairs and"
echo "    models have been trained with the data. So:"
echo "      Skipped if no PHOBRAIN_LOCAL/picture_vectors"
echo "      Skipped if no 'pairvecs' entries in picture_vectors"
echo

set -e

PHOBRAIN_LOCAL="`../bin/get_phobrain_local.sh`"

if [ ! -f "$PHOBRAIN_LOCAL/picture_vectors" ] ; then
    echo "== $0 No $PHOBRAIN_LOCAL/picture_vectors defined, skipping SILENTLY!!!!!"
    exit 0
fi

NVECMODELS=`egrep '^pairvecs' "$PHOBRAIN_LOCAL/picture_vectors" | wc -l`

if [ $NVECMODELS == "0" ] ; then
    echo "== $0 No pairvecs defined in $PHOBRAIN_LOCAL/picture_vectors, skipping SILENTLY!!!!!"
    exit 0
fi

MLDIR_V=`../bin/phobrain_property.sh ml.v.dir`

FAIL_V=
if [ ! -d $MLDIR_V ] ; then
    FAIL_V="Not a dir: $MLDIR_V"
fi
# h is placeholder
FAIL_H=
#if [ ! -d $MLDIR_H ] ; then
#    FAIL_H="Not a dir: $MLDIR_H"
#fi

if [[ "$FAIL_V" || "$FAIL_H" ]] ; then
    echo "=== $0 FAIL: $FAIL_V $FAIL_H"
    exit 1
fi

PICKLE_CMD="../bin/pickle_training_data.py"
PICKLE_ARG="HIST_JPGV"

echo "=== $0"
echo "    Updating HIST_JPGV_VGG16.dil:"
echo "      Pickling histogram/imagenet data for pair vector generation"
echo "      with $ $PICKLE_CMD $PICKLE_ARG"

"$PICKLE_CMD" "$PICKLE_ARG"

echo
echo "=== $0"
echo "    Making $NVECMODELS ML latent 'pair vecs' from V models for image import"
echo "        V in $MLDIR_V"
#echo "        H in $MLDIR_H"
echo "    Tasks:"
egrep '^pairvecs' "$PHOBRAIN_LOCAL/picture_vectors" | awk '{print $2}' | while read i ; do
    echo "      REMAKE $i"
done
echo
echo "--- starting in 5"

sleep 5

START=`date`

( cd $MLDIR_V ; ./0_predict_list_vecs.sh )

echo "== $0"
echo "   Updating all(todo:only new/changed) pair vecs"
echo "   in pr.picture with pair vecs"

( cd update ; gradle fatJar ; ./run.pairvecs --archives $* )

echo "==== $0  DONE"
echo "START $START"
echo "END   `date`"
