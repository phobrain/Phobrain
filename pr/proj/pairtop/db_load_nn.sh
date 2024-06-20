#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2024 Bill Ross <phobrain@sonic.net>
#

if [ $# != 1 ] ; then
    echo "Usage: $0 <v|h>"
    exit 1
fi
if [ "$1" != "v" -a "$1" != "h" ] ; then
    echo "Usage: $0 <v|h>"
    exit 1
fi

DUMP=pr.pairtop_nn_${1}_dump.sql

echo Main table:
du -sh $DUMP
echo "Load starting in 5.."

sleep 5

psql -d pr -f $DUMP
