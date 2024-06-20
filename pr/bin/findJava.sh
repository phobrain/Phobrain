#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === findJava.sh - find a string in all java files under $1.

if [ "$#" -ne 2 ] ; then
  echo usage $0 dir string $#
  exit 1
fi

find $1 -name "*.java" | while read i ; do
  echo %%% $i
  grep $2 $i 
done
