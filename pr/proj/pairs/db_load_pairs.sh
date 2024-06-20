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

echo Main table:
du -sh pr.pairs_${1}_dump.sql_body
echo "Load starting in 5.."

sleep 5

psql -d pr -f pr.pairs_${1}_dump.sql_body -f pr.pairs_${1}_dump.sql_tail
