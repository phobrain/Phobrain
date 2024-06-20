#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# === 2_mk_prs.sh - If user has labeled pairs yes/no and
#                   trained models to predict personal
#                   preferences, run pairwise ML predictions 
#                   with models trained on the labeled pairs
#                   using imagenet vecs and histograms. 
#                   Latent vectors from these models are 
#                   combined and used to train further models,
#                   so first .vecs are predicted, then these
#                   are used to predict with the 'final' models..
#
#                   1. calc all the pairwise (l,r) latent 
#                      vectors with the 'level 0' models
#                      w/ the imagenet vecs and histograms.
#                      (Selected pairwise latent vectors are 
#                      also used for vector matching, e.g. 
#                      pr.picture.left_2 pr.picture.right_12.)
#
#                   2. predict pairwise values using the
#                      latent vecs on the 'level 1' models.
#                      Averaging these predictions gives
#                      pr.pairs.d0 values. Calcing top per-pic
#                      pairs from individual and averaged models'
#                      predictions is used for pr.pairtop files.
#
# Note: histogram-based pairtop files were replaced by vector
#       matching (pgvector). Adding distance functions to
#       pgvector would be the best way I see to continue 
#       exploring that.     (2024/2)

echo "=== $0"
echo "    Predicting pairs with user-trained models"

if [ $# != 0 ]; then
    echo "Usage: $0"
    echo "(does all pairs)"
    exit 1
fi

PHOBRAIN_LOCAL="`../bin/get_phobrain_local.sh`"

if [ ! -f "$PHOBRAIN_LOCAL/picture_vectors" ] ; then
    echo "== $0"
    echo "   No $PHOBRAIN_LOCAL/picture_vectors defined, skipping SILENTLY!!!!!"
    exit 0
fi

NVECMODELS=`egrep '^pairvecs' "$PHOBRAIN_LOCAL/picture_vectors" | wc -l`

if [ $NVECMODELS == "0" ] ; then
    echo "== $0"
    echo "   No pairvecs defined in $PHOBRAIN_LOCAL/picture_vectors, skipping SILENTLY!!!!!"
    exit 0
fi

set -e

# run first-level ML predictions of latent vecs 
#                       from ml histos and imagenet vecs
#
#       For now, runs v models to gen vecs for both v,h
#
# TODO redoes 4 .vecs just done by 1_mk_arch.sh for
#               update/import to pr.picture table.

MLDIR_V=`../bin/phobrain_property.sh ml.v.dir`

echo
echo "=== $0"
echo "    Predicting pair vecs from imagenet vectors and histograms"
echo "    making ML latent vecs from V models"
echo "    V in $MLDIR_V"
#echo "    H in $MLDIR_H"

FAIL_V=
if [ ! -d $MLDIR_V ] ; then
    FAIL_V="Not a dir: $MLDIR_V"
fi
FAIL_H=
#if [ ! -d $MLDIR_H ] ; then
#    FAIL_H="Not a dir: $MLDIR_H"
#fi

if [ "$FAIL_V" -o "$FAIL_H" ] ; then
    echo "=== $0"
    echo "    FAIL: $FAIL_V $FAIL_H"
    exit 1
fi

do_the_mldir() {

    ORIENT=$1
    MLDIR=$2

    START=`date`

    echo "=== $0"
    echo "    $ORIENT predicting ML vectors from JPG+histograms in $MLDIR"
    cd $MLDIR
    echo "-- $0"
    echo "   $ORIENT - sleeping 5, then removing all .vecs in $MLDIR"
    sleep 5
    find . -name "*.vecs" | while read i ; do rm $i; done
    echo "=== $0"
    echo "    $ORIENT - predicting vecs in parallel in $MLDIR"
    ./1_predict_all_vecs.sh

    echo "=== $0"
    echo "    $ORIENT all DONE in $MLDIR"
    echo "       START $START"
    echo "       END   `date`"
}

START=`date`

UPDATE_DIR="`pwd`/update"
time {
    do_the_mldir V $MLDIR_V
    echo "== $0"
    echo "   v,h vecs are both from V for now"
    # do_the_mldir H $MLDIR_H
    cd "$UPDATE_DIR" ; gradle fatJar ; ./run.mlvecs
}

echo
echo "==== $0"
echo "     .vecs DONE"
echo "     START $START"
echo "     END   `date`"

echo
echo "=== $0"
echo "    making ML pairs, V (then you copy H.. wip)"
echo "    V in $MLDIR_V"
#echo "    H in $MLDIR_H"

do_the_mldir_2() {

    ORIENT=$1
    MLDIR=$2

    START=`date`

    #echo "=== $0 $ORIENT predicting ML vectors from JPG+histograms in $MLDIR"
    #cd $MLDIR
    #echo "-- $0 $ORIENT - sleeping 5, then removing all .vecs in $MLDIR"
    #sleep 5
    #find . -name "*.vecs" | while read i ; do rm $i; done
    #echo "=== $0 $ORIENT - predicting vecs in parallel in $MLDIR"
    #./1_predict_all_vecs.sh

    cd $MLDIR
    echo
    echo "=== $0"
    echo "    $ORIENT - predicting ML pairs from ML vecs in $MLDIR"
    echo
    echo "    sleep 5, then removing all .pairs and .pairs_bin and .pb in $MLDIR"
    sleep 5
    find . -name "*.pairs" | while read i ; do rm $i ; done
    find . -name "*.pairs_bin" | while read i ; do rm $i ; done
    find . -name "*.pb" | while read i ; do rm $i ; done

    echo
    echo "=== $0"
    echo "    $ORIENT - predicting: running ./2_predict_jh.sh in $MLDIR"
    ./2_predict_all_jh.sh

    echo
    echo "=== $0"
    echo "    $ORIENT - summing in $MLDIR"

    ./3_sum_by_orient.sh .

    echo
    echo "=== $0"
    echo "    $ORIENT - all DONE in $MLDIR"
    echo "       START $START"
    echo "       END   `date`"
}

START=`date`

time {
    do_the_mldir_2 V $MLDIR_V
    # do_the_mldir_2 H $MLDIR_H
}

echo
echo "=== $0"
echo "    do/copy h to $MLDIR_H"

# make pairwise dump.sql files
#      from histogram distance and ml

echo "=== $0"
echo "    Running pairs scripts serially"

#START=`date`

time {

    2_mk_prs_scripts/pr2_pairs_update.sh
    2_mk_prs_scripts/pr2_pairtop_nn.sh

}

echo "=== $0"
echo "    START $START"
echo "    END   `date`"

if [ -x "$(command -v funwait.sh)" ] ; then
    funwait.sh
fi
