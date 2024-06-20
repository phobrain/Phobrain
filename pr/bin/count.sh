#!/usr/bin/env bash
#
#  SPDX-FileCopyrightText: 2023 Bill Ross <phobrain@sonic.net>
#
#  SPDX-License-Identifier: MIT-0
#

# === count.sh - count files/lines in .java files under current dir.

/bin/rm -f /tmp/x.$$

CT=0

find . -name "*.java" > /tmp/x.$$
F=`wc -l /tmp/x.$$ | awk '{print $1}'`

/bin/rm -f /tmp/y.$$
touch /tmp/y.$$

while read -r i ; do
 cat $i >> /tmp/y.$$
done < /tmp/x.$$

L=`wc -l /tmp/y.$$ | awk '{print $1}'`

awk 'NF > 0' /tmp/y.$$ > /tmp/z.$$
C=`wc -l /tmp/z.$$ | awk '{print $1}'`

#rm /tmp/x.$$ /tmp/y.$$ /tmp/z.$$

echo Files: $F All lines: $L Non-empty: $C
