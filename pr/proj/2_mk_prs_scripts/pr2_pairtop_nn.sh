#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

START=`date`

echo "=== $0"

set -e

cd pairtop

gradle fatJar

# only v - arbitrary

./run.nn.v

echo === $0 Loading pr.pairtop_nn_v_dump.sql

psql -d pr -f pr.pairtop_nn_v_dump.sql

echo "=== $0"
echo "    START $START"
echo "    END   `date`"

