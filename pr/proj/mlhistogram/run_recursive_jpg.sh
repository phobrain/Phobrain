#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: CC-BY-SA-4.0
#

# run_recursive_jpg.sh - put .hist files alongside .jpg in a tree.
#       No phobrain import done.

set -e

echo "=== $0 making ML histograms for jpg in $1 recursively"

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

START=`date`

time java -Xmx64g -jar \
    $SCRIPT_DIR/build/libs/mlhistogram-all-1.0.jar $1

echo "== $0   DONE"

echo "START: " $START
echo "END:   " `date`

