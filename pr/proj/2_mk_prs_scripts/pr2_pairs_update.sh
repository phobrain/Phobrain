#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: AGPL-3.0-or-later
#

# TODO: size up v,h needs & memory to determine
TOO_BIG=1

START=`date`

echo "=== $0"

set -e

cd pairs
gradle fatJar

if [ $TOO_BIG ] ; then
    echo "-- $0 'too big', so running h first"
    ./run.h
    echo "-- $0 loading pr.pairs_h_dump.sql in parallel with v gen"
    ( ./db_load_pairs.sh h )&
else
    echo "-- $0 running run.h and loading pr.pairs_h_dump.sql in parallel with v gen"
    (./run.h ; echo ======== $0 Loading pr.pairs_h_dump.sql_body/tail ; ./db_load_pairs.sh h )&
fi

echo "-- $0"
echo "   run.v"

./run.v 

echo "======== $0"
echo "         Loading pr.pairs_v_dump.sql_body"

./db_load_pairs.sh v

wait

echo "======== $0"
echo "         Making angles pairtops"

cd ../histogram
gradle fatJar

./run_pairtop_angles_only_tbl.sh v
psql -d pr -f pr.pairtop_col_v_dump.sql

./run_pairtop_angles_only_tbl.sh h
psql -d pr -f pr.pairtop_col_h_dump.sql

echo "======== $0"
echo "         Running update"

cd ../update
gradle fatJar

./run.color
./run.d0.v &
./run.d0.h

wait

echo "=== $0"
echo "    START $START"
echo "    END   `date`"

