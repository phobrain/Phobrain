#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === dump_tbl.sh - convenience pg_dump script.

# Official version in pr/db

if [ $# != 2 ] ; then
	echo Usage: "$0 <tbl_name> <dir>"
	exit 1
fi

OUT="$2"/"$1"_dump.sql

echo dumping to $OUT

pg_dump -c --if-exists -W -d pr -t $1 > $OUT
