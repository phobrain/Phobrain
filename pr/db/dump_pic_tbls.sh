#!/bin/bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === dmp_for_upload.sh - pg_dump picture tables.

for i in picture picture_prop keywords ; do
  echo $i
  #pg_dump -U pr -d pr -h 127.0.0.1  -t $i > ${i}_dump.sql
  pg_dump -d pr -t pr.$i > pr.${i}_dump.sql
done
